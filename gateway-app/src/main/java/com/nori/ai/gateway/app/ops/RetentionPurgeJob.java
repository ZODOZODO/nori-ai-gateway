package com.nori.ai.gateway.app.ops;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.job.recovery.ReadinessGate;
import com.nori.ai.gateway.app.observability.MetricsCatalog;
import com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.event.EventListener;

/**
 * 대화 30일·감사 90일 정리 Job이다. 복구 완료 신호 뒤에만 시작하고 Gateway Clock 기준 1시간 주기로 실행한다.
 * 각 batch는 저장소의 짧은 자체 tx로 끝나며, 삭제 0이 될 때까지 순차 반복한다. 정리 실패와, 성공한 주기 뒤에도 만료 후 24h를 넘겨 남은 대화는
 * 같은 무라벨 실패 counter와 고정 코드로 그 주기에 한 번 알린다(AIGW-RETENTION-PURGE-FAILED).
 */
public final class RetentionPurgeJob implements AutoCloseable {
    static final Duration PERIOD = Duration.ofHours(1);
    static final Duration RESIDUAL_LIMIT = Duration.ofHours(24);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("retention-purge-", 0).factory());
    private final RetentionMaintenanceRepository retention;
    private final Clock clock;
    private final Optional<PausePointRegistry> pauses;
    private final Counter conversations, audit, failures;
    private volatile boolean started;
    private Instant nextRunAt;

    public RetentionPurgeJob(RetentionMaintenanceRepository retention, Clock clock, Optional<PausePointRegistry> pauses, MeterRegistry meters) {
        this.retention = Objects.requireNonNull(retention); this.clock = Objects.requireNonNull(clock); this.pauses = Objects.requireNonNull(pauses);
        conversations = purgedRows(meters, "conversation"); audit = purgedRows(meters, "audit");
        failures = Counter.builder(MetricsCatalog.RETENTION_PURGE_FAILURES).register(meters);
    }
    @EventListener public void start(ReadinessGate.RecoveryCompleted event) {
        if (started) return;
        started = true;
        // 주기를 실시간 대신 Gateway Clock으로 판정하여 제어 시계 시험과 운영이 같은 경로를 쓰게 합니다.
        scheduler.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
    }
    private synchronized void tick() {
        Instant now = clock.instant();
        if (nextRunAt != null && now.isBefore(nextRunAt)) return;
        try {
            if (purgeNow(now)) nextRunAt = now.plus(PERIOD);
        } catch (RuntimeException failure) {
            // 실패 batch만 rollback되고 이전 커밋은 남습니다. 다음 주기에 다시 시도하며 경보는 실패 지표로 판정합니다.
            nextRunAt = now.plus(PERIOD);
            signalFailure();
        }
    }
    /** 보류 지점이 걸려 있으면 그 주기를 건너뛰고 false를 반환한다. 정리가 끝난 뒤 남은 가장 오래된 만료가 now보다 24h 넘게 앞서면 한 번 알린다. */
    private boolean purgeNow(Instant now) {
        if (pauses.map(registry -> registry.held(PausePointRegistry.Point.PURGE_HOLD)).orElse(false)) return false;
        for (int deleted; (deleted = retention.purgeExpiredConversationsBatch(RetentionMaintenanceRepository.DEFAULT_PURGE_BATCH_SIZE)) > 0;) conversations.increment(deleted);
        for (int deleted; (deleted = retention.purgeExpiredAuditBatch(RetentionMaintenanceRepository.DEFAULT_PURGE_BATCH_SIZE)) > 0;) audit.increment(deleted);
        // 정각은 초과가 아니며 저장 정밀도로 내리지 않고 Instant로 비교합니다.
        if (retention.findOldestConversationExpiry().filter(expiresAt -> now.isAfter(expiresAt.plus(RESIDUAL_LIMIT))).isPresent()) signalFailure();
        return true;
    }
    private void signalFailure() {
        failures.increment();
        System.getLogger(RetentionPurgeJob.class.getName()).log(System.Logger.Level.WARNING, "RETENTION_PURGE_FAILED");
    }
    private static Counter purgedRows(MeterRegistry meters, String kind) { return Counter.builder(MetricsCatalog.RETENTION_PURGED_ROWS).tag("kind", kind).register(meters); }
    public boolean started() { return started; }
    @Override public void close() { started = false; scheduler.shutdownNow(); }
}
