package com.nori.ai.gateway.core.budget;

import java.util.List;
import java.util.Objects;

/**
 * 축소 전 Prompt 구성 요소. 시스템 프롬프트·boundSystem 제약·현재 질문은 {@link PromptReducer} 가 바꾸지 않는다.
 *
 * @param history 오래된 것부터 정렬한 이전 대화 턴
 * @param logSummary 결정론 Log Summarizer 요약(없으면 빈 문자열)
 */
public record PromptDraft(String systemPrompt, String boundSystemConstraint, List<Turn> history, List<RagChunk> ragChunks,
                          String logSummary, List<String> rawLogLines, List<ToolResult> toolResults, String currentQuestion) {
    public record Turn(String user, String assistant) {
        public Turn { Objects.requireNonNull(user, "user"); Objects.requireNonNull(assistant, "assistant"); }
    }
    public record RagChunk(String id, double score, String text) {
        public RagChunk {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(text, "text");
            if (!Double.isFinite(score)) throw new IllegalArgumentException("RAG chunk 점수는 유한해야 합니다");
        }
    }
    /** @param requiredFieldsOnly outputSchema 필수 필드만 남긴 같은 결과(축소 4단계에서 {@code text} 를 대체) */
    public record ToolResult(String ref, String text, String requiredFieldsOnly) {
        public ToolResult { Objects.requireNonNull(ref, "ref"); Objects.requireNonNull(text, "text"); Objects.requireNonNull(requiredFieldsOnly, "requiredFieldsOnly"); }
        boolean reduced() { return text.equals(requiredFieldsOnly); }
        ToolResult toRequiredFields() { return new ToolResult(ref, requiredFieldsOnly, requiredFieldsOnly); }
    }
    public PromptDraft {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(boundSystemConstraint, "boundSystemConstraint");
        Objects.requireNonNull(logSummary, "logSummary");
        Objects.requireNonNull(currentQuestion, "currentQuestion");
        history = List.copyOf(history); ragChunks = List.copyOf(ragChunks); rawLogLines = List.copyOf(rawLogLines); toolResults = List.copyOf(toolResults);
    }
    PromptDraft withHistory(List<Turn> value) { return new PromptDraft(systemPrompt, boundSystemConstraint, value, ragChunks, logSummary, rawLogLines, toolResults, currentQuestion); }
    PromptDraft withRagChunks(List<RagChunk> value) { return new PromptDraft(systemPrompt, boundSystemConstraint, history, value, logSummary, rawLogLines, toolResults, currentQuestion); }
    PromptDraft withRawLogLines(List<String> value) { return new PromptDraft(systemPrompt, boundSystemConstraint, history, ragChunks, logSummary, value, toolResults, currentQuestion); }
    PromptDraft withToolResults(List<ToolResult> value) { return new PromptDraft(systemPrompt, boundSystemConstraint, history, ragChunks, logSummary, rawLogLines, value, currentQuestion); }
}
