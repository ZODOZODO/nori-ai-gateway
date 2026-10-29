package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;

class BoundaryGenJudgeTest {
    @TempDir Path directory;

    /** 모든 필드를 CONFIRMED 로 둔 시험용 limits 로 NORMAL·MAX·ESCAPE 를 만든 뒤 사전등록을 다시 맞춥니다. */
    static F0TestRoot confirmedRoot(Path directory) throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        f.write("eval/f0/arg-limits-v1.json", ArgLimitsSchemaTest.confirmedLimits(40).toString());
        f.write("eval/f0/output-boundary-cases.jsonl", F0TestRoot.cases(BoundaryCases.generate(ArgLimits.load(f.root.resolve("eval/f0/arg-limits-v1.json")), RegistryFixture.load(f.root.resolve("eval/golden/tc-registry-fixture-v1.json")))));
        f.prereg(F0TestRoot.REGISTERED);
        return f;
    }
    int boundary(F0TestRoot f, OllamaStub stub, String run) {
        return f.exec("boundary-gen", "--endpoint", stub.endpoint(), "--cases", "eval/f0/output-boundary-cases.jsonl", "--fixture", "eval/golden/tc-registry-fixture-v1.json",
            "--prompt", "eval/f0/prompts/classify-v1.txt", "--repeats", "2", "--run-id", run, "--out", "eval/f0/results/" + run + "/boundary");
    }
    @Test void exactCompleteOutputsPassEveryRoundAndOnlyHashesAreStored() throws Exception {
        F0TestRoot f = confirmedRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(boundary(f, stub, "R1")).isZero();
            JsonNode result = f.json("eval/f0/results/R1/boundary/boundary-results.json");
            assertThat(result.path("complete").asBoolean()).isTrue();
            assertThat(result.path("cases")).hasSize(6);
            for (JsonNode c : result.path("cases")) {
                assertThat(c.path("passRounds").asInt()).isEqualTo(2); assertThat(c.path("totalRounds").asInt()).isEqualTo(2);
                for (JsonNode a : c.path("attempts")) assertThat(GoldenSet.keys(a)).isEqualTo(Set.of("round", "status", "retried", "finishReason", "completionTokens", "schemaValid", "argsExact", "latencyMs", "outputSha256"));
            }
            String stored = f.allOutput("eval/f0/results/R1");
            for (JsonNode c : BoundaryCases.load(f.root.resolve("eval/f0/output-boundary-cases.jsonl"))) {
                String q = c.path("expectedOutput").path("q").asText(null);
                if (q != null) assertThat(stored).doesNotContain(q).doesNotContain(Json.canonicalString(c.path("expectedOutput").path("q")));
            }
            assertThat(stored).doesNotContain(OllamaStub.SENTINEL);
            // 라운드로빈: 회차 1 의 모든 케이스 다음 회차 2
            List<String> order = stub.inference().stream().map(r -> OllamaStub.question(r.body())).toList();
            assertThat(order.subList(0, 6)).containsExactlyElementsOf(order.subList(6, 12)).doesNotHaveDuplicates();
        }
    }
    @Test void lengthRetriesOnceTruncationBackslashLossAndOmissionAreNotExact() throws Exception {
        F0TestRoot f = confirmedRoot(directory);
        Function<JsonNode,JsonNode> correct = StubAnswers.correct(f.root);
        Map<String,Integer> calls = new ConcurrentHashMap<>();
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = body -> {
                String question = OllamaStub.question(body); int n = calls.merge(question, 1, Integer::sum);
                JsonNode right = correct.apply(body); String content = right.path("choices").path(0).path("message").path("content").asText();
                JsonNode expected = Json.parse(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (question.startsWith("PHOTO_001 설비에서 2026-10-03 00:00부터")) return n % 2 == 1 ? OllamaStub.chatResponse(content.substring(0, 20), "length", 300, 128) : right; // NORMAL: 첫 시도 잘림 → 재시도 성공
                if (expected.path("q").asText("").startsWith("가A1")) { ObjectNode cut = expected.deepCopy(); String q = cut.path("q").asText(); cut.put("q", q.substring(0, q.length() - 1)); return OllamaStub.chatResponse(Json.canonicalString(cut), "stop", 300, 40); }
                if (expected.path("q").asText("").startsWith("\"\\\u0001")) { ObjectNode lost = expected.deepCopy(); lost.put("q", lost.path("q").asText().replace("\\", "")); return OllamaStub.chatResponse(Json.canonicalString(lost), "stop", 300, 40); }
                if (expected.path("q").asText("").startsWith("가나다")) { ObjectNode omitted = expected.deepCopy(); omitted.remove("q"); return OllamaStub.chatResponse(Json.canonicalString(omitted), "stop", 300, 40); }
                if (expected.path("q").asText("").startsWith("0123")) return OllamaStub.chatResponse(content, "stop", 300, 129);
                return right;
            };
            assertThat(boundary(f, stub, "R2")).isZero();
            Map<String,JsonNode> byCase = new HashMap<>(); f.json("eval/f0/results/R2/boundary/boundary-results.json").path("cases").forEach(c -> byCase.put(c.path("caseId").asText(), c));
            JsonNode normal = byCase.get("NORMAL-01");
            assertThat(normal.path("passRounds").asInt()).isEqualTo(2);
            normal.path("attempts").forEach(a -> { assertThat(a.path("retried").asBoolean()).isTrue(); assertThat(a.path("finishReason").asText()).isEqualTo("stop"); });
            assertThat(calls.entrySet().stream().filter(e -> e.getKey().startsWith("PHOTO_001 설비에서 2026-10-03 00:00부터")).findFirst().orElseThrow().getValue()).isEqualTo(4);
            for (String id : List.of("MAX-MIXED", "ESCAPE-01", "MAX-HANGUL", "MAX-DIGIT")) {
                JsonNode c = byCase.get(id);
                assertThat(c.path("passRounds").asInt()).as(id).isZero(); assertThat(c.path("totalRounds").asInt()).as(id).isEqualTo(2);
                c.path("attempts").forEach(a -> assertThat(a.path("argsExact").asBoolean()).as(id).isFalse());
            }
            byCase.get("MAX-HANGUL").path("attempts").forEach(a -> { assertThat(a.path("schemaValid").asBoolean()).isFalse(); assertThat(a.path("retried").asBoolean()).isTrue(); });
            byCase.get("MAX-DIGIT").path("attempts").forEach(a -> assertThat(a.path("completionTokens").asInt()).isEqualTo(129));
            assertThat(byCase.get("MAX-ASCII").path("passRounds").asInt()).isEqualTo(2);
        }
    }
    @Test void argsExactComparesCodePointsForEveryField() {
        JsonNode expected = Json.parse("{\"t\":\"INC\",\"eqp\":\"E\",\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-04T00:00\",\"q\":\"a\\\\\\u0001\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String,String> args = new HashMap<>(Map.of("eqp", "E", "from", "2026-10-03T00:00", "to", "2026-10-04T00:00")); args.put("q", "a\\\u0001");
        assertThat(F0Measurements.argsExact(new ClassificationJudge.Verdict(true, null, "INC", args, List.of()), expected)).isTrue();
        args.put("q", "a\\"); assertThat(F0Measurements.argsExact(new ClassificationJudge.Verdict(true, null, "INC", args, List.of()), expected)).isFalse();
        args.put("q", "a\\\u0001"); assertThat(F0Measurements.argsExact(new ClassificationJudge.Verdict(true, null, "LQ", args, List.of()), expected)).isFalse();
        args.put("q", null); assertThat(F0Measurements.argsExact(new ClassificationJudge.Verdict(true, null, "INC", args, List.of()), expected)).isFalse();
    }
}
