package com.nori.ai.gateway.core.budget;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 실제 LLM 호출(재시도 포함)을 보내기 직전의 예산 assert(SPEC §14.5, AC-31·AC-43).
 * {@link Exceeded} 이면 호출자는 전송하지 않고 {@code INPUT_BUDGET_EXCEEDED} 로 처리한다.
 */
public final class PreCallBudgetAssert {
    public sealed interface Outcome permits Allowed, Exceeded { int count(); }
    public record Allowed(int count) implements Outcome {}
    public record Exceeded(int count, Set<TokenBudgetPolicy.Violation> violations) implements Outcome {
        public Exceeded {
            violations = Set.copyOf(violations);
            if (violations.isEmpty()) throw new IllegalArgumentException("초과 사유가 필요합니다");
        }
    }

    private final PromptTokenCounting counter;
    private final TokenBudgetPolicy policy;

    public PreCallBudgetAssert(PromptTokenCounting counter, TokenBudgetPolicy policy) {
        this.counter = Objects.requireNonNull(counter, "counter");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** {@code maxTokens} 는 그 유형의 정책값(답변 상한 + 사고 상한)이어야 한다. 다른 값은 설정 한 곳 원칙 위반이라 거부한다. */
    public Outcome check(CallType type, List<ChatMessage> messages, int maxTokens) {
        if (maxTokens != policy.budget(type).requestMaxTokens()) throw new IllegalArgumentException("요청 max_tokens 가 호출 유형 정책값과 다릅니다");
        int count = counter.count(List.copyOf(messages));
        var violations = policy.violations(type, count, maxTokens);
        return violations.isEmpty() ? new Allowed(count) : new Exceeded(count, violations);
    }
}
