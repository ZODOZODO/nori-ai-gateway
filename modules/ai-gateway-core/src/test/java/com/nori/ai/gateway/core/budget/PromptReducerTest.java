package com.nori.ai.gateway.core.budget;

import com.nori.ai.gateway.core.budget.ChatMessage.Role;
import com.nori.ai.gateway.core.budget.PromptDraft.RagChunk;
import com.nori.ai.gateway.core.budget.PromptDraft.ToolResult;
import com.nori.ai.gateway.core.budget.PromptDraft.Turn;
import com.nori.ai.gateway.core.budget.PromptReducer.Step;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy.CallBudget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 축소 순서·중단·불변 요소 검사. 계수 포트는 문자 1개 = 토큰 1개인 결정론 계수로 두어 단계별 기대값을 직접 계산한다. */
class PromptReducerTest {
    private static final PromptTokenCounting CHARACTERS = messages -> messages.stream().mapToInt(m -> m.content().length()).sum();
    private static final String SYSTEM = "S".repeat(10), BOUND = "B".repeat(5), QUESTION = "Q".repeat(10), SUMMARY = "L".repeat(5);

    /** system 메시지(system|bound) 다음 이전 턴, 마지막 user 메시지에 데이터 구획과 현재 질문을 둔다. */
    private static List<ChatMessage> assemble(PromptDraft draft) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage(Role.SYSTEM, draft.systemPrompt() + "|" + draft.boundSystemConstraint()));
        for (Turn turn : draft.history()) { messages.add(new ChatMessage(Role.USER, turn.user())); messages.add(new ChatMessage(Role.ASSISTANT, turn.assistant())); }
        StringBuilder data = new StringBuilder();
        draft.ragChunks().forEach(c -> data.append(c.text()));
        data.append(draft.logSummary());
        draft.rawLogLines().forEach(data::append);
        draft.toolResults().forEach(t -> data.append(t.text()));
        messages.add(new ChatMessage(Role.USER, data + draft.currentQuestion()));
        return messages;
    }
    private static TokenBudgetPolicy policy(int inputLimit) {
        var budget = new CallBudget(inputLimit, 10, 0);
        return new TokenBudgetPolicy(Map.of(CallType.CLASSIFY, budget, CallType.PLAN, budget, CallType.COMPOSER, budget));
    }
    private static Turn turn(char c) { return new Turn(String.valueOf(c).repeat(10), String.valueOf(c).toUpperCase().repeat(10)); }
    private static RagChunk chunk(String id, double score) { return new RagChunk(id, score, "r".repeat(10)); }
    private static ToolResult tool(String ref) { return new ToolResult(ref, "t".repeat(20), "t".repeat(5)); }
    /** 기본 31(system 16 + 질문 10 + 요약 5) + 턴 3개 60 + chunk 2개 20 + 원시 로그 2줄 20 + Tool 2개 40 = 171 */
    private static PromptDraft full() {
        return new PromptDraft(SYSTEM, BOUND, List.of(turn('a'), turn('b'), turn('c')), List.of(chunk("high", 0.9), chunk("low", 0.1)),
            SUMMARY, List.of("x".repeat(10), "y".repeat(10)), List.of(tool("tool-1"), tool("tool-2")), QUESTION);
    }
    private static void assertInvariantsKept(PromptReducer.Reduction reduction) {
        assertThat(reduction.draft().systemPrompt()).isEqualTo(SYSTEM);
        assertThat(reduction.draft().boundSystemConstraint()).isEqualTo(BOUND);
        assertThat(reduction.draft().currentQuestion()).isEqualTo(QUESTION);
        assertThat(reduction.draft().logSummary()).isEqualTo(SUMMARY);
        assertThat(reduction.messages().getFirst().content()).isEqualTo(SYSTEM + "|" + BOUND);
        assertThat(reduction.messages().getLast().content()).endsWith(QUESTION);
        assertThat(reduction.inputTokens()).isEqualTo(CHARACTERS.count(reduction.messages()));
    }

    @Test void draftWithinBudgetIsReturnedWithoutAnyStep() {
        var reduction = new PromptReducer(CHARACTERS, policy(171)).reduce(CallType.CLASSIFY, full(), PromptReducerTest::assemble);
        assertThat(reduction.withinBudget()).isTrue();
        assertThat(reduction.steps()).isEmpty();
        assertThat(reduction.draft()).isEqualTo(full());
        assertThat(reduction.inputTokens()).isEqualTo(171);
    }

    @Test void appliesStagesInSpecOrderAndStopsAsSoonAsTheBudgetFits() {
        var reduction = new PromptReducer(CHARACTERS, policy(100)).reduce(CallType.CLASSIFY, full(), PromptReducerTest::assemble);
        assertThat(reduction.withinBudget()).isTrue();
        // ① 오래된 턴 2개 제거(직전 1턴 유지) ② chunk 2개 제거 ③ 원시 로그 제거에서 91 ≤ 100 이 되어 ④ 는 적용하지 않는다.
        assertThat(reduction.steps()).containsExactly(new Step(1, 171, 131), new Step(2, 131, 111), new Step(3, 111, 91));
        assertThat(reduction.draft().history()).containsExactly(turn('c'));
        assertThat(reduction.draft().ragChunks()).isEmpty();
        assertThat(reduction.draft().rawLogLines()).isEmpty();
        assertThat(reduction.draft().toolResults()).containsExactly(tool("tool-1"), tool("tool-2"));
        assertInvariantsKept(reduction);
    }

    @Test void firstStageRemovesOnlyAsManyOldTurnsAsNeeded() {
        var reduction = new PromptReducer(CHARACTERS, policy(151)).reduce(CallType.CLASSIFY, full(), PromptReducerTest::assemble);
        assertThat(reduction.steps()).containsExactly(new Step(1, 171, 151));
        assertThat(reduction.draft().history()).containsExactly(turn('b'), turn('c'));
        assertThat(reduction.draft().ragChunks()).hasSize(2);
    }

    @Test void toolResultsShrinkToRequiredFieldsLastAndOverBudgetIsReported() {
        var fits = new PromptReducer(CHARACTERS, policy(61)).reduce(CallType.CLASSIFY, full(), PromptReducerTest::assemble);
        assertThat(fits.steps()).containsExactly(new Step(1, 171, 131), new Step(2, 131, 111), new Step(3, 111, 91), new Step(4, 91, 61));
        assertThat(fits.withinBudget()).isTrue();
        assertThat(fits.draft().toolResults()).extracting(ToolResult::text).containsExactly("t".repeat(5), "t".repeat(5));

        var over = new PromptReducer(CHARACTERS, policy(60)).reduce(CallType.CLASSIFY, full(), PromptReducerTest::assemble);
        assertThat(over.withinBudget()).isFalse();
        assertThat(over.inputTokens()).isEqualTo(61);
        assertThat(over.steps()).containsExactlyElementsOf(fits.steps());
        assertThat(over.draft().history()).containsExactly(turn('c'));
        assertInvariantsKept(over);
    }

    @Test void ragChunksAreRemovedLowestScoreFirstAndLaterPositionFirstOnTies() {
        var draft = new PromptDraft(SYSTEM, BOUND, List.of(), List.of(chunk("c1", 0.9), chunk("c2", 0.1), chunk("c3", 0.5), chunk("c4", 0.1)),
            SUMMARY, List.of(), List.of(), QUESTION);
        var one = new PromptReducer(CHARACTERS, policy(61)).reduce(CallType.CLASSIFY, draft, PromptReducerTest::assemble);
        assertThat(one.draft().ragChunks()).extracting(RagChunk::id).containsExactly("c1", "c2", "c3");
        var two = new PromptReducer(CHARACTERS, policy(51)).reduce(CallType.CLASSIFY, draft, PromptReducerTest::assemble);
        assertThat(two.draft().ragChunks()).extracting(RagChunk::id).containsExactly("c1", "c3");
        var three = new PromptReducer(CHARACTERS, policy(41)).reduce(CallType.CLASSIFY, draft, PromptReducerTest::assemble);
        assertThat(three.draft().ragChunks()).extracting(RagChunk::id).containsExactly("c1");
        assertThat(three.steps()).containsExactly(new Step(2, 71, 41));
    }

    @Test void theCallTypeInputLimitOfTheCurrentPolicyDecidesWhetherToReduce() {
        // system 2,001 + 질문 600 + 이전 턴 2개 300 = 2,901: 분류 3,072 이내, Composer 2,816 초과
        var draft = new PromptDraft("S".repeat(2_000), "", List.of(new Turn("u".repeat(75), "a".repeat(75)), new Turn("v".repeat(75), "b".repeat(75))),
            List.of(), "", List.of(), List.of(), "Q".repeat(600));
        var reducer = new PromptReducer(CHARACTERS, TokenBudgetPolicy.current());
        assertThat(reducer.reduce(CallType.CLASSIFY, draft, PromptReducerTest::assemble).steps()).isEmpty();
        var composer = reducer.reduce(CallType.COMPOSER, draft, PromptReducerTest::assemble);
        assertThat(composer.steps()).containsExactly(new Step(1, 2_901, 2_751));
        assertThat(composer.withinBudget()).isTrue();
    }
}
