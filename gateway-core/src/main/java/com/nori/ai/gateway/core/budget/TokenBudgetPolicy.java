package com.nori.ai.gateway.core.budget;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 호출 유형별 입력·답변·사고 상한을 한 곳에 둔다. 요청 {@code max_tokens} = 답변 상한 + 사고 상한이다.
 * 운영값은 {@link #current()} 하나이며, 다른 값은 단위·통합 시험의 정책 fixture 로만 주입한다.
 */
public final class TokenBudgetPolicy {
    /** 생성 모델 num_ctx(SPEC §14.5 D51). Gateway 는 요청에 싣지 않고 사전 검사에만 쓴다. */
    public static final int CONTEXT_TOKENS = 8192;

    public enum Violation { INPUT_LIMIT, CONTEXT_WINDOW }

    public record CallBudget(int inputLimit, int answerLimit, int thinkingLimit) {
        public CallBudget {
            if (inputLimit < 1 || answerLimit < 1 || thinkingLimit < 0) throw new IllegalArgumentException("호출 예산 상한이 올바르지 않습니다");
        }
        public int requestMaxTokens() { return answerLimit + thinkingLimit; }
    }

    private final Map<CallType, CallBudget> budgets;

    public TokenBudgetPolicy(Map<CallType, CallBudget> budgets) {
        Objects.requireNonNull(budgets, "budgets");
        if (!budgets.keySet().equals(EnumSet.allOf(CallType.class))) throw new IllegalArgumentException("모든 호출 유형의 예산이 필요합니다");
        this.budgets = new EnumMap<>(budgets);
    }

    /**
     * SPEC §14.5 현재 profile: 입력/답변 상한은 2026-11-02 기술 선정값, 사고 상한은 F5a 정식 시험 전 확정되는
     * 잠정 안전 초기값(T_cls 4,928·T_pln 4,864·T_cmp 5,120)이다.
     */
    public static TokenBudgetPolicy current() {
        return new TokenBudgetPolicy(Map.of(
            CallType.CLASSIFY, new CallBudget(3_072, 192, 4_928),
            CallType.PLAN, new CallBudget(3_072, 256, 4_864),
            CallType.COMPOSER, new CallBudget(2_816, 128, 5_120)));
    }

    public CallBudget budget(CallType type) { return budgets.get(Objects.requireNonNull(type, "type")); }

    /** 호출 직전 두 조건(입력 ≤ 유형 상한, 입력 + max_tokens ≤ num_ctx) 중 어긴 것. 비어 있으면 호출할 수 있다. */
    public Set<Violation> violations(CallType type, int inputTokens, int maxTokens) {
        if (inputTokens < 0 || maxTokens < 1) throw new IllegalArgumentException("토큰 수가 올바르지 않습니다");
        EnumSet<Violation> violations = EnumSet.noneOf(Violation.class);
        if (inputTokens > budget(type).inputLimit()) violations.add(Violation.INPUT_LIMIT);
        if ((long) inputTokens + maxTokens > CONTEXT_TOKENS) violations.add(Violation.CONTEXT_WINDOW);
        return Set.copyOf(violations);
    }
}
