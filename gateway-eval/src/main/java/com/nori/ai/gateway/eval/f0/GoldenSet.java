package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** F0 골든셋(jsonl) 로더와 스키마·필수 문항 검사. */
public final class GoldenSet {
    public static final String AC38_QUESTION = "WM 으로 보낸 WORK_ORDER_REPLY 가 왜 실패했어?";
    public static final Set<String> REQUIRED_CATEGORIES = Set.of("PLANNER_INCIDENT", "PLANNER_STATUS_LOT", "PLANNER_WM_INVENTORY", "PLANNER_KNOWLEDGE", "GQ_POSITIVE", "GQ_BYPASS_NEGATIVE", "UNS_LATER_PHASE", "AC38_CROSS_SYSTEM_NAME");
    public record Expected(String t, String eqp, String from, String to, String q, String workflow, boolean unsupported) {
        public String field(String name) { return switch (name) { case "eqp" -> eqp; case "from" -> from; case "to" -> to; case "q" -> q; default -> throw new IllegalArgumentException(); }; }
    }
    public record Question(String id, String category, String question, JsonNode context, Expected expected) {
        @Override public String toString() { return "Question[" + id + "]"; }
    }
    public static final List<String> ARG_FIELDS = List.of("eqp", "from", "to", "q");
    private final List<Question> questions;
    private final byte[] bytes;
    private GoldenSet(byte[] bytes, List<Question> questions) { this.bytes = bytes; this.questions = questions; }
    public List<Question> questions() { return questions; }
    public byte[] bytes() { return bytes.clone(); }
    public Question byId(String id) { return questions.stream().filter(q -> q.id().equals(id)).findFirst().orElseThrow(() -> new EvalException(2, "GOLDEN_QUESTION_MISSING")); }

    public static GoldenSet load(Path path, RegistryFixture fixture) { return parse(Json.bytes(path), fixture); }
    public static GoldenSet parse(byte[] bytes, RegistryFixture fixture) {
        List<Question> questions = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank()) continue;
            JsonNode n = Json.parse(line.getBytes(StandardCharsets.UTF_8));
            if (!keys(n).equals(Set.of("id", "question", "context", "expected", "category"))) throw new EvalException(2, "GOLDEN_INVALID");
            String id = n.path("id").asText("");
            if (!id.matches("[A-Z0-9_-]{1,32}") || !ids.add(id) || !n.path("question").isTextual() || n.path("question").asText().isBlank() || !n.path("category").asText("").matches("[A-Z0-9_]{1,64}")) throw new EvalException(2, "GOLDEN_INVALID");
            JsonNode context = n.path("context"), e = n.path("expected");
            validateContext(context, fixture);
            if (!keys(e).equals(Set.of("t", "eqp", "from", "to", "q", "workflow", "unsupported")) || !RegistryFixture.TASK_TYPES.contains(e.path("t").asText()) || !e.path("unsupported").isBoolean()) throw new EvalException(2, "GOLDEN_INVALID");
            for (String f : List.of("eqp", "from", "to", "q", "workflow")) if (!e.path(f).isNull() && !(e.path(f).isTextual() && !e.path(f).asText().isEmpty())) throw new EvalException(2, "GOLDEN_INVALID");
            String t = e.path("t").asText();
            Expected expected = new Expected(t, text(e, "eqp"), text(e, "from"), text(e, "to"), text(e, "q"), text(e, "workflow"), e.path("unsupported").asBoolean());
            // 기대값은 Registry 와 모순될 수 없습니다(UNS ⇔ unsupported, workflow = t 의 고정 workflow, eqp 는 등록 리소스).
            if (expected.unsupported() != t.equals("UNS") || !Objects.equals(expected.workflow(), fixture.workflowFor(t)) || (expected.eqp() != null && !fixture.resources().eqpExists(expected.eqp()))) throw new EvalException(2, "GOLDEN_INVALID");
            for (String f : List.of("from", "to")) if (expected.field(f) != null && !expected.field(f).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}")) throw new EvalException(2, "GOLDEN_INVALID");
            questions.add(new Question(id, n.path("category").asText(), n.path("question").asText(), context.deepCopy(), expected));
        }
        if (questions.size() < 20) throw new EvalException(2, "GOLDEN_TOO_SMALL");
        Set<String> categories = new HashSet<>(); questions.forEach(q -> categories.add(q.category()));
        if (!categories.containsAll(REQUIRED_CATEGORIES)) throw new EvalException(2, "GOLDEN_REQUIRED_MISSING");
        List<Question> ac38 = questions.stream().filter(q -> q.category().equals("AC38_CROSS_SYSTEM_NAME")).toList();
        if (ac38.size() != 1 || !ac38.getFirst().question().equals(AC38_QUESTION) || !ac38Context(ac38.getFirst().context()) || !ac38.getFirst().expected().t().equals("INC")) throw new EvalException(2, "GOLDEN_AC38_INVALID");
        return new GoldenSet(bytes.clone(), List.copyOf(questions));
    }
    /** SPEC §27.2 AC-38 공통 TC Context: selectedResourceRefs=[EQP PHOTO_001], timezone=+09:00, timeWindow [2026-10-03T00:00, 2026-10-04T00:00). */
    public static boolean ac38Context(JsonNode context) {
        JsonNode refs = context.path("selectedResourceRefs"), window = context.path("timeWindow");
        return context.path("timezone").asText().equals("+09:00") && refs.size() == 1 && refs.path(0).path("type").asText().equals("EQP") && refs.path(0).path("id").asText().equals("PHOTO_001")
            && window.path("from").asText().equals("2026-10-03T00:00") && window.path("to").asText().equals("2026-10-04T00:00");
    }
    static void validateContext(JsonNode context, RegistryFixture fixture) {
        if (!keys(context).equals(Set.of("timezone", "referenceTime", "timeWindow", "selectedResourceRefs")) || !context.path("timezone").asText("").matches("[+-]\\d{2}:\\d{2}") || !context.path("referenceTime").asText("").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}")) throw new EvalException(2, "CONTEXT_INVALID");
        JsonNode window = context.path("timeWindow");
        if (!window.isNull() && (!keys(window).equals(Set.of("from", "to")) || !window.path("from").asText("").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}") || !window.path("to").asText("").matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}") || window.path("from").asText().compareTo(window.path("to").asText()) >= 0)) throw new EvalException(2, "CONTEXT_INVALID");
        if (!context.path("selectedResourceRefs").isArray()) throw new EvalException(2, "CONTEXT_INVALID");
        for (JsonNode ref : context.path("selectedResourceRefs")) if (!keys(ref).equals(Set.of("type", "id")) || !fixture.resources().eqpExists(ref.path("id").asText()) || !ref.path("type").asText().equals("EQP")) throw new EvalException(2, "CONTEXT_INVALID");
    }
    static Set<String> keys(JsonNode node) { Set<String> keys = new HashSet<>(); if (node.isObject()) node.fieldNames().forEachRemaining(keys::add); return keys; }
    private static String text(JsonNode node, String key) { return node.path(key).isNull() ? null : node.path(key).asText(); }
}
