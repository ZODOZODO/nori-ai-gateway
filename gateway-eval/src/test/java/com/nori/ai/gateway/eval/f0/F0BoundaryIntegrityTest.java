package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

class F0BoundaryIntegrityTest {
    static final String TOKENIZER_SHA = "a".repeat(64), CASES_SHA = "b".repeat(64);
    static final String NORMAL = "AC-37-OUT-NORMAL";
    /** v2 profile 의 경계 출력 상한(분류 max_tokens 와 같음). 과거 128 경계가 아니라 등록 profile 로 판정해야 합니다. */
    static final int LIMIT = Prereg.Budget.V2.outputLimit();

    static final class Inputs {
        final List<JsonNode> registered = new ArrayList<>();
        final Map<String,JsonNode> files = new LinkedHashMap<>();
        final ObjectNode boundary = Json.MAPPER.createObjectNode();
        final Prereg.Document prereg;

        Inputs(boolean versioned) {
            ObjectNode block = Json.MAPPER.createObjectNode().put("schema", versioned ? "f0-prereg-v2" : "f0-prereg-v1");
            ObjectNode parameters = block.putObject("parameters").put("repeats", 2).put("outputLimitTokens", (versioned ? Prereg.Budget.V2 : Prereg.Budget.V1).outputLimit()).put("terminationMarginTokens", 1);
            prereg = new Prereg.Document(block, "c".repeat(40), Map.of(), "d".repeat(64), parameters);
            boundary.put("complete", true).put("repeats", 2);
            boundary.putArray("cases"); files.put("boundary", boundary);
            for (String source : List.of("gguf", "official")) {
                ObjectNode counts = Json.MAPPER.createObjectNode().put("complete", true).put("repeats", 1);
                counts.put("tokenizerSha256", TOKENIZER_SHA).put("casesSha256", CASES_SHA).putArray("rows");
                String field = source + "Tokens";
                ArrayNode calibration = counts.putArray("calibration");
                calibration.addObject().put("id", "SPEC-14.5-OLD-EXAMPLE").put(field, 57);
                calibration.addObject().put("id", "SPEC-14.5-OLD-EXAMPLE-NO-Q").put(field, 53);
                files.put(source, counts);
            }
            addCase("NORMAL-01", "NORMAL"); addCase("NORMAL-02", "NORMAL");
            addCase("MAX-01", "MAX"); addCase("ESCAPE-01", "ESCAPE");
        }

        void addCase(String id, String kind) {
            registered.add(Json.MAPPER.createObjectNode().put("caseId", id).put("kind", kind));
            ObjectNode c = ((ArrayNode)boundary.path("cases")).addObject().put("caseId", id).put("kind", kind).put("passRounds", 2).put("totalRounds", 2);
            ArrayNode attempts = c.putArray("attempts");
            for (int round = 1; round <= 2; round++) attempts.addObject().put("round", round).put("status", "OK").put("retried", false)
                .put("finishReason", "stop").put("completionTokens", 60).put("schemaValid", true).put("argsExact", true).put("latencyMs", 100L * round);
            for (String source : List.of("gguf", "official")) ((ArrayNode)files.get(source).path("rows")).addObject().put("caseId", id).put("kind", kind).put(source + "Tokens", 57);
        }

        ObjectNode caseRow() { return (ObjectNode)boundary.path("cases").path(0); }
        ObjectNode attempt() { return (ObjectNode)caseRow().path("attempts").path(0); }
        F0Judgement.Outcome judge() { return F0Judgement.judge(new F0Judgement.Results(files, Map.of()), prereg, null, null, TOKENIZER_SHA, CASES_SHA, registered); }
        F0Judgement.Verdict verdict() { return judge().verdicts().get(NORMAL); }
    }

    @Test void registeredCasesAndAllIndependentAttemptsProducePassWithoutChangingInputs() {
        Inputs input = new Inputs(true);
        String before = Json.canonicalString(Json.MAPPER.valueToTree(input.files));
        F0Judgement.Outcome outcome = input.judge();
        assertThat(outcome.verdicts().keySet()).containsExactlyInAnyOrder(NORMAL, "AC-37-OUT-MAX", "AC-37-OUT-ESCAPE");
        outcome.verdicts().values().forEach(v -> assertThat(v.result()).isEqualTo("PASS"));
        assertThat(outcome.verdicts().get(NORMAL).metrics().path("totalRounds").asInt()).isEqualTo(4);
        assertThat(outcome.verdicts().get(NORMAL).metrics().path("passRounds").asInt()).isEqualTo(4);
        assertThat(Json.canonicalString(Json.MAPPER.valueToTree(input.files))).isEqualTo(before);
    }

