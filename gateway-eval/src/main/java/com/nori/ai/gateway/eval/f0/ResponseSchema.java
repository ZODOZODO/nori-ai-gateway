package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;

/**
 * F0 v3 요청별 OpenAI 호환 response_format(json_schema) 생성기(정책 JSON_SCHEMA_V1).
 * 스키마는 wire 계약의 구조(필수 키·추가 키 금지·t enum·nullable 인자·steps 문자열 배열)와 같은 요청 QRefContext 의 발급 참조만 담는다.
 * 골든 정답·기대 출력·리소스 목록·capability 목록은 넣지 않으며(비활성·미등록 Tool backstop 은 판정 단계 그대로), 서버 제약 여부와 무관하게 Gateway 판정은 같다.
 */
public final class ResponseSchema {
    public static final String POLICY = "JSON_SCHEMA_V1";
    private ResponseSchema() {}

    public static ObjectNode responseFormat(MessageAssembler.Kind kind, QRefContext references) {
        ObjectNode schema = Json.MAPPER.createObjectNode(); schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode taskTypes = properties.putObject("t").put("type", "string").putArray("enum");
        RegistryFixture.TASK_TYPES.forEach(taskTypes::add);
        for (String field : java.util.List.of("eqp", "from", "to")) properties.putObject(field).putArray("type").add("string").add("null");
        // 후보가 없으면 null 만, 있으면 그 요청에 발급한 ID 만 허용합니다(null·다른 요청 ID 불허 — SPEC §14.5 qRef 계약).
        ArrayNode catalog = references.catalog();
        if (catalog.isEmpty()) properties.putObject("qRef").put("type", "null");
        else properties.putObject("qRef").put("type", "string").set("enum", catalog);
        ArrayNode required = schema.putArray("required").add("t").add("eqp").add("from").add("to").add("qRef");
        if (kind == MessageAssembler.Kind.PLAN) {
            properties.putObject("steps").put("type", "array").putObject("items").put("type", "string");
            required.add("steps");
        }
        schema.put("additionalProperties", false);
        ObjectNode format = Json.MAPPER.createObjectNode(); format.put("type", "json_schema");
        ObjectNode jsonSchema = format.putObject("json_schema");
        jsonSchema.put("name", kind == MessageAssembler.Kind.PLAN ? "f0_plan" : "f0_classify"); jsonSchema.put("strict", true); jsonSchema.set("schema", schema);
        return format;
    }
    public static String sha256(ObjectNode responseFormat) { return Json.sha(Json.canonical(responseFormat)); }
}
