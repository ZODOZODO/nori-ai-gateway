package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.registry.Availability;
import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 고정 workflow 기본 경로의 LLM_STUB 방어 시험(Plan P20 시험 목록 AC-22·AC-30·AC-32·AC-43·동시 동일대화 입력). */
class LlmStubWorkflowIT extends LlmStubSupport {
    private static final String MISSING_KEY = "{\"t\":\"LQ\",\"eqp\":\"EQP-WRONG\",\"from\":null,\"to\":null}";

    private void assertNoToolAndNoComposer(JobAdmissionService.Accepted job) {
        assertThat(tools(job.jobId())).as("Tool 실행").isZero();
        assertThat(calls().count("getEqpSnapshot")).as("업무 Source 호출").isZero();
        assertThat(scripts().observedCalls(job.jobId(), CallType.COMPOSER)).as("Composer 호출").isZero();
    }

    @Test @Tag("AC-32-RETRYOK") void retryAfterASchemaViolationProceedsOnlyWithTheRetryOutput() throws Exception {
        var job = admitScripted("retry-ok", "EQP-001 상태 알려줘", windowOnly(),
            List.of(classify(MISSING_KEY), classify(wire("LQ", "EQP-001", null, null, null)), compose("EQP-001 은 연결되어 운전 중이다[e1].")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("SUCCEEDED");
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).isEqualTo(2);
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isOne();
        assertThat(result(job.jobId())).containsEntry("status", "SUCCESS").containsEntry("error_code", null);
        assertThat(observations().workflows.getLast().classification()).containsEntry("eqp", "EQP-001");
        assertThat(calls().count("getEqpSnapshot")).isOne();
        String stored = jdbc().sql("SELECT string_agg(payload::text, '') FROM gw.tool_execution WHERE job_id=:id").param("id", job.jobId()).query(String.class).single();
        assertThat(stored).doesNotContain("EQP-WRONG");
        assertThat(payload(job.jobId()).toString()).doesNotContain("EQP-WRONG");
        assertThat(payload(job.jobId()).path("answer").asString()).isEqualTo("EQP-001 은 연결되어 운전 중이다[e1].");
    }

    @Test @Tag("AC-32-SCHEMA") @Tag("AC-32-LENGTH") void twoSchemaOrLengthFailuresEndAsPlanInvalidWithoutAnyTool() throws Exception {
        var length = new TestHookRegistry.LlmReply(CallType.CLASSIFY, "{\"t\":\"LQ\",\"eqp\":\"EQP-0", "length", null, 5_120);
        for (var reply : List.of(classify(MISSING_KEY), length)) {
            calls().reset();
            var job = admitScripted("fail-" + reply.finishReason(), "EQP-001 상태 알려줘", windowOnly(), List.of(reply, reply), Optional.empty(), null);
            assertThat(terminal(job)).isEqualTo("FAILED");
            assertThat(result(job.jobId())).containsEntry("status", "FAILED").containsEntry("error_code", "PLAN_INVALID");
            assertClarification(job.jobId(), "PLAN_INVALID", List.of("q"));
            assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).isEqualTo(2);
            assertNoToolAndNoComposer(job);
        }
    }

    @Test @Tag("AC-30-EQ") @Tag("AC-30-WM") @Tag("AC-30-ABSENT") void damagedForeignOrAbsentEquipmentStopsAtResourceValidationWithAClarification() throws Exception {
        for (String eqp : List.of("EQ", "WM", "EQP-NOT-FOUND")) {
            calls().reset();
            var job = admitScripted("resource-" + eqp, "설비 상태 알려줘", windowOnly(), List.of(classify(wire("LQ", eqp, null, null, null))), Optional.empty(), null);
            assertThat(terminal(job)).as(eqp).isEqualTo("FAILED");
            assertThat(result(job.jobId())).as(eqp).containsEntry("status", "FAILED").containsEntry("error_code", "RESOURCE_NOT_FOUND");
            // ⑧ 만 targetResources 필드의 RESOURCE_NOT_FOUND 확인 요청을 만든다(⑨ UNSUPPORTED 아님).
            assertClarification(job.jobId(), "RESOURCE_NOT_FOUND", List.of("targetResources"));
            assertThat(calls().count("resolveResource")).as(eqp).isPositive();
            assertNoToolAndNoComposer(job);
        }
    }

    @Test @Tag("AC-22-STUB-TOOL") @Tag("AC-22-STUB-SYSTEM") void unregisteredToolOrForeignSystemFieldsAreRejectedAsPlanInvalidAndBoundSystemStaysTc() throws Exception {
        var tool = classify("{\"t\":\"LQ\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null,\"capabilityId\":\"tc.unknown.tool\"}");
        var system = classify("{\"t\":\"LQ\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null,\"targetSystem\":\"WM\",\"capabilityId\":\"wm.inventory.get\"}");
        for (var reply : List.of(tool, system)) {
            calls().reset();
            var job = admitScripted("stub-" + reply.content().length(), "EQP-001 상태 알려줘", windowOnly(), List.of(reply, reply), Optional.empty(), null);
            assertThat(terminal(job)).isEqualTo("FAILED");
            assertThat(result(job.jobId())).containsEntry("error_code", "PLAN_INVALID");
            assertThat(jdbc().sql("SELECT owner_system FROM gw.job WHERE id=:id").param("id", job.jobId()).query(String.class).single()).isEqualTo("TC");
            assertNoToolAndNoComposer(job);
        }
    }

