package com.nori.ai.gateway.eval.f0;

import com.nori.ai.gateway.eval.EvalException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ClassificationJudgeTest {
    static OllamaChatClient.ChatResult result(String content, String finish, int completion) { return new OllamaChatClient.ChatResult(content, finish, 40, completion); }
    static final String VALID = "{\"t\":\"INC\",\"eqp\":\"PHOTO_001\",\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-04T00:00\",\"q\":null}";

    @Test void acceptsCompleteCompressedSchemaAndRejectsMissingExtraEnumTypeFormatAndLength() {
        ClassificationJudge.Verdict valid = ClassificationJudge.judge(result(VALID, "stop", 30), 128, MessageAssembler.Kind.CLASSIFY);
        assertThat(valid.valid()).isTrue(); assertThat(valid.t()).isEqualTo("INC"); assertThat(valid.args()).containsEntry("eqp", "PHOTO_001").containsEntry("q", null);
        Map<String,String> cases = new LinkedHashMap<>();
        cases.put("{\"t\":\"INC\",\"eqp\":\"PHOTO_001\",\"from\":null,\"to\":null}", "SCHEMA_KEYS_INVALID");
        cases.put("{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"q\":null,\"extra\":1}", "SCHEMA_KEYS_INVALID");
        cases.put("{\"t\":\"INCIDENT_ANALYSIS\",\"eqp\":null,\"from\":null,\"to\":null,\"q\":null}", "ENUM_INVALID");
        cases.put("{\"t\":\"INC\",\"eqp\":7,\"from\":null,\"to\":null,\"q\":null}", "TYPE_INVALID");
        cases.put("{\"t\":\"INC\",\"eqp\":\"\",\"from\":null,\"to\":null,\"q\":null}", "TYPE_INVALID");
        cases.put("{\"t\":\"INC\",\"eqp\":null,\"from\":\"10월 3일\",\"to\":null,\"q\":null}", "FORMAT_INVALID");
        cases.put("```json\n" + VALID + "\n```", "JSON_PARSE_FAILED");
        cases.put("[1,2]", "JSON_PARSE_FAILED");
        cases.forEach((content, code) -> assertThat(ClassificationJudge.judge(result(content, "stop", 30), 128, MessageAssembler.Kind.CLASSIFY).errorCode()).as(code).isEqualTo(code));
        assertThat(ClassificationJudge.judge(result(VALID, "length", 128), 128, MessageAssembler.Kind.CLASSIFY).errorCode()).isEqualTo("FINISH_LENGTH");
        assertThat(ClassificationJudge.judge(result(VALID, "tool_calls", 20), 128, MessageAssembler.Kind.CLASSIFY).errorCode()).isEqualTo("FINISH_REASON_INVALID");
        assertThat(ClassificationJudge.judge(result(VALID, "stop", 129), 128, MessageAssembler.Kind.CLASSIFY).errorCode()).isEqualTo("USAGE_EXCEEDS_LIMIT");
    }
    @Test void planSchemaRequiresStepsOnlyForToolTaskTypes() {
        String plan = "{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"q\":null,\"steps\":[\"tc.logs.search\"]}";
        assertThat(ClassificationJudge.judge(result(plan, "stop", 60), 160, MessageAssembler.Kind.PLAN).valid()).isTrue();
        assertThat(ClassificationJudge.judge(result(plan.replace("[\"tc.logs.search\"]", "[]"), "stop", 60), 160, MessageAssembler.Kind.PLAN).errorCode()).isEqualTo("STEPS_INVALID");
        assertThat(ClassificationJudge.judge(result(plan.replace("\"INC\"", "\"UNS\""), "stop", 60), 160, MessageAssembler.Kind.PLAN).errorCode()).isEqualTo("STEPS_INVALID");
        assertThat(ClassificationJudge.judge(result(VALID, "stop", 60), 160, MessageAssembler.Kind.PLAN).errorCode()).isEqualTo("SCHEMA_KEYS_INVALID");
    }
    @Test void retriesExactlyOnceWithErrorSummaryAndSucceedsOnRetry() {
        List<List<Map<String,String>>> sent = new ArrayList<>();
        Iterator<OllamaChatClient.ChatResult> answers = List.of(result("{\"t\":\"INC\",\"eqp\":\"PHO", "length", 128), result(VALID, "stop", 30)).iterator();
        List<Map<String,String>> original = List.of(Map.of("role", "system", "content", "s"), Map.of("role", "user", "content", "{}"));
        ClassificationJudge.Outcome outcome = ClassificationJudge.execute(m -> { sent.add(m); return answers.next(); }, original, 128, MessageAssembler.Kind.CLASSIFY);
        assertThat(outcome.status()).isEqualTo("OK"); assertThat(outcome.retried()).isTrue(); assertThat(outcome.firstValid()).isFalse();
        assertThat(outcome.finishReasons()).containsExactly("length", "stop"); assertThat(outcome.errorCodes()).containsExactly("FINISH_LENGTH"); assertThat(outcome.outputSha256()).hasSize(2);
        assertThat(sent).hasSize(2); assertThat(sent.get(1)).hasSize(3).startsWith(original.toArray(Map[]::new));
        assertThat(sent.get(1).get(2).get("content")).contains("FINISH_LENGTH").doesNotContain("PHO");
    }
    @Test void secondFailureIsFinalInvalidWithoutThirdCallAndTimeoutKeepsElapsed() {
        int[] calls = {0};
        ClassificationJudge.Outcome invalid = ClassificationJudge.execute(m -> { calls[0]++; return result("not json", "stop", 5); }, List.of(Map.of("role", "user", "content", "{}")), 128, MessageAssembler.Kind.CLASSIFY);
        assertThat(calls[0]).isEqualTo(2); assertThat(invalid.status()).isEqualTo("INVALID"); assertThat(invalid.retried()).isTrue(); assertThat(invalid.errorCodes()).containsExactly("JSON_PARSE_FAILED", "JSON_PARSE_FAILED");
        ClassificationJudge.Outcome timeout = ClassificationJudge.execute(m -> { try { Thread.sleep(30); } catch (InterruptedException e) { throw new IllegalStateException(e); } throw new EvalException(5, "ENDPOINT_TIMEOUT"); }, List.of(Map.of("role", "user", "content", "{}")), 128, MessageAssembler.Kind.CLASSIFY);
        assertThat(timeout.status()).isEqualTo("TIMEOUT"); assertThat(timeout.latencyMs()).isGreaterThanOrEqualTo(25); assertThat(timeout.verdict()).isNull();
        assertThatThrownBy(() -> ClassificationJudge.execute(m -> { throw new EvalException(5, "ENDPOINT_HTTP_ERROR"); }, List.of(Map.of("role", "user", "content", "{}")), 128, MessageAssembler.Kind.CLASSIFY))
            .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("ENDPOINT_HTTP_ERROR"));
    }
    @Test void verdictAndResultToStringNeverExposeContent() {
        OllamaChatClient.ChatResult raw = result("{\"q\":\"" + OllamaStub.SENTINEL + "\"}", "stop", 3);
        assertThat(raw.toString()).doesNotContain(OllamaStub.SENTINEL);
        assertThat(ClassificationJudge.judge(result(VALID.replace("null}", "\"" + OllamaStub.SENTINEL + "\"}"), "stop", 3), 128, MessageAssembler.Kind.CLASSIFY).toString()).doesNotContain(OllamaStub.SENTINEL);
    }
}
