package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.*;
import java.util.*;

/** 보존된 원시 결과만 입력으로 PREREG 판정 규칙을 적용한다(측정 판정·재판정이 같은 코드). 필수 자료가 없는 leaf 는 판정을 만들지 않는다. */
public final class F0Judgement {
    public static final List<String> AC37_LEAVES = List.of("AC-37-CMP", "AC-37-OUT-NORMAL", "AC-37-OUT-MAX", "AC-37-OUT-ESCAPE");
    public static final List<String> LEAVES = List.of("AC-37-CMP", "AC-37-OUT-NORMAL", "AC-37-OUT-MAX", "AC-37-OUT-ESCAPE", "AC-38-F0");
    public record Verdict(String leafId, String result, String reasonCode, ObjectNode metrics) {}
    public record Outcome(Map<String,Verdict> verdicts, Map<String,MetricsAggregator.Metrics> paths, List<ObjectNode> boundaryRows, ObjectNode usage, List<String> notes) {}
    /** 결과 디렉터리 안의 측정 결과(측정 명령별 run-binding 과 짝)와 Python 공식 계수. */
    public record Results(Map<String,JsonNode> files, Map<String,Path> paths) {
        public JsonNode get(String key) { return files.get(key); }
    }
    private F0Judgement() {}

    public static Outcome judge(Results results, Prereg.Document prereg, GoldenSet golden, RegistryFixture fixture, String tokenizerSha256, String casesSha256) {
        Map<String,Verdict> verdicts = new LinkedHashMap<>(); Map<String,MetricsAggregator.Metrics> paths = new TreeMap<>(); List<String> notes = new ArrayList<>();
        int repeats = prereg.repeats();
        JsonNode a = complete(results.get("A"), repeats), b = complete(results.get("B"), repeats);
        if (a != null && b != null) {
            ObjectNode metrics = Json.MAPPER.createObjectNode();
            try {
                for (var entry : Map.of("A", a, "B", b).entrySet()) {
                    List<JsonNode> attempts = new ArrayList<>(); entry.getValue().path("attempts").forEach(attempts::add);
                    if (entry.getValue().path("questionCount").asInt() != golden.questions().size()) throw new EvalException(2, "DENOMINATOR_DRIFT");
                    MetricsAggregator.Metrics m = MetricsAggregator.aggregate(attempts, golden, repeats);
                    paths.put(entry.getKey(), m); metrics.set(entry.getKey(), Json.MAPPER.valueToTree(m));
                }
                String decision = candidate(paths.get("A"), paths.get("B"), prereg.parameters().path("latencyBudgetMsByT"));
                metrics.put("decision", decision); metrics.put("finalPromotionRecorded", false);
                verdicts.put("AC-37-CMP", new Verdict("AC-37-CMP", "PASS", decision, metrics));
                verdicts.put("AC-38-F0", ac38(a, b, golden, fixture));
            } catch (EvalException e) {
                if (!e.getMessage().equals("DENOMINATOR_DRIFT")) throw e;
                verdicts.put("AC-37-CMP", new Verdict("AC-37-CMP", "FAIL", "DENOMINATOR_DRIFT", Json.MAPPER.createObjectNode()));
            }
        } else notes.add("CMP_INPUT_MISSING");
        List<ObjectNode> rows = new ArrayList<>();
        JsonNode boundary = complete(results.get("boundary"), repeats), gguf = complete(results.get("gguf"), 1), official = results.get("official");
        if (official != null && (!official.path("tokenizerSha256").asText().equals(tokenizerSha256) || !official.path("casesSha256").asText().equals(casesSha256))) { notes.add("OFFICIAL_COUNT_UNBOUND"); official = null; }
        if (boundary != null && gguf != null && official != null) {
            boolean calibrated = calibrated(gguf, "ggufTokens") && calibrated(official, "officialTokens");
            for (String kind : List.of("NORMAL", "MAX", "ESCAPE")) {
                String leaf = "AC-37-OUT-" + kind;
                List<JsonNode> cases = new ArrayList<>(); boundary.path("cases").forEach(c -> { if (c.path("kind").asText().equals(kind)) cases.add(c); });
                if (cases.isEmpty()) { notes.add(leaf + "_NO_CASES"); continue; }
                if (!calibrated) { verdicts.put(leaf, new Verdict(leaf, "NOT_RUN", "TOKEN_COUNT_CALIBRATION_MISMATCH", Json.MAPPER.createObjectNode())); continue; }
                verdicts.put(leaf, outVerdict(leaf, cases, gguf, official, prereg, repeats, rows));
            }
        } else notes.add("OUT_INPUT_MISSING");
        ObjectNode usage = results.get("usage") == null || !results.get("usage").path("complete").asBoolean() ? null : (ObjectNode)results.get("usage").path("equalOnCacheHit").deepCopy();
        return new Outcome(verdicts, paths, rows, usage, notes);
    }
    static JsonNode complete(JsonNode node, int repeats) { return node != null && node.path("complete").asBoolean() && node.path("repeats").asInt() == repeats ? node : null; }
    private static boolean calibrated(JsonNode counts, String field) {
        Map<String,Integer> expected = Map.of("SPEC-14.5-OLD-EXAMPLE", 57, "SPEC-14.5-OLD-EXAMPLE-NO-Q", 53); Set<String> seen = new HashSet<>();
        for (JsonNode c : counts.path("calibration")) { if (!Objects.equals(expected.get(c.path("id").asText()), c.path(field).isInt() ? c.path(field).asInt() : null)) return false; seen.add(c.path("id").asText()); }
        return seen.equals(expected.keySet());
    }
    /** PREREG: 각 케이스 max(공식, GGUF) + 종료 여유 ≤ 128 AND 모든 회차 pass(분모 = 케이스 × R, 실패 회차 포함). */
    private static Verdict outVerdict(String leaf, List<JsonNode> cases, JsonNode gguf, JsonNode official, Prereg.Document prereg, int repeats, List<ObjectNode> rows) {
        int limit = prereg.parameters().path("outputLimitTokens").asInt(128), margin = prereg.parameters().path("terminationMarginTokens").asInt(1);
        boolean pass = true; int maxTokens = 0, passRounds = 0, totalRounds = 0, retried = 0; List<Long> latency = new ArrayList<>();
        for (JsonNode c : cases) {
            Integer o = tokens(official, c.path("caseId").asText(), "officialTokens"), g = tokens(gguf, c.path("caseId").asText(), "ggufTokens");
            if (o == null || g == null || c.path("totalRounds").asInt() != repeats || c.path("attempts").size() != repeats) return new Verdict(leaf, "NOT_RUN", "BOUNDARY_INPUT_INCOMPLETE", Json.MAPPER.createObjectNode());
            int worst = Math.max(o, g); maxTokens = Math.max(maxTokens, worst);
            passRounds += c.path("passRounds").asInt(); totalRounds += c.path("totalRounds").asInt();
            for (JsonNode attempt : c.path("attempts")) { latency.add(attempt.path("latencyMs").asLong()); if (attempt.path("retried").asBoolean()) retried++; }
            if (worst + margin > limit || c.path("passRounds").asInt() != c.path("totalRounds").asInt()) pass = false;
            ObjectNode row = Json.MAPPER.createObjectNode(); row.put("caseId", c.path("caseId").asText()); row.put("kind", c.path("kind").asText()); row.put("officialTokens", o); row.put("ggufTokens", g);
            row.put("terminationMargin", limit - worst); row.put("passRounds", c.path("passRounds").asInt()); row.put("totalRounds", c.path("totalRounds").asInt()); rows.add(row);
        }
        ObjectNode metrics = Json.MAPPER.createObjectNode();
        metrics.put("caseCount", cases.size()); metrics.put("maxExpectedTokens", maxTokens); metrics.put("terminationMargin", limit - maxTokens); metrics.put("outputLimitTokens", limit);
        metrics.put("passRounds", passRounds); metrics.put("totalRounds", totalRounds); metrics.put("retriedRounds", retried);
        metrics.put("latencyP50Ms", MetricsAggregator.nearestRank(latency, 50)); metrics.put("latencyP95Ms", MetricsAggregator.nearestRank(latency, 95));
        return new Verdict(leaf, pass ? "PASS" : "FAIL", pass ? "WITHIN_LIMIT_COMPLETE" : "OUTPUT_LIMIT_OR_GENERATION_FAILED", metrics);
    }
    private static Integer tokens(JsonNode counts, String caseId, String field) {
        for (JsonNode row : counts.path("rows")) if (row.path("caseId").asText().equals(caseId) && row.path(field).isInt()) return row.path(field).asInt();
        return null;
    }
    /** AC-38-F0: 두 경로의 고정 문항 전 회차가 t=INC·기대 workflow(TC 로그/코드)이고 t=UNS 0건. 실패·timeout 회차는 불합격. */
    private static Verdict ac38(JsonNode a, JsonNode b, GoldenSet golden, RegistryFixture fixture) {
        GoldenSet.Question q = golden.questions().stream().filter(x -> x.category().equals("AC38_CROSS_SYSTEM_NAME")).findFirst().orElseThrow();
        int samples = 0, inc = 0, workflow = 0, uns = 0;
        for (var entry : List.of(Map.entry("A", a), Map.entry("B", b))) for (JsonNode row : entry.getValue().path("attempts")) {
            if (!row.path("questionId").asText().equals(q.id())) continue;
            samples++;
            boolean ok = MetricsAggregator.ok(row);
            if (ok && row.path("predicted").path("effectiveT").asText().equals("INC")) inc++;
            if (ok && row.path("predicted").path("effectiveT").asText().equals("UNS")) uns++;
            if (ok && PlanJudge.workflowMatches(row.path("predicted"), q.expected().workflow(), fixture, entry.getKey())) workflow++;
        }
        ObjectNode metrics = Json.MAPPER.createObjectNode(); metrics.put("samples", samples); metrics.put("incCount", inc); metrics.put("workflowMatchCount", workflow); metrics.put("unsCount", uns);
        boolean pass = samples > 0 && inc == samples && workflow == samples && uns == 0;
        return new Verdict("AC-38-F0", pass ? "PASS" : "FAIL", pass ? "INC_TC_WORKFLOW" : "AC38_MISMATCH", metrics);
    }
    /** (A) 잠정 후보 = 3축 모두 ≥ (B) AND 1축 이상 엄격 우위 AND (A) 기대 taskType 별 p95 ≤ 경로 예산. 동률이면 (B) 유지. 최종 승격 기록 0. */
    static String candidate(MetricsAggregator.Metrics a, MetricsAggregator.Metrics b, JsonNode budgets) {
        int[] da = {a.taskType().correct(), a.args().correct(), a.unsupported().correct()}, db = {b.taskType().correct(), b.args().correct(), b.unsupported().correct()};
        boolean atLeast = true, strict = false;
        for (int i = 0; i < 3; i++) { if (da[i] < db[i]) atLeast = false; if (da[i] > db[i]) strict = true; }
        boolean withinBudget = true;
        for (var entry : a.latencyP95MsByExpectedT().entrySet()) { JsonNode budget = budgets.path(entry.getKey()); if (!budget.canConvertToLong() || entry.getValue() > budget.asLong()) withinBudget = false; }
        return atLeast && strict && withinBudget ? "PROVISIONAL_CANDIDATE_A" : "KEEP_B";
    }
}
