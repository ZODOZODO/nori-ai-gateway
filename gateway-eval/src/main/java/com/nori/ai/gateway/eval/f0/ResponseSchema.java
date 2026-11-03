package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * F0 v3 요청별 OpenAI 호환 response_format(json_schema) 생성기(정책 JSON_SCHEMA_V1·JSON_SCHEMA_PLAN_ORDERED_V1).
 * 스키마는 wire 계약의 구조(필수 키·추가 키 금지·t enum·nullable 인자·steps 문자열 배열)와 같은 요청 QRefContext 의 발급 참조만 담는다.
 * 골든 정답·기대 출력·리소스 목록·capability 목록은 넣지 않으며(비활성·미등록 Tool backstop 은 판정 단계 그대로), 서버 제약 여부와 무관하게 Gateway 판정은 같다.
 */
public final class ResponseSchema {
    public static final String POLICY = "JSON_SCHEMA_V1";
    /**
     * JSON_SCHEMA_V1 과 같은 스키마 내용이되 계획(PLAN) 스키마의 properties 만 선언 순서(t, eqp, from, to, qRef, steps)로 송신합니다.
     * 요청 본문은 키 정렬 canonical JSON 이라 V1 의 properties 는 eqp, from, qRef, steps, t, to 순서로 서버에 가고, 계획 출력은 이 순서로 생성돼
     * steps 가 t 보다 먼저 확정됐습니다(Devreport §12.19·§12.20). 분류(CLASSIFY) 스키마는 V1 과 송신 바이트가 같습니다.
     */
    public static final String PLAN_ORDERED_POLICY = "JSON_SCHEMA_PLAN_ORDERED_V1";
    private static final List<String> PROPERTIES_PATH = List.of("json_schema", "schema", "properties");
    private ResponseSchema() {}

    /** 송신할 response_format 과 그 직렬화 규칙. responseFormatSha256 = bytes()(요청 본문 안 response_format 값의 실제 송신 바이트)의 SHA-256 입니다. */
    public record Format(ObjectNode node, Set<List<String>> orderedPaths) {
        public byte[] bytes() { return canonicalKeepingOrder(node, orderedPaths); }
        public String sha256() { return Json.sha(bytes()); }
    }
    /** 등록 parameters.responseFormat 값의 송신 형식. NONE 이면 null(v1/v2 와 같은 요청 body)입니다. */
    public static Format forPolicy(String policy, MessageAssembler.Kind kind, QRefContext references) {
        return switch (policy) {
            case "NONE" -> null;
            case POLICY -> new Format(responseFormat(kind, references), Set.of());
            case PLAN_ORDERED_POLICY -> new Format(responseFormat(kind, references), kind == MessageAssembler.Kind.PLAN ? Set.of(PROPERTIES_PATH) : Set.of());
            default -> throw new EvalException(2, "PREREG_INVALID");
        };
    }
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
    /**
     * Json.canonical 과 같은 문자열·숫자 규칙이되 keepOrder 에 든 경로(루트에서의 키 이름 목록)의 객체만 키를 정렬하지 않고 노드 순서로 씁니다.
     * keepOrder 가 비면 Json.canonical 그대로이며, 경로 밖 값은 Json.canonicalString 바이트를 그대로 씁니다(배열 안 객체는 경로로 지정되지 않음).
     */
    static byte[] canonicalKeepingOrder(JsonNode node, Set<List<String>> keepOrder) {
        if (keepOrder.isEmpty()) return Json.canonical(node);
        return render(node, List.of(), keepOrder).getBytes(StandardCharsets.UTF_8);
    }
    private static String render(JsonNode node, List<String> path, Set<List<String>> keepOrder) {
        if (!node.isObject() || keepOrder.stream().noneMatch(p -> p.size() >= path.size() && p.subList(0, path.size()).equals(path))) return Json.canonicalString(node);
        List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add);
        if (!keepOrder.contains(path)) Collections.sort(names);
        List<String> fields = new ArrayList<>();
        for (String name : names) {
            List<String> child = new ArrayList<>(path); child.add(name);
            fields.add(Json.canonicalString(TextNode.valueOf(name)) + ":" + render(node.get(name), child, keepOrder));
        }
        return "{" + String.join(",", fields) + "}";
    }
}
