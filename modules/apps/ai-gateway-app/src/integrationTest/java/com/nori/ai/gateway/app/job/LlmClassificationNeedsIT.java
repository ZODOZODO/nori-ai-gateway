package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.LlmScriptRegistry;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.job.JobContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * SPEC §27.2 AC-32-NEEDS 사례표 ⓐ~ⓝ 의 F2 IT component(§28.9, Plan §16.9): 실제 Job 경로(접수 → JobWorker → ClassificationService → JobContext 결속 →
 * WorkflowExecutor)에서 분류 출력만 LLM_STUB 으로 고정한다. 거부 사례는 초회·재시도 모두 같은 위반이다. ⓞ 의 pin tokenizer 계수는 ClassificationWireV3TokenBudgetTest.
 */
class LlmClassificationNeedsIT extends LlmStubSupport {
    private static String v3(String t, String n, String a) {
        return "{\"t\":\"" + t + "\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null,\"n\":" + n + ",\"a\":" + a + "}";
    }
    private Optional<JobContext> bound(String jobId) { return observations().workflows.stream().filter(job -> job.jobId().equals(jobId)).findFirst(); }
    private JobAdmissionService.Accepted run(String key, String content, Map<String,Object> fields) throws Exception {
        var job = admitScripted(key, "EQP-001 진행이 멈췄어", fields, List.of(classify(content), compose("확인된 자료를 정리했다[e1].")), Optional.empty(), null);
        terminal(job);
        return job;
    }

    @Tag("AC-32-NEEDS") @Test void acceptedCasesBindTheNormalizedNeedsAndAnswerModeToTheJobWithoutRetry() throws Exception {
        Map<String,Object[]> cases = new LinkedHashMap<>();
        cases.put("a", new Object[]{v3("INC", "[\"S\",\"H\"]", "\"D\""), List.of("S", "H"), List.of(), "D"});
        cases.put("b", new Object[]{v3("INC", "[\"H\",\"S\"]", "\"B\""), List.of("S", "H"), List.of(), "B"});
        cases.put("c", new Object[]{v3("INC", "[\"C\"]", "\"B\""), List.of("H", "N", "I", "C"), List.of("H", "N", "I"), "B"});
        cases.put("d", new Object[]{v3("KQ", "[\"R\"]", "\"B\""), List.of("N", "R"), List.of("N"), "B"});
        cases.put("e", new Object[]{v3("LQ", "[\"N\"]", "\"B\""), List.of("S", "N"), List.of("S"), "B"});
        for (var entry : cases.entrySet()) {
            var job = run("needs-ok-" + entry.getKey(), (String) entry.getValue()[0], windowOnly());
            var classification = bound(job.jobId()).orElseThrow().classification();
            assertThat(classification.get("evidenceNeeds")).as(entry.getKey()).isEqualTo(entry.getValue()[1]);
            assertThat(classification.get("serverAddedNeeds")).as(entry.getKey()).isEqualTo(entry.getValue()[2]);
            assertThat(classification.get("answerMode")).as(entry.getKey()).isEqualTo(entry.getValue()[3]);
            assertThat(classification.get("wireVersion")).isEqualTo("classify-wire-v3");
            assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).as(entry.getKey() + " 재시도 0건").isOne();
        }
    }

    @Tag("AC-32-NEEDS") @Test void rejectedCasesRetryOnceWithTheMappedCodeAndEndAsPlanInvalidWithNoToolAndNoDefaults() throws Exception {
        Map<String,String[]> cases = new LinkedHashMap<>();
        cases.put("f", new String[]{"{\"t\":\"INC\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null}", "SCHEMA_KEYS_INVALID"});
        cases.put("g", new String[]{"{\"t\":\"INC\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null,\"n\":[\"S\",\"H\"],\"a\":\"D\",\"tool\":\"tc.logs.search\"}", "SCHEMA_KEYS_INVALID"});
        cases.put("h", new String[]{"{\"t\":\"INC\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null,\"n\":[\"S\",\"H\"],\"a\":\"D\",\"scope\":\"WM\"}", "SCHEMA_KEYS_INVALID"});
        cases.put("i", new String[]{v3("INC", "[\"tc.logs.search\"]", "\"B\""), "ENUM_INVALID"});
        cases.put("j", new String[]{v3("INC", "[\"S\",\"S\"]", "\"B\""), "FORMAT_INVALID"});
        cases.put("k", new String[]{v3("GQ", "[\"N\"]", "\"B\""), "FORMAT_INVALID"});
        cases.put("l", new String[]{v3("KQ", "[]", "\"B\""), "FORMAT_INVALID"});
        cases.put("m", new String[]{v3("INC", "[\"S\"]", "\"X\""), "ENUM_INVALID"});
        for (var entry : cases.entrySet()) {
            String content = entry.getValue()[0], code = entry.getValue()[1];
            var watch = Optional.of(new TestHookRegistry.PromptWatch(List.of("오류: " + code + ")")));
            var job = admitScripted("needs-bad-" + entry.getKey(), "EQP-001 진행이 멈췄어", windowOnly(), List.of(classify(content), classify(content)), watch, null);
            assertThat(terminal(job)).as(entry.getKey()).isEqualTo("FAILED");
            assertThat(result(job.jobId())).as(entry.getKey()).containsEntry("status", "FAILED").containsEntry("error_code", "PLAN_INVALID");
            assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).as(entry.getKey() + " 분류 호출 2건").isEqualTo(2);
            assertThat(tools(job.jobId())).as(entry.getKey() + " Tool 실행 0건").isZero();
            assertThat(bound(job.jobId())).as(entry.getKey() + " 기본값 보충으로 결속된 Job 0건").isEmpty();
            assertThat(scripts().promptWatch(job.jobId()).orElseThrow()).as(entry.getKey() + " 재시도 요지 코드").contains(new LlmScriptRegistry.WatchRow(CallType.CLASSIFY, 0, 1));
        }
    }

    @Tag("AC-32-NEEDS") @Test void needsInjectedThroughTheRequestContextAreNeverBound() throws Exception {
        Map<String,Object> injected = new HashMap<>(windowOnly());
        var context = new HashMap<String,Object>(castMap(injected.get("context"))); context.put("evidenceNeeds", List.of("I"));
        injected.put("context", context); injected.put("evidenceNeeds", List.of("I"));
        var job = run("needs-injected", v3("INC", "[\"S\",\"H\"]", "\"D\""), injected);
        assertThat(bound(job.jobId()).orElseThrow().classification().get("evidenceNeeds")).as("ⓝ 주입값 반영 0건").isEqualTo(List.of("S", "H"));
        assertThat(jdbc().sql("SELECT count(*) FROM gw.tool_execution WHERE job_id=:id AND capability_id='tc.git.searchCode'").param("id", job.jobId()).query(Integer.class).single())
            .as("주입된 I 로 git 단계가 선택되지 않는다").isZero();
    }

    @SuppressWarnings("unchecked") private static Map<String,Object> castMap(Object value) { return (Map<String,Object>) value; }
}
