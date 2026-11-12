package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.PromptDraft;
import com.nori.ai.gateway.core.budget.PromptReducer;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.job.JobContext;
import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.workflow.ComposerOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.node.ObjectNode;

/**
 * Response Composer 서술(SPEC §14.5 서술 2,816/128/T_cmp, §14.6 ②, §22.3). 입력은 Gateway 가 만든 결정론 근거 요약이고
 * Source 원문은 user 메시지의 data 구획에만 둔다. 입력 조립 순서는 호출자 sanitizer 적용분 → {@link PromptReducer} → 관찰 → Client 내부 예산 assert 다.
 * 실패는 {@link ComposerOutcome} 으로만 돌려주며 대체 문장을 만들지 않는다. 사고 표지가 든 content 는 결과에 싣지 않는다.
 */
public final class ComposerService {
    /** 재시도 요지(분류 재시도와 같은 형식, 직전 출력 원문은 다시 싣지 않는다). */
    static final String RETRY_PREFIX = "직전 출력이 무효다(오류: ", RETRY_SUFFIX = "). 2~4문장 평문과 [eN] 인용 표기로만 다시 답한다.";
    private static final Pattern CITATION = Pattern.compile("\\[e(\\d{1,9})]");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    /** 근거 하나의 결정론 요약. 호출자가 마스킹을 마친 값만 넣는다. 인용 표기 id 는 목록 순서의 {@code e1…eN} 이다. */
    public record EvidenceDigest(String sourceType, String eventTime, String observedAt) {
        public EvidenceDigest { Objects.requireNonNull(sourceType, "sourceType"); }
    }

    /**
     * 요구 근거 하나의 서버 수집 상태(SPEC §28.8, Plan §16.3 R4): 근거 코드·상태·근거 단계 ID·현재 READ 가 제공하지 않는 요청 항목. 원문·PII 는 없다.
     * 근거 계획이 없는 실행은 비어 있으며 그때 data 구획에 coverage 를 싣지 않는다.
     */
    public record CoverageDigest(String need, String status, List<String> sources, List<String> missingItems) {
        public CoverageDigest { Objects.requireNonNull(need); Objects.requireNonNull(status); sources = List.copyOf(sources); missingItems = List.copyOf(missingItems); }
    }

    /**
     * Composer 입력. 모든 문자열은 호출자가 ContentSanitizer 를 적용한 값이다.
     * @param toolResults 단계 결과(축소 4단계의 필수 필드 투영 포함)
     * @param logSummary 결정론 Log Summarizer 요약(없으면 빈 문자열)
     */
    public record Input(List<EvidenceDigest> evidence, List<PromptDraft.ToolResult> toolResults, String logSummary, List<String> rawLogLines, String question, List<CoverageDigest> coverage) {
        public Input(List<EvidenceDigest> evidence, List<PromptDraft.ToolResult> toolResults, String logSummary, List<String> rawLogLines, String question) {
            this(evidence, toolResults, logSummary, rawLogLines, question, List.of());
        }
        public Input {
            evidence = List.copyOf(evidence); toolResults = List.copyOf(toolResults); rawLogLines = List.copyOf(rawLogLines); coverage = List.copyOf(coverage);
            Objects.requireNonNull(logSummary, "logSummary"); Objects.requireNonNull(question, "question");
        }
        @Override public String toString() { return "ComposerInput[보호됨]"; }
    }

    /**
     * {@code narrative} 는 {@link ComposerOutcome#COMPOSED} 일 때만 있다. 위반 수는 기록용이며 차단하지 않는다(AC-22-LIVE).
     * {@code reductionSteps} 는 적용한 축소 단계와 전후 토큰 수(원문 없음)다.
     */
    public record Result(ComposerOutcome outcome, Optional<String> narrative, int calls, int citationViolations, int numberViolations, List<PromptReducer.Step> reductionSteps) {
        public Result {
            Objects.requireNonNull(outcome, "outcome"); Objects.requireNonNull(narrative, "narrative"); reductionSteps = List.copyOf(reductionSteps);
            if (outcome == ComposerOutcome.NOT_INVOKED || narrative.isPresent() != (outcome == ComposerOutcome.COMPOSED)) throw new IllegalArgumentException("서술 결과 조합이 잘못되었습니다");
        }
        @Override public String toString() { return "ComposerResult[" + outcome + "]"; }
    }

    private final InferenceClient inference;
    private final PromptReducer reducer;
    private final TokenBudgetPolicy.CallBudget budget;
    private final AnswerTokenCounting answers;
    private final ExpiryCheckpoint expiry;
    private final LlmCallObserver observer;
    private final String systemPrompt;

    public ComposerService(InferenceClient inference, PromptReducer reducer, TokenBudgetPolicy policy, AnswerTokenCounting answers, ExpiryCheckpoint expiry,
            LlmCallObserver observer, String systemPrompt) {
        this.inference = Objects.requireNonNull(inference, "inference");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        this.budget = Objects.requireNonNull(policy, "policy").budget(CallType.COMPOSER);
        this.answers = Objects.requireNonNull(answers, "answers");
        this.expiry = Objects.requireNonNull(expiry, "expiry");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
        if (systemPrompt.isBlank() || systemPrompt.indexOf('\r') >= 0) throw new IllegalArgumentException("서술 프롬프트 형식이 잘못되었습니다");
    }

