package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.integration.inference.InferenceEndpointConfig;
import com.nori.ai.gateway.integration.inference.InferenceReadinessService;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 * readiness 응답 고정 stub 전환(Plan P19, 라벨 LLM_STUB). AC-33·AC-44 수용은 추론 서버를 재시작·중지·재로드하지 않고 이 hook 만 쓴다.
 * 고정 {@code /api/ps} 응답을 실제 판정 경로({@link OllamaReadinessProbe#evaluate})에 넣고 즉시 1회 확인한다. 응답은 상태값만 준다.
 */
@RestController
@RequestMapping("/__test")
@Profile("verify")
@ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "true")
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
public final class ReadinessStubHook {
    private static final Set<String> FIELDS = Set.of("kind");
    private static final String EMBEDDING = "{\"name\":\"bge-m3:latest\",\"model\":\"bge-m3:latest\",\"digest\":\"" + "e".repeat(64) + "\",\"context_length\":8192}";
    private final InferenceReadinessService readiness;
    private final String expectedDigest;

    public ReadinessStubHook(InferenceReadinessService readiness, InferenceEndpointConfig endpoint) {
        this.readiness = readiness;
        this.expectedDigest = endpoint.expectedGenerationDigest();
    }

    @PostMapping("/readiness-stub")
    public ResponseEntity<Map<String,String>> stub(@RequestBody JsonNode body) {
        if (!body.isObject() || !FIELDS.equals(Set.copyOf(body.propertyNames())) || !body.get("kind").isString()) return ResponseEntity.badRequest().build();
        String other = expectedDigest.charAt(0) == '0' ? "1" + expectedDigest.substring(1) : "0" + expectedDigest.substring(1);
        switch (body.get("kind").asString()) {
            case "MODEL_LOADING" -> readiness.overrideSource(() -> ps(EMBEDDING));
            case "MODEL_CONTEXT_MISMATCH" -> readiness.overrideSource(() -> ps(EMBEDDING + "," + generation(4096, expectedDigest)));
            case "MODEL_DIGEST_MISMATCH" -> readiness.overrideSource(() -> ps(EMBEDDING + "," + generation(8192, other)));
            case "INFERENCE_UNAVAILABLE" -> readiness.overrideSource(() -> null);
            case "OFF" -> readiness.overrideSource(null);
            default -> { return ResponseEntity.badRequest().build(); }
        }
        return ResponseEntity.ok(Map.of("state", readiness.refresh().state().name()));
    }

    private static String generation(int contextLength, String digest) {
        return "{\"name\":\"qwen3:4b\",\"model\":\"qwen3:4b\",\"digest\":\"" + digest + "\",\"context_length\":" + contextLength + "}";
    }
    private static OllamaReadinessProbe.Response ps(String models) {
        return new OllamaReadinessProbe.Response(200, ("{\"models\":[" + models + "]}").getBytes(StandardCharsets.UTF_8));
    }
}
