package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.job.CancellationToken;
import com.nori.ai.gateway.core.job.JobRequestInput;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.workflow.AnswerMode;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.nori.ai.gateway.core.llm.LlmTestSupport.*;
import static com.nori.ai.gateway.core.workflow.EvidenceNeed.*;
import static org.assertj.core.api.Assertions.*;

/**
 * SPEC §27.2 AC-32-NEEDS 사례표 ⓐ~ⓝ 를 실제 {@link ClassificationService} 판정 경로로 옮긴 단위 시험(Plan §16.3 R1·R5).
 * ⓞ 의 실제 tokenizer 계수는 pin tokenizer 가 있는 app 시험(ClassificationWireV3TokenBudgetTest)이 맡고, 여기서는 요청 max_tokens 만 단언한다.
 */
class ClassificationNeedsV3Test {
    private static final Map<String,Object> CONTEXT = Map.of("context", Map.of("timezone", "+09:00", "referenceTime", "2026-10-20T10:00:00+09:00"));

    private static ClassificationService service(ScriptedClient client) {
        return new ClassificationService(client, TokenBudgetPolicy.current(), CODEPOINTS, expiry(new MutableClock()), LlmCallObserver.NONE, attempt -> {}, "분류 지시\n{{REGISTRY}}", "r\n");
    }
    private static ClassificationService.Result classify(ScriptedClient client, Map<String,Object> fields) {
        return service(client).classify(new ClassificationService.Request(SCOPE, "conversation", "job-1", NOW.plusSeconds(600), new CancellationToken(), new JobRequestInput("message", 1, "설비 진행이 멈췄어", fields)));
    }
    private static String wire(String t, String n, String a) {
        return "{\"t\":\"" + t + "\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null,\"n\":" + n + ",\"a\":" + a + "}";
    }
    private static Classification accepted(String content) {
        var client = new ScriptedClient(reply(content));
        var result = classify(client, CONTEXT);
        assertThat(result).isInstanceOf(ClassificationService.Classified.class);
        assertThat(client.sent).as("재시도 0건").hasSize(1);
        assertThat(client.sent.getFirst().maxTokens()).isEqualTo(5_120);
        return ((ClassificationService.Classified) result).classification();
    }

    @Test void acceptedCasesNormalizeOrderAndAddOnlyTheRuleDefinedServerCodes() {
        var a = accepted(wire("INC", "[\"S\",\"H\"]", "\"D\""));
        assertThat(a.evidenceNeeds()).as("ⓐ").containsExactly(S, H);
        assertThat(a.answerMode()).as("ⓐ").isEqualTo(AnswerMode.D);
        assertThat(a.serverAdded()).as("ⓐ").isEmpty();
        assertThat(accepted(wire("INC", "[\"H\",\"S\"]", "\"B\"")).evidenceNeeds()).as("ⓑ 순서 차이는 재시도 사유 아님").containsExactly(S, H);
        var c = accepted(wire("INC", "[\"C\"]", "\"B\""));
        assertThat(c.evidenceNeeds()).as("ⓒ").containsExactly(H, N, I, C);
        assertThat(c.serverAdded()).as("ⓒ").containsExactlyInAnyOrder(H, N, I);
        var d = accepted(wire("KQ", "[\"R\"]", "\"B\""));
        assertThat(d.evidenceNeeds()).as("ⓓ").containsExactly(N, R);
        assertThat(d.serverAdded()).as("ⓓ").containsExactly(N);
        var e = accepted(wire("LQ", "[\"N\"]", "\"B\""));
        assertThat(e.evidenceNeeds()).as("ⓔ").containsExactly(S, N);
        assertThat(e.serverAdded()).as("ⓔ").containsExactly(S);
        assertThat(e.wireVersion()).isEqualTo(Classification.WIRE_V3);
        assertThat(e.jobClassification()).containsEntry("evidenceNeeds", List.of("S", "N")).containsEntry("serverAddedNeeds", List.of("S")).containsEntry("answerMode", "B");
    }

