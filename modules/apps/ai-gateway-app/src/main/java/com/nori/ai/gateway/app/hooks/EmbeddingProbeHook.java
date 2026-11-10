package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.integration.inference.InferenceReadinessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * P22 embedding 프로필 측정 hook(Plan §4.5 M-5). 배포 Gateway 의 실제 {@link InferenceClient#embed} 를 고정 입력으로 1회 호출해
 * 차원 수와 readiness 가 마지막으로 읽은 bge-m3 digest 만 돌려준다(벡터 값·입력·주소 반환 0, 요청 본문 미사용).
 */
@RestController
@RequestMapping("/__test")
@Profile("verify")
@ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "true")
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
public final class EmbeddingProbeHook {
    static final String PROBE_INPUT = "probe";
    private final InferenceClient inference;
    private final InferenceReadinessService readiness;

    public EmbeddingProbeHook(InferenceClient inference, InferenceReadinessService readiness) {
        this.inference = inference;
        this.readiness = readiness;
    }

    @PostMapping("/embedding-probe")
    public ResponseEntity<Map<String,Object>> probe() {
        final List<float[]> vectors;
        try { vectors = inference.embed(List.of(PROBE_INPUT)); }
        catch (InferenceClient.EmbeddingFailed failed) { return ResponseEntity.status(503).body(Map.of("reason", failed.reason().name())); }
        var response = new LinkedHashMap<String,Object>();
        response.put("dimension", vectors.getFirst().length);
        response.put("modelDigest", readiness.snapshot().embeddingDigest());
        return ResponseEntity.ok(response);
    }
}
