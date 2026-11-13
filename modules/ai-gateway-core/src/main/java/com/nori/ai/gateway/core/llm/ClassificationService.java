package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.job.CancellationToken;
import com.nori.ai.gateway.core.job.JobRequestInput;
import com.nori.ai.gateway.core.plan.InputValidator;
import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.result.ErrorCode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 고정 workflow 기본 경로(D50)의 분류·인자 추출(SPEC §14.5 qRef 계약, §28.3 분류 wire v3, Plan P20 "qRef 실제 연결 책임"·§16.3 R1).
 * 순서: 입력 literal 추출 → 원문 q256 검사 → 요청별 불변 바인딩 → 질문+후보 ID 목록 메시지 조립 → 분류 호출 → wire 검증(qRef 결속 포함, 기존 필드 뒤 n·a)
 * → 원문 q 복원·근거 요구 정규화/보완 → resolved {@link Classification}. 판정 실패는 같은 바인딩·고정 오류 요지로 1회만 재시도한다.
 * 프롬프트·모델 출력·후보 원문은 결과·예외·로그에 싣지 않는다.
 */
public final class ClassificationService {
    /** F0 MessageAssembler.retry 와 같은 재시도 요지 문구(직전 출력 원문은 다시 싣지 않는다). */
    static final String RETRY_PREFIX = "직전 출력이 무효다(오류: ", RETRY_SUFFIX = "). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다.";
    /** arg-limits v2 CONFIRMED: eqp 64 CODEPOINT. 시간 35 CODEPOINT 는 아래 형식이 상한을 같이 강제한다. */
    static final int EQP_MAX_CODEPOINTS = 64;
    private static final Pattern TIME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:\\d{2})?");
    /** 분류 wire v3 일곱 키(모두 필수, 추가 키 금지). v2 다섯 키 응답은 기본값 보충 없이 SCHEMA_KEYS_INVALID 다. */
    private static final Set<String> WIRE_KEYS = Set.of("t", "eqp", "from", "to", "qRef", "n", "a");
    private static final Map<String,TaskType> TASKS = Map.of("KQ", TaskType.KNOWLEDGE_QUERY, "LQ", TaskType.LIVE_QUERY, "INC", TaskType.INCIDENT_ANALYSIS,
        "GQ", TaskType.GENERAL_QUERY, "UNS", TaskType.UNSUPPORTED_REQUEST);
    private static final JsonMapper STRICT = JsonMapper.builder().build();

    /** 분류 요청. {@code input} 은 JobContext 에 결속한 바로 그 접수 입력이다. */
    /** {@code scope}·{@code conversationId} 는 호출 시도 감사(LLM_CALL)의 Job 결속에만 쓴다. */
    public record Request(com.nori.ai.gateway.core.scope.VerifiedSystemScope scope, String conversationId, String jobId, Instant conversationExpiresAt, CancellationToken cancellation, JobRequestInput input) {
        public Request {
            Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(conversationId, "conversationId"); Objects.requireNonNull(jobId, "jobId"); Objects.requireNonNull(conversationExpiresAt, "conversationExpiresAt");
            Objects.requireNonNull(cancellation, "cancellation"); Objects.requireNonNull(input, "input");
        }
    }

    /** {@code calls} = 실제 전송까지 간 분류 호출 수(예산 초과로 막힌 시도는 세지 않는다). */
    public sealed interface Result permits Classified, Rejected, Unavailable, BudgetExceeded { int calls(); }
    public record Classified(Classification classification, int calls) implements Result {}
    /** 확인 요청 계열({@code PLAN_INVALID}·검색어 초과 {@code INPUT_BUDGET_EXCEEDED}, clarification fields {@code ["q"]}). */
    public record Rejected(ErrorCode code, int calls) implements Result {
        public Rejected { if (code != ErrorCode.PLAN_INVALID && code != ErrorCode.INPUT_BUDGET_EXCEEDED) throw new IllegalArgumentException("분류 거부 코드가 잘못되었습니다"); }
    }
    /** 분류 LLM 호출 실패(연결·timeout·5xx·잘림 감지) — {@code LLM_UNAVAILABLE}. */
    public record Unavailable(int calls) implements Result {}
    /** 호출 직전 토큰 예산 초과 — {@code INPUT_BUDGET_EXCEEDED}(확인 요청 아님). */
    public record BudgetExceeded(int calls) implements Result {}

    private record Verdict(Classification value, String failure) {}

    private final InferenceClient inference;
    private final TokenBudgetPolicy.CallBudget budget;
    private final AnswerTokenCounting answers;
    private final ExpiryCheckpoint expiry;
    private final LlmCallObserver observer;
    private final LlmCallAudit audit;
    private final String systemPrompt;

    /** @param registryReference 프롬프트 {@code {{REGISTRY}}} 에 넣을 참고 정보({@link ClassificationRegistryText#render}) */
    public ClassificationService(InferenceClient inference, TokenBudgetPolicy policy, AnswerTokenCounting answers, ExpiryCheckpoint expiry,
            LlmCallObserver observer, LlmCallAudit audit, String promptTemplate, String registryReference) {
        this.inference = Objects.requireNonNull(inference, "inference");
        this.budget = Objects.requireNonNull(policy, "policy").budget(CallType.CLASSIFY);
        this.answers = Objects.requireNonNull(answers, "answers");
        this.expiry = Objects.requireNonNull(expiry, "expiry");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.audit = Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(promptTemplate, "promptTemplate"); Objects.requireNonNull(registryReference, "registryReference");
        int at = promptTemplate.indexOf("{{REGISTRY}}");
        if (promptTemplate.indexOf('\r') >= 0 || at < 0 || at != promptTemplate.lastIndexOf("{{REGISTRY}}")) throw new IllegalArgumentException("분류 프롬프트 형식이 잘못되었습니다");
        String rendered = promptTemplate.replace("{{REGISTRY}}", registryReference);
        if (rendered.contains("{{")) throw new IllegalArgumentException("분류 프롬프트 형식이 잘못되었습니다");
        this.systemPrompt = rendered;
    }

    public Result classify(Request request) {
        expiry.assertLive(request.conversationExpiresAt(), request.cancellation());
        String question = request.input().maskedQuestion();
        final QRefContext references;
        try { references = QRefContext.extract(question); }
        catch (QRefContext.Rejected rejected) { return new Rejected(rejected.code(), 0); }
        JsonNode context = modelContext(request.input());
        List<ChatMessage> original = messages(question, context, references);
        ZoneId zone = zone(context);
        List<ChatMessage> messages = original;
        int calls = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            expiry.assertLive(request.conversationExpiresAt(), request.cancellation());
            InferenceClient.ChatOutcome outcome;
            try (var scope = observer.open(request.jobId(), CallType.CLASSIFY, messages)) {
                outcome = inference.chat(CallType.CLASSIFY, messages, budget.requestMaxTokens());
            }
            Verdict verdict = outcome instanceof InferenceClient.Completed completed ? judge(completed, references, zone) : null;
            // 보낸 호출은 만료·취소와 무관하게 그 시도의 감사를 먼저 남긴다(분류는 축소 단계가 없다).
            audit.record(LlmCallAudit.attempt(request.scope(), request.conversationId(), request.jobId(), CallType.CLASSIFY, budget.requestMaxTokens(), outcome,
                verdict == null || verdict.failure() == null ? null : ErrorCode.PLAN_INVALID, List.of()));
            // 진행 중 만료·취소가 되면 받은 응답을 결과에 쓰지 않는다.
            expiry.assertLive(request.conversationExpiresAt(), request.cancellation());
            if (outcome instanceof InferenceClient.BudgetExceeded) return new BudgetExceeded(calls);
            calls++;
            if (outcome instanceof InferenceClient.CallFailed) return new Unavailable(calls);
            if (verdict.failure() == null) return new Classified(verdict.value(), calls);
            messages = retry(original, AnswerCheck.retryCode(verdict.failure()));
        }
        return new Rejected(ErrorCode.PLAN_INVALID, calls);
    }

    /** 분류 user 메시지의 Context: F0 v2 등록과 같은 네 키(timezone·referenceTime·timeWindow·selectedResourceRefs). 값이 없으면 null. */
    static JsonNode modelContext(JobRequestInput input) {
        ObjectNode context = SchemaContracts.JSON.createObjectNode();
        Object raw = input.requestFields().get("context");
        Map<?,?> fields = raw instanceof Map<?,?> map ? map : Map.of();
        context.set("timezone", SchemaContracts.JSON.valueToTree(fields.get("timezone")));
        context.set("referenceTime", SchemaContracts.JSON.valueToTree(fields.get("referenceTime")));
        context.set("timeWindow", SchemaContracts.JSON.valueToTree(fields.get("timeWindow")));
        var refs = context.putArray("selectedResourceRefs");
        for (var ref : input.selectedResourceRefs()) refs.addObject().put("type", ref.type()).put("ref", ref.ref());
        return context;
    }

    List<ChatMessage> messages(String question, JsonNode context, QRefContext references) {
        if (!references.boundTo(question)) throw new IllegalArgumentException("요청 바인딩이 다른 질문입니다");
        ObjectNode user = SchemaContracts.JSON.createObjectNode();
        user.set("context", context); user.put("question", question);
        var catalog = user.putArray("qRefCatalog"); references.catalog().forEach(catalog::add);
        return List.of(new ChatMessage(ChatMessage.Role.SYSTEM, systemPrompt), new ChatMessage(ChatMessage.Role.USER, canonical(user)));
    }

    static List<ChatMessage> retry(List<ChatMessage> original, String code) {
        List<ChatMessage> messages = new ArrayList<>(original);
        messages.add(new ChatMessage(ChatMessage.Role.USER, RETRY_PREFIX + code + RETRY_SUFFIX));
        return List.copyOf(messages);
    }

    static String canonical(JsonNode node) { return SchemaContracts.canonicalize(SchemaContracts.JSON.writeValueAsString(node)); }

    private static ZoneId zone(JsonNode context) {
        return context.path("timezone").isString() ? ZoneId.of(context.get("timezone").asString()) : null;
    }

    private Verdict judge(InferenceClient.Completed completed, QRefContext references, ZoneId zone) {
        String failure = AnswerCheck.failure(completed, budget, answers);
        if (failure != null) return invalid(failure);
        final JsonNode node;
        try { node = STRICT.readTree(completed.content()); }
        catch (RuntimeException parse) { return invalid("JSON_PARSE_FAILED"); }
        if (node == null || !node.isObject()) return invalid("JSON_PARSE_FAILED");
        if (!Set.copyOf(node.propertyNames()).equals(WIRE_KEYS)) return invalid("SCHEMA_KEYS_INVALID");
        if (!node.get("t").isString() || !TASKS.containsKey(node.get("t").asString())) return invalid("ENUM_INVALID");
        if (!references.accepts(node.get("qRef"))) return invalid("QREF_INVALID");
        String[] values = new String[3];
        String[] fields = {"eqp", "from", "to"};
        for (int i = 0; i < fields.length; i++) {
            JsonNode value = node.get(fields[i]);
            if (value.isNull()) continue;
            if (!value.isString() || value.asString().isEmpty()) return invalid("TYPE_INVALID");
            String text = value.asString();
            boolean time = i > 0;
            if (time && !TIME.matcher(text).matches()) return invalid("FORMAT_INVALID");
            if (text.codePoints().anyMatch(c -> c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)) return invalid("TYPE_INVALID");
            if (!time && text.codePointCount(0, text.length()) > EQP_MAX_CODEPOINTS) return invalid("ARG_LENGTH_EXCEEDED");
            // 실제 달력 날짜·시각·offset 만 허용하며 잘못된 날짜를 보정하지 않는다. 원문 표기는 그대로 둔다.
            if (time) {
                try { new InputValidator().parseTime(text, zone); }
                catch (IllegalArgumentException | DateTimeException invalid) { return invalid("FORMAT_INVALID"); }
            }
            values[i] = text;
        }
        // 기존 필드 검사를 모두 통과한 뒤에만 신규 n·a 를 본다(SPEC §28.3 ① 검사 순서).
        String taskCode = node.get("t").asString();
        String needs = ClassificationNeeds.violation(taskCode, node.get("n"), node.get("a"));
        if (needs != null) return invalid(needs);
        TaskType task = TASKS.get(taskCode);
        var resolved = ClassificationNeeds.resolve(task, ClassificationNeeds.codes(node.get("n")));
        var classification = new Classification(task, values[0], values[1], values[2], references.resolve(node.get("qRef")),
            resolved.evidenceNeeds(), resolved.serverAdded(), ClassificationNeeds.answerMode(node.get("a")), Classification.WIRE_V3);
        return new Verdict(classification, null);
    }

    private static Verdict invalid(String code) { return new Verdict(null, code); }
}