    @Test void forgedPassingSummaryCannotOverrideAnUnsuccessfulAttempt() {
        List<Consumer<ObjectNode>> failures = List.of(a -> a.put("status", "INVALID"), a -> a.put("finishReason", "length"),
            a -> a.put("schemaValid", false), a -> a.put("argsExact", false), a -> a.put("completionTokens", LIMIT + 1), a -> a.putNull("completionTokens"));
        for (Consumer<ObjectNode> failure : failures) {
            Inputs input = new Inputs(true); failure.accept(input.attempt());
            assertThat(input.verdict().result()).isEqualTo("NOT_RUN");
            assertThat(input.verdict().reasonCode()).isEqualTo("BOUNDARY_SUMMARY_MISMATCH");
        }
    }

    @Test void honestFailedAttemptsRemainFailuresAndKeepTheDenominator() {
        List<Consumer<ObjectNode>> failures = List.of(a -> a.put("status", "INVALID"), a -> a.put("finishReason", "length"),
            a -> a.put("schemaValid", false), a -> a.put("argsExact", false), a -> a.put("completionTokens", LIMIT + 1));
        for (Consumer<ObjectNode> failure : failures) {
            Inputs input = new Inputs(true); failure.accept(input.attempt()); input.caseRow().put("passRounds", 1);
            F0Judgement.Verdict verdict = input.verdict();
            assertThat(verdict.result()).isEqualTo("FAIL");
            assertThat(verdict.metrics().path("passRounds").asInt()).isEqualTo(3);
            assertThat(verdict.metrics().path("totalRounds").asInt()).isEqualTo(4);
        }
    }

    @Test void duplicateMissingAndOutOfRangeRoundsAreRejected() {
        for (int round : List.of(0, 2, 3)) {
            Inputs input = new Inputs(true); input.attempt().put("round", round);
            assertThat(input.verdict().reasonCode()).isEqualTo("BOUNDARY_ROUND_MISMATCH");
        }
        Inputs missing = new Inputs(true); ((ArrayNode)missing.caseRow().path("attempts")).remove(0);
        assertThat(missing.verdict().reasonCode()).isEqualTo("BOUNDARY_INPUT_INCOMPLETE");
    }

    @Test void missingDuplicateExtraOrWrongKindBoundaryCasesAreRejected() {
        for (String mutation : List.of("missing", "duplicate", "extra", "kind")) {
            Inputs input = new Inputs(true); ArrayNode rows = (ArrayNode)input.boundary.path("cases");
            switch (mutation) {
                case "missing" -> rows.remove(1);
                case "duplicate" -> rows.set(1, rows.get(0).deepCopy());
                case "extra" -> rows.add(rows.get(0).deepCopy());
                case "kind" -> input.caseRow().put("kind", "MAX");
                default -> throw new AssertionError();
            }
            assertThat(input.verdict().reasonCode()).isEqualTo("BOUNDARY_CASE_SET_MISMATCH");
        }
    }

    @Test void countTablesMustMatchTheRegisteredSetWithoutDuplicateMissingOrWrongKindRows() {
        for (String source : List.of("official", "gguf")) for (String mutation : List.of("missing", "duplicate", "kind", "negative")) {
            Inputs input = new Inputs(true); ArrayNode rows = (ArrayNode)input.files.get(source).path("rows");
            switch (mutation) {
                case "missing" -> rows.remove(1);
                case "duplicate" -> rows.set(1, rows.get(0).deepCopy());
                case "kind" -> ((ObjectNode)rows.get(0)).put("kind", "MAX");
                case "negative" -> ((ObjectNode)rows.get(0)).put(source + "Tokens", -1);
                default -> throw new AssertionError();
            }
            assertThat(input.verdict().reasonCode()).isEqualTo("BOUNDARY_CASE_SET_MISMATCH");
        }
    }

