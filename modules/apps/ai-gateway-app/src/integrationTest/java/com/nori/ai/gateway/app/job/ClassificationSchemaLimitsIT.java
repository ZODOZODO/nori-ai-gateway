package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.job.JobContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 실제 Gateway 분류 경로의 qRef·인자 한도(Plan P20 ClassificationSchemaLimitsIT). 원문 q 한도는 arg-limits v2 CONFIRMED 256 CODEPOINT,
 * wire 는 요청별 qRef 동적 목록으로 검증하고 복원한 q 는 원문 코드포인트 그대로 resolved Classification 에 들어간다.
 */
class ClassificationSchemaLimitsIT extends LlmStubSupport {
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private JobContext classified(String jobId) {
        return observations().workflows.stream().filter(job -> job.jobId().equals(jobId)).findFirst().orElseThrow();
    }
    private JobAdmissionService.Accepted run(String key, String question, String qRef, String expectedState) throws Exception {
        var job = admitScripted(key, question, windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, qRef)), compose("상태를 확인했다[e1].")), Optional.empty(), null);
        assertThat(terminal(job)).as(key).isEqualTo(expectedState);
        return job;
    }

    @Test void literalFailuresStopBeforeAnyInferenceOrSourceWithTheStructuredClarification() throws Exception {
        for (var entry : Map.of("empty", "빈 \"\" 검색", "over-257", "\"" + EMOJI.repeat(257) + "\" 검색", "bad-json", "json:\"bad \\q\" 검색", "unclosed", "닫힘 \"없음").entrySet()) {
            calls().reset();
            var job = admitScripted("literal-" + entry.getKey(), entry.getValue(), windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, "Q1"))), Optional.empty(), null);
            assertThat(terminal(job)).as(entry.getKey()).isEqualTo("FAILED");
            String reason = entry.getKey().equals("over-257") ? "INPUT_BUDGET_EXCEEDED" : "PLAN_INVALID";
            assertThat(result(job.jobId())).as(entry.getKey()).containsEntry("error_code", reason);
            assertClarification(job.jobId(), reason, List.of("q"));
            assertThat(scripts().observedCalls(job.jobId(), CallType.CLASSIFY)).as("분류 호출").isZero();
            assertThat(calls().count("resolveResource") + calls().count("getEqpSnapshot")).as("Source").isZero();
            assertThat(tools(job.jobId())).isZero();
        }
    }

    @Test void restoredQueryKeepsTheExactOriginalCodepointsForEveryLiteralForm() throws Exception {
        var cases = new LinkedHashMap<String,String[]>();
        cases.put("windows", new String[]{"로그 \"C:\\temp\\new\" 찾아줘", "Q1", "C:\\temp\\new"});
        cases.put("json-escape", new String[]{"json:\"a \\\"b\\\" \\\\ c\\td\" 찾아줘", "Q1", "a \"b\" \\ c\td"});
        cases.put("supplementary-256", new String[]{"\"" + EMOJI.repeat(256) + "\"", "Q1", EMOJI.repeat(256)});
        cases.put("multiple", new String[]{"\"first\" 말고 \"second\" 찾아줘", "Q2", "second"});
        cases.put("duplicate", new String[]{"\"dup\" 그리고 다시 \"dup\"", "Q2", "dup"});
        cases.put("request-a", new String[]{"\"alpha-request\"", "Q1", "alpha-request"});
        cases.put("request-b", new String[]{"\"beta-request\"", "Q1", "beta-request"});
        for (var entry : cases.entrySet()) {
            var job = run("restore-" + entry.getKey(), entry.getValue()[0], entry.getValue()[1], "SUCCEEDED");
            Object q = classified(job.jobId()).classification().get("q");
            assertThat(q).as(entry.getKey()).isEqualTo(entry.getValue()[2]);
            assertThat(((String) q).codePoints().toArray()).as(entry.getKey()).isEqualTo(entry.getValue()[2].codePoints().toArray());
            assertThat(classified(job.jobId()).classification()).doesNotContainKey("qRef");
        }
    }

    @Test void timeTextNanosAndOffsetAndTheLongestEquipmentArePreservedWithoutTruncation() throws Exception {
        var job = admitScripted("time-precise", "EQP-001 상태", windowOnly(),
            List.of(classify(wire("LQ", "EQP-001", "2026-11-05T23:00:00.123456789+09:00", "2026-11-05T23:30:00.987654321+09:00", null)), compose("상태를 확인했다[e1].")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("SUCCEEDED");
        assertThat(classified(job.jobId()).classification()).containsEntry("from", "2026-11-05T23:00:00.123456789+09:00").containsEntry("to", "2026-11-05T23:30:00.987654321+09:00");
        String window = jdbc().sql("SELECT resolved_time_window::text FROM gw.message WHERE id=:id").param("id", job.messageId()).query(String.class).single();
        assertThat(window).contains("123456789").contains("987654321");
        String eqp = "포토설비A동1라인EQP0001경계최대길이검증용식별자abcdefghij0123456789KLMN가나다라마바사아자차카타";
        assertThat(eqp.codePointCount(0, eqp.length())).isEqualTo(64);
        var longest = admitScripted("eqp-64", "설비 상태", windowOnly(), List.of(classify(wire("LQ", eqp, null, null, null))), Optional.empty(), null);
        assertThat(terminal(longest)).isEqualTo("FAILED");
        assertThat(classified(longest.jobId()).classification()).containsEntry("eqp", eqp);
        assertThat(result(longest.jobId())).containsEntry("error_code", "RESOURCE_NOT_FOUND");
    }

    @Test void forgedNullRawQueryAndImpossibleDateFailTheRetryAndEndAsPlanInvalidWithoutSource() throws Exception {
        var cases = new LinkedHashMap<String,String>();
        cases.put("forged", wire("LQ", "EQP-001", null, null, "Q9"));
        cases.put("null-with-candidate", wire("LQ", "EQP-001", null, null, null));
        cases.put("raw-q", "{\"t\":\"LQ\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":\"Q1\",\"q\":\"TIMEOUT\"}");
        cases.put("impossible-date", wire("LQ", "EQP-001", "2026-02-30T00:00Z", "2026-03-01T00:00Z", "Q1"));
        for (var entry : cases.entrySet()) {
            calls().reset();
            var reply = classify(entry.getValue());
            var job = admitScripted("retry-" + entry.getKey(), "EQP-001 \"TIMEOUT\" 로그", windowOnly(), List.of(reply, reply), Optional.empty(), null);
            assertThat(terminal(job)).as(entry.getKey()).isEqualTo("FAILED");
            assertThat(result(job.jobId())).as(entry.getKey()).containsEntry("error_code", "PLAN_INVALID");
            assertClarification(job.jobId(), "PLAN_INVALID", List.of("q"));
            assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).as(entry.getKey()).isEqualTo(2);
            assertThat(calls().count("resolveResource") + calls().count("getEqpSnapshot")).as("Source").isZero();
            assertThat(scripts().observedCalls(job.jobId(), CallType.COMPOSER)).isZero();
            assertThat(payload(job.jobId()).toString()).doesNotContain("TIMEOUT");
        }
    }

    @Test void theClassificationWireNeverCarriesTheOriginalQueryOnlyTheCatalog() throws Exception {
        var watch = Optional.of(new TestHookRegistry.PromptWatch(List.of("\"qRefCatalog\":[\"Q1\",\"Q2\"]", "\"q\":")));
        var job = admitScripted("catalog", "\"one\" 또는 \"two\" 찾아줘", windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, "Q1")), compose("상태를 확인했다[e1].")), watch, null);
        assertThat(terminal(job)).isEqualTo("SUCCEEDED");
        assertThat(scripts().promptWatch(job.jobId()).orElseThrow()).contains(new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(CallType.CLASSIFY, 0, 1),
            new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(CallType.CLASSIFY, 1, 0));
        assertThat(classified(job.jobId()).classification()).containsEntry("q", "one");
    }
}
