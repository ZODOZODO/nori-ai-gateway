package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.PromptReducer;
import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.List;
import java.util.Objects;

/**
 * LLM 호출 시도 1회의 감사 포트(SPEC §14.5 "적용한 축소 단계 번호와 단계별 전후 입력 토큰 수를 그 호출의 Audit 에 기록", §12.3 허용 목록).
 * 단위 = 호출 직전 assert 에 도달한 {@link InferenceClient#chat} 1회(재시도 포함)다. assert 로 막혀 보내지 않은 시도는 {@code sent=false} 로 남긴다.
 * Prompt·답변·사고문은 받지 않는다. 운영 구현은 앱의 일반 감사 경로(LLM_CALL)다.
 */
@FunctionalInterface
public interface LlmCallAudit {
    void record(Attempt attempt);

    /**
     * {@code promptTokens}·{@code completionTokens} 는 서버가 응답 usage 를 돌려준 경우에만 있다. {@code reduction} 은 축소 단계(원문 없음)다.
     * {@code status}·{@code errorCode} 는 그 시도의 결과다(SUCCESS, 또는 FAILED + 그 시도에서 정해진 고정 코드).
     */
    record Attempt(VerifiedSystemScope scope, String conversationId, String jobId, CallType callType, boolean sent, int inputTokens, int maxTokens,
                   Integer promptTokens, Integer completionTokens, long latencyMs, ResultStatus status, ErrorCode errorCode, List<PromptReducer.Step> reduction) {
        public Attempt {
            Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(jobId, "jobId"); Objects.requireNonNull(callType, "callType");
            Objects.requireNonNull(status, "status"); reduction = List.copyOf(reduction);
        }
        @Override public String toString() { return "LlmCallAttempt[" + callType + "," + status + "]"; }
    }

    /**
     * 호출 결과를 시도 기록으로 바꾼다. {@code answerFailure} 는 Completed 답변 판정이 실패했을 때의 고정 코드이고 통과면 null 이다.
     * 예산 초과는 전송 0 이라 usage·지연이 없다.
     */
    static Attempt attempt(VerifiedSystemScope scope, String conversationId, String jobId, CallType type, int maxTokens, InferenceClient.ChatOutcome outcome,
                           ErrorCode answerFailure, List<PromptReducer.Step> reduction) {
        return switch (outcome) {
            case InferenceClient.BudgetExceeded exceeded -> new Attempt(scope, conversationId, jobId, type, false, exceeded.count(), maxTokens, null, null, 0,
                ResultStatus.FAILED, ErrorCode.INPUT_BUDGET_EXCEEDED, reduction);
            case InferenceClient.CallFailed failed -> new Attempt(scope, conversationId, jobId, type, failed.reason() != InferenceClient.FailureReason.INTERRUPTED, failed.inputTokens(), maxTokens,
                null, null, failed.latency().toMillis(), ResultStatus.FAILED, ErrorCode.LLM_UNAVAILABLE, reduction);
            case InferenceClient.Completed completed -> new Attempt(scope, conversationId, jobId, type, true, completed.inputTokens(), maxTokens, completed.promptTokens(),
                completed.completionTokens(), completed.latency().toMillis(), answerFailure == null ? ResultStatus.SUCCESS : ResultStatus.FAILED, answerFailure, reduction);
        };
    }
}
