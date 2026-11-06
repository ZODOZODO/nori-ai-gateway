package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.persistence.repository.*;
import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScopedRepositoryIT extends PgIntegrationSupport {
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private PgTestDatabase database;
    private ConfigurableApplicationContext context;
    static class TestClock { @Bean Clock testClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }
    @BeforeAll void prepare() {
        database = rawDatabase(); database.bootstrap(false); database.migrate(); context = application(database, TestClock.class);
        assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
    }
    @AfterAll void release() { try { if (context != null) context.close(); } finally { if (database != null) database.close(); } }
    @BeforeEach void rows() throws Exception {
        try (var connection = database.connection("postgres"); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE gw.conversation, gw.audit_event CASCADE");
        }
        bean(org.springframework.transaction.support.TransactionTemplate.class).executeWithoutResult(status -> {
        for (var entry : Map.of("tc", scope(OwnerSystem.TC,"a"), "wm", scope(OwnerSystem.WM,"a"), "other", scope(OwnerSystem.TC,"b"),
                "expired", scope(OwnerSystem.TC,"a"), "edge", scope(OwnerSystem.TC,"a")).entrySet()) {
            assertThat(bean(ConversationRepository.class).insert(entry.getValue(), entry.getKey())).isTrue();
            String key = Set.of("expired", "edge").contains(entry.getKey()) ? "key-" + entry.getKey() : "key";
            assertThat(bean(JobRepository.class).insert(entry.getValue(), new JobRepository.NewJob("j-" + entry.getKey(), entry.getKey(), key, "digest", "B"), null)).isTrue();
            populate(entry.getValue(), entry.getKey());
        }
        });
        try (var connection = database.connection("postgres"); var statement = connection.prepareStatement(
                "UPDATE gw.conversation SET created_at = ?, expires_at = ? WHERE id = ?")) {
            for (String id : List.of("expired", "edge")) {
                statement.setTimestamp(1, Timestamp.from(NOW.minus(Duration.ofDays(30))));
                statement.setTimestamp(2, Timestamp.from(id.equals("edge") ? NOW : NOW.minusSeconds(1)));
                statement.setString(3, id); statement.executeUpdate();
            }
        }
    }
    private void populate(VerifiedSystemScope scope, String conversation) {
        String job = "j-" + conversation;
        assertThat(bean(MessageRepository.class).insert(scope, conversation, message("m-" + conversation))).isTrue();
        assertThat(bean(JobEventRepository.class).insert(scope, job, "job.queued", "{}", false)).isTrue();
        assertThat(bean(ResultRepository.class).insert(scope, job, result())).isTrue();
        assertThat(bean(EvidenceRepository.class).insert(scope, job, evidence("e-" + conversation))).isTrue();
        assertThat(bean(ToolExecutionRepository.class).insert(scope, job, tool("t-" + conversation))).isTrue();
        assertThat(bean(CacheRepository.class).put(scope, conversation, "cache-" + conversation, cache(NOW.plusSeconds(60)))).isTrue();
        assertThat(bean(AuditRepository.class).insert(scope, audit(conversation))).isTrue();
    }
    static Stream<org.junit.jupiter.params.provider.Arguments> cases() {
        return Stream.of("Conversation", "Message", "Job", "JobEvent", "Result", "Evidence", "ToolExecution", "Cache")
            .flatMap(repository -> Stream.of("tc", "wm", "other", "expired", "edge")
                .map(conversation -> org.junit.jupiter.params.provider.Arguments.of(repository, conversation)));
    }
    @ParameterizedTest(name="{0}: {1}") @MethodSource("cases")
    void everyRepositoryReadAndWriteEnforcesSystemActorAndParentExpiry(String repository, String conversation) {
        var scope = scope(OwnerSystem.TC,"a"); String job = "j-" + conversation; boolean allowed = conversation.equals("tc");
        switch (repository) {
            case "Conversation" -> {
                assertThat(bean(ConversationRepository.class).find(scope, conversation).isPresent()).isEqualTo(allowed);
                assertThat(bean(ConversationRepository.class).touch(scope, conversation)).isEqualTo(allowed);
                assertThat(bean(ConversationRepository.class).saveContext(scope, conversation, "{\"ownerSystem\":\"WM\"}")).isEqualTo(allowed);
                assertThat(bean(ConversationRepository.class).findContext(scope, conversation).isPresent()).isEqualTo(allowed);
                assertThat(bean(ConversationRepository.class).saveSummary(scope, conversation, "{}")).isEqualTo(allowed);
                assertThat(bean(ConversationRepository.class).findSummary(scope, conversation).isPresent()).isEqualTo(allowed);
            }
            case "Message" -> {
                assertThat(bean(MessageRepository.class).findByConversation(scope, conversation).size()).isEqualTo(allowed ? 1 : 0);
                assertThat(bean(MessageRepository.class).insert(scope, conversation, message("new-m"))).isEqualTo(allowed);
            }
            case "Job" -> {
                assertThat(bean(JobRepository.class).find(scope, job).isPresent()).isEqualTo(allowed);
                assertThat(bean(JobRepository.class).insert(scope, new JobRepository.NewJob("new-j", conversation, "new-key", "digest", "B"), null)).isEqualTo(allowed);
                assertThat(bean(JobRepository.class).requestCancel(scope, job, "USER_REQUEST")).isEqualTo(allowed);
            }
            case "JobEvent" -> {
                assertThat(bean(JobEventRepository.class).findAfter(scope, job, 0).size()).isEqualTo(allowed ? 1 : 0);
                assertThat(bean(JobEventRepository.class).insert(scope, job, "job.queued", "{}", false)).isEqualTo(allowed);
            }
            case "Result" -> {
                assertThat(bean(ResultRepository.class).find(scope, job).isPresent()).isEqualTo(allowed);
                String targetJob = allowed ? "result-new" : job;
                if (allowed) bean(JobRepository.class).insert(scope, new JobRepository.NewJob(targetJob, conversation, null, "digest", "B"), null);
                assertThat(bean(ResultRepository.class).insert(scope, targetJob, result())).isEqualTo(allowed);
            }
            case "Evidence" -> {
                assertThat(bean(EvidenceRepository.class).find(scope, job, "e-" + conversation).isPresent()).isEqualTo(allowed);
                assertThat(bean(EvidenceRepository.class).insert(scope, job, evidence("new-e"))).isEqualTo(allowed);
            }
            case "ToolExecution" -> {
                assertThat(bean(ToolExecutionRepository.class).find(scope, job, "t-" + conversation).isPresent()).isEqualTo(allowed);
                assertThat(bean(ToolExecutionRepository.class).insert(scope, job, tool("new-t"))).isEqualTo(allowed);
            }
            case "Cache" -> {
                assertThat(bean(CacheRepository.class).find(scope, conversation, "cache-" + conversation).isPresent()).isEqualTo(allowed);
                assertThat(bean(CacheRepository.class).put(scope, conversation, "cache-" + conversation, cache(NOW.plusSeconds(60)))).isEqualTo(allowed);
            }
            default -> throw new AssertionError("정의되지 않은 저장소 시험입니다");
        }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"tc", "wm", "other", "expired", "edge"})
    void auditUsesOwnRetentionAndStillEnforcesSystemActorAndParentOwnership(String conversation) {
        var scope = scope(OwnerSystem.TC, "a");
        boolean owned = !Set.of("wm", "other").contains(conversation);
        // 감사는 부모 만료 경계의 종결도 기록하고, 자체 90일 동안만 조회합니다.
        assertThat(bean(AuditRepository.class).findByRequest(scope, "audit-" + conversation)).hasSize(owned ? 1 : 0);
        assertThat(bean(AuditRepository.class).insert(scope, audit(conversation))).isEqualTo(owned);
    }
    @Test void missingNullDuplicateEmptyAndCrossParentCasesDoNotBypassScope() {
        var scope = scope(OwnerSystem.TC,"a");
        assertThat(bean(ConversationRepository.class).find(scope, "missing")).isEmpty();
        assertThat(bean(ConversationRepository.class).find(scope, "tc")).hasValueSatisfying(value ->
            assertThat(value.expiresAt()).isEqualTo(value.createdAt().plus(Duration.ofDays(30))));
        assertThatThrownBy(() -> bean(ConversationRepository.class).find(null, "tc")).isInstanceOf(NullPointerException.class);
        assertThat(bean(JobEventRepository.class).findAfter(scope, "missing", 0)).isEmpty();
        assertThat(bean(JobRepository.class).findByIdempotencyKey(scope, "key")).map(JobRepository.Job::conversationId).contains("tc");
        assertThat(bean(JobRepository.class).findByIdempotencyKey(scope, "")).isEmpty();
        assertThat(bean(JobRepository.class).findByIdempotencyKey(scope, "key-expired")).isEmpty();
        assertThat(bean(JobRepository.class).findByIdempotencyKey(scope, "key-edge")).isEmpty();
        assertThat(bean(JobRepository.class).findByIdempotencyKey(scope, null)).isEmpty();
        assertThatThrownBy(() -> bean(JobRepository.class).insert(scope, new JobRepository.NewJob("duplicate", "tc", "key", "digest", "B"), null))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(bean(JobRepository.class).find(scope, "duplicate")).isEmpty();
        assertThat(bean(EvidenceRepository.class).find(scope, "j-tc", "e-wm")).isEmpty();
        assertThat(bean(ToolExecutionRepository.class).find(scope, "j-tc", "t-wm")).isEmpty();
        assertThat(bean(CacheRepository.class).find(withPdg(scope,"changed"), "tc", "cache-tc")).isEmpty();
        assertThat(bean(CacheRepository.class).put(scope, "tc", "expired-cache", cache(NOW))).isFalse();
        assertThat(bean(AuditRepository.class).insert(scope, new AuditRepository.Event("bad-parent", "tc", "j-wm", null,
            "TEST", null, null, null, null, null, null, null, null, null))).isFalse();
        assertThat(bean(AuditRepository.class).insert(scope, new AuditRepository.Event("unlinked", null, null, null,
            "TEST", null, null, null, null, null, null, null, null, null))).isTrue();
        assertThat(bean(AuditRepository.class).findByRequest(scope,"unlinked")).hasSize(1);
        assertThat(bean(AuditRepository.class).findByRequest(scope(OwnerSystem.WM,"a"),"unlinked")).isEmpty();
    }
    @Test void auditNinetyDayAndCacheOwnExpiryBoundariesAreIndependentOfLiveConversation() throws Exception {
        var scope = scope(OwnerSystem.TC,"a");
        try (var connection = database.connection("aigw_app");
                var audit = connection.prepareStatement("UPDATE gw.audit_event SET recorded_at = ? WHERE request_id = 'audit-tc'");
                var cache = connection.prepareStatement("UPDATE gw.cache_entry SET expires_at = ? WHERE conversation_id = 'tc'")) {
            audit.setTimestamp(1, Timestamp.from(NOW.minus(Duration.ofDays(90)))); audit.executeUpdate();
            cache.setTimestamp(1, Timestamp.from(NOW)); cache.executeUpdate();
        }
        assertThat(bean(ConversationRepository.class).find(scope,"tc")).isPresent();
        assertThat(bean(AuditRepository.class).findByRequest(scope,"audit-tc")).isEmpty();
        assertThat(bean(CacheRepository.class).find(scope,"tc","cache-tc")).isEmpty();
        assertThat(bean(com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository.class).purgeExpiredAudit()).isEqualTo(1);
    }
    @Test void maintenanceUsesExplicitTransactionsAndDoesNotPurgeActiveExpiredJobs() throws Exception {
        var queue = bean(com.nori.ai.gateway.persistence.maintenance.QueueMaintenanceRepository.class);
        assertThatThrownBy(queue::lockControl).isInstanceOf(IllegalStateException.class);
        bean(org.springframework.transaction.support.TransactionTemplate.class).executeWithoutResult(status -> queue.lockControl());
        assertThat(bean(com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository.class).purgeExpiredConversations()).isZero();
        try (var connection = database.connection("aigw_app"); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE gw.job SET status = 'CANCELLED' WHERE conversation_id IN ('expired','edge')");
        }
        assertThat(bean(com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository.class).purgeExpiredConversations()).isEqualTo(2);
        assertThat(bean(com.nori.ai.gateway.persistence.maintenance.RecoveryMaintenanceRepository.class).databaseSystemIdentifier()).isEmpty();
    }
    private <T> T bean(Class<T> type) { return context.getBean(type); }
    private static VerifiedSystemScope scope(OwnerSystem owner, String actor) {
        return new VerifiedSystemScope(owner,"service",new ActorRef(actor),actor,Set.of(),"pdg",NOW.minusSeconds(1),NOW.plusSeconds(60),"request");
    }
    private static VerifiedSystemScope withPdg(VerifiedSystemScope scope, String pdg) {
        return new VerifiedSystemScope(scope.boundSystem(),scope.authenticatedServiceRef(),scope.actorRef(),scope.sub(),scope.perm(),pdg,scope.issuedAt(),scope.expiresAt(),scope.requestId());
    }
    private static MessageRepository.Message message(String id) { return new MessageRepository.Message(id,"user","제거된 시험 문구","[]","[]",null,null); }
    private static ResultRepository.Result result() { return new ResultRepository.Result("SUCCESS",null,"{}",null,"[]"); }
    private static EvidenceRepository.Entry evidence(String id) { return new EvidenceRepository.Entry(id,"source",null,null,"{}"); }
    private static ToolExecutionRepository.Entry tool(String id) { return new ToolExecutionRepository.Entry(id,"tc.eqp.getOperationalSnapshot","SUCCESS","{}"); }
    private static CacheRepository.Entry cache(Instant expiresAt) { return new CacheRepository.Entry("tc.eqp.getOperationalSnapshot","{}","[]",expiresAt); }
    private static AuditRepository.Event audit(String conversation) {
        return new AuditRepository.Event("audit-" + conversation,conversation,"j-" + conversation,null,"TEST",null,null,"ALLOW","SUCCESS",null,null,null,null,null);
    }
}
