package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository;
import com.nori.ai.gateway.persistence.repository.*;
import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.time.*;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetentionBoundaryIT extends PgIntegrationSupport {
    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00.123456Z");
    private static final Duration CONVERSATION_TTL = Duration.ofDays(30);
    private static final Duration AUDIT_TTL = Duration.ofDays(90);
    private PgTestDatabase database;
    private ConfigurableApplicationContext context;
    private MutableClock clock;

    static class MutableClock extends Clock {
        Instant now = BASE;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    static class TestClock { @Bean MutableClock testClock() { return new MutableClock(); } }

    @BeforeAll void prepare() {
        database = rawDatabase();
        database.bootstrap(false);
        database.migrate();
        context = application(database, TestClock.class);
        clock = bean(MutableClock.class);
        assertThat(bean(JdbcClient.class).sql("SELECT current_user").query(String.class).single()).isEqualTo("aigw_app");
    }
    @AfterAll void release() {
        try { if (context != null) context.close(); }
        finally { if (database != null) database.close(); }
    }
    @BeforeEach void reset() throws Exception {
        clock.now = BASE;
        try (var connection = database.connection("postgres"); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE gw.conversation, gw.audit_event CASCADE");
        }
    }

    @Test void cacheInsertAndConflictUpdateClampToParentWhilePreservingShorterTtlAndPdg() {
        var conversations = bean(ConversationRepository.class);
        var cache = bean(CacheRepository.class);
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        assertThat(conversations.insert(owner, "conversation")).isTrue();
        Instant parentExpiry = conversations.find(owner, "conversation").orElseThrow().expiresAt();
        assertThat(cache.put(owner, "conversation", "key", entry(parentExpiry.plus(Duration.ofDays(1))))).isTrue();
        assertCacheExpiry(owner, "key", parentExpiry);
        Instant shortExpiry = BASE.plusSeconds(10);
        assertThat(cache.put(owner, "conversation", "key", entry(shortExpiry))).isTrue();
        assertCacheExpiry(owner, "key", shortExpiry);
        assertThat(cache.put(owner, "conversation", "key", entry(parentExpiry.plusSeconds(1)))).isTrue();
        assertCacheExpiry(owner, "key", parentExpiry);
        assertThat(cache.put(owner, "conversation", "short-insert", entry(shortExpiry))).isTrue();
        assertCacheExpiry(owner, "short-insert", shortExpiry);

        var changedPdg = scope(OwnerSystem.TC, "actor", "new-pdg");
        assertThat(cache.find(changedPdg, "conversation", "key")).isEmpty();
        assertThat(cache.put(changedPdg, "conversation", "key", entry(shortExpiry))).isTrue();
        assertCacheExpiry(changedPdg, "key", shortExpiry);
        assertCacheExpiry(owner, "key", parentExpiry);
        for (var denied : new VerifiedSystemScope[] {scope(OwnerSystem.WM, "actor", "pdg"), scope(OwnerSystem.TC, "other", "pdg")}) {
            assertThat(cache.find(denied, "conversation", "key")).isEmpty();
            assertThat(cache.put(denied, "conversation", "key", entry(shortExpiry))).isFalse();
        }
        assertThat(cache.put(owner, "missing", "key", entry(shortExpiry))).isFalse();
        assertThat(cache.put(owner, "conversation", "key", entry(BASE))).isFalse();
        assertCacheExpiry(owner, "key", parentExpiry);
        assertThat(bean(JdbcClient.class).sql("SELECT count(*) FROM gw.cache_entry WHERE expires_at > "
            + "(SELECT expires_at FROM gw.conversation WHERE id = 'conversation')").query(Long.class).single()).isZero();
        clock.now = shortExpiry;
        assertThat(cache.find(changedPdg, "conversation", "key")).isEmpty();
        assertThat(cache.find(owner, "conversation", "key")).isPresent();
        clock.now = parentExpiry;
        assertThat(cache.find(owner, "conversation", "key")).isEmpty();
        assertThat(cache.put(owner, "conversation", "key", entry(parentExpiry.plusSeconds(1)))).isFalse();
    }

    @Test void cacheInsertAndConflictUpdateNeverRoundRequestedExpiryIntoTheFuture() {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var cache = bean(CacheRepository.class);
        assertThat(bean(ConversationRepository.class).insert(owner, "conversation")).isTrue();
        for (long nanos : new long[] {499, 500, 999}) {
            String key = "nano-key-" + nanos;
            for (int write = 0; write < 2; write++) {
                clock.now = BASE;
                Instant microsecondExpiry = BASE.plusSeconds(10L + write * 10L);
                Instant requestedExpiry = microsecondExpiry.plusNanos(nanos);
                // 같은 키의 두 번째 쓰기는 실제 ON CONFLICT 갱신 경로를 검증합니다.
                assertThat(cache.put(owner, "conversation", key, entry(requestedExpiry))).isTrue();
                Instant storedExpiry = bean(JdbcClient.class).sql("SELECT expires_at FROM gw.cache_entry "
                    + "WHERE key_digest = :key AND owner_system = 'TC' AND actor_ref = 'actor' AND pdg = 'pdg'")
                    .param("key", key).query((rs, row) -> rs.getTimestamp(1).toInstant()).single();
                assertThat(storedExpiry).isBeforeOrEqualTo(requestedExpiry).isEqualTo(microsecondExpiry);
                clock.now = storedExpiry.minusNanos(1);
                assertCacheExpiry(owner, key, storedExpiry);
                clock.now = storedExpiry;
                assertThat(cache.find(owner, "conversation", key)).isEmpty();
                clock.now = requestedExpiry;
                assertThat(cache.find(owner, "conversation", key)).isEmpty();
            }
        }
    }

    @ParameterizedTest @ValueSource(longs = {-1000, -1, 0, 1, 1000})
    void conversationReadsWritesAndPurgeRespectStoredExpiryToTheNanosecond(long offsetNanos) {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var conversations = bean(ConversationRepository.class);
        assertThat(conversations.insert(owner, "conversation")).isTrue();
        Instant expiry = conversations.find(owner, "conversation").orElseThrow().expiresAt();
        assertThat(bean(CacheRepository.class).put(owner, "conversation", "key", entry(expiry))).isTrue();
        assertThat(bean(MessageRepository.class).insert(owner, "conversation", message("message", null))).isTrue();
        clock.now = expiry.plusNanos(offsetNanos);
        boolean live = offsetNanos < 0;
        assertThat(conversations.find(owner, "conversation").isPresent()).isEqualTo(live);
        assertThat(conversations.touch(owner, "conversation")).isEqualTo(live);
        assertThat(conversations.saveContext(owner, "conversation", "{}")).isEqualTo(live);
        assertThat(bean(MessageRepository.class).findByConversation(owner, "conversation")).hasSize(live ? 1 : 0);
        assertThat(bean(CacheRepository.class).find(owner, "conversation", "key").isPresent()).isEqualTo(live);
        assertThat(bean(RetentionMaintenanceRepository.class).purgeExpiredConversations()).isEqualTo(live ? 0 : 1);
        assertThat(bean(JdbcClient.class).sql("SELECT count(*) FROM gw.conversation").query(Long.class).single()).isEqualTo(live ? 1 : 0);
        if (live) {
            // 경계 직전 정리가 실제로 행을 보존했는지 확인한 뒤 정확한 만료 시각으로 전진합니다.
            clock.now = expiry;
            assertThat(conversations.find(owner, "conversation")).isEmpty();
            assertThat(bean(RetentionMaintenanceRepository.class).purgeExpiredConversations()).isEqualTo(1);
        }
    }

    @ParameterizedTest @ValueSource(longs = {-1000, -1, 0, 1, 1000})
    void unlinkedAuditReadsAndPurgeRespectItsOwnNinetyDayBoundary(long offsetNanos) {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var audit = bean(AuditRepository.class);
        assertThat(audit.insert(owner, event("unlinked", null, null))).isTrue();
        Instant recordedAt = bean(JdbcClient.class).sql("SELECT recorded_at FROM gw.audit_event WHERE request_id = 'unlinked'")
            .query((rs, row) -> rs.getTimestamp(1).toInstant()).single();
        clock.now = recordedAt.plus(AUDIT_TTL).plusNanos(offsetNanos);
        boolean live = offsetNanos < 0;
        assertThat(audit.findByRequest(owner, "unlinked")).hasSize(live ? 1 : 0);
        assertThat(audit.findByRequest(scope(OwnerSystem.WM, "actor", "pdg"), "unlinked")).isEmpty();
        assertThat(audit.findByRequest(scope(OwnerSystem.TC, "other", "pdg"), "unlinked")).isEmpty();
        assertThat(bean(RetentionMaintenanceRepository.class).purgeExpiredAudit()).isEqualTo(live ? 0 : 1);
        assertThat(bean(JdbcClient.class).sql("SELECT count(*) FROM gw.audit_event").query(Long.class).single()).isEqualTo(live ? 1 : 0);
        if (live) {
            assertThat(audit.findByRequest(owner, "unlinked")).hasSize(1);
            clock.now = recordedAt.plus(AUDIT_TTL);
            assertThat(audit.findByRequest(owner, "unlinked")).isEmpty();
            assertThat(bean(RetentionMaintenanceRepository.class).purgeExpiredAudit()).isEqualTo(1);
        }
    }

    @Test void auditSurvivesConversationExpiryAndPurgeUntilItsOwnExpiry() {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var conversations = bean(ConversationRepository.class);
        var audit = bean(AuditRepository.class);
        var retention = bean(RetentionMaintenanceRepository.class);
        assertThat(conversations.insert(owner, "conversation")).isTrue();
        assertThat(bean(MessageRepository.class).insert(owner,"conversation",message("message",null))).isTrue();
        assertThat(bean(JobRepository.class).insert(owner, new JobRepository.NewJob("job", "conversation", null, "digest", "B", "message", Map.of()), null)).isTrue();
        assertThat(audit.insert(owner, event("linked", "conversation", "job"))).isTrue();
        clock.now = BASE.plus(CONVERSATION_TTL);
        assertThat(conversations.find(owner, "conversation")).isEmpty();
        assertThat(audit.insert(owner, event("edge", "conversation", "job"))).isTrue();
        clock.now = BASE.plus(Duration.ofDays(31));
        assertThat(audit.findByRequest(owner, "linked")).hasSize(1);
        assertThat(audit.insert(owner, event("expired-parent", "conversation", "job"))).isTrue();
        assertThat(audit.insert(owner, event("unlinked", null, null))).isTrue();
        bean(JdbcClient.class).sql("UPDATE gw.job SET status = 'CANCELLED' WHERE id = 'job'").update();
        assertThat(retention.purgeExpiredConversations()).isEqualTo(1);
        assertThat(bean(JdbcClient.class).sql("SELECT count(*) FROM gw.job").query(Long.class).single()).isZero();
        assertThat(audit.findByRequest(owner, "linked")).hasSize(1);
        assertThat(audit.insert(owner, event("purged-parent", "conversation", "job"))).isFalse();
        clock.now = BASE.plus(AUDIT_TTL).minusNanos(1);
        assertThat(audit.findByRequest(owner, "linked")).hasSize(1);
        assertThat(audit.findByRequest(scope(OwnerSystem.WM, "actor", "pdg"), "linked")).isEmpty();
        assertThat(audit.findByRequest(scope(OwnerSystem.TC, "other", "pdg"), "linked")).isEmpty();
        assertThat(retention.purgeExpiredAudit()).isZero();
        clock.now = BASE.plus(AUDIT_TTL);
        assertThat(audit.findByRequest(owner, "linked")).isEmpty();
        assertThat(retention.purgeExpiredAudit()).isEqualTo(1);
        clock.now = BASE.plus(Duration.ofDays(121)).minusNanos(1);
        assertThat(audit.findByRequest(owner, "unlinked")).hasSize(1);
        assertThat(retention.purgeExpiredAudit()).isEqualTo(1);
        assertThat(audit.findByRequest(owner, "unlinked")).hasSize(1);
        assertThat(audit.findByRequest(owner, "expired-parent")).hasSize(1);
        clock.now = BASE.plus(Duration.ofDays(121));
        assertThat(audit.findByRequest(owner, "unlinked")).isEmpty();
        assertThat(retention.purgeExpiredAudit()).isEqualTo(2);
    }

    @Test void auditInsertRejectsUnknownForeignAndMismatchedParentsEvenAfterExpiry() {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var conversations = bean(ConversationRepository.class);
        var jobs = bean(JobRepository.class);
        var audit = bean(AuditRepository.class);
        for (String id : new String[] {"owned", "owned-other", "foreign-owner", "foreign-actor"}) {
            var actualOwner = id.equals("foreign-owner") ? scope(OwnerSystem.WM, "actor", "pdg")
                : id.equals("foreign-actor") ? scope(OwnerSystem.TC, "other", "pdg") : owner;
            assertThat(conversations.insert(actualOwner, id)).isTrue();
            assertThat(bean(MessageRepository.class).insert(actualOwner,id,message("message-"+id,null))).isTrue();
            assertThat(jobs.insert(actualOwner, new JobRepository.NewJob("job-" + id, id, null, "digest", "B", "message-"+id, Map.of()), null)).isTrue();
        }
        clock.now = BASE.plus(Duration.ofDays(31));
        for (String id : new String[] {"missing", "foreign-owner", "foreign-actor"}) {
            assertThat(audit.insert(owner, event("denied", id, null))).isFalse();
            assertThat(audit.insert(owner, event("denied", null, "job-" + id))).isFalse();
            assertThat(audit.insert(owner, event("denied", "owned", "job-" + id))).isFalse();
            assertThat(audit.insert(owner, event("denied", id, "job-owned"))).isFalse();
        }
        assertThat(audit.insert(owner, event("denied", "owned", "job-owned-other"))).isFalse();
        assertThat(audit.findByRequest(owner, "denied")).isEmpty();
        assertThat(audit.insert(owner, event("owned", "owned", "job-owned"))).isTrue();
        assertThat(audit.insert(owner, event("conversation-only", "owned", null))).isTrue();
        assertThat(audit.insert(owner, event("job-only", null, "job-owned"))).isTrue();
        assertThat(audit.insert(owner, event("unlinked", null, null))).isTrue();
    }

    @Test void stepClockCreationKeepsExactlyThirtyDaysAndJsonTimeWindowsKeepNanoseconds() {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        Clock ticking = new MutableClock() {
            @Override public Instant instant() { Instant value = now; now = now.plusSeconds(1); return value; }
        };
        var conversations = new ConversationRepository(bean(JdbcClient.class), bean(TransactionTemplate.class), ticking);
        assertThat(conversations.insert(owner, "conversation")).isTrue();
        var original = conversations.find(owner, "conversation").orElseThrow();
        assertThat(Duration.between(original.createdAt(), original.expiresAt())).isEqualTo(CONVERSATION_TTL);
        assertThat(conversations.touch(owner, "conversation")).isTrue();
        String from = "2026-10-31T12:00:00.123456789Z";
        String to = "2026-10-31T13:00:00.987654321Z";
        String reference = "2026-11-01T00:00:00.111222333Z";
        String window = "{\"from\":\"" + from + "\",\"to\":\"" + to + "\"}";
        String contextJson = "{\"ownerSystem\":\"TC\",\"timezone\":\"UTC\",\"referenceTime\":\"" + reference + "\",\"timeWindow\":" + window + "}";
        assertThat(conversations.saveContext(owner, "conversation", contextJson)).isTrue();
        assertThat(conversations.findContext(owner, "conversation")).hasValueSatisfying(value -> assertThat(value).contains(from, to, reference));
        assertThat(bean(MessageRepository.class).insert(owner, "conversation", message("message", window))).isTrue();
        assertThat(bean(MessageRepository.class).findByConversation(owner, "conversation")).singleElement()
            .satisfies(value -> assertThat(value.resolvedTimeWindow()).contains(from, to));
        assertThat(conversations.find(owner, "conversation")).hasValueSatisfying(value -> {
            assertThat(value.createdAt()).isEqualTo(original.createdAt());
            assertThat(value.expiresAt()).isEqualTo(original.expiresAt());
        });
    }

    @Test void boundedConversationBatchesCommitIndependentlyAndOnlyTheFailedBatchRollsBack() throws Exception {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var conversations = bean(ConversationRepository.class);
        var retention = bean(RetentionMaintenanceRepository.class);
        for (int i = 0; i < 7; i++) assertThat(conversations.insert(owner, "c" + i)).isTrue();
        assertThat(bean(MessageRepository.class).insert(owner, "c6", message("message", null))).isTrue();
        assertThat(bean(JobRepository.class).insert(owner, new JobRepository.NewJob("active", "c6", null, "digest", "B", "message", Map.of()), null)).isTrue();
        clock.now = BASE.plus(CONVERSATION_TTL);
        assertThatThrownBy(() -> retention.purgeExpiredConversationsBatch(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(retention.purgeExpiredConversationsBatch(2)).isEqualTo(2);
        assertThat(committedCount("gw.conversation")).isEqualTo(5);
        try (var connection = database.connection("postgres"); var statement = connection.createStatement()) {
            statement.execute("CREATE FUNCTION public.p14_reject_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'p14 batch failure'; END $$");
            statement.execute("CREATE TRIGGER p14_reject_delete BEFORE DELETE ON gw.conversation FOR EACH ROW WHEN (OLD.id = 'c4') EXECUTE FUNCTION public.p14_reject_delete()");
        }
        try {
            assertThat(retention.purgeExpiredConversationsBatch(2)).isEqualTo(2);
            assertThat(committedCount("gw.conversation")).isEqualTo(3);
            assertThatThrownBy(() -> retention.purgeExpiredConversationsBatch(2)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            // 실패 batch(c4,c5)만 rollback되고 앞 batch 커밋은 남습니다.
            assertThat(committedCount("gw.conversation")).isEqualTo(3);
        } finally {
            try (var connection = database.connection("postgres"); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER p14_reject_delete ON gw.conversation");
                statement.execute("DROP FUNCTION public.p14_reject_delete()");
            }
        }
        assertThat(retention.purgeExpiredConversations()).isEqualTo(2);
        assertThat(bean(JdbcClient.class).sql("SELECT id FROM gw.conversation").query(String.class).list()).containsExactly("c6");
        bean(JdbcClient.class).sql("UPDATE gw.job SET status = 'CANCELLED' WHERE id = 'active'").update();
        assertThat(retention.purgeExpiredConversations()).isEqualTo(1);
    }

    @Test void concurrentConversationBatchesSkipLockedRowsAndDeleteEachRowOnce() throws Exception {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        for (int i = 0; i < 40; i++) assertThat(bean(ConversationRepository.class).insert(owner, "c" + i)).isTrue();
        clock.now = BASE.plus(CONVERSATION_TTL);
        var retention = bean(RetentionMaintenanceRepository.class);
        var total = new java.util.concurrent.atomic.AtomicInteger();
        var ready = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 4; worker++) futures.add(workers.submit(() -> {
                ready.await();
                for (int deleted; (deleted = retention.purgeExpiredConversationsBatch(3)) > 0;) total.addAndGet(deleted);
                return null;
            }));
            ready.countDown();
            for (var future : futures) future.get(60, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(total.get()).isEqualTo(40);
        assertThat(committedCount("gw.conversation")).isZero();
    }

    @Test void boundedAuditBatchesKeepTheirOwnNinetyDayCutoffAndTheNoArgumentApiRepeatsBeyondTheDefaultLimit() throws Exception {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var audit = bean(AuditRepository.class);
        int old = 2 * RetentionMaintenanceRepository.DEFAULT_PURGE_BATCH_SIZE + 5;
        for (int i = 0; i < old; i++) assertThat(audit.insert(owner, event("old-" + i, null, null))).isTrue();
        clock.now = BASE.plusNanos(1000);
        assertThat(audit.insert(owner, event("later", null, null))).isTrue();
        var retention = bean(RetentionMaintenanceRepository.class);
        clock.now = BASE.plus(AUDIT_TTL);
        assertThat(retention.purgeExpiredAuditBatch(2)).isEqualTo(2);
        assertThat(committedCount("gw.audit_event")).isEqualTo(old - 1);
        // 기본 한도 100보다 많은 남은 203행을 noarg가 여러 batch로 반복해 모두 지우고 합계를 돌려줍니다.
        assertThat(retention.purgeExpiredAudit()).isEqualTo(old - 2);
        assertThat(bean(JdbcClient.class).sql("SELECT request_id FROM gw.audit_event").query(String.class).list()).containsExactly("later");
        assertThatThrownBy(() -> retention.purgeExpiredAuditBatch(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void noArgumentConversationPurgeRepeatsBeyondTheDefaultLimitAndTheOldestRemainingExpiryIsReadExactly() throws Exception {
        var owner = scope(OwnerSystem.TC, "actor", "pdg");
        var conversations = bean(ConversationRepository.class);
        var retention = bean(RetentionMaintenanceRepository.class);
        assertThat(retention.findOldestConversationExpiry()).isEmpty();
        int expired = 2 * RetentionMaintenanceRepository.DEFAULT_PURGE_BATCH_SIZE + 5;
        for (int i = 0; i < expired; i++) assertThat(conversations.insert(owner, "bulk-" + i)).isTrue();
        clock.now = BASE.plusNanos(1000);
        assertThat(conversations.insert(owner, "later")).isTrue();
        assertThat(retention.findOldestConversationExpiry()).contains(BASE.plus(CONVERSATION_TTL));
        clock.now = BASE.plus(CONVERSATION_TTL);
        assertThat(retention.purgeExpiredConversations()).isEqualTo(expired);
        assertThat(committedCount("gw.conversation")).isOne();
        // 남은 가장 오래된 만료는 저장된 microsecond 값 그대로입니다.
        assertThat(retention.findOldestConversationExpiry()).contains(BASE.plusNanos(1000).plus(CONVERSATION_TTL));
    }

    private long committedCount(String table) throws Exception {
        // 다른 연결에서 조회하여 각 batch의 커밋 여부를 확인합니다.
        try (var connection = database.connection("postgres"); var statement = connection.createStatement(); var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next(); return result.getLong(1);
        }
    }
    private void assertCacheExpiry(VerifiedSystemScope owner, String key, Instant expiry) {
        assertThat(bean(CacheRepository.class).find(owner, "conversation", key)).hasValueSatisfying(value -> assertThat(value.expiresAt()).isEqualTo(expiry));
    }
    private <T> T bean(Class<T> type) { return context.getBean(type); }
    private static VerifiedSystemScope scope(OwnerSystem system, String actor, String pdg) {
        return new VerifiedSystemScope(system, "service", new ActorRef(actor), actor, Set.of(), pdg, BASE.minusSeconds(1), BASE.plusSeconds(60), "request");
    }
    private static CacheRepository.Entry entry(Instant expiry) { return new CacheRepository.Entry("tc.logs.search", "{}", "[]", expiry); }
    private static MessageRepository.Message message(String id, String window) { return new MessageRepository.Message(id, "user", "제거된 시험 문구", "[]", "[]", null, window); }
    private static AuditRepository.Event event(String request, String conversation, String job) {
        return new AuditRepository.Event(request, conversation, job, null, "TEST", null, null, "ALLOW", "SUCCESS", null, "{}", "{}", "{}", "{}");
    }
}
