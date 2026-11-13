package com.nori.ai.gateway.core.audit;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.PromptReducer;
import com.nori.ai.gateway.core.plan.RejectReason;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.List;
import java.util.Objects;

/**
 * 일반 감사 한 건이다. 신원·requestId·service는 검증된 범위에서, Job 이벤트의 requestId·traceId는 같은 Job의 JOB_ACCEPTED에서
 * 저장소가 파생하므로 여기에는 없다. 코드 값은 기존 enum 타입만 받고 자유 텍스트·임의 key map은 두지 않는다.
 * 이벤트별 필드 집합과 Tool 등록 소속은 {@link AuditFieldAllowlist}가 저장 경계에서 검사한다.
 * {@code llmCall} 은 LLM_CALL 의 토큰·버전·축소 단계(SPEC §12.3 허용 목록, 원문 없음)이며 다른 종류에는 없다.
 */
public record AuditEvent(VerifiedSystemScope scope, AuditEventType eventType, String traceId,
        String conversationId, String jobId, String toolId, String sourceRef,
        TaskType taskType, ResultStatus status, ErrorCode errorCode, RejectReason decision, Long latencyMs, LlmCall llmCall) {
    /**
     * LLM 호출 시도 1회의 감사 값. {@code sent=false} 는 호출 직전 assert 로 보내지 않은 시도다. usage 두 값은 서버가 돌려준 경우에만 있다.
     * {@code model}·{@code promptVersion} 은 배포 설정의 식별자다.
     */
    public record LlmCall(CallType callType, boolean sent, int inputTokens, int maxTokens, Integer promptTokens, Integer completionTokens,
                          List<PromptReducer.Step> reduction, String model, String promptVersion) {
        public LlmCall {
            Objects.requireNonNull(callType, "callType"); reduction = List.copyOf(reduction);
        }
    }
    public AuditEvent {
        Objects.requireNonNull(scope, "검증된 신원이 필요합니다");
        Objects.requireNonNull(eventType, "감사 종류가 필요합니다");
    }
    /** LLM_CALL 이전 종류의 기존 호출자 형태다. {@code llmCall} 은 없다. */
    public AuditEvent(VerifiedSystemScope scope, AuditEventType eventType, String traceId, String conversationId, String jobId, String toolId, String sourceRef,
            TaskType taskType, ResultStatus status, ErrorCode errorCode, RejectReason decision, Long latencyMs) {
        this(scope, eventType, traceId, conversationId, jobId, toolId, sourceRef, taskType, status, errorCode, decision, latencyMs, null);
    }
    public static AuditEvent apiRequest(VerifiedSystemScope scope, String traceId) {
        return new AuditEvent(scope, AuditEventType.API_REQUEST, traceId, null, null, null, null, null, null, null, null, null);
    }
    public static AuditEvent jobAccepted(VerifiedSystemScope scope, String traceId, String conversationId, String jobId) {
        return new AuditEvent(scope, AuditEventType.JOB_ACCEPTED, traceId, conversationId, jobId, null, null, null, null, null, null, null);
    }
    public static AuditEvent jobClassified(VerifiedSystemScope scope, String conversationId, String jobId, TaskType taskType) {
        return new AuditEvent(scope, AuditEventType.JOB_CLASSIFIED, null, conversationId, jobId, null, null, taskType, null, null, null, null);
    }
    public static AuditEvent toolCall(VerifiedSystemScope scope, String conversationId, String jobId, String toolId, String sourceRef,
            ResultStatus status, ErrorCode errorCode, long latencyMs) {
        return new AuditEvent(scope, AuditEventType.TOOL_CALL, null, conversationId, jobId, toolId, sourceRef, null, status, errorCode, null, latencyMs);
    }
    public static AuditEvent toolBlocked(VerifiedSystemScope scope, String conversationId, String jobId, String toolId,
            ResultStatus status, ErrorCode errorCode, RejectReason decision) {
        return new AuditEvent(scope, AuditEventType.TOOL_BLOCKED, null, conversationId, jobId, toolId, null, null, status, errorCode, decision, null);
    }
    public static AuditEvent llmCall(VerifiedSystemScope scope, String conversationId, String jobId, ResultStatus status, ErrorCode errorCode, long latencyMs, LlmCall call) {
        return new AuditEvent(scope, AuditEventType.LLM_CALL, null, conversationId, jobId, null, null, null, status, errorCode, null, latencyMs, Objects.requireNonNull(call, "call"));
    }
    @Override public String toString() { return "AuditEvent[eventType=" + eventType + "]"; }
}
