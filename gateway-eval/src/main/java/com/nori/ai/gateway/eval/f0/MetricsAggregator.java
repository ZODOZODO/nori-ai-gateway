package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import java.util.*;

/**
 * PREREG 집계 규칙. 정확도 3축 = 문항마다 회차 1 표본 1개(분모 = 문항 수, best-of 0, 실패·잘림·timeout 은 오답으로 분모 유지).
 * 지연·JSON 유효율·재시도율·출력 토큰 = 회차 1~R 전부(분모 = 문항 수 × R, 실패·timeout 포함). 백분위 = nearest-rank.
 */
public final class MetricsAggregator {
    public record Axis(int correct, int denominator) {}
    public record Metrics(int questionCount, int repeats, Axis taskType, Axis args, Axis unsupported, int unsupportedFalsePositives,
                          int attemptDenominator, int validCount, int firstValidCount, int retriedCount, int timeoutCount,
                          long latencyP50Ms, long latencyP95Ms, Map<String,Long> latencyP95MsByExpectedT,
                          Integer outputTokensP50, Integer outputTokensP95, Integer outputTokensMax, int roundDisagreementCount) {}
    private MetricsAggregator() {}

    public static Metrics aggregate(List<JsonNode> attempts, GoldenSet golden, int repeats) {
        Map<String,Map<Integer,JsonNode>> byQuestion = index(attempts, golden, repeats);
        int t = 0, a = 0, u = 0, falsePositive = 0, valid = 0, firstValid = 0, retried = 0, timeout = 0, disagreement = 0;
        List<Long> latency = new ArrayList<>(); List<Integer> tokens = new ArrayList<>(); Map<String,List<Long>> byT = new TreeMap<>();
        for (GoldenSet.Question q : golden.questions()) {
            JsonNode first = byQuestion.get(q.id()).get(1);
            if (taskTypeCorrect(first, q)) t++;
            if (argsCorrect(first, q)) a++;
            if (unsupportedCorrect(first, q)) u++;
            if (!q.expected().unsupported() && ok(first) && first.path("predicted").path("effectiveT").asText().equals("UNS")) falsePositive++;
            Set<String> signatures = new HashSet<>();
            for (int round = 1; round <= repeats; round++) {
                JsonNode row = byQuestion.get(q.id()).get(round);
                long ms = row.path("latencyMs").asLong(); latency.add(ms); byT.computeIfAbsent(q.expected().t(), k -> new ArrayList<>()).add(ms);
                if (ok(row)) valid++;
                if (row.path("firstValid").asBoolean()) firstValid++;
                if (row.path("retried").asBoolean()) retried++;
                if (row.path("status").asText().equals("TIMEOUT")) timeout++;
                JsonNode completion = row.path("completionTokens");
                if (completion.isArray() && !completion.isEmpty()) tokens.add(completion.get(completion.size() - 1).asInt());
                signatures.add(row.path("status").asText() + "|" + row.path("predicted").toString());
            }
            if (signatures.size() > 1) disagreement++;
        }
        int questions = golden.questions().size(), denominator = questions * repeats;
        Map<String,Long> p95ByT = new TreeMap<>(); byT.forEach((key, values) -> p95ByT.put(key, nearestRank(values, 95)));
        return new Metrics(questions, repeats, new Axis(t, questions), new Axis(a, questions), new Axis(u, questions), falsePositive,
            denominator, valid, firstValid, retried, timeout, nearestRank(latency, 50), nearestRank(latency, 95), p95ByT,
            tokens.isEmpty() ? null : nearestRank(tokens, 50), tokens.isEmpty() ? null : nearestRank(tokens, 95), tokens.isEmpty() ? null : Collections.max(tokens), disagreement);
    }
    /** 문항·회차마다 정확히 한 표본이어야 합니다. 누락·중복·범위 밖 회차는 분모 drift 로 거부합니다. */
    static Map<String,Map<Integer,JsonNode>> index(List<JsonNode> attempts, GoldenSet golden, int repeats) {
        Map<String,Map<Integer,JsonNode>> byQuestion = new HashMap<>();
        for (GoldenSet.Question q : golden.questions()) byQuestion.put(q.id(), new HashMap<>());
        for (JsonNode row : attempts) {
            Map<Integer,JsonNode> rounds = byQuestion.get(row.path("questionId").asText());
            int round = row.path("round").asInt(-1);
            if (rounds == null || round < 1 || round > repeats || rounds.put(round, row) != null) throw new EvalException(2, "DENOMINATOR_DRIFT");
        }
        if (attempts.size() != golden.questions().size() * repeats) throw new EvalException(2, "DENOMINATOR_DRIFT");
        return byQuestion;
    }
    static boolean ok(JsonNode row) { return row != null && row.path("status").asText().equals("OK") && row.path("predicted").isObject(); }
    public static boolean taskTypeCorrect(JsonNode row, GoldenSet.Question q) { return ok(row) && row.path("predicted").path("effectiveT").asText().equals(q.expected().t()); }
    public static boolean argsCorrect(JsonNode row, GoldenSet.Question q) {
        if (!ok(row)) return false;
        for (String field : GoldenSet.ARG_FIELDS) if (!row.path("predicted").path("argSha256").path(field).asText().equals(ResourceFixture.valueSha(q.expected().field(field)))) return false;
        return true;
    }
    public static boolean unsupportedCorrect(JsonNode row, GoldenSet.Question q) { return ok(row) && row.path("predicted").path("effectiveT").asText().equals("UNS") == q.expected().unsupported(); }
    public static <T extends Comparable<T>> T nearestRank(List<T> values, int percentile) {
        if (values.isEmpty()) throw new EvalException(2, "SAMPLES_EMPTY");
        List<T> sorted = new ArrayList<>(values); Collections.sort(sorted);
        int rank = (int)Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.max(1, rank) - 1);
    }
}
