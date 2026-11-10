package com.nori.ai.gateway.integration.inference;

import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe.Reason;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe.State;
import com.nori.ai.gateway.integration.inference.OllamaStubServer.Json;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 실제 HTTP 로 /api/ps stub 을 읽어 SPEC §14.6 판정 순서(형식 → 상주 → ctx → digest)를 확인한다. */
class OllamaReadinessProbeTest {
    static final String EXPECTED = "1".repeat(64);
    static final String OTHER = "2".repeat(64);
    static final String EMBEDDING_DIGEST = "3".repeat(64);
    private OllamaStubServer stub;
    private OllamaReadinessProbe probe;

    static String gen(Object contextLength, Object digest) {
        return "{\"name\":\"qwen3:4b\",\"model\":\"qwen3:4b\",\"size\":1,\"digest\":" + json(digest) + ",\"context_length\":" + json(contextLength) + "}";
    }
    static String embedding(int contextLength) {
        return "{\"name\":\"bge-m3:latest\",\"model\":\"bge-m3:latest\",\"digest\":\"" + EMBEDDING_DIGEST + "\",\"context_length\":" + contextLength + "}";
    }
    static String ps(String... models) { return "{\"models\":[" + String.join(",", models) + "]}"; }
    private static String json(Object value) { return value instanceof String text ? "\"" + text + "\"" : String.valueOf(value); }

    private OllamaReadinessProbe.Observation observe(int status, String body) {
        if (stub == null) { stub = new OllamaStubServer(request -> new Json(status, body)); probe = new OllamaReadinessProbe(new InferenceEndpointConfig(stub.baseUrl(), EXPECTED)); }
        else stub.respond(request -> new Json(status, body));
        return probe.probe();
    }
    @AfterEach void close() { if (probe != null) probe.close(); if (stub != null) stub.close(); }

    @Test void readyOnlyWhenBothResidentGenerationContextIs8192AndDigestMatchesAndBgeContextIsIgnored() {
        assertThat(observe(200, ps(gen(8192, EXPECTED), embedding(8192)))).isEqualTo(new OllamaReadinessProbe.Observation(State.READY, Reason.NONE, EMBEDDING_DIGEST));
        assertThat(observe(200, ps(embedding(4096), gen(8192, EXPECTED))).state()).as("bge-m3 context 는 판정하지 않는다").isEqualTo(State.READY);
        assertThat(stub.received()).allSatisfy(request -> { assertThat(request.method()).isEqualTo("GET"); assertThat(request.path()).isEqualTo("/api/ps"); });
    }

    @Test void residentModelsContextAndDigestFailuresMapToTheirOwnStates() {
        assertThat(observe(200, ps(embedding(8192))).state()).isEqualTo(State.MODEL_LOADING);
        assertThat(observe(200, ps(gen(8192, EXPECTED))).state()).isEqualTo(State.MODEL_LOADING);
        assertThat(observe(200, ps()).state()).isEqualTo(State.MODEL_LOADING);
        assertThat(observe(200, ps(gen(4096, EXPECTED), embedding(8192)))).isEqualTo(new OllamaReadinessProbe.Observation(State.MODEL_CONTEXT_MISMATCH, Reason.NONE, EMBEDDING_DIGEST));
        assertThat(observe(200, ps(gen(8192, OTHER), embedding(8192)))).isEqualTo(new OllamaReadinessProbe.Observation(State.MODEL_DIGEST_MISMATCH, Reason.NONE, EMBEDDING_DIGEST));
    }

    @Test void evaluationOrderIsFormatThenResidencyThenContextThenDigest() {
        assertThat(observe(200, ps(gen(4096, OTHER), embedding(8192))).state()).as("ctx·digest 동시 불일치").isEqualTo(State.MODEL_CONTEXT_MISMATCH);
        assertThat(observe(200, ps(gen(8192, OTHER))).state()).as("bge-m3 미상주 + digest 불일치").isEqualTo(State.MODEL_LOADING);
        assertThat(observe(200, ps(gen(4096, OTHER))).state()).as("미상주 + ctx·digest 불일치").isEqualTo(State.MODEL_LOADING);
        assertThat(observe(200, ps(embedding(8192), "{\"name\":\"qwen3:8b\",\"digest\":\"" + OTHER + "\",\"context_length\":8192}")).state())
            .as("다른 태그만 상주하면 생성 모델 미상주").isEqualTo(State.MODEL_LOADING);
        var endpointError = new OllamaReadinessProbe.Observation(State.INFERENCE_UNAVAILABLE, Reason.ENDPOINT_ERROR, null);
        for (String body : new String[]{ps("{\"name\":\"qwen3:4b\",\"context_length\":8192}", embedding(8192)), ps(gen(8192, 7), embedding(8192)),
                ps(gen(8192, "not-a-digest"), embedding(8192)), ps(gen(4096, "XYZ")), ps(gen("8192", EXPECTED), embedding(8192)),
                ps("{\"name\":\"qwen3:4b\",\"digest\":\"" + EXPECTED + "\"}", embedding(8192))})
            assertThat(observe(200, body)).as(body).isEqualTo(endpointError);
    }

    @Test void nonSuccessIncluding401And403AndUnparsableBodiesAreEndpointErrorNotModelLoading() {
        var endpointError = new OllamaReadinessProbe.Observation(State.INFERENCE_UNAVAILABLE, Reason.ENDPOINT_ERROR, null);
        for (int status : new int[]{401, 403, 404, 500, 503}) assertThat(observe(status, ps(gen(8192, EXPECTED), embedding(8192)))).as("status " + status).isEqualTo(endpointError);
        for (String body : new String[]{"not json", "", "[]", "{}", "{\"models\":{}}", "{\"models\":[1]}", "{\"models\":[{\"size\":1}]}", "null"})
            assertThat(observe(200, body)).as(body).isEqualTo(endpointError);
    }

    @Test void noResponseIsInferenceUnavailableWithNoResponseReason() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        try (var closed = new OllamaReadinessProbe(new InferenceEndpointConfig("http://127.0.0.1:" + port, EXPECTED))) {
            assertThat(closed.probe()).isEqualTo(new OllamaReadinessProbe.Observation(State.INFERENCE_UNAVAILABLE, Reason.NO_RESPONSE, null));
            assertThat(closed.evaluate(null).reason()).isEqualTo(Reason.NO_RESPONSE);
        }
    }
}