    public Result compose(JobContext job, Input input) {
        // 만료·취소면 Prompt 조립도 하지 않는다.
        expiry.assertLive(job.conversationExpiresAt(), job.cancellation());
        var draft = new PromptDraft(systemPrompt, "boundSystem: " + job.scope().boundSystem().name(), List.of(), List.of(), input.logSummary(),
            input.rawLogLines(), input.toolResults(), input.question());
        var reduction = reducer.reduce(CallType.COMPOSER, draft, value -> messages(value, input.evidence(), input.coverage()));
        if (!reduction.withinBudget()) return failed(ComposerOutcome.INPUT_BUDGET_EXCEEDED, 0, reduction.steps());
        String data = reduction.messages().getLast().content();
        List<ChatMessage> messages = reduction.messages();
        int calls = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            expiry.assertLive(job.conversationExpiresAt(), job.cancellation());
            InferenceClient.ChatOutcome outcome;
            try (var scope = observer.open(job.jobId(), CallType.COMPOSER, messages)) {
                outcome = inference.chat(CallType.COMPOSER, messages, budget.requestMaxTokens());
            }
            expiry.assertLive(job.conversationExpiresAt(), job.cancellation());
            if (outcome instanceof InferenceClient.BudgetExceeded) return failed(ComposerOutcome.INPUT_BUDGET_EXCEEDED, calls, reduction.steps());
            calls++;
            if (outcome instanceof InferenceClient.CallFailed) return failed(ComposerOutcome.LLM_UNAVAILABLE, calls, reduction.steps());
            var completed = (InferenceClient.Completed) outcome;
            String failure = AnswerCheck.failure(completed, budget, answers);
            if (failure == null) return composed(completed.content(), input.evidence().size(), data, calls, reduction.steps());
            List<ChatMessage> retry = new ArrayList<>(reduction.messages());
            retry.add(new ChatMessage(ChatMessage.Role.USER, RETRY_PREFIX + AnswerCheck.retryCode(failure) + RETRY_SUFFIX));
            messages = List.copyOf(retry);
        }
        return failed(ComposerOutcome.LLM_UNAVAILABLE, calls, reduction.steps());
    }

    /** system = 고정 지시 + boundSystem 제약, user = 질문과 data 구획(canonical JSON). 같은 draft 는 같은 메시지를 만든다. */
    static List<ChatMessage> messages(PromptDraft draft, List<EvidenceDigest> evidence) { return messages(draft, evidence, List.of()); }

    static List<ChatMessage> messages(PromptDraft draft, List<EvidenceDigest> evidence, List<CoverageDigest> coverage) {
        ObjectNode user = SchemaContracts.JSON.createObjectNode();
        user.put("question", draft.currentQuestion());
        ObjectNode data = user.putObject("data");
        var digests = data.putArray("evidence");
        for (int i = 0; i < evidence.size(); i++) {
            var item = digests.addObject().put("id", "e" + (i + 1)).put("sourceType", evidence.get(i).sourceType());
            item.put("eventTime", evidence.get(i).eventTime()); item.put("observedAt", evidence.get(i).observedAt());
        }
        var history = data.putArray("history");
        for (var turn : draft.history()) history.addObject().put("user", turn.user()).put("assistant", turn.assistant());
        var chunks = data.putArray("ragChunks");
        for (var chunk : draft.ragChunks()) chunks.addObject().put("id", chunk.id()).put("text", chunk.text());
        data.put("logSummary", draft.logSummary());
        var lines = data.putArray("rawLogLines"); draft.rawLogLines().forEach(lines::add);
        var tools = data.putArray("toolResults");
        for (var tool : draft.toolResults()) tools.addObject().put("ref", tool.ref()).put("text", tool.text());
        if (!coverage.isEmpty()) {
            var needs = data.putArray("coverage");
            for (var value : coverage) {
                var item = needs.addObject().put("need", value.need()).put("status", value.status());
                var sources = item.putArray("sources"); value.sources().forEach(sources::add);
                var missing = item.putArray("missingItems"); value.missingItems().forEach(missing::add);
            }
        }
        return List.of(new ChatMessage(ChatMessage.Role.SYSTEM, draft.systemPrompt() + "\n" + draft.boundSystemConstraint()),
            new ChatMessage(ChatMessage.Role.USER, ClassificationService.canonical(user)));
    }

    private static Result composed(String content, int evidenceCount, String data, int calls, List<PromptReducer.Step> steps) {
        StringBuilder cleaned = new StringBuilder();
        int citationViolations = 0;
        Matcher citation = CITATION.matcher(content);
        while (citation.find()) {
            long index = Long.parseLong(citation.group(1));
            boolean valid = index >= 1 && index <= evidenceCount;
            if (!valid) citationViolations++;
            citation.appendReplacement(cleaned, valid ? Matcher.quoteReplacement(citation.group()) : "");
        }
        citation.appendTail(cleaned);
        String narrative = cleaned.toString();
        int numberViolations = 0;
        Matcher number = NUMBER.matcher(CITATION.matcher(narrative).replaceAll(""));
        while (number.find()) if (!data.contains(number.group())) numberViolations++;
        return new Result(ComposerOutcome.COMPOSED, Optional.of(narrative), calls, citationViolations, numberViolations, steps);
    }

    private static Result failed(ComposerOutcome outcome, int calls, List<PromptReducer.Step> steps) {
        return new Result(outcome, Optional.empty(), calls, 0, 0, steps);
    }
}