    @Test @Tag("AC-22-GQ-HELP") @Tag("AC-22-GQ-BYPASS-DATA") @Tag("AC-22-GQ-BYPASS-XSYS") void generalQueryReadsNoBusinessSourceAndComposesOnlyFromManifestPhrases() throws Exception {
        var registry = context.getBean(CapabilityRegistry.class);
        var expected = registry.view(scope("actor")).capabilities().stream().filter(m -> m.availability() == Availability.IMPLEMENTED && m.requiredPermission().stream().anyMatch(perms::contains)).toList();
        String description = expected.getFirst().description();
        String snippet = description.substring(0, Math.min(40, description.length()));
        var watch = Optional.of(new TestHookRegistry.PromptWatch(List.of(snippet, "tc.knowledge.search")));
        for (String question : List.of("여기서 뭘 물어볼 수 있어?", "EQP_001 지금 상태 알려줘", "WM 에서 LOT-001 재고 알려줘")) {
            calls().reset();
            var job = admitScripted("gq-" + question.hashCode(), question, Map.of(), List.of(classify(wire("GQ", null, null, null, null)), compose("물어볼 수 있는 기능 범위를 안내한다.")), watch, null);
            assertThat(terminal(job)).as(question).isEqualTo("SUCCEEDED");
            assertThat(result(job.jobId())).containsEntry("status", "SUCCESS");
            assertThat(tools(job.jobId())).isZero();
            assertThat(calls().count("getEqpSnapshot") + calls().count("resolveResource") + calls().count("listDlq")).as("업무 Source Read").isZero();
            var value = payload(job.jobId());
            var rows = new ArrayList<String>(); value.path("blocks").get(0).path("content").path("rows").forEach(row -> rows.add(row.path("capabilityId").asString()));
            assertThat(rows).containsExactlyElementsOf(expected.stream().map(m -> m.capabilityId()).toList()).doesNotContain("tc.knowledge.search", "tc.knowledge.getSource");
            assertThat(value.has("claims")).isFalse();
            assertThat(value.path("evidence")).isEmpty();
            var observed = scripts().promptWatch(job.jobId()).orElseThrow();
            assertThat(observed).contains(new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(CallType.COMPOSER, 0, 1), new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(CallType.COMPOSER, 1, 0));
            assertThat(jdbc().sql("SELECT task_type FROM gw.audit_event WHERE job_id=:id AND event_type='JOB_CLASSIFIED'").param("id", job.jobId()).query(String.class).single()).isEqualTo("GENERAL_QUERY");
        }
    }

    @Test @Tag("AC-43-CLS") void anOverBudgetClassificationInputIsNeverSentAndFailsWithInputBudgetExceeded() throws Exception {
        String question = "EQP-001 의 최근 통신 오류와 로그를 모두 자세히 분석해 줘. ".repeat(400);
        var job = admitScripted("cls-budget", question, windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, null)), compose("서술")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("FAILED");
        assertThat(result(job.jobId())).containsEntry("status", "FAILED").containsEntry("error_code", "INPUT_BUDGET_EXCEEDED");
        assertThat(payload(job.jobId()).has("clarification")).isFalse();
        assertThat(scripts().observedCalls(job.jobId(), CallType.CLASSIFY)).as("예산 assert 까지 간 분류 시도").isOne();
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).as("분류 전송").isZero();
        assertNoToolAndNoComposer(job);
    }

    @Test @Tag("AC-43-COMP") void composerInputThatStaysOverBudgetAfterReductionSendsNoComposerCallAndNoFallbackSentence() throws Exception {
        var server = context.getBean(WorkflowSourceFixture.Server.class);
        // 추적 결과의 span 이름은 축소 4단계(outputSchema 필수 필드)에도 남는 Tool 결과다.
        server.traceVersions = List.of("image-tag"); server.spanName = "설비추적구간이름".repeat(700);
        var job = admitScripted("comp-budget", "EQP-001 장애 원인 찾아줘", workflowFields(),
            List.of(classify(wire("INC", "EQP-001", null, null, null)), compose("대체 서술 FALLBACKMARK")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("FAILED");
        assertThat(result(job.jobId())).containsEntry("status", "FAILED").containsEntry("error_code", "INPUT_BUDGET_EXCEEDED");
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).isOne();
        assertThat(scripts().observedCalls(job.jobId(), CallType.COMPOSER)).isZero();
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isZero();
        var value = payload(job.jobId());
        assertThat(value.has("answer")).isFalse();
        assertThat(value.toString()).doesNotContain("FALLBACKMARK");
        assertThat(value.path("blocks")).isNotEmpty();
    }

    @Test void concurrentJobsOfTheSameConversationEachClassifyAndComposeOnlyTheirOwnInput() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        var watch = Optional.of(new TestHookRegistry.PromptWatch(List.of("S-ONE", "S-TWO")));
        var script = List.of(classify(wire("GQ", null, null, null, null)), compose("안내한다."));
        var first = admitScripted("same-one", "S-ONE 무엇을 물을 수 있나", Map.of(), script, watch, null);
        var second = admitScripted("same-two", "S-TWO 무엇을 물을 수 있나", Map.of(), script, watch, first.conversationId());
        pauses().release(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        assertThat(terminal(first)).isEqualTo("SUCCEEDED");
        assertThat(terminal(second)).isEqualTo("SUCCEEDED");
        for (var type : List.of(CallType.CLASSIFY, CallType.COMPOSER)) {
            assertThat(scripts().promptWatch(first.jobId()).orElseThrow()).contains(new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(type, 0, 1), new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(type, 1, 0));
            assertThat(scripts().promptWatch(second.jobId()).orElseThrow()).contains(new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(type, 0, 0), new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(type, 1, 1));
        }
        var inputs = observations().workflows.stream().map(job -> job.requestInput().maskedQuestion()).toList();
        assertThat(inputs).containsExactlyInAnyOrder("S-ONE 무엇을 물을 수 있나", "S-TWO 무엇을 물을 수 있나");
    }
}
