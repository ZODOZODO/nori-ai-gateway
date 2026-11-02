package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class F0ArgumentContractTest {
    @TempDir Path directory;

    static ObjectNode output(String from, String to, String q) {
        return Json.MAPPER.createObjectNode().put("t", "INC").put("eqp", "PHOTO_001").put("from", from).put("to", to).put("q", q);
    }
    static OllamaChatClient.ChatResult response(JsonNode output) { return new OllamaChatClient.ChatResult(Json.canonicalString(output), "stop", 100, 60); }
    static ClassificationJudge.Verdict judge(JsonNode output) {
        String question = output.path("q").isNull() ? "검색어 없음" : "json:" + Json.canonicalString(output.path("q"));
        QRefContext references = QRefContext.extract(question);
        return ClassificationJudge.judge(response(references.wireExpected(output)), 128, MessageAssembler.Kind.CLASSIFY, true, "+09:00", references);
    }

    @Test void limitsUseUnicodeCodepointsWithoutTruncatingSupplementaryCharacters() {
        String q = "😀".repeat(256), eqp = "𠮷".repeat(64);
        ObjectNode exact = output(null, null, q).put("eqp", eqp);
        ClassificationJudge.Verdict verdict = judge(exact);
        assertThat(verdict.valid()).isTrue();
        assertThat(verdict.args()).containsEntry("q", q).containsEntry("eqp", eqp);
        assertThatThrownBy(() -> QRefContext.extract("json:" + Json.canonicalString(Json.MAPPER.valueToTree(q + "가")))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("ARG_LENGTH_EXCEEDED"));
        assertThat(judge(exact.deepCopy().put("eqp", eqp + "A")).errorCode()).isEqualTo("ARG_LENGTH_EXCEEDED");
        assertThat(judge(output(null, null, "\"\\\u0001".repeat(85) + "끝")).valid()).isTrue();
    }

    @Test void actualCalendarClockAndOffsetAreStrictlyValidatedOnlyForV2() {
        List<String> invalid = List.of("2026-02-29T12:00", "2026-04-31T12:00", "2026-13-01T12:00", "2026-01-01T24:00",
            "2026-01-01T12:60", "2026-01-01T12:00:60", "2026-01-01T12:00+18:01", "2026-01-01T12:00-19:00",
            "2026-01-01T12:00:00.1234567890", "2026-01-01T12:00+01:60");
        for (String time : invalid) assertThat(judge(output(time, null, null)).errorCode()).isEqualTo("FORMAT_INVALID");
        assertThat(judge(output("2024-02-29T12:00:00.123456789+18:00", "2024-03-01T12:00-18:00", null)).valid()).isTrue();
        ObjectNode legacy = output("2026-02-29T12:00", null, "가".repeat(257));
        assertThat(ClassificationJudge.judge(response(legacy), 128, MessageAssembler.Kind.CLASSIFY).valid()).isTrue();
        assertThat(ClassificationJudge.judge(response(legacy), 128, MessageAssembler.Kind.CLASSIFY, false, null).valid()).isTrue();
    }

    @Test void normalizationPreservesInstantNanosecondsAndWrittenPrecisionAcrossDays() {
        assertThat(ArgLimits.normalizeTime("2026-10-03T00:00", "+09:00")).isEqualTo("2026-10-03T00:00");
        assertThat(ArgLimits.normalizeTime("2026-10-03T00:00:00", "+09:00")).isEqualTo("2026-10-03T00:00:00");
        assertThat(ArgLimits.normalizeTime("2026-10-03T00:00:00.1200", "+09:00")).isEqualTo("2026-10-03T00:00:00.1200");
        String input = "2026-10-03T23:59:59.123456789Z", normalized = ArgLimits.normalizeTime(input, "+09:00");
        assertThat(normalized).isEqualTo("2026-10-04T08:59:59.123456789+09:00");
        assertThat(OffsetDateTime.parse(normalized).toInstant()).isEqualTo(OffsetDateTime.parse(input).toInstant());
        assertThat(OffsetDateTime.parse(normalized).getNano()).isEqualTo(123456789);
        assertThat(ArgLimits.normalizeTime("2026-10-03T00:01+09:00", "-04:00")).isEqualTo("2026-10-02T11:01-04:00");
        assertThatThrownBy(() -> ArgLimits.normalizeTime(input, "+19:00")).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("DATETIME_INVALID"));
    }

    @Test void semanticValidationNeverTurnsADifferentModelStringIntoAnExactOracleMatch() {
        String from = "2026-10-03T00:00:00.123456789Z", to = "2026-10-04T00:00:00.123456789Z";
        ClassificationJudge.Verdict verdict = judge(output(from, to, null));
        assertThat(verdict.valid()).isTrue(); assertThat(verdict.args()).containsEntry("from", from).containsEntry("to", to);
        assertThat(F0Measurements.argsExact(verdict, output(ArgLimits.normalizeTime(from, "+09:00"), ArgLimits.normalizeTime(to, "+09:00"), null))).isFalse();
    }

    @Test void rawModelQueryGetsOnlyTheExistingSingleSchemaRetry() {
        AtomicInteger calls = new AtomicInteger();
        QRefContext references = QRefContext.extract("검색어 없음");
        ObjectNode user = Json.MAPPER.createObjectNode().put("question", "검색어 없음"); user.set("qRefCatalog", references.catalog());
        ClassificationJudge.Outcome outcome = ClassificationJudge.execute(messages -> {
            calls.incrementAndGet(); return response(output(null, null, "가".repeat(257)));
        }, List.of(Map.of("role", "user", "content", Json.canonicalString(user))), 128, MessageAssembler.Kind.CLASSIFY, true, "+09:00", references);
        assertThat(calls.get()).isEqualTo(2); assertThat(outcome.status()).isEqualTo("INVALID");
        assertThat(outcome.errorCodes()).containsExactly("SCHEMA_KEYS_INVALID", "SCHEMA_KEYS_INVALID");
    }

    @Test void actualRunAndBoundaryCallsApplyTheSelectedV2Contract() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = body -> OllamaStub.chatResponse(Json.canonicalString(output(null, null, "가".repeat(257))), "stop", 100, 60);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            JsonNode run = f.json("eval/f0/results/" + F0VersionedInputsTest.RUN + "/B/f0-results.json");
            assertThat(run.path("attempts")).allSatisfy(row -> {
                assertThat(row.path("status").asText()).isEqualTo("INVALID");
                assertThat(row.path("errorCodes")).extracting(JsonNode::asText).containsOnly("SCHEMA_KEYS_INVALID");
            });
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"),
                "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", F0VersionedInputsTest.RUN, "--out", "eval/f0/results/" + F0VersionedInputsTest.RUN + "/boundary")).isZero();
            f.json("eval/f0/results/" + F0VersionedInputsTest.RUN + "/boundary/boundary-results.json").path("cases").forEach(c ->
                assertThat(c.path("attempts")).allSatisfy(row -> assertThat(row.path("status").asText()).isEqualTo("INVALID")));
        }
    }
}
