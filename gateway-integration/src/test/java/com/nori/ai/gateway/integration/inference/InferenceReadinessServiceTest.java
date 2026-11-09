package com.nori.ai.gateway.integration.inference;

import com.nori.ai.gateway.core.result.AdmissionErrorCode;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe.Reason;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe.State;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static com.nori.ai.gateway.integration.inference.OllamaReadinessProbeTest.*;
import static org.assertj.core.api.Assertions.*;

/** 제어 시계로 600s 유예·즉시 경보·READY 해제와 상태 지표를 확인한다(실제 판정 경로 evaluate 사용). */
class InferenceReadinessServiceTest {
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-09T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    private final MutableClock clock = new MutableClock();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final OllamaReadinessProbe probe = new OllamaReadinessProbe(new InferenceEndpointConfig("http://127.0.0.1:1", EXPECTED));
    private final AtomicReference<OllamaReadinessProbe.Response> next = new AtomicReference<>();
    private final AtomicInteger reads = new AtomicInteger();
    private final InferenceReadinessService service = new InferenceReadinessService(probe, clock, meters);
    { service.overrideSource(() -> { reads.incrementAndGet(); return next.get(); }); }
    @AfterEach void close() { service.stop(); probe.close(); }

    private InferenceReadinessService.Snapshot observe(String body, long secondsLater) {
        clock.now = clock.now.plusSeconds(secondsLater);
        next.set(body == null ? null : new OllamaReadinessProbe.Response(200, body.getBytes(StandardCharsets.UTF_8)));
        return service.refresh();
    }
    private double gauge(String name, State state) { return meters.get(name).tag("state", state.name()).gauge().value(); }

    @Test void startsNotReadyAndRaisesPersistentAlertOnlyAfterMoreThan600sUntilReady() {
        assertThat(service.ready()).isFalse();
        assertThat(service.admissionBlock()).contains(AdmissionErrorCode.INFERENCE_UNAVAILABLE);
        String loading = ps(embedding(8192));
        assertThat(observe(loading, 0).alerts()).isEmpty();
        assertThat(observe(loading, 300).alerts()).isEmpty();
        assertThat(observe(loading, 300).alerts()).as("정확히 600s 는 유예 안").isEmpty();
        assertThat(service.admissionBlock()).contains(AdmissionErrorCode.MODEL_LOADING);
        assertThat(observe(loading, 1).alerts()).containsExactly(State.MODEL_LOADING);
        assertThat(gauge("aigw.inference.alert", State.MODEL_LOADING)).isEqualTo(1.0);
        // READY 전에는 다른 비READY 상태로 바뀌어도 경보를 해제하지 않는다.
        var unavailable = observe(null, 30);
        assertThat(unavailable.state()).isEqualTo(State.INFERENCE_UNAVAILABLE);
        assertThat(unavailable.reason()).isEqualTo(Reason.NO_RESPONSE);
        assertThat(unavailable.alerts()).containsExactly(State.MODEL_LOADING);
        assertThat(service.admissionBlock()).contains(AdmissionErrorCode.INFERENCE_UNAVAILABLE);
        assertThat(observe(null, 600).alerts()).containsExactly(State.MODEL_LOADING);
        assertThat(observe(null, 1).alerts()).containsExactlyInAnyOrder(State.MODEL_LOADING, State.INFERENCE_UNAVAILABLE);
        var ready = observe(ps(gen(8192, EXPECTED), embedding(8192)), 30);
        assertThat(ready.alerts()).isEmpty();
        assertThat(service.ready()).isTrue();
        assertThat(service.admissionBlock()).isEmpty();
        assertThat(ready.embeddingDigest()).isEqualTo(EMBEDDING_DIGEST);
        for (State state : State.values()) if (state != State.READY) assertThat(gauge("aigw.inference.alert", state)).isZero();
        assertThat(reads).hasValue(8);
    }

    @Test void contextAndDigestMismatchAlertImmediatelyWithoutGraceAndClearOnReady() {
        var context = observe(ps(gen(4096, EXPECTED), embedding(8192)), 0);
        assertThat(context.state()).isEqualTo(State.MODEL_CONTEXT_MISMATCH);
        assertThat(context.alerts()).containsExactly(State.MODEL_CONTEXT_MISMATCH);
        assertThat(service.admissionBlock()).contains(AdmissionErrorCode.MODEL_CONTEXT_MISMATCH);
        assertThat(observe(ps(gen(8192, EXPECTED), embedding(8192)), 0).alerts()).isEmpty();
        var digest = observe(ps(gen(8192, OTHER), embedding(8192)), 0);
        assertThat(digest.state()).isEqualTo(State.MODEL_DIGEST_MISMATCH);
        assertThat(digest.alerts()).containsExactly(State.MODEL_DIGEST_MISMATCH);
        assertThat(gauge("aigw.inference.alert", State.MODEL_DIGEST_MISMATCH)).isEqualTo(1.0);
        assertThat(gauge("aigw.inference.readiness.state", State.MODEL_DIGEST_MISMATCH)).isEqualTo(1.0);
        assertThat(gauge("aigw.inference.readiness.state", State.READY)).isZero();
        assertThat(service.ready()).isFalse();
        assertThat(service.admissionBlock()).contains(AdmissionErrorCode.MODEL_DIGEST_MISMATCH);
        assertThat(observe(ps(gen(8192, OTHER), embedding(8192)), 3600).alerts()).as("저절로 풀리지 않는다").containsExactly(State.MODEL_DIGEST_MISMATCH);
        observe(ps(gen(8192, EXPECTED), embedding(8192)), 0);
        assertThat(gauge("aigw.inference.alert", State.MODEL_DIGEST_MISMATCH)).isZero();
        assertThat(gauge("aigw.inference.readiness.state", State.READY)).isEqualTo(1.0);
    }

    @Test void endpointErrorKeepsTheStateEnumAndCarriesTheReasonOnly() {
        next.set(new OllamaReadinessProbe.Response(403, "{}".getBytes(StandardCharsets.UTF_8)));
        var snapshot = service.refresh();
        assertThat(snapshot.state()).isEqualTo(State.INFERENCE_UNAVAILABLE);
        assertThat(snapshot.reason()).isEqualTo(Reason.ENDPOINT_ERROR);
        assertThat(service.admissionBlock()).isEqualTo(Optional.of(AdmissionErrorCode.INFERENCE_UNAVAILABLE));
        assertThat(meters.find("aigw.inference.readiness.state").gauges()).hasSize(State.values().length)
            .allSatisfy(gauge -> assertThat(gauge.getId().getTags()).extracting(tag -> tag.getKey()).containsExactly("state"));
    }
}
