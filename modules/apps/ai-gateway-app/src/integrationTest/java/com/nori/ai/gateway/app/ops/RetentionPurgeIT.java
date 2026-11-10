package com.nori.ai.gateway.app.ops;

import com.nori.ai.gateway.app.hooks.PausePointRegistry.Point;
import com.nori.ai.gateway.app.job.P09QueueSupport;
import com.nori.ai.gateway.app.job.SlotLedger;
import com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository;
import com.nori.ai.gateway.persistence.repository.AuditRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 보존 정리·만료 경계 Job의 복구 완료 결속, 보류 지점, Gateway Clock 주기를 실제 앱 경로로 확인한다. */
@Tag("stage-F1") @Tag("label-MOCK_API")
class RetentionPurgeIT extends P09QueueSupport {
    private static final Duration CONVERSATION_TTL = Duration.ofDays(30);

    @Test @Tag("AC-24-RETAIN-JOBEDGE")
    void expiredRunningJobIsCancelledByTheCancellerAfterActualTerminationWithoutResultAndReleasesOneSlot() throws Exception {
        var canceller = context.getBean(ExpiryEdgeJobCanceller.class);
        await(canceller::started);
        pauses().hold(Point.SWEEPER_HOLD, "*");
        pauses().hold(Point.EXPIRY_CANCELLER_HOLD, "*");
        var job = admit("actor", "edge", Set.of(Point.BEFORE_CLASSIFICATION));
        await(() -> pauses().reachedCount(Point.BEFORE_CLASSIFICATION, job.jobId()) == 1);
        var slots = context.getBean(SlotLedger.class);
        int released = slots.releaseCount();
        clock().advance(CONVERSATION_TTL.toSeconds());
        Thread.sleep(2500);
        // 보류 중에는 만료 경계 Job이 신호를 보내지 않으므로 Worker는 진행 중으로 남습니다.
        assertThat(state(job.jobId())).isEqualTo("RUNNING");
        pauses().release(Point.EXPIRY_CANCELLER_HOLD, "*");
        await(() -> state(job.jobId()).equals("CANCELLED"));
        await(() -> slots.activeCount() == 0);
        assertThat(slots.releaseCount()).isEqualTo(released + 1);
        var row = jdbc().sql("SELECT error_code, result_ref, delegation_id FROM gw.job WHERE id=:id").param("id", job.jobId()).query((rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}).single();
        assertThat(row).containsOnlyNulls();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.result WHERE job_id=:id").param("id", job.jobId()).query(Integer.class).single()).isZero();
        assertThat(jdbc().sql("SELECT type || ':' || external_suppressed FROM gw.job_event WHERE job_id=:id AND type LIKE 'job.%' AND type NOT IN ('job.queued','job.started')").param("id", job.jobId()).query(String.class).list())
            .containsExactly("job.cancelled:true");
        assertThat(jdbc().sql("SELECT decision FROM gw.audit_event WHERE job_id=:id AND event_type='JOB_TERMINAL'").param("id", job.jobId()).query(String.class).list()).containsExactly("EXPIRY_EDGE");
        assertJobAuditLineage(job.jobId(), true);
    }

    @Test @Tag("AC-24-RETAIN-PURGE")
    void expiredConversationWithRunningJobIsKeptUntilTheCancellerEndsItAndThenPurgedWithinOneDay() throws Exception {
        var purge = context.getBean(RetentionPurgeJob.class);
        await(purge::started);
        pauses().hold(Point.SWEEPER_HOLD, "*");
        pauses().hold(Point.EXPIRY_CANCELLER_HOLD, "*");
        var job = admit("actor", "purge", Set.of(Point.BEFORE_CLASSIFICATION));
        await(() -> pauses().reachedCount(Point.BEFORE_CLASSIFICATION, job.jobId()) == 1);
        Instant expiresAt = jdbc().sql("SELECT expires_at FROM gw.conversation WHERE id=:id").param("id", job.conversationId()).query(Timestamp.class).single().toInstant();
        Instant afterExpiry = expiresAt.plusSeconds(3600);
        // 다음 정리 주기가 실제로 돌았음을 그 시각에만 만료되는 별도 감사 행의 삭제로 확인합니다.
        insertAudit("purge-probe", afterExpiry.minus(Duration.ofDays(90)));
        clock().advance(Duration.between(clock().instant(), afterExpiry).toSeconds());
        await(() -> auditCount("purge-probe") == 0);
        assertThat(count("conversation")).isEqualTo(1);
        assertThat(state(job.jobId())).isEqualTo("RUNNING");
        pauses().release(Point.EXPIRY_CANCELLER_HOLD, "*");
        await(() -> state(job.jobId()).equals("CANCELLED"));
        pauses().hold(Point.PURGE_HOLD, "*");
        clock().advance(3600);
        Thread.sleep(2500);
        assertThat(count("conversation")).as("보류 지점이 걸린 주기는 건너뜁니다").isEqualTo(1);
        pauses().release(Point.PURGE_HOLD, "*");
        await(() -> count("conversation") == 0);
        assertThat(Duration.between(expiresAt, clock().instant())).isLessThanOrEqualTo(Duration.ofHours(24));
        assertThat(count("job")).isZero();
        assertThat(count("job_event")).isZero();
        // 감사는 대화 정리와 독립 수명이므로 접수 원점부터 종결까지 같은 원점 결속으로 남습니다.
        assertThat(assertJobAuditLineage(job.jobId(), true)).startsWith("JOB_ACCEPTED").endsWith("JOB_TERMINAL");
    }

