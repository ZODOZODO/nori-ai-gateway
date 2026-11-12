package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.LlmScriptRegistry;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.app.inference.OllamaEndpointStub;
import com.nori.ai.gateway.core.budget.CallType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Composer 실패 매핑 L-5 ⓐ~ⓓ 와 Source 응답 마스킹 → 축소 → 예산 assert 실제 경로(Plan P20 LlmComposerFailureMappingIT). */
class LlmComposerFailureMappingIT extends LlmStubSupport {
    /** ContentSanitizer 규칙에 걸리지 않는 표지(마스킹으로 우연히 사라지지 않게 한다). */
    private static final String THINK_MARK = "THINKMARKQZ";

    private String allStored(String job) {
        return jdbc().sql("SELECT coalesce((SELECT payload::text FROM gw.result WHERE job_id=:id),'') || coalesce((SELECT string_agg(content_masked,'') FROM gw.message m WHERE EXISTS (SELECT 1 FROM gw.job j WHERE j.id=:id AND j.conversation_id=m.conversation_id)),'')")
            .param("id", job).query(String.class).single();
    }

    @Test void blocksWithAComposerTransportFailureArePartialWithNarrativeUnavailableAndNoRetry() throws Exception {
        var chats = new AtomicInteger();
        stub.respond(request -> request.path().equals("/v1/chat/completions")
            ? (chats.getAndIncrement() == 0 ? chat(wire("LQ", "EQP-001", null, null, null), 100_000) : new OllamaEndpointStub.Json(503, "{\"error\":\"busy\"}"))
            : OllamaEndpointStub.healthy(request));
        var job = admitUnarmed("composer-5xx", "EQP-001 상태 알려줘", windowOnly(), null);
        assertThat(terminal(job)).isEqualTo("PARTIAL");
        assertThat(result(job.jobId())).containsEntry("status", "PARTIAL").containsEntry("error_code", "LLM_UNAVAILABLE");
        var value = payload(job.jobId());
        assertThat(value.path("blocks")).isNotEmpty();
        assertThat(value.path("missingFields").get(0).asString()).isEqualTo("answer");
        assertThat(value.has("answer")).isFalse();
        assertThat(chats).as("분류 1 + Composer 1(전송 실패 재시도 0)").hasValue(2);
    }

    @Test void noVerifiedBlockOrEvidenceWithAComposerFailureIsFailedLlmUnavailable() throws Exception {
        // 리소스 카탈로그 장애로 Source 조회 단계가 모두 SOURCE_UNAVAILABLE 이고 검증된 블록·Evidence 가 0개다.
        context.getBean(com.nori.ai.gateway.tcmock.MockScenarioStore.class).select("catalog-down");
        var empty = new TestHookRegistry.LlmReply(CallType.COMPOSER, null, "stop", null, 0);
        var job = admitScripted("composer-no-data", "EQP-001 상태 알려줘", windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, null)), empty), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("FAILED");
        assertThat(result(job.jobId())).containsEntry("status", "FAILED").containsEntry("error_code", "LLM_UNAVAILABLE");
        var value = payload(job.jobId());
        assertThat(value.path("blocks")).isEmpty();
        assertThat(value.path("evidence")).isEmpty();
        assertThat(value.has("answer")).isFalse();
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isOne();
        assertThat(value.path("toolSummary").get(0).path("status").asString()).isIn("DENIED", "SOURCE_UNAVAILABLE");
    }

    @Test void overBudgetComposerInputIsFailedInputBudgetExceededWithBlocksAndNoComposerCall() throws Exception {
        var server = context.getBean(WorkflowSourceFixture.Server.class);
        server.traceVersions = List.of("image-tag"); server.spanName = "설비추적구간이름".repeat(700);
        var job = admitScripted("composer-budget", "EQP-001 장애 원인 찾아줘", workflowFields(), List.of(classify(wire("INC", "EQP-001", null, null, null)), compose("FALLBACKMARK")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("FAILED");
        assertThat(result(job.jobId())).containsEntry("error_code", "INPUT_BUDGET_EXCEEDED");
        assertThat(payload(job.jobId()).path("blocks")).isNotEmpty();
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isZero();
        assertThat(allStored(job.jobId())).doesNotContain("FALLBACKMARK");
    }

    @Test @ExtendWith(OutputCaptureExtension.class) void thinkMarkersFailBothAttemptsWithoutBeingStoredOrLoggedAndARecoveredRetryComposes(CapturedOutput output) throws Exception {
        String marked = "</think>" + THINK_MARK + " 설비는 정상이다[e1].";
        var failed = admitScripted("think-twice", "EQP-001 상태 알려줘", windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, null)), compose(marked), compose(marked)), Optional.empty(), null);
        assertThat(terminal(failed)).isEqualTo("PARTIAL");
        assertThat(result(failed.jobId())).containsEntry("status", "PARTIAL").containsEntry("error_code", "LLM_UNAVAILABLE");
        assertThat(scripts().scriptedCalls(failed.jobId(), CallType.COMPOSER)).isEqualTo(2);
        assertThat(payload(failed.jobId()).path("blocks")).isNotEmpty();
        assertThat(allStored(failed.jobId())).doesNotContain(THINK_MARK, "think>");
        assertThat(output.getAll()).doesNotContain(THINK_MARK);
        var recovered = admitScripted("think-once", "EQP-001 상태 알려줘", windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, null)), compose("<think>" + THINK_MARK), compose("설비는 정상이다[e1].")), Optional.empty(), null);
        assertThat(terminal(recovered)).isEqualTo("SUCCEEDED");
        assertThat(payload(recovered.jobId()).path("answer").asString()).isEqualTo("설비는 정상이다[e1].");
        assertThat(allStored(recovered.jobId())).doesNotContain(THINK_MARK);
        assertThat(jdbc().sql("SELECT content_masked FROM gw.message WHERE conversation_id=:c AND role='assistant'").param("c", recovered.conversationId()).query(String.class).single()).isEqualTo("설비는 정상이다[e1].");
    }

    @Test void sourceTextReachesTheComposerPromptOnlyAfterMaskingInTheActualPath() throws Exception {
        var server = context.getBean(WorkflowSourceFixture.Server.class);
        server.logText = "EQP-001 EQUIPMENT_ERROR operator-x@example.com 원문표지QZ";
        var watch = Optional.of(new TestHookRegistry.PromptWatch(List.of("operator-x@example.com", "EQUIPMENT_ERROR", "원문표지QZ")));
        var job = admitScripted("source-mask", "EQP-001 장애 원인 찾아줘", workflowFields(), List.of(classify(wire("INC", "EQP-001", null, null, null)), compose("장애 로그가 확인되었다[e1].")), watch, null);
        assertThat(terminal(job)).isIn("SUCCEEDED", "PARTIAL");
        var rows = scripts().promptWatch(job.jobId()).orElseThrow();
        assertThat(rows).contains(new LlmScriptRegistry.WatchRow(CallType.COMPOSER, 0, 0), new LlmScriptRegistry.WatchRow(CallType.COMPOSER, 1, 1), new LlmScriptRegistry.WatchRow(CallType.COMPOSER, 2, 1));
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isOne();
    }
}
