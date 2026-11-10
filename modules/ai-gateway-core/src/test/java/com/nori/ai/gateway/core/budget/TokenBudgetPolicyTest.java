package com.nori.ai.gateway.core.budget;

import com.nori.ai.gateway.core.budget.TokenBudgetPolicy.CallBudget;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy.Violation;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TokenBudgetPolicyTest {
    /** Plan P18 시험 전용 fixture: Composer 사고 상한만 T_cmp=5,249(요청 max_tokens 5,377)로 바꿔 총합 검사를 독립 확인한다. */
    static TokenBudgetPolicy composerContextFixture() {
        var current = TokenBudgetPolicy.current();
        return new TokenBudgetPolicy(Map.of(CallType.CLASSIFY, current.budget(CallType.CLASSIFY), CallType.PLAN, current.budget(CallType.PLAN),
            CallType.COMPOSER, new CallBudget(2_816, 128, 5_249)));
    }

    @Test void currentProfileIsSpec145WithRequestMaxTokensAsAnswerPlusThinking() {
        var policy = TokenBudgetPolicy.current();
        assertThat(TokenBudgetPolicy.CONTEXT_TOKENS).isEqualTo(8_192);
        assertThat(policy.budget(CallType.CLASSIFY)).isEqualTo(new CallBudget(3_072, 192, 4_928));
        assertThat(policy.budget(CallType.PLAN)).isEqualTo(new CallBudget(3_072, 256, 4_864));
        assertThat(policy.budget(CallType.COMPOSER)).isEqualTo(new CallBudget(2_816, 128, 5_120));
        assertThat(policy.budget(CallType.CLASSIFY).requestMaxTokens()).isEqualTo(5_120);
        assertThat(policy.budget(CallType.PLAN).requestMaxTokens()).isEqualTo(5_120);
        assertThat(policy.budget(CallType.COMPOSER).requestMaxTokens()).isEqualTo(5_248);
    }

    @Test void currentProfileSeparatesInputLimitFromContextWindowAtExactBoundaries() {
        var policy = TokenBudgetPolicy.current();
        for (CallType type : new CallType[]{CallType.CLASSIFY, CallType.PLAN}) {
            assertThat(policy.violations(type, 3_072, 5_120)).isEmpty();
            assertThat(policy.violations(type, 3_073, 5_120)).containsExactlyInAnyOrder(Violation.INPUT_LIMIT, Violation.CONTEXT_WINDOW);
        }
        assertThat(policy.violations(CallType.COMPOSER, 2_816, 5_248)).isEmpty();
        // 2,817 + 5,248 = 8,065 ≤ 8,192 이므로 유형별 입력 상한만 어긴다.
        assertThat(policy.violations(CallType.COMPOSER, 2_817, 5_248)).containsExactly(Violation.INPUT_LIMIT);
    }

    @Test void fixtureChecksTheContextSumIndependentlyOfTheInputLimit() {
        var fixture = composerContextFixture();
        assertThat(fixture.budget(CallType.COMPOSER).requestMaxTokens()).isEqualTo(5_377);
        assertThat(fixture.violations(CallType.COMPOSER, 2_815, 5_377)).isEmpty();
        assertThat(fixture.violations(CallType.COMPOSER, 2_816, 5_377)).containsExactly(Violation.CONTEXT_WINDOW);
        assertThat(TokenBudgetPolicy.current().budget(CallType.COMPOSER).thinkingLimit()).isEqualTo(5_120);
    }

    @Test void rejectsIncompleteOrInvalidBudgets() {
        var current = TokenBudgetPolicy.current();
        assertThatThrownBy(() -> new TokenBudgetPolicy(Map.of(CallType.CLASSIFY, current.budget(CallType.CLASSIFY)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CallBudget(0, 192, 4_928)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CallBudget(3_072, 0, 4_928)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CallBudget(3_072, 192, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> current.violations(CallType.CLASSIFY, -1, 5_120)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> current.violations(CallType.CLASSIFY, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Set.copyOf(current.violations(CallType.PLAN, 0, 5_120))).isEmpty();
    }
}
