package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 추출 인자의 리소스 존재·타입 판정. 결과 파일에는 값 대신 SHA 만 남기므로 SHA 로도 조회한다. */
public final class ResourceFixture {
    private final Map<String,String> typeById = new LinkedHashMap<>();
    private final Set<String> eqpValueShas = new HashSet<>();
    ResourceFixture(JsonNode resources) {
        if (!resources.isArray() || resources.isEmpty()) throw new EvalException(2, "FIXTURE_INVALID");
        for (JsonNode r : resources) {
            String type = r.path("type").asText(""), id = r.path("id").asText("");
            if (!type.equals("EQP") || id.isBlank() || id.chars().anyMatch(c -> c < 32) || typeById.put(id, type) != null) throw new EvalException(2, "FIXTURE_INVALID");
            eqpValueShas.add(valueSha(id));
        }
    }
    public boolean eqpExists(String id) { return id != null && "EQP".equals(typeById.get(id)); }
    public boolean eqpShaExists(String sha) { return eqpValueShas.contains(sha); }
    public List<String> eqpIds() { return List.copyOf(typeById.keySet()); }
    /** 인자 값의 비교용 해시 — 값(문자열 또는 null)의 RFC 8785 JSON 바이트 SHA-256. */
    public static String valueSha(String value) {
        return Json.sha((value == null ? "null" : Json.canonicalString(Json.MAPPER.getNodeFactory().textNode(value))).getBytes(StandardCharsets.UTF_8));
    }
}
