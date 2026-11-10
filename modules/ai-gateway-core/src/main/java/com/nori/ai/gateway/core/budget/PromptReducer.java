package com.nori.ai.gateway.core.budget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * 예산 초과 시 SPEC §14.5 순서로 Prompt 를 줄인다: ①오래된 턴(직전 1턴 유지) ②RAG chunk 낮은 점수부터
 * ③원시 로그 라인(Summarizer 요약 유지) ④Tool 결과를 필수 필드로. 시스템 프롬프트·boundSystem 제약·현재 질문은 바꾸지 않는다.
 * 각 단계는 예산 안에 들어오는 즉시 멈추며, 적용한 단계 번호와 전후 입력 토큰 수를 남긴다(원문은 남기지 않음).
 */
public final class PromptReducer {
    public record Step(int stage, int beforeTokens, int afterTokens) {}
    /** @param withinBudget false 이면 4단계 뒤에도 초과다 — 호출자는 호출하지 않고 {@code INPUT_BUDGET_EXCEEDED} 로 끝낸다. */
    public record Reduction(PromptDraft draft, List<ChatMessage> messages, int inputTokens, List<Step> steps, boolean withinBudget) {
        public Reduction { messages = List.copyOf(messages); steps = List.copyOf(steps); }
    }

    private final PromptTokenCounting counter;
    private final TokenBudgetPolicy policy;

    public PromptReducer(PromptTokenCounting counter, TokenBudgetPolicy policy) {
        this.counter = Objects.requireNonNull(counter, "counter");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** @param assembler 호출 유형의 메시지 조립(같은 draft 는 같은 메시지를 만들어야 한다) */
    public Reduction reduce(CallType type, PromptDraft draft, Function<PromptDraft, List<ChatMessage>> assembler) {
        int maxTokens = policy.budget(type).requestMaxTokens();
        List<Step> steps = new ArrayList<>();
        Measured current = measure(draft, assembler);
        current = stage(1, current, type, maxTokens, assembler, steps, d -> d.history().size() > 1 ? d.withHistory(d.history().subList(1, d.history().size())) : null);
        current = stage(2, current, type, maxTokens, assembler, steps, PromptReducer::withoutLowestScoredChunk);
        current = stage(3, current, type, maxTokens, assembler, steps, d -> d.rawLogLines().isEmpty() ? null : d.withRawLogLines(List.of()));
        current = stage(4, current, type, maxTokens, assembler, steps, d -> d.toolResults().stream().allMatch(PromptDraft.ToolResult::reduced) ? null
            : d.withToolResults(d.toolResults().stream().map(PromptDraft.ToolResult::toRequiredFields).toList()));
        return new Reduction(current.draft(), current.messages(), current.tokens(), steps, fits(type, current.tokens(), maxTokens));
    }

    private record Measured(PromptDraft draft, List<ChatMessage> messages, int tokens) {}

    private Measured measure(PromptDraft draft, Function<PromptDraft, List<ChatMessage>> assembler) {
        List<ChatMessage> messages = List.copyOf(assembler.apply(draft));
        return new Measured(draft, messages, counter.count(messages));
    }

    private boolean fits(CallType type, int tokens, int maxTokens) { return policy.violations(type, tokens, maxTokens).isEmpty(); }

    /** {@code next} 가 null 을 돌려주면 그 단계에서 더 줄일 것이 없다. */
    private Measured stage(int number, Measured start, CallType type, int maxTokens, Function<PromptDraft, List<ChatMessage>> assembler,
                           List<Step> steps, UnaryOperator<PromptDraft> next) {
        Measured current = start;
        while (!fits(type, current.tokens(), maxTokens)) {
            PromptDraft reduced = next.apply(current.draft());
            if (reduced == null) break;
            current = measure(reduced, assembler);
        }
        if (current != start) steps.add(new Step(number, start.tokens(), current.tokens()));
        return current;
    }

    /** 점수가 가장 낮은 chunk 하나를 뺀다. 같은 점수면 뒤에 놓인 것을 먼저 뺀다. */
    private static PromptDraft withoutLowestScoredChunk(PromptDraft draft) {
        List<PromptDraft.RagChunk> chunks = draft.ragChunks();
        if (chunks.isEmpty()) return null;
        int lowest = 0;
        for (int i = 1; i < chunks.size(); i++) if (chunks.get(i).score() <= chunks.get(lowest).score()) lowest = i;
        List<PromptDraft.RagChunk> remaining = new ArrayList<>(chunks);
        remaining.remove(lowest);
        return draft.withRagChunks(remaining);
    }
}
