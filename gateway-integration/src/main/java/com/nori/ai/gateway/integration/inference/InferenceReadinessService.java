package com.nori.ai.gateway.integration.inference;

import com.nori.ai.gateway.core.job.InferenceReadiness;
import com.nori.ai.gateway.core.result.AdmissionErrorCode;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe.Observation;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe.Reason;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe.State;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;

/**
 * 추론 readiness 상태 기계(SPEC §14.6). 30s 마다 {@link OllamaReadinessProbe} 를 읽고, Gateway {@link Clock} 기준으로
 * MODEL_LOADING·INFERENCE_UNAVAILABLE 가 600s 를 넘으면 지속 경보, MODEL_CONTEXT_MISMATCH·MODEL_DIGEST_MISMATCH 는 즉시 경보를 올린다.
 * 경보는 READY 를 관측할 때만 해제한다. 모델 pull·변경·로드·재시작 같은 복구 동작은 하지 않는다.
 */
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
public final class InferenceReadinessService implements InferenceReadiness, SmartLifecycle {
    static final Duration PROBE_INTERVAL = Duration.ofSeconds(30);
    static final Duration ALERT_GRACE = Duration.ofSeconds(600);
    private static final Set<State> IMMEDIATE_ALERT = EnumSet.of(State.MODEL_CONTEXT_MISMATCH, State.MODEL_DIGEST_MISMATCH);
    private static final Set<State> GRACE_ALERT = EnumSet.of(State.MODEL_LOADING, State.INFERENCE_UNAVAILABLE);
    private static final System.Logger LOG = System.getLogger(InferenceReadinessService.class.getName());

    public record Snapshot(State state, Reason reason, Instant since, String embeddingDigest, Set<State> alerts) {
        public Snapshot { alerts = Set.copyOf(alerts); }
    }

    private final OllamaReadinessProbe probe;
    private final Clock clock;
    private final AtomicReference<Supplier<OllamaReadinessProbe.Response>> override = new AtomicReference<>();
    private volatile Snapshot current;
    private ScheduledExecutorService scheduler;

    public InferenceReadinessService(OllamaReadinessProbe probe, Clock clock, MeterRegistry meters) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.clock = Objects.requireNonNull(clock, "clock");
        // 첫 확인 전에는 응답을 본 적이 없으므로 무응답과 같이 접수하지 않는다.
        this.current = new Snapshot(State.INFERENCE_UNAVAILABLE, Reason.NO_RESPONSE, clock.instant(), null, Set.of());
        for (State state : State.values())
            Gauge.builder("aigw.inference.readiness.state", () -> current.state() == state ? 1 : 0).tag("state", state.name()).register(meters);
        for (State state : State.values()) if (state != State.READY)
            Gauge.builder("aigw.inference.alert", () -> current.alerts().contains(state) ? 1 : 0).tag("state", state.name()).register(meters);
    }

    /** 한 번 확인하고 상태·경보를 갱신한다. 주기 실행과 verify readiness stub hook 이 같은 경로를 쓴다. */
    public synchronized Snapshot refresh() {
        var source = override.get();
        return record(probe.evaluate(source == null ? probe.fetch() : source.get()));
    }

    /**
     * verify readiness stub hook 전용: 다음 확인부터 실제 endpoint 대신 고정 응답(null = 무응답)을 판정한다.
     * {@code null} 을 넘기면 실제 endpoint 로 돌아간다.
     */
    public void overrideSource(Supplier<OllamaReadinessProbe.Response> source) { override.set(source); }

    private Snapshot record(Observation observation) {
        Instant now = clock.instant();
        Snapshot previous = current;
        Instant since = previous.state() == observation.state() ? previous.since() : now;
        EnumSet<State> alerts = EnumSet.noneOf(State.class);
        if (observation.state() != State.READY) {
            alerts.addAll(previous.alerts());
            if (IMMEDIATE_ALERT.contains(observation.state())
                || GRACE_ALERT.contains(observation.state()) && Duration.between(since, now).compareTo(ALERT_GRACE) > 0) alerts.add(observation.state());
        }
        current = new Snapshot(observation.state(), observation.reason(), since, observation.embeddingDigest(), alerts);
        if (previous.state() != observation.state() || previous.reason() != observation.reason())
            LOG.log(System.Logger.Level.INFO, "INFERENCE_READINESS_" + observation.state() + (observation.reason() == Reason.NONE ? "" : "_" + observation.reason()));
        for (State raised : alerts) if (!previous.alerts().contains(raised)) LOG.log(System.Logger.Level.WARNING, "INFERENCE_ALERT_RAISED_" + raised);
        if (!previous.alerts().isEmpty() && alerts.isEmpty()) LOG.log(System.Logger.Level.INFO, "INFERENCE_ALERT_CLEARED");
        return current;
    }

    private void refreshSafely() {
        try { refresh(); }
        catch (RuntimeException failure) {
            // 판정 자체가 실패해도 주기를 멈추지 않고 fail-closed 로 기록한다.
            synchronized (this) { record(new Observation(State.INFERENCE_UNAVAILABLE, Reason.ENDPOINT_ERROR, null)); }
        }
    }

    @Override public boolean ready() { return current.state() == State.READY; }
    public Snapshot snapshot() { return current; }
    /** READY 가 아니면 그 상태 이름의 접수 거절 errorCode(SPEC §14.6 닫힌 네 값). */
    public Optional<AdmissionErrorCode> admissionBlock() {
        State state = current.state();
        return state == State.READY ? Optional.empty() : Optional.of(AdmissionErrorCode.valueOf(state.name()));
    }

    @Override public synchronized void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("inference-readiness-", 0).daemon(true).factory());
        scheduler.scheduleWithFixedDelay(this::refreshSafely, 0, PROBE_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }
    @Override public synchronized void stop() {
        if (scheduler == null) return;
        scheduler.shutdownNow();
        scheduler = null;
    }
    @Override public synchronized boolean isRunning() { return scheduler != null; }
}