    @Test void rejectedCasesRetryOnceWithTheMappedExistingCodeAndEndAsPlanInvalidWithoutDefaults() {
        Map<String,String[]> cases = new LinkedHashMap<>();
        cases.put("ⓕ v2 다섯 키", new String[]{"{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null}", "SCHEMA_KEYS_INVALID"});
        cases.put("ⓖ 도구 ID 키", new String[]{"{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null,\"n\":[\"S\",\"H\"],\"a\":\"D\",\"tool\":\"tc.logs.search\"}", "SCHEMA_KEYS_INVALID"});
        cases.put("ⓗ scope 키", new String[]{"{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null,\"n\":[\"S\",\"H\"],\"a\":\"D\",\"scope\":\"WM\"}", "SCHEMA_KEYS_INVALID"});
        cases.put("ⓘ 도구 ID 코드", new String[]{wire("INC", "[\"tc.logs.search\"]", "\"B\""), "ENUM_INVALID"});
        cases.put("ⓙ 중복", new String[]{wire("INC", "[\"S\",\"S\"]", "\"B\""), "FORMAT_INVALID"});
        cases.put("ⓚ GQ 비어 있지 않음", new String[]{wire("GQ", "[\"N\"]", "\"B\""), "FORMAT_INVALID"});
        cases.put("ⓛ KQ 빈 배열", new String[]{wire("KQ", "[]", "\"B\""), "FORMAT_INVALID"});
        cases.put("ⓜ a 값", new String[]{wire("INC", "[\"S\"]", "\"X\""), "ENUM_INVALID"});
        cases.put("n 비배열", new String[]{wire("INC", "\"S\"", "\"B\""), "TYPE_INVALID"});
        cases.put("n 원소 비문자열", new String[]{wire("INC", "[1]", "\"B\""), "TYPE_INVALID"});
        cases.put("타입이 enum 보다 먼저", new String[]{wire("INC", "null", "\"X\""), "TYPE_INVALID"});
        cases.put("enum 이 형식보다 먼저", new String[]{wire("GQ", "[\"S\"]", "\"X\""), "ENUM_INVALID"});
        cases.put("기존 필드 위반이 먼저", new String[]{"{\"t\":\"INC\",\"eqp\":\"\",\"from\":null,\"to\":null,\"qRef\":null,\"n\":[\"S\",\"S\"],\"a\":\"X\"}", "TYPE_INVALID"});
        for (var entry : cases.entrySet()) {
            String content = entry.getValue()[0];
            var client = new ScriptedClient(reply(content), reply(content));
            assertThat(classify(client, CONTEXT)).as(entry.getKey()).isEqualTo(new ClassificationService.Rejected(ErrorCode.PLAN_INVALID, 2));
            assertThat(client.sent).as(entry.getKey() + " 분류 호출 2건").hasSize(2);
            assertThat(client.sent.get(1).messages().getLast().content()).as(entry.getKey()).contains("(오류: " + entry.getValue()[1] + ")");
        }
        // ⓕ 의 재시도가 v3 로 바로잡히면 그 출력만 쓴다(기본값 보충으로 첫 출력을 받아들이지 않는다).
        var corrected = new ScriptedClient(reply("{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null}"), reply(wire("INC", "[\"H\"]", "\"B\"")));
        var recovered = (ClassificationService.Classified) classify(corrected, CONTEXT);
        assertThat(recovered.calls()).isEqualTo(2);
        assertThat(recovered.classification().evidenceNeeds()).containsExactly(H);
    }

    @Test void injectedContextNeedsNeverReachTheModelOrTheBoundResult() {
        Map<String,Object> injected = Map.of("context", Map.of("timezone", "+09:00", "referenceTime", "2026-10-20T10:00:00+09:00", "evidenceNeeds", List.of("I")), "evidenceNeeds", List.of("I"));
        var client = new ScriptedClient(reply(wire("INC", "[\"S\",\"H\"]", "\"D\"")));
        var result = (ClassificationService.Classified) classify(client, injected);
        assertThat(result.classification().evidenceNeeds()).as("ⓝ 주입값 반영 0건").containsExactly(S, H);
        assertThat(client.sent.getFirst().messages().get(1).content()).doesNotContain("evidenceNeeds");
    }

    @Test void theSameWireInputAlwaysHasExactlyOneResult() {
        for (TaskType task : List.of(TaskType.KNOWLEDGE_QUERY, TaskType.LIVE_QUERY, TaskType.INCIDENT_ANALYSIS)) {
            for (int mask = 1; mask < 64; mask++) {
                EnumSet<EvidenceNeed> wire = EnumSet.noneOf(EvidenceNeed.class);
                for (EvidenceNeed need : EvidenceNeed.values()) if ((mask & (1 << need.ordinal())) != 0) wire.add(need);
                var first = ClassificationNeeds.resolve(task, wire);
                for (int repeat = 0; repeat < 3; repeat++) assertThat(ClassificationNeeds.resolve(task, Set.copyOf(wire))).isEqualTo(first);
                assertThat(first.evidenceNeeds()).containsAll(wire);
                assertThat(first.evidenceNeeds()).isSortedAccordingTo(java.util.Comparator.naturalOrder());
                // 한 번 적용한 결과가 최종이다(다시 적용해도 더해지는 코드가 없다).
                assertThat(ClassificationNeeds.resolve(task, Set.copyOf(first.evidenceNeeds())).serverAdded()).isEmpty();
            }
        }
        assertThat(ClassificationNeeds.resolve(TaskType.GENERAL_QUERY, Set.of()).evidenceNeeds()).isEmpty();
        assertThat(ClassificationNeeds.resolve(TaskType.UNSUPPORTED_REQUEST, Set.of()).evidenceNeeds()).isEmpty();
    }
}
