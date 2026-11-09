package com.nori.ai.gateway.core.port;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 생성·임베딩 추론 호출 포트(SPEC §4.3·§14.5). 구현은 매 chat 호출 직전 예산 assert 를 거치며, 예산 초과면 전송하지 않는다.
 * 결과와 실패는 고정 코드·클래스명만 담는다. Prompt·응답 본문·예외 메시지는 실패 결과에 싣지 않는다.
 */
public interface InferenceClient {
    /** {@code maxTokens} 는 그 호출 유형의 정책값(답변 상한 + 사고 상한)이다. */
    ChatOutcome chat(CallType type, List<ChatMessage> messages, int maxTokens);

    /** 입력 순서대로 벡터를 돌려준다. 실패는 {@link EmbeddingFailed} 다. */
    List<float[]> embed(List<String> inputs);

    /** 호출 실패의 닫힌 사유. 재시도 여부는 호출자(P20 명시 정책)가 정한다. */
    enum FailureReason { TRANSPORT, EMPTY_RESPONSE, TRUNCATED, INTERRUPTED }

    sealed interface ChatOutcome permits Completed, BudgetExceeded, CallFailed {
        static ChatOutcome budgetExceeded(int count) { return new BudgetExceeded(count); }
    }

    /**
     * 정상 응답. {@code content} 는 답변(message.content)만이며 사고문은 담지 않는다(SPEC §14.5 방법 (b)).
     * {@code finishReason} 은 OpenAI wire 값의 소문자(예: {@code stop}, {@code length})다.
     */
    record Completed(String content, String finishReason, int promptTokens, int completionTokens, Duration latency) implements ChatOutcome {
        public Completed {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(finishReason, "finishReason");
            Objects.requireNonNull(latency, "latency");
        }
        @Override public String toString() { return "Completed[보호됨]"; }
    }

    /** 호출 직전 예산 초과. HTTP 전송은 0 이며 호출자는 {@code INPUT_BUDGET_EXCEEDED} 로 매핑한다. */
    record BudgetExceeded(int count) implements ChatOutcome {}

    /** 전송·응답 실패. {@code errorClass} 는 원인 예외의 클래스명(없으면 null)뿐이다. */
    record CallFailed(FailureReason reason, String errorClass, Duration latency) implements ChatOutcome {
        public CallFailed {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(latency, "latency");
        }
    }

    /** 임베딩 호출 실패. 원인 예외를 연결하지 않아 스택 출력에도 응답·입력 원문이 실리지 않는다. */
    final class EmbeddingFailed extends RuntimeException {
        private final FailureReason reason;
        private final String errorClass;
        public EmbeddingFailed(FailureReason reason, String errorClass) {
            super("임베딩 호출에 실패했습니다", null, false, false);
            this.reason = Objects.requireNonNull(reason, "reason");
            this.errorClass = errorClass;
        }
        public FailureReason reason() { return reason; }
        public String errorClass() { return errorClass; }
    }
}
