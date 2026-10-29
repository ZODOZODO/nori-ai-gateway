package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

class GoldenSetSchemaTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    final RegistryFixture fixture = RegistryFixture.load(ROOT.resolve("eval/golden/tc-registry-fixture-v1.json"));

    @Test void repositoryGoldenSetHasRequiredItemsAndExactAc38Context() {
        GoldenSet golden = GoldenSet.load(ROOT.resolve("eval/golden/tc-f0-v1.jsonl"), fixture);
        assertThat(golden.questions().size()).isGreaterThanOrEqualTo(20);
        Map<String,GoldenSet.Question> byCategory = new HashMap<>(); golden.questions().forEach(q -> byCategory.putIfAbsent(q.category(), q));
        assertThat(byCategory.keySet()).containsAll(GoldenSet.REQUIRED_CATEGORIES);
        assertThat(byCategory.get("PLANNER_WM_INVENTORY").expected().t()).isEqualTo("UNS");
        assertThat(byCategory.get("UNS_LATER_PHASE").question()).isEqualTo("이 모델 체크아웃해줘"); assertThat(byCategory.get("UNS_LATER_PHASE").expected().t()).isEqualTo("UNS");
        assertThat(byCategory.get("GQ_POSITIVE").question()).isEqualTo("여기서 뭘 물어볼 수 있어?"); assertThat(byCategory.get("GQ_POSITIVE").expected().t()).isEqualTo("GQ");
        assertThat(byCategory.get("GQ_BYPASS_NEGATIVE").expected().t()).isNotEqualTo("GQ");
        assertThat(byCategory.get("PLANNER_STATUS_LOT").expected().eqp()).isEqualTo("EQP_001");
        GoldenSet.Question ac38 = byCategory.get("AC38_CROSS_SYSTEM_NAME");
        assertThat(ac38.question()).isEqualTo("WM 으로 보낸 WORK_ORDER_REPLY 가 왜 실패했어?");
        assertThat(ac38.expected().t()).isEqualTo("INC"); assertThat(ac38.expected().workflow()).isEqualTo("INCIDENT_ANALYSIS"); assertThat(ac38.expected().unsupported()).isFalse();
        assertThat(Json.canonicalString(ac38.context())).isEqualTo("{\"referenceTime\":\"2026-10-04T09:00:00+09:00\",\"selectedResourceRefs\":[{\"id\":\"PHOTO_001\",\"type\":\"EQP\"}],\"timeWindow\":{\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-04T00:00\"},\"timezone\":\"+09:00\"}");
        assertThat(fixture.stepsFor("INC")).contains("tc.logs.search", "tc.git.searchCode");
        long positives = golden.questions().stream().filter(q -> q.expected().unsupported()).count();
        assertThat(positives).isBetween(1L, (long)golden.questions().size() - 1);
    }
    @Test void schemaViolationsAndMissingRequiredItemsAreRejected() throws Exception {
        List<String> lines = Files.readAllLines(ROOT.resolve("eval/golden/tc-f0-v1.jsonl"), StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
        rejects(lines, "G05", n -> n.put("category", "OTHER"), "GOLDEN_REQUIRED_MISSING");
        rejects(lines, "G08", n -> ((ObjectNode)n.path("context")).put("timezone", "+00:00"), "GOLDEN_AC38_INVALID");
        rejects(lines, "G08", n -> ((ObjectNode)n.path("context").path("timeWindow")).put("to", "2026-10-03T23:59"), "GOLDEN_AC38_INVALID");
        rejects(lines, "G08", n -> ((ObjectNode)n.path("expected")).put("t", "UNS").put("unsupported", true).putNull("workflow"), "GOLDEN_AC38_INVALID");
        rejects(lines, "G01", n -> ((ObjectNode)n.path("expected")).put("eqp", "WM"), "GOLDEN_INVALID");
        rejects(lines, "G01", n -> ((ObjectNode)n.path("expected")).put("unsupported", true), "GOLDEN_INVALID");
        rejects(lines, "G01", n -> ((ObjectNode)n.path("expected")).put("workflow", "LIVE_QUERY"), "GOLDEN_INVALID");
        rejects(lines, "G01", n -> ((ObjectNode)n.path("expected")).put("from", "2026-10-03T00:00:00"), "GOLDEN_INVALID");
        rejects(lines, "G01", n -> n.put("extra", 1), "GOLDEN_INVALID");
        rejects(lines, "G01", n -> ((ObjectNode)n.path("context")).putArray("selectedResourceRefs").addObject().put("type", "EQP").put("id", "NOT_REGISTERED"), "CONTEXT_INVALID");
        assertThatThrownBy(() -> GoldenSet.parse(String.join("\n", lines.subList(0, 19)).getBytes(StandardCharsets.UTF_8), fixture)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("GOLDEN_TOO_SMALL"));
        List<String> duplicated = new ArrayList<>(lines); duplicated.add(lines.get(0));
        assertThatThrownBy(() -> GoldenSet.parse(String.join("\n", duplicated).getBytes(StandardCharsets.UTF_8), fixture)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("GOLDEN_INVALID"));
    }
    void rejects(List<String> lines, String id, Consumer<ObjectNode> change, String code) {
        List<String> changed = new ArrayList<>();
        for (String line : lines) { ObjectNode n = (ObjectNode)Json.parse(line.getBytes(StandardCharsets.UTF_8)); if (n.path("id").asText().equals(id)) change.accept(n); changed.add(Json.canonicalString(n)); }
        assertThatThrownBy(() -> GoldenSet.parse(String.join("\n", changed).getBytes(StandardCharsets.UTF_8), fixture)).as(code).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo(code));
    }
    @Test void classifyMessagesPutStaticRegistryPrefixFirstAndQuestionContextLast() throws Exception {
        GoldenSet golden = GoldenSet.load(ROOT.resolve("eval/golden/tc-f0-v1.jsonl"), fixture);
        MessageAssembler classify = MessageAssembler.load(ROOT.resolve("eval/f0/prompts/classify-v1.txt"), fixture), plan = MessageAssembler.load(ROOT.resolve("eval/f0/prompts/plan-v1.txt"), fixture);
        assertThat(classify.kind()).isEqualTo(MessageAssembler.Kind.CLASSIFY); assertThat(classify.maxTokens()).isEqualTo(128);
        assertThat(plan.kind()).isEqualTo(MessageAssembler.Kind.PLAN); assertThat(plan.maxTokens()).isEqualTo(160);
        var a = classify.messages(golden.questions().get(0).question(), golden.questions().get(0).context());
        var b = classify.messages(golden.questions().get(1).question(), golden.questions().get(1).context());
        assertThat(a.get(0)).isEqualTo(b.get(0));
        String system = a.get(0).get("content");
        assertThat(system).contains("이름 언급이 아니라 요청 대상 자료의 소유 시스템으로 판단한다", "시스템 WM", "tc.model.checkout(PHASE_2 비활성)", "\"t\":\"KQ|LQ|INC|GQ|UNS\"").doesNotContain("{{").doesNotContain(golden.questions().get(0).question());
        assertThat(a.get(1).get("content")).contains(golden.questions().get(0).question());
        assertThat(plan.messages("q", golden.questions().get(0).context()).get(0).get("content")).contains("tc.logs.search 인자: eqp, from, to, q", "160 토큰");
        assertThatThrownBy(() -> new MessageAssembler("no placeholder".getBytes(StandardCharsets.UTF_8), fixture)).isInstanceOf(EvalException.class);
    }
}
