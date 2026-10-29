package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MetricsAggregatorTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    final RegistryFixture fixture = RegistryFixture.load(ROOT.resolve("eval/golden/tc-registry-fixture-v1.json"));
    final GoldenSet golden = GoldenSet.load(ROOT.resolve("eval/golden/tc-f0-v1.jsonl"), fixture);

    static ObjectNode row(GoldenSet.Question q, int round, String status, boolean correct, long latency) {
        ObjectNode row = Json.MAPPER.createObjectNode(); row.put("questionId", q.id()); row.put("round", round); row.put("status", status); row.put("latencyMs", latency);
        row.put("retried", false); row.put("firstValid", status.equals("OK")); row.putArray("completionTokens").add(30);
        if (!status.equals("OK")) { row.putNull("predicted"); return row; }
        ObjectNode p = row.putObject("predicted"); String t = correct ? q.expected().t() : (q.expected().t().equals("UNS") ? "INC" : "UNS");
        p.put("t", t); p.put("effectiveT", t);
        ObjectNode args = p.putObject("argSha256");
        for (String f : GoldenSet.ARG_FIELDS) args.put(f, ResourceFixture.valueSha(correct ? q.expected().field(f) : "WRONG"));
        return row;
    }
    List<JsonNode> all(int repeats) { List<JsonNode> rows = new ArrayList<>(); for (int r = 1; r <= repeats; r++) for (var q : golden.questions()) rows.add(row(q, r, "OK", true, 100L * r)); return rows; }

    @Test void accuracyDenominatorIsQuestionCountForEveryRepeatAndLatencyDenominatorIsQuestionsTimesRepeats() {
        int questions = golden.questions().size();
        for (int repeats : List.of(3, 5, 7)) {
            MetricsAggregator.Metrics m = MetricsAggregator.aggregate(all(repeats), golden, repeats);
            assertThat(List.of(m.taskType().denominator(), m.args().denominator(), m.unsupported().denominator())).containsOnly(questions);
            assertThat(m.attemptDenominator()).isEqualTo(questions * repeats);
            assertThat(m.taskType().correct()).isEqualTo(questions);
        }
    }
    @Test void roundOneWrongRoundTwoRightStaysWrongAndRoundOneTimeoutKeepsDenominatorAndLatencySample() {
        List<JsonNode> rows = new ArrayList<>(); GoldenSet.Question first = golden.questions().get(0), second = golden.questions().get(1);
        for (var q : golden.questions()) {
            if (q == first) { rows.add(row(q, 1, "OK", false, 100)); rows.add(row(q, 2, "OK", true, 100)); }
            else if (q == second) { rows.add(row(q, 1, "TIMEOUT", false, 120_000)); rows.add(row(q, 2, "OK", true, 100)); }
            else { rows.add(row(q, 1, "OK", true, 100)); rows.add(row(q, 2, "OK", true, 100)); }
        }
        MetricsAggregator.Metrics m = MetricsAggregator.aggregate(rows, golden, 2);
        int questions = golden.questions().size();
        assertThat(m.taskType().correct()).isEqualTo(questions - 2); assertThat(m.args().correct()).isEqualTo(questions - 2); assertThat(m.unsupported().correct()).isEqualTo(questions - 2);
        assertThat(m.taskType().denominator()).isEqualTo(questions);
        assertThat(m.timeoutCount()).isEqualTo(1); assertThat(m.validCount()).isEqualTo(questions * 2 - 1); assertThat(m.attemptDenominator()).isEqualTo(questions * 2);
        assertThat(m.latencyP95Ms()).isEqualTo(100); // 44 표본 중 1개만 120000 → nearest-rank p95 = 42번째 = 100
        assertThat(m.roundDisagreementCount()).isEqualTo(2);
        assertThat(m.unsupportedFalsePositives()).isEqualTo(first.expected().unsupported() ? 0 : 1);
    }
    @Test void nearestRankUsesCeilingRankWithoutInterpolation() {
        List<Long> twenty = new ArrayList<>(); for (long i = 1; i <= 20; i++) twenty.add(i);
        assertThat(MetricsAggregator.nearestRank(twenty, 50)).isEqualTo(10L); assertThat(MetricsAggregator.nearestRank(twenty, 95)).isEqualTo(19L);
        assertThat(MetricsAggregator.nearestRank(List.of(50L, 15L, 40L, 20L, 35L), 50)).isEqualTo(35L);
        assertThat(MetricsAggregator.nearestRank(List.of(50L, 15L, 40L, 20L, 35L), 95)).isEqualTo(50L);
        assertThat(MetricsAggregator.nearestRank(List.of(7L), 95)).isEqualTo(7L);
    }
    @Test void missingDuplicateOrOutOfRangeSamplesAreDenominatorDrift() {
        List<JsonNode> missing = all(2); missing.remove(5);
        List<JsonNode> duplicate = all(2); duplicate.set(5, duplicate.get(4));
        List<JsonNode> outOfRange = all(2); ((ObjectNode)outOfRange.get(0)).put("round", 3);
        for (List<JsonNode> rows : List.of(missing, duplicate, outOfRange))
            assertThatThrownBy(() -> MetricsAggregator.aggregate(rows, golden, 2)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("DENOMINATOR_DRIFT"));
    }
}
