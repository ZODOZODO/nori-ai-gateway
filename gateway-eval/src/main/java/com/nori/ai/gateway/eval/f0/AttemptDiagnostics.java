package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.util.*;

/**
 * 시도(초회·재시도)마다 응답 원문을 메모리에서만 읽어 필드별로 독립 진단합니다. ClassificationJudge 는 첫 위반에서 멈추므로
 * 그 뒤 필드의 상태가 결과에 남지 않았습니다. 이 진단은 판정(valid·실행 인자)을 만들거나 바꾸지 않으며,
 * 값 대신 고정 분류·일치 여부·불리언·개수만 남깁니다(원문·카탈로그 값·부분 문자열·길이 0).
 */
public final class AttemptDiagnostics {
    private static final Set<String> WIRE_KEYS = Set.of("t", "eqp", "from", "to", "qRef"), PLAN_KEYS = Set.of("t", "eqp", "from", "to", "qRef", "steps");
    private AttemptDiagnostics() {}

    /** expected = {t, eqp, from, to, q}(복원 원문 기준 정답). question·context·references 는 같은 요청의 입력입니다. */
    public static ObjectNode diagnose(OllamaChatClient.ChatResult result, int maxTokens, MessageAssembler.Kind kind, QRefContext references, JsonNode expected,
                                      String question, JsonNode context, String timezone, RegistryFixture fixture) {
        ObjectNode d = Json.MAPPER.createObjectNode();
        d.put("finish", result.finishReason().equals("stop") ? "STOP" : result.finishReason().equals("length") ? "LENGTH" : "OTHER");
        d.put("usageWithinLimit", result.completionTokens() >= 0 && result.completionTokens() <= maxTokens);
        JsonNode node;
        try { node = Json.MAPPER.readTree(result.content()); } catch (IOException | RuntimeException e) { node = null; }
        if (node != null && node.isMissingNode()) node = null;
        boolean object = node != null && node.isObject();
        d.put("json", object ? "OBJECT" : node == null ? "PARSE_FAILED" : "NOT_OBJECT");
        if (!object) node = Json.MAPPER.createObjectNode();
        Set<String> expectedKeys = kind == MessageAssembler.Kind.PLAN ? PLAN_KEYS : WIRE_KEYS, keys = GoldenSet.keys(node);
        boolean missing = !keys.containsAll(expectedKeys), extra = !expectedKeys.containsAll(keys);
        d.put("keys", !object ? "NOT_EVALUABLE" : missing && extra ? "MISSING_AND_EXTRA" : missing ? "MISSING" : extra ? "EXTRA" : "EXACT");
        d.put("rawQKey", node.has("q"));

        JsonNode t = node.get("t");
        String tValue = !object ? "NOT_EVALUABLE" : t == null ? "ABSENT" : !t.isTextual() ? "NON_TEXT" : RegistryFixture.TASK_TYPES.contains(t.textValue()) ? t.textValue() : "UNKNOWN_TEXT";
        d.putObject("t").put("value", tValue).put("match", !object ? "NOT_EVALUABLE" : t != null && t.isTextual() && t.textValue().equals(expected.path("t").asText()) ? "MATCH" : "MISMATCH");

        JsonNode reference = node.get("qRef"); ObjectNode qRef = d.putObject("qRef");
        qRef.put("kind", !object ? "NOT_EVALUABLE" : referenceKind(reference, references)); qRef.put("candidateCount", references.catalog().size());
        boolean accepted = object && reference != null && references.accepts(reference);
        qRef.put("accepted", accepted);
        qRef.put("textInQuestion", reference != null && reference.isTextual() && !reference.textValue().isEmpty() && question.contains(reference.textValue()));

        Set<String> contextEqp = new HashSet<>(); context.path("selectedResourceRefs").forEach(ref -> contextEqp.add(ref.path("id").asText()));
        ObjectNode eqp = argument(d, "eqp", node, object, expected);
        String eqpText = textOf(node.get("eqp"));
        eqp.put("inQuestion", eqpText != null && question.contains(eqpText)); eqp.put("equalsContext", eqpText != null && contextEqp.contains(eqpText));
        eqp.put("equalsQuoteCandidate", eqpText != null && references.isCandidate(eqpText)); eqp.put("registered", eqpText != null && fixture.resources().eqpExists(eqpText));
        for (String field : List.of("from", "to")) {
            ObjectNode time = argument(d, field, node, object, expected);
            String text = textOf(node.get(field));
            time.put("formatValid", text != null && validTime(text, timezone));
            time.put("equalsContext", text != null && text.equals(context.path("timeWindow").path(field).asText(null)));
        }
        String resolved = accepted ? references.resolve(reference) : null;
        d.putObject("q").put("match", !accepted ? "NOT_EVALUABLE" : Objects.equals(resolved, expected.path("q").isNull() ? null : expected.path("q").asText()) ? "MATCH" : "MISMATCH");
        if (kind == MessageAssembler.Kind.PLAN) steps(d.putObject("steps"), node, object, tValue, fixture);
        return d;
    }
    private static String referenceKind(JsonNode reference, QRefContext references) {
        if (reference == null) return "ABSENT";
        if (reference.isNull()) return "JSON_NULL";
        if (!reference.isTextual()) return "NON_TEXT";
        String text = reference.textValue();
        if (text.isEmpty()) return "EMPTY_TEXT";
        if (!text.matches("Q[1-9][0-9]*")) return "TEXT_OTHER";
        return references.accepts(reference) ? "ID_IN_RANGE" : "ID_OUT_OF_RANGE";
    }
    private static ObjectNode argument(ObjectNode d, String field, JsonNode node, boolean object, JsonNode expected) {
        JsonNode value = node.get(field); ObjectNode out = d.putObject(field);
        out.put("value", !object ? "NOT_EVALUABLE" : value == null ? "ABSENT" : value.isNull() ? "NULL" : !value.isTextual() ? "NON_TEXT" : value.textValue().isEmpty() ? "EMPTY_TEXT" : "TEXT");
        String expectedValue = expected.path(field).isNull() ? null : expected.path(field).asText();
        boolean evaluable = object && value != null && (value.isNull() || value.isTextual());
        out.put("match", !object || value == null ? "NOT_EVALUABLE" : evaluable && Objects.equals(value.isNull() ? null : value.textValue(), expectedValue) ? "MATCH" : "MISMATCH");
        return out;
    }
    private static String textOf(JsonNode value) { return value != null && value.isTextual() && !value.textValue().isEmpty() ? value.textValue() : null; }
    private static boolean validTime(String text, String timezone) {
        if (!text.matches(ClassificationJudge.TIME)) return false;
        try { ArgLimits.normalizeTime(text, timezone); return true; } catch (EvalException e) { return false; }
    }
    private static void steps(ObjectNode out, JsonNode node, boolean object, String tValue, RegistryFixture fixture) {
        JsonNode array = node.get("steps"); int unknown = 0, inactive = 0;
        String shape = !object ? "NOT_EVALUABLE" : array == null ? "ABSENT" : !array.isArray() ? "NOT_ARRAY" : array.size() > 10 ? "TOO_MANY" : "OK";
        if (shape.equals("OK")) for (JsonNode step : array) {
            if (!step.isTextual()) { shape = "ITEM_NOT_TEXT"; continue; }
            if (!step.textValue().matches("[a-z]+(?:\\.[A-Za-z]+){1,4}") && shape.equals("OK")) shape = "ITEM_FORMAT_INVALID";
            if (!fixture.known(step.textValue())) unknown++; else if (!fixture.implemented(step.textValue())) inactive++;
        }
        out.put("shape", shape); out.put("unknownCount", unknown); out.put("inactiveCount", inactive);
        if (!shape.equals("OK") || !RegistryFixture.TASK_TYPES.contains(tValue)) { out.put("toolConsistency", "NOT_EVALUABLE"); return; }
        boolean noTool = Set.of("GQ", "UNS").contains(tValue);
        out.put("toolConsistency", noTool == array.isEmpty() ? "CONSISTENT" : noTool ? "NONEMPTY_FOR_NO_TOOL_TASK" : "EMPTY_FOR_TOOL_TASK");
    }
}
