package com.nori.ai.gateway.integration.tokenizer;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.ChatMessage.Role;
import com.nori.ai.gateway.core.budget.PreCallBudgetAssert;
import com.nori.ai.gateway.core.budget.PreCallBudgetAssert.Allowed;
import com.nori.ai.gateway.core.budget.PreCallBudgetAssert.Exceeded;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy.CallBudget;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy.Violation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 실제 chat template 렌더 + pin tokenizer 계수로 만든 정확한 토큰 수의 입력에서 호출 직전 assert 경계를 확인한다(계수기 fake 0). */
class PreCallBudgetAssertTest {
    private static HfTokenizerCounter tokenizer;
    private static PromptTokenCounter counter;
    @BeforeAll static void load() { tokenizer = new HfTokenizerCounter(); counter = new PromptTokenCounter(new QwenChatTemplateRenderer(), tokenizer); }
    @AfterAll static void close() { tokenizer.close(); }

    /** 렌더 전체 계수가 정확히 {@code target} 인 system + user 메시지. " token" 하나가 한 토큰이라 개수로 맞춘 뒤 실제 계수로 다시 확인한다. */
    private static List<ChatMessage> messagesOf(int target) {
        int base = counter.count(input(0));
        List<ChatMessage> messages = input(target - base);
        assertThat(counter.count(messages)).as("입력 구성 계수").isEqualTo(target);
        return messages;
    }
    private static List<ChatMessage> input(int fillers) {
        return List.of(new ChatMessage(Role.SYSTEM, "TC 질문을 분류한다."), new ChatMessage(Role.USER, "질문:" + " token".repeat(fillers)));
    }
    private static TokenBudgetPolicy composerContextFixture() {
        var current = TokenBudgetPolicy.current();
        return new TokenBudgetPolicy(Map.of(CallType.CLASSIFY, current.budget(CallType.CLASSIFY), CallType.PLAN, current.budget(CallType.PLAN),
            CallType.COMPOSER, new CallBudget(2_816, 128, 5_249)));
    }

    @Test void classifyAndPlanAllowInputLimitPlusMaxTokensExactlyAtTheContextWindow() {
        var guard = new PreCallBudgetAssert(counter, TokenBudgetPolicy.current());
        for (CallType type : new CallType[]{CallType.CLASSIFY, CallType.PLAN}) {
            assertThat(guard.check(type, messagesOf(3_072), 5_120)).isEqualTo(new Allowed(3_072));
            assertThat(guard.check(type, messagesOf(3_073), 5_120)).isEqualTo(new Exceeded(3_073, java.util.Set.of(Violation.INPUT_LIMIT, Violation.CONTEXT_WINDOW)));
        }
    }

    @Test void composerInputLimitAloneRejects2817() {
        var guard = new PreCallBudgetAssert(counter, TokenBudgetPolicy.current());
        assertThat(guard.check(CallType.COMPOSER, messagesOf(2_816), 5_248)).isEqualTo(new Allowed(2_816));
        // 2,817 + 5,248 = 8,065 ≤ 8,192 — 총합이 아니라 유형별 입력 상한만 위반한다.
        assertThat(guard.check(CallType.COMPOSER, messagesOf(2_817), 5_248)).isEqualTo(new Exceeded(2_817, java.util.Set.of(Violation.INPUT_LIMIT)));
    }

    @Test void contextSumIsCheckedIndependentlyWithTheTestOnlyComposerFixture() {
        var guard = new PreCallBudgetAssert(counter, composerContextFixture());
        assertThat(guard.check(CallType.COMPOSER, messagesOf(2_815), 5_377)).isEqualTo(new Allowed(2_815));
        // 두 입력 모두 유형 상한 2,816 이하이며 2,816 + 5,377 = 8,193 만 넘는다.
        assertThat(guard.check(CallType.COMPOSER, messagesOf(2_816), 5_377)).isEqualTo(new Exceeded(2_816, java.util.Set.of(Violation.CONTEXT_WINDOW)));
    }

    @Test void requestMaxTokensOtherThanThePolicyValueIsRejected() {
        var guard = new PreCallBudgetAssert(counter, TokenBudgetPolicy.current());
        var messages = input(1);
        assertThatThrownBy(() -> guard.check(CallType.CLASSIFY, messages, 5_121)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.check(CallType.COMPOSER, messages, 5_120)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.check(CallType.CLASSIFY, List.of(new ChatMessage(Role.USER, "U"), new ChatMessage(Role.ASSISTANT, "A")), 5_120))
            .hasMessage("CHAT_TEMPLATE_INPUT_UNSUPPORTED");
    }
}
