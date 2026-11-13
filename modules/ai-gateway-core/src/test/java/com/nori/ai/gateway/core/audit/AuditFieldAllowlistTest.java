package com.nori.ai.gateway.core.audit;

import com.nori.ai.gateway.core.plan.RejectReason;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.scope.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuditFieldAllowlistTest {
    private static final String TRACE = "0af7651916cd43dd8448eb211c80319c";
    private final CapabilityRegistry registry = new CapabilityRegistry(new ManifestLoader().loadClasspath("manifests/tc/index.json"),
        new ManifestLoader().loadWorkflowsClasspath("workflows/tc/index.json"), Set.of(ImplementationPhase.PHASE_1), new ContractVersionPolicy());
    private final AuditFieldAllowlist allowlist = new AuditFieldAllowlist(new ContentSanitizer(), registry);
    private final VerifiedSystemScope scope = scope(OwnerSystem.TC, "service:TC", UUID.randomUUID().toString());
    private final String conversation = UUID.randomUUID().toString(), job = UUID.randomUUID().toString();

    private static VerifiedSystemScope scope(OwnerSystem owner, String service, String requestId) {
        return new VerifiedSystemScope(owner, service, new ActorRef("actor"), "actor", Set.of(), "a".repeat(64),
            Instant.parse("2026-11-06T00:00:00Z"), Instant.parse("2026-11-06T00:01:00Z"), requestId);
    }

    @Test void eachGeneralEventAcceptsOnlyItsFieldSetWithRegisteredToolValues() {
        var tool = registry.find("tc.logs.search").orElseThrow();
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.apiRequest(scope, TRACE))).doesNotThrowAnyException();
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.jobAccepted(scope, TRACE, conversation, job))).doesNotThrowAnyException();
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.jobClassified(scope, conversation, job, TaskType.INCIDENT_ANALYSIS))).doesNotThrowAnyException();
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.toolCall(scope, conversation, job, tool.toolId(), tool.sourceAllowlistRef(), ResultStatus.SUCCESS, null, 12))).doesNotThrowAnyException();
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.toolBlocked(scope, conversation, job, tool.toolId(), ResultStatus.DENIED, null, RejectReason.DENIED))).doesNotThrowAnyException();
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.toolBlocked(scope, conversation, job, null, ResultStatus.UNSUPPORTED, ErrorCode.SYSTEM_SCOPE_MISMATCH, RejectReason.OWNER_MISMATCH))).doesNotThrowAnyException();
    }

    @Test void authRejectedAndJobTerminalNeverUseTheGeneralPath() {
        for (var type : new AuditEventType[]{AuditEventType.AUTH_REJECTED, AuditEventType.JOB_TERMINAL})
            assertThatThrownBy(() -> allowlist.requireGeneral(new AuditEvent(scope, type, null, conversation, job, null, null, null, ResultStatus.SUCCESS, null, null, null)))
                .hasMessageContaining("eventType");
    }

    @Test void requestOriginNeedsAValidNonZeroTraceAServiceRefAndAUuidRequest() {
        for (String trace : new String[]{null, "0".repeat(32), TRACE.toUpperCase(), TRACE.substring(1), "SENTINEL_SECRET_0123456789abcdef"})
            assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.apiRequest(scope, trace))).hasMessageContaining("traceId");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.apiRequest(scope(OwnerSystem.TC, "service:TC", "질문 user@example.com"), TRACE))).hasMessageContaining("requestId");
        // 같은 mask-v1이 바꾸는 service 참조는 받지 않습니다.
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.apiRequest(scope(OwnerSystem.TC, "01012345678", UUID.randomUUID().toString()), TRACE))).hasMessageContaining("serviceRef");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.jobAccepted(scope, TRACE, "conversation", job))).hasMessageContaining("conversationId");
    }

    @Test void fieldsOutsideTheEventSetAndUnregisteredOrForeignToolsAreRejected() {
        var tool = registry.find("tc.logs.search").orElseThrow();
        assertThatThrownBy(() -> allowlist.requireGeneral(new AuditEvent(scope, AuditEventType.API_REQUEST, TRACE, null, null, tool.toolId(), null, null, null, null, null, null)))
            .hasMessageContaining("field");
        assertThatThrownBy(() -> allowlist.requireGeneral(new AuditEvent(scope, AuditEventType.JOB_CLASSIFIED, TRACE, conversation, job, null, null, TaskType.LIVE_QUERY, null, null, null, null)))
            .hasMessageContaining("field");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.jobClassified(scope, conversation, job, null))).hasMessageContaining("taskType");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.toolCall(scope, conversation, job, "tc.SENTINEL.secret", null, ResultStatus.SUCCESS, null, 1))).hasMessageContaining("toolId");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.toolCall(scope(OwnerSystem.WM, "service:WM", UUID.randomUUID().toString()), conversation, job, tool.toolId(), null, ResultStatus.SUCCESS, null, 1)))
            .hasMessageContaining("toolId");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.toolCall(scope, conversation, job, tool.toolId(), "sources://TC/other-backend", ResultStatus.SUCCESS, null, 1))).hasMessageContaining("sourceRef");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.toolCall(scope, conversation, job, tool.toolId(), tool.sourceAllowlistRef(), ResultStatus.SUCCESS, null, -1))).hasMessageContaining("latencyMs");
        // 차단 Tool에는 source 참조를 저장하지 않습니다.
        assertThatThrownBy(() -> allowlist.requireGeneral(new AuditEvent(scope, AuditEventType.TOOL_BLOCKED, null, conversation, job, tool.toolId(), tool.sourceAllowlistRef(), null, ResultStatus.DENIED, null, null, null)))
            .hasMessageContaining("sourceRef");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.toolBlocked(scope, conversation, job, null, null, null, RejectReason.DENIED))).hasMessageContaining("status");
        assertThatThrownBy(() -> new AuditEvent(null, AuditEventType.API_REQUEST, TRACE, null, null, null, null, null, null, null, null, null)).isInstanceOf(NullPointerException.class);
    }

    private static AuditEvent.LlmCall call(boolean sent, Integer prompt, java.util.List<com.nori.ai.gateway.core.budget.PromptReducer.Step> steps, String model) {
        return new AuditEvent.LlmCall(com.nori.ai.gateway.core.budget.CallType.COMPOSER, sent, 2_700, 5_248, prompt, prompt == null ? null : 40, steps, model, "compose-v1");
    }
    /** LLM_CALL 은 Job 결속·상태·지연·토큰/버전/축소 단계만 받고, 다른 종류는 LLM 값을 싣지 못한다. */
    @Test void llmCallAcceptsOnlyTokenVersionAndOrderedReductionValues() {
        var steps = java.util.List.of(new com.nori.ai.gateway.core.budget.PromptReducer.Step(1, 4_000, 3_200), new com.nori.ai.gateway.core.budget.PromptReducer.Step(3, 3_200, 2_700));
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.SUCCESS, null, 900, call(true, 2_700, steps, "qwen3:4b")))).doesNotThrowAnyException();
        assertThatCode(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.FAILED, ErrorCode.INPUT_BUDGET_EXCEEDED, 0, call(false, null, steps, "qwen3:4b")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.FAILED, ErrorCode.INPUT_BUDGET_EXCEEDED, 0, call(false, 10, steps, "qwen3:4b")))).hasMessageContaining("sent");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.SUCCESS, null, 1, call(true, 1, java.util.List.of(steps.get(1), steps.get(0)), "qwen3:4b")))).hasMessageContaining("reduction");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.SUCCESS, null, 1, call(true, 1, java.util.List.of(new com.nori.ai.gateway.core.budget.PromptReducer.Step(5, 9, 1)), "qwen3:4b")))).hasMessageContaining("reduction");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.SUCCESS, null, 1, call(true, 1, java.util.List.of(), "모델 \"원문\"")))).hasMessageContaining("versions");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.SUCCESS, ErrorCode.LLM_UNAVAILABLE, 1, call(true, 1, java.util.List.of(), "qwen3:4b")))).hasMessageContaining("status");
        assertThatThrownBy(() -> allowlist.requireGeneral(AuditEvent.llmCall(scope, conversation, job, ResultStatus.SUCCESS, null, -1, call(true, 1, java.util.List.of(), "qwen3:4b")))).hasMessageContaining("latencyMs");
        assertThatThrownBy(() -> allowlist.requireGeneral(new AuditEvent(scope, AuditEventType.JOB_CLASSIFIED, null, conversation, job, null, null, TaskType.LIVE_QUERY, null, null, null, null, call(true, 1, java.util.List.of(), "qwen3:4b"))))
            .hasMessageContaining("llmCall");
        assertThatThrownBy(() -> allowlist.requireGeneral(new AuditEvent(scope, AuditEventType.LLM_CALL, null, conversation, job, null, null, null, ResultStatus.SUCCESS, null, null, 1L, null))).hasMessageContaining("llmCall");
    }
}