    @Test void duplicateCalibrationRowsAreRejectedEvenWhenTheirValuesMatch() {
        for (String source : List.of("official", "gguf")) {
            Inputs input = new Inputs(true); ArrayNode calibration = (ArrayNode)input.files.get(source).path("calibration");
            calibration.add(calibration.get(0).deepCopy());
            assertThat(input.verdict().reasonCode()).isEqualTo("BOUNDARY_CALIBRATION_INVALID");
        }
    }

    @Test void invalidAttemptTypesAndNegativeLatencyAreRejected() {
        List<Consumer<ObjectNode>> invalid = List.of(a -> a.put("latencyMs", -1), a -> a.put("latencyMs", 0.5), a -> a.put("round", "1"),
            a -> a.remove("retried"), a -> a.put("schemaValid", "true"), a -> a.put("completionTokens", -1), a -> a.remove("finishReason"));
        for (Consumer<ObjectNode> mutation : invalid) {
            Inputs input = new Inputs(true); mutation.accept(input.attempt());
            assertThat(input.verdict().result()).isEqualTo("NOT_RUN");
        }
    }

    @Test void summaryCountersAndHeaderTypesCannotChangeTheRegisteredDenominator() {
        Inputs total = new Inputs(true); total.caseRow().put("totalRounds", 1);
        assertThat(total.verdict().reasonCode()).isEqualTo("BOUNDARY_INPUT_INCOMPLETE");
        for (int passRounds : List.of(-1, 1, 3)) {
            Inputs input = new Inputs(true); input.caseRow().put("passRounds", passRounds);
            assertThat(input.verdict().reasonCode()).isEqualTo("BOUNDARY_SUMMARY_MISMATCH");
        }
        Inputs header = new Inputs(true); header.boundary.put("complete", "true");
        assertThat(header.verdict().reasonCode()).isEqualTo("BOUNDARY_INPUT_INCOMPLETE");
    }

    @Test void timeoutIsAFailedRoundAndTokenBoundaryUsesBothGenerationAndExpectedCounts() {
        Inputs timeout = new Inputs(true); timeout.attempt().put("status", "TIMEOUT").putNull("finishReason").putNull("completionTokens")
            .put("schemaValid", false).put("argsExact", false); timeout.caseRow().put("passRounds", 1);
        assertThat(timeout.verdict().result()).isEqualTo("FAIL");
        Inputs edge = new Inputs(true); edge.attempt().put("completionTokens", LIMIT);
        ((ObjectNode)edge.files.get("official").path("rows").path(0)).put("officialTokens", LIMIT - 1);
        assertThat(edge.verdict().result()).isEqualTo("PASS");
        ((ObjectNode)edge.files.get("official").path("rows").path(0)).put("officialTokens", LIMIT);
        assertThat(edge.verdict().result()).isEqualTo("FAIL");
    }

    @Test void v2MissingRegistrationFailsClosedEvenThroughTheLegacyOverload() {
        Inputs input = new Inputs(true); input.registered.clear();
        assertThat(input.verdict().reasonCode()).isEqualTo("BOUNDARY_REGISTERED_CASES_MISSING");
        F0Judgement.Outcome legacyCall = F0Judgement.judge(new F0Judgement.Results(input.files, Map.of()), input.prereg, null, null, TOKENIZER_SHA, CASES_SHA);
        assertThat(legacyCall.verdicts().values()).allSatisfy(v -> assertThat(v.result()).isEqualTo("NOT_RUN"));
    }

    @Test void v1KeepsItsOriginalSummaryBasedJudgementWithEitherOverload() {
        // v1의 불변 증거를 새 v2 무결성 계약으로 소급 재판정하지 않는 회귀입니다.
        Inputs input = new Inputs(false); input.attempt().put("argsExact", false); input.registered.clear();
        F0Judgement.Outcome legacy = F0Judgement.judge(new F0Judgement.Results(input.files, Map.of()), input.prereg, null, null, TOKENIZER_SHA, CASES_SHA);
        assertThat(legacy.verdicts().get(NORMAL).result()).isEqualTo("PASS");
        JsonNode actual = Json.MAPPER.valueToTree(input.judge()), expected = Json.MAPPER.valueToTree(legacy);
        assertThat(actual).isEqualTo(expected);
    }
}
