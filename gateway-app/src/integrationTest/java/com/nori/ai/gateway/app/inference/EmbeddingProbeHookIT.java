package com.nori.ai.gateway.app.inference;

import com.nori.ai.gateway.app.job.P09QueueSupport;
import com.nori.ai.gateway.integration.inference.InferenceReadinessService;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.nori.ai.gateway.app.inference.OllamaEndpointStub.*;
import static org.assertj.core.api.Assertions.*;

/** P22 embedding 프로필 측정 hook: 실제 InferenceClient.embed 1회로 차원·bge-m3 digest 만 돌려주고 벡터·입력은 반환하지 않는다. */
@Tag("stage-F2") @Tag("label-MOCK_API")
class EmbeddingProbeHookIT extends P09QueueSupport {
    private static final int DIMENSION = 1024;
    private final OllamaEndpointStub stub = new OllamaEndpointStub(request ->
        request.path().equals("/v1/embeddings") ? new Json(200, embeddingResponse(DIMENSION)) : healthy(request));

    @Override protected Map<String,Object> extraApplicationProperties() {
        var properties = new HashMap<String,Object>(super.extraApplicationProperties());
        properties.putAll(Map.of("ai.gateway.inference.mode", "enabled", "ai.gateway.inference.baseUrl", stub.baseUrl(), "ai.gateway.inference.generationModelDigest", EXPECTED_DIGEST));
        return properties;
    }
    @AfterEach void stopStub() { stub.close(); }

    @Test void returnsOnlyDimensionAndModelDigestFromOneRealEmbedCallAndHidesItselfWithoutTheHookToken() throws Exception {
        await(() -> context.getBean(InferenceReadinessService.class).ready());
        String sentinel = "embed-sentinel-" + UUID.randomUUID();
        var response = hook("/__test/embedding-probe", "POST", "{\"input\":\"" + sentinel + "\",\"baseUrl\":\"http://127.0.0.1:1\"}", hookToken);
        assertThat(response.statusCode()).isEqualTo(200);
        var body = json.readTree(response.body());
        assertThat(Set.copyOf(body.propertyNames())).isEqualTo(Set.of("dimension", "modelDigest"));
        assertThat(body.get("dimension").asInt()).isEqualTo(DIMENSION);
        assertThat(body.get("modelDigest").asString()).isEqualTo(EMBEDDING_DIGEST);
        assertThat(response.body()).doesNotContain("0.5", sentinel);
        assertThat(stub.count("/v1/embeddings")).isOne();
        var embedRequest = stub.received().stream().filter(request -> request.path().equals("/v1/embeddings")).findFirst().orElseThrow();
        assertThat(embedRequest.body()).contains("\"probe\"").doesNotContain(sentinel);
        assertThat(hook("/__test/embedding-probe", "POST", "{}", null).statusCode()).as("hook 토큰 없음").isEqualTo(404);
        assertThat(hook("/__test/embedding-probe", "POST", "{}", "wrong-" + hookToken).statusCode()).as("hook 토큰 불일치").isEqualTo(404);
        assertThat(stub.count("/v1/embeddings")).as("거부된 요청은 추론 호출 0").isOne();
        stub.respond(request -> request.path().equals("/v1/embeddings") ? new Json(500, "{}") : healthy(request));
        var failed = hook("/__test/embedding-probe", "POST", "{}", hookToken);
        assertThat(failed.statusCode()).isEqualTo(503);
        assertThat(json.readTree(failed.body()).get("reason").asString()).isEqualTo("TRANSPORT");
        assertThat(stub.count("/v1/embeddings")).as("실패도 시도당 1회").isEqualTo(2);
    }
}