    @Test @Tag("AC-24-RETAIN-PURGE")
    void residualBeyondTwentyFourHoursAfterExpirySignalsOncePerSuccessfulCycleWithoutDeletingTheActiveConversation() throws Exception {
        var purge = context.getBean(RetentionPurgeJob.class);
        await(purge::started);
        pauses().hold(Point.SWEEPER_HOLD, "*");
        pauses().hold(Point.EXPIRY_CANCELLER_HOLD, "*");
        var job = admit("actor", "residual", Set.of(Point.BEFORE_CLASSIFICATION));
        await(() -> pauses().reachedCount(Point.BEFORE_CLASSIFICATION, job.jobId()) == 1);
        Instant expiresAt = jdbc().sql("SELECT expires_at FROM gw.conversation WHERE id=:id").param("id", job.conversationId()).query(Timestamp.class).single().toInstant();
        var retention = context.getBean(RetentionMaintenanceRepository.class);
        assertThat(retention.findOldestConversationExpiry()).contains(expiresAt);
        // nano 경계: 같은 실제 DB 저장소와 nano 정밀 Gateway Clock을 받은 같은 정리 Job의 첫 주기로 판정합니다(정각은 초과 아님).
        for (long offset : new long[]{-1, 0, 1}) {
            var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            try (var cycle = new RetentionPurgeJob(retention, java.time.Clock.fixed(expiresAt.plus(Duration.ofHours(24)).plusNanos(offset), java.time.ZoneOffset.UTC), java.util.Optional.empty(), meters)) {
                cycle.start(new com.nori.ai.gateway.app.job.recovery.ReadinessGate.RecoveryCompleted());
                Thread.sleep(2500);
                assertThat(meters.get(com.nori.ai.gateway.app.observability.MetricsCatalog.RETENTION_PURGE_FAILURES).counter().count()).as("offset %d ns", offset).isEqualTo(offset > 0 ? 1.0 : 0.0);
            }
        }
        assertThat(count("conversation")).as("활성 Job 대화는 삭제하지 않습니다").isEqualTo(1);

        var failures = context.getBean(io.micrometer.core.instrument.MeterRegistry.class).get(com.nori.ai.gateway.app.observability.MetricsCatalog.RETENTION_PURGE_FAILURES).counter();
        double before = failures.count();
        var captured = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); captured.start();
        var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME); root.addAppender(captured);
        try {
            pauses().hold(Point.PURGE_HOLD, "*");
            clock().freezeAt(expiresAt.plus(Duration.ofHours(25)), true);
            Thread.sleep(2500);
            assertThat(failures.count()).as("보류 중인 주기는 조회·신호 0").isEqualTo(before);
            pauses().release(Point.PURGE_HOLD, "*");
            await(() -> failures.count() == before + 1);
            Thread.sleep(2500);
            assertThat(failures.count()).as("한 주기에 한 번").isEqualTo(before + 1);
            assertThat(count("conversation")).isEqualTo(1);
            assertThat(state(job.jobId())).isEqualTo("RUNNING");
            clock().advance(Duration.ofHours(1).toSeconds());
            await(() -> failures.count() == before + 2);
            assertThat(captured.list.stream().filter(event -> event.getLoggerName().equals(RetentionPurgeJob.class.getName())).map(ch.qos.logback.classic.spi.ILoggingEvent::getMessage).toList())
                .containsExactly("RETENTION_PURGE_FAILED", "RETENTION_PURGE_FAILED");
            // 종결 뒤 다음 주기는 대화를 정리하고, 남은 만료 묶음이 없으므로 신호를 내지 않습니다.
            pauses().release(Point.EXPIRY_CANCELLER_HOLD, "*");
            pauses().release(Point.BEFORE_CLASSIFICATION, job.jobId());
            await(() -> !state(job.jobId()).equals("RUNNING"));
            clock().advance(Duration.ofHours(1).toSeconds());
            await(() -> count("conversation") == 0);
            Thread.sleep(1500);
            assertThat(failures.count()).isEqualTo(before + 2);
            assertThat(retention.findOldestConversationExpiry()).isEmpty();
        } finally { root.detachAppender(captured); captured.stop(); }
    }

    @Test @Tag("AC-24-AUDIT-EXPIRY")
    void auditIsKeptJustBeforeNinetyDaysHiddenAtTheBoundaryAndPurgedWithinTheNextDay() throws Exception {
        await(context.getBean(RetentionPurgeJob.class)::started);
        Instant recorded = clock().instant();
        String target = UUID.randomUUID().toString();
        insertAudit(target, recorded);
        insertAudit("older", recorded.minusNanos(1000));
        clock().freezeAt(recorded.plus(Duration.ofDays(90)).minusNanos(1000), true);
        await(() -> auditCount("older") == 0);
        assertThat(auditCount(target)).as("90일 직전").isEqualTo(1);
        clock().freezeAt(recorded.plus(Duration.ofDays(90)), true);
        assertThat(context.getBean(AuditRepository.class).findByRequest(scope("actor"), target)).as("정각부터 조회 차단").isEmpty();
        clock().freezeAt(recorded.plus(Duration.ofDays(90)).plus(Duration.ofHours(24)), true);
        await(() -> auditCount(target) == 0);
    }

    private void insertAudit(String request, Instant recordedAt) {
        jdbc().sql("INSERT INTO gw.audit_event(recorded_at, owner_system, actor_ref, request_id, event_type, status) VALUES (:at, 'TC', 'actor', :request, 'JOB_TERMINAL', 'SUCCEEDED')")
            .param("at", Timestamp.from(recordedAt)).param("request", request).update();
    }
    private int auditCount(String request) {
        return jdbc().sql("SELECT count(*) FROM gw.audit_event WHERE request_id=:request").param("request", request).query(Integer.class).single();
    }
}
