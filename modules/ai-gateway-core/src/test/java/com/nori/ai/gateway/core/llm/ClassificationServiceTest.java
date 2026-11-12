package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.job.CancellationToken;
import com.nori.ai.gateway.core.job.JobRequestInput;
import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.result.ErrorCode;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.nori.ai.gateway.core.llm.LlmTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class ClassificationServiceTest {
    private static final String PROMPT = "분류 지시\nRegistry 참고:\n{{REGISTRY}}";
    private static final Map<String,Object> CONTEXT = Map.of("context", Map.of("timezone", "+09:00", "referenceTime", "2026-10-20T10:00:00+09:00"));
    private final MutableClock clock = new MutableClock();
    private final List<List<ChatMessage>> observed = new ArrayList<>();
    private final LlmCallObserver observer = (job, type, messages) -> { assertThat(type).isEqualTo(CallType.CLASSIFY); observed.add(messages); return () -> {}; };

    private ClassificationService service(ScriptedClient client) {
        return new ClassificationService(client, TokenBudgetPolicy.current(), CODEPOINTS, expiry(clock), observer, PROMPT, "boundSystem: X\n");
    }
    private ClassificationService.Request request(String question, Map<String,Object> fields) {
        return new ClassificationService.Request("job-1", NOW.plusSeconds(600), new CancellationToken(), new JobRequestInput("message", 1, question, fields));
    }
    private static String wire(String t, String eqp, String from, String to, String qRef) {
        return "{\"t\":\"" + t + "\",\"eqp\":" + quoted(eqp) + ",\"from\":" + quoted(from) + ",\"to\":" + quoted(to) + ",\"qRef\":" + quoted(qRef) + "}";
    }
    private static String quoted(String value) { return value == null ? "null" : "\"" + value + "\""; }

    @Test void validWireRestoresTheExactOriginalAndKeepsTimeTextNanosAndOffsetWithoutTruncation() {
        String query = "C:\\log\\" + new String(Character.toChars(0x1F600)) + "TIMEOUT";
        String eqp = "포토설비A동1라인EQP0001경계최대길이검증용식별자abcdefghij0123456789KLMN가나다라마바사아자차카타";
        assertThat(eqp.codePointCount(0, eqp.length())).isEqualTo(64);
        var client = new ScriptedClient(reply(wire("INC", eqp, "2026-10-03T00:00:00.123456789+09:00", "2026-10-04T00:00Z", "Q1")));
        var result = service(client).classify(request("\"" + query + "\" 원인 찾아줘", CONTEXT));
        var classified = (ClassificationService.Classified) result;
        assertThat(classified.calls()).isOne();
        assertThat(classified.classification()).isEqualTo(new com.nori.ai.gateway.core.llm.Classification(TaskType.INCIDENT_ANALYSIS, eqp, "2026-10-03T00:00:00.123456789+09:00", "2026-10-04T00:00Z", query));
        assertThat(classified.classification().q().codePoints().toArray()).isEqualTo(query.codePoints().toArray());
        var sent = client.sent.getFirst();
        assertThat(sent.maxTokens()).isEqualTo(5_120);
        assertThat(sent.messages().getFirst().content()).isEqualTo("분류 지시\nRegistry 참고:\nboundSystem: X\n");
        String user = sent.messages().get(1).content();
        assertThat(user).contains("\"qRefCatalog\":[\"Q1\"]").startsWith("{\"context\":{\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"selectedResourceRefs\":[],\"timeWindow\":null,\"timezone\":\"+09:00\"}");
        assertThat(observed).containsExactly(sent.messages());
        assertThat(classified.classification().jobClassification()).containsEntry("taskType", "INCIDENT_ANALYSIS").containsEntry("q", query).doesNotContainKey("qRef");
    }

    @Test void schemaFailureRetriesOnceWithTheSameBindingAndOnlyAFixedErrorThenUsesTheRetryValues() {
        var client = new ScriptedClient(reply("{\"t\":\"LQ\",\"eqp\":\"FIRST\",\"from\":null,\"to\":null,\"q\":\"invented\"}"), reply(wire("LQ", "SECOND", null, null, "Q2")));
        var result = (ClassificationService.Classified) service(client).classify(request("\"a\" 와 \"b\"", CONTEXT));
        assertThat(result.calls()).isEqualTo(2);
        assertThat(result.classification().eqp()).isEqualTo("SECOND");
        assertThat(result.classification().q()).isEqualTo("b");
        var first = client.sent.get(0).messages(); var retry = client.sent.get(1).messages();
        assertThat(retry.subList(0, 2)).isEqualTo(first);
        assertThat(retry.getLast()).isEqualTo(new ChatMessage(ChatMessage.Role.USER, "직전 출력이 무효다(오류: SCHEMA_KEYS_INVALID). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다."));
        assertThat(retry.toString()).doesNotContain("invented", "FIRST");
    }

    @Test void everyWireOrAnswerViolationFailsBothAttemptsAndEndsAsPlanInvalidAfterExactlyTwoCalls() {
        var cases = new java.util.LinkedHashMap<String,InferenceClient.Completed>();
        cases.put("missing key", reply("{\"t\":\"LQ\",\"eqp\":null,\"from\":null,\"to\":null}"));
        cases.put("q key", reply("{\"t\":\"LQ\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":\"Q1\",\"q\":\"x\"}"));
        cases.put("extra capability", reply("{\"t\":\"LQ\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":\"Q1\",\"capabilityId\":\"wm.inventory.get\"}"));
        cases.put("enum", reply(wire("XX", null, null, null, "Q1")));
        cases.put("forged ref", reply(wire("LQ", null, null, null, "Q2")));
        cases.put("null with candidate", reply(wire("LQ", null, null, null, null)));
        cases.put("impossible date", reply(wire("LQ", null, "2026-02-30T00:00", "2026-03-01T00:00", "Q1")));
        cases.put("ten fraction digits", reply(wire("LQ", null, "2026-10-03T00:00:00.1234567890Z", "2026-10-04T00:00Z", "Q1")));
        cases.put("empty eqp", reply(wire("LQ", "", null, null, "Q1")));
        cases.put("eqp 65", reply(wire("LQ", "E".repeat(65), null, null, "Q1")));
        cases.put("length", reply(wire("LQ", null, null, null, "Q1"), "length"));
        cases.put("other finish", reply(wire("LQ", null, null, null, "Q1"), "tool_calls"));
        cases.put("answer over 192", reply("{\"t\":\"LQ\"," + " ".repeat(200) + "\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":\"Q1\"}"));
        cases.put("think marker", reply("</think>" + wire("LQ", null, null, null, "Q1")));
        cases.put("not json", reply("LQ 입니다"));
        for (var entry : cases.entrySet()) {
            var client = new ScriptedClient(entry.getValue(), entry.getValue());
            var result = service(client).classify(request("\"q\" 찾아줘", CONTEXT));
            assertThat(result).as(entry.getKey()).isEqualTo(new ClassificationService.Rejected(ErrorCode.PLAN_INVALID, 2));
            assertThat(client.sent).as(entry.getKey()).hasSize(2);
        }
        var budget = new ScriptedClient(reply("{\"t\":\"LQ\"," + " ".repeat(200) + "\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null}"), reply(wire("GQ", null, null, null, null)));
        service(budget).classify(request("질문", CONTEXT));
        assertThat(budget.sent.get(1).messages().getLast().content()).contains("(오류: USAGE_EXCEEDS_LIMIT)");
    }

    @Test void inputLiteralFailuresStopBeforeAnyCallWithTheExistingClarificationCodes() {
        var client = new ScriptedClient();
        assertThat(service(client).classify(request("\"" + "가".repeat(257) + "\"", CONTEXT))).isEqualTo(new ClassificationService.Rejected(ErrorCode.INPUT_BUDGET_EXCEEDED, 0));
        assertThat(service(client).classify(request("닫힘 없는 \"인용", CONTEXT))).isEqualTo(new ClassificationService.Rejected(ErrorCode.PLAN_INVALID, 0));
        assertThat(client.sent).isEmpty();
        assertThat(observed).isEmpty();
    }

    @Test void callFailureIsUnavailableWithoutRetryAndBudgetExceededIsNeverSent() {
        var failed = new ScriptedClient(new InferenceClient.CallFailed(InferenceClient.FailureReason.TRANSPORT, "java.io.IOException", Duration.ZERO));
        assertThat(service(failed).classify(request("질문", CONTEXT))).isEqualTo(new ClassificationService.Unavailable(1));
        assertThat(failed.sent).hasSize(1);
        var budget = new ScriptedClient(InferenceClient.ChatOutcome.budgetExceeded(6_000));
        assertThat(service(budget).classify(request("질문", CONTEXT))).isEqualTo(new ClassificationService.BudgetExceeded(0));
        var retryBudget = new ScriptedClient(reply("x"), InferenceClient.ChatOutcome.budgetExceeded(3_100));
        assertThat(service(retryBudget).classify(request("질문", CONTEXT))).isEqualTo(new ClassificationService.BudgetExceeded(1));
    }

    @Test void localTimeWithoutAContextTimezoneCannotBeResolvedAndIsAFormatFailure() {
        var client = new ScriptedClient(reply(wire("LQ", null, "2026-10-03T00:00", "2026-10-04T00:00", null)), reply(wire("LQ", null, "2026-10-03T00:00Z", "2026-10-04T00:00Z", null)));
        var result = (ClassificationService.Classified) service(client).classify(request("질문", Map.of()));
        assertThat(client.sent.get(1).messages().getLast().content()).contains("(오류: FORMAT_INVALID)");
        assertThat(result.classification().from()).isEqualTo("2026-10-03T00:00Z");
        assertThat(client.sent.getFirst().messages().get(1).content()).startsWith("{\"context\":{\"referenceTime\":null,\"selectedResourceRefs\":[],\"timeWindow\":null,\"timezone\":null}");
    }

    @Test void expiryBeforeAssemblyStopsTheCallAndExpiryDuringTheCallDiscardsTheResponse() {
        var before = new ScriptedClient(reply(wire("GQ", null, null, null, null)));
        clock.now = NOW.plusSeconds(600);
        assertThatThrownBy(() -> service(before).classify(request("질문", CONTEXT))).isInstanceOf(ConversationExpiryGuard.ConversationExpired.class);
        assertThat(before.sent).isEmpty();
        clock.now = NOW;
        var during = new ScriptedClient(reply(wire("GQ", null, null, null, null)));
        during.onSend = sent -> clock.now = NOW.plusSeconds(600);
        assertThatThrownBy(() -> service(during).classify(request("질문", CONTEXT))).isInstanceOf(ConversationExpiryGuard.ConversationExpired.class);
        assertThat(during.sent).hasSize(1);
        clock.now = NOW;
        var cancelled = new ScriptedClient(reply(wire("GQ", null, null, null, null)));
        var token = new CancellationToken(); token.signal(CancellationToken.Reason.CANCELLED);
        assertThatThrownBy(() -> service(cancelled).classify(new ClassificationService.Request("job-1", NOW.plusSeconds(600), token, new JobRequestInput("message", 1, "질문", CONTEXT))))
            .isInstanceOf(IllegalStateException.class);
        assertThat(cancelled.sent).isEmpty();
    }

    @Test void twoRequestsWithTheSameIdsNeverShareBindings() {
        var client = new ScriptedClient(reply(wire("KQ", null, null, null, "Q1")), reply(wire("KQ", null, null, null, "Q1")));
        var service = service(client);
        var first = (ClassificationService.Classified) service.classify(request("\"alpha\"", CONTEXT));
        var second = (ClassificationService.Classified) service.classify(request("\"beta\"", CONTEXT));
        assertThat(first.classification().q()).isEqualTo("alpha");
        assertThat(second.classification().q()).isEqualTo("beta");
        assertThat(client.sent.get(1).messages().get(1).content()).doesNotContain("alpha");
    }

    @Test void templateMustHaveExactlyOneRegistrySlotAndNoLeftoverPlaceholder() {
        var client = new ScriptedClient();
        for (String bad : List.of("no slot", "{{REGISTRY}} {{REGISTRY}}", "a\r\n{{REGISTRY}}", "{{REGISTRY}} {{TOOLS}}"))
            assertThatThrownBy(() -> new ClassificationService(client, TokenBudgetPolicy.current(), CODEPOINTS, expiry(clock), observer, bad, "r")).as(bad).isInstanceOf(IllegalArgumentException.class);
    }
}
