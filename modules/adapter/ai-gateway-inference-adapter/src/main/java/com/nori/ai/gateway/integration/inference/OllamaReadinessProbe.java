package com.nori.ai.gateway.integration.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 추론 서버의 기존 {@code GET <baseUrl>/api/ps} 를 읽기 전용으로 확인한다(SPEC §14.6). 모델 관리·로드 API 는 호출하지 않는다.
 * 판정 순서 = ①응답 형식(비 2xx·파싱 불가, 상주한 생성 모델 항목의 digest·context_length 부재 포함) → ②두 모델 상주 →
 * ③생성 모델 context_length 8192 → ④생성 모델 digest = 배포 설정 기대값. bge-m3 는 상주만 본다.
 */
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
public final class OllamaReadinessProbe implements AutoCloseable {
    public enum State { READY, MODEL_LOADING, MODEL_CONTEXT_MISMATCH, MODEL_DIGEST_MISMATCH, INFERENCE_UNAVAILABLE }
    /** {@link State#INFERENCE_UNAVAILABLE} 의 사유. 상태 enum 은 늘리지 않는다(SPEC §14.6 2026-11-09 명시). */
    public enum Reason { NONE, NO_RESPONSE, ENDPOINT_ERROR }
    public record Observation(State state, Reason reason, String embeddingDigest) {
        public Observation { Objects.requireNonNull(state, "state"); Objects.requireNonNull(reason, "reason"); }
    }
    /** 응답의 상태 코드와 본문 바이트. 무응답은 이 값 대신 null 이다. */
    public record Response(int status, byte[] body) {
        public Response { Objects.requireNonNull(body, "body"); }
    }

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final String DIGEST = "[0-9a-f]{64}";
    private final URI uri;
    private final String expectedDigest;
    private final HttpClient transport;
    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaReadinessProbe(InferenceEndpointConfig endpoint) {
        this.uri = endpoint.readinessUri();
        this.expectedDigest = endpoint.expectedGenerationDigest();
        this.transport = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public Observation probe() { return evaluate(fetch()); }

    /** 연결·시간 초과·전송 오류는 무응답(null)이다. 상태 코드는 판정하지 않고 그대로 돌려준다. */
    public Response fetch() {
        try {
            return http.get().uri(uri).exchange((request, response) -> new Response(response.getStatusCode().value(), response.getBody().readNBytes(MAX_BODY_BYTES + 1)));
        } catch (RestClientException failure) { return null; }
    }

    public Observation evaluate(Response response) {
        if (response == null) return new Observation(State.INFERENCE_UNAVAILABLE, Reason.NO_RESPONSE, null);
        if (response.status() < 200 || response.status() > 299 || response.body().length > MAX_BODY_BYTES) return endpointError();
        final JsonNode root;
        try { root = mapper.readTree(response.body()); } catch (IOException invalid) { return endpointError(); }
        if (root == null || !root.isObject() || !root.path("models").isArray()) return endpointError();
        JsonNode generation = null, embedding = null;
        for (JsonNode model : root.get("models")) {
            if (!model.isObject()) return endpointError();
            String name = text(model, "name"), alias = text(model, "model");
            if (name == null && alias == null) return endpointError();
            if (generation == null && matches(InferenceEndpointConfig.GENERATION_MODEL, name, alias)) generation = model;
            else if (embedding == null && matches(InferenceEndpointConfig.EMBEDDING_MODEL, name, alias)) embedding = model;
        }
        String embeddingDigest = embedding == null ? null : digest(embedding);
        if (generation != null && (digest(generation) == null || !generation.path("context_length").isIntegralNumber())) return endpointError();
        if (generation == null || embedding == null) return new Observation(State.MODEL_LOADING, Reason.NONE, embeddingDigest);
        if (generation.get("context_length").asLong() != TokenBudgetPolicy.CONTEXT_TOKENS) return new Observation(State.MODEL_CONTEXT_MISMATCH, Reason.NONE, embeddingDigest);
        if (!expectedDigest.equals(digest(generation))) return new Observation(State.MODEL_DIGEST_MISMATCH, Reason.NONE, embeddingDigest);
        return new Observation(State.READY, Reason.NONE, embeddingDigest);
    }

    private static Observation endpointError() { return new Observation(State.INFERENCE_UNAVAILABLE, Reason.ENDPOINT_ERROR, null); }
    private static String text(JsonNode node, String field) { JsonNode value = node.get(field); return value != null && value.isTextual() ? value.asText() : null; }
    private static String digest(JsonNode model) { String value = text(model, "digest"); return value != null && value.matches(DIGEST) ? value : null; }
    // Ollama 는 태그 없는 이름을 ":latest" 로 등록한다(예: bge-m3 → bge-m3:latest).
    private static boolean matches(String expected, String name, String alias) { return tagged(expected).equals(tagged(name)) || tagged(expected).equals(tagged(alias)); }
    private static String tagged(String model) { return model == null ? "" : model.contains(":") ? model : model + ":latest"; }

    @Override public void close() { transport.close(); }
}
