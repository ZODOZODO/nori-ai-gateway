package com.nori.ai.gateway.app.ops;

import com.nori.ai.gateway.integration.source.TcSourcePolicy;
import com.nori.ai.gateway.app.config.SourceConnectorConfiguration.LokiProbeFactory;
import com.nori.ai.gateway.app.config.SourceConnectorConfiguration.ProbeWindowInput;
import com.nori.ai.gateway.app.job.recovery.ReadinessGate;
import com.nori.ai.gateway.app.observability.MetricsCatalog;
import com.nori.ai.gateway.integration.loki.LokiRetentionProbe;
import com.nori.ai.gateway.integration.loki.LokiSourcePolicy;
import com.nori.ai.gateway.persistence.retention.SourceRetentionProbeRepository;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;

/**
 * Loki 보존 탐침 스케줄러다. 복구 완료 신호 뒤에만 시작하며 시작 직후 1회 + Gateway Clock 기준 24h 주기로 실행한다.
 * 매 주기 명시 확인된 범위를 다시 받아 앱별 탐침을 만들고, 실제 관측값이 있을 때만 같은 앱 SourceId로 저장한다.
 * factory·입력·범위가 없으면 그 앱의 탐침 생성·측정·저장은 없다(Gate 미확정은 NOT_RUN).
 */
public final class SourceRetentionProbeScheduler implements AutoCloseable {
    static final Duration PERIOD = Duration.ofHours(24);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("retention-probe-", 0).factory());
    private final SourceRetentionProbeRepository probes;
    private final ObjectProvider<LokiProbeFactory> factories;
    private final ObjectProvider<ProbeWindowInput> windows;
    private final Clock clock;
    private final Counter failures;
    private volatile boolean started;
    private Instant nextRunAt;

    public SourceRetentionProbeScheduler(SourceRetentionProbeRepository probes, ObjectProvider<LokiProbeFactory> factories,
            ObjectProvider<ProbeWindowInput> windows, Clock clock, MeterRegistry meters) {
        this.probes = Objects.requireNonNull(probes); this.factories = Objects.requireNonNull(factories); this.windows = Objects.requireNonNull(windows);
        this.clock = Objects.requireNonNull(clock); failures = Counter.builder(MetricsCatalog.RETENTION_PROBE_FAILURES).register(meters);
    }
    @EventListener public void start(ReadinessGate.RecoveryCompleted event) {
        if (started) return;
        started = true;
        scheduler.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
    }
    private synchronized void tick() {
        Instant now = clock.instant();
        if (nextRunAt != null && now.isBefore(nextRunAt)) return;
        nextRunAt = now.plus(PERIOD);
        try { probeNow(now); }
        catch (RuntimeException failure) {
            // 저장 실패는 행을 바꾸지 않으며 다음 주기에 다시 측정합니다.
            failures.increment();
            System.getLogger(SourceRetentionProbeScheduler.class.getName()).log(System.Logger.Level.WARNING, "RETENTION_PROBE_FAILED");
        }
    }
    void probeNow(Instant now) {
        LokiProbeFactory factory = factories.getIfAvailable();
        ProbeWindowInput input = windows.getIfAvailable();
        if (factory == null || input == null) return;
        for (String app : TcSourcePolicy.APPS) {
            String sourceId = LokiSourcePolicy.retentionSourceId(app);
            var window = input.window(sourceId, now);
            if (window.isEmpty()) continue;
            Optional<LokiRetentionProbe.ProbeObservation> observed;
            try { observed = factory.create(window.get()).measure(app); }
            catch (RuntimeException failure) { failures.increment(); continue; }
            observed.ifPresent(value -> probes.upsert(sourceId, value.verifiedOldestAt(), value.probeLimit(), now));
        }
    }
    public boolean started() { return started; }
    @Override public void close() { started = false; scheduler.shutdownNow(); }
}
