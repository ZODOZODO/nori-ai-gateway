package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** 분류(Path B)·계획(Path A) 메시지 조립. 정적 접두부(시스템 지시 + Registry 참고 정보)를 먼저 두고 질문·Context 는 마지막 user 메시지에만 둔다. */
public final class MessageAssembler {
    public enum Kind { CLASSIFY, PLAN }
    /**
     * LLM 에 보이는 Context 범위. FULL = 기존 등록과 같은 전체 Context 바이트.
     * EXTRACTION_V1 = 상대 시간 해석에 필요한 timezone·referenceTime 만 전달합니다. timeWindow·selectedResourceRefs 는
     * SPEC §14.5/§22.2 상 Gateway 가 LLM 출력 뒤에 적용하므로 추출 입력에서 뺍니다(원 Context·판정·진단은 그대로 전체 값 사용).
     */
    public static final String FULL = "FULL", EXTRACTION_V1 = "EXTRACTION_V1";
    private static final List<String> EXTRACTION_FIELDS = List.of("timezone", "referenceTime");
    private final String system;
    private final Kind kind;
    private final byte[] promptBytes;
    private final String contextProjection;

    public MessageAssembler(byte[] promptBytes, RegistryFixture fixture) { this(promptBytes, fixture, FULL); }
    public MessageAssembler(byte[] promptBytes, RegistryFixture fixture, String contextProjection) {
        if (!FULL.equals(contextProjection) && !EXTRACTION_V1.equals(contextProjection)) throw new EvalException(2, "CONTEXT_PROJECTION_INVALID");
        this.contextProjection = contextProjection;
        this.promptBytes = promptBytes.clone();
        String prompt = new String(promptBytes, StandardCharsets.UTF_8);
        if (prompt.indexOf('\r') >= 0 || !prompt.contains("{{REGISTRY}}") || prompt.indexOf("{{REGISTRY}}") != prompt.lastIndexOf("{{REGISTRY}}")) throw new EvalException(2, "PROMPT_INVALID");
        kind = prompt.contains("{{TOOLS}}") ? Kind.PLAN : Kind.CLASSIFY;
        String rendered = prompt.replace("{{REGISTRY}}", fixture.renderRegistry());
        if (kind == Kind.PLAN) rendered = rendered.replace("{{TOOLS}}", fixture.renderTools());
        if (rendered.contains("{{")) throw new EvalException(2, "PROMPT_INVALID");
        system = rendered;
    }
    public static MessageAssembler load(Path prompt, RegistryFixture fixture) { return new MessageAssembler(Json.bytes(prompt), fixture); }
    public static MessageAssembler load(Path prompt, RegistryFixture fixture, String contextProjection) { return new MessageAssembler(Json.bytes(prompt), fixture, contextProjection); }
    public Kind kind() { return kind; }
    public String contextProjection() { return contextProjection; }
    public byte[] promptBytes() { return promptBytes.clone(); }
    public String systemPrefixSha256() { return Json.sha(system.getBytes(StandardCharsets.UTF_8)); }
    public List<Map<String,String>> messages(String question, JsonNode context) {
        ObjectNode user = Json.MAPPER.createObjectNode();
        user.set("context", project(context)); user.put("question", question);
        return List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", Json.canonicalString(user)));
    }
    public List<Map<String,String>> messages(String question, JsonNode context, QRefContext references) {
        references.requireQuestionMatch(question);
        ObjectNode user = Json.MAPPER.createObjectNode();
        user.set("context", project(context)); user.put("question", question); user.set("qRefCatalog", references.catalog());
        return List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", Json.canonicalString(user)));
    }
    /** FULL 은 받은 Context 그대로, EXTRACTION_V1 은 새 노드에 두 필드만 복사합니다(원 Context 는 변경 0). 필드가 문자열이 아니면 호출 전에 거부합니다. */
    private JsonNode project(JsonNode context) {
        if (contextProjection.equals(FULL)) return context;
        ObjectNode projected = Json.MAPPER.createObjectNode();
        for (String field : EXTRACTION_FIELDS) {
            if (!context.path(field).isTextual()) throw new EvalException(2, "CONTEXT_PROJECTION_INVALID");
            projected.put(field, context.path(field).textValue());
        }
        return projected;
    }
    /** 1회 재시도 메시지: 원 메시지에 오류 요지(고정 코드)만 덧붙입니다. 직전 출력 원문은 다시 싣지 않습니다. */
    public static List<Map<String,String>> retry(List<Map<String,String>> original, String errorCode) {
        List<Map<String,String>> messages = new ArrayList<>(original);
        messages.add(Map.of("role", "user", "content", "직전 출력이 무효다(오류: " + errorCode + "). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다."));
        return List.copyOf(messages);
    }
}
