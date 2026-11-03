package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.Path;
import java.util.*;

/**
 * F0 v2 전용 호출 전 입력 예산 확인. 사전등록한 유한 입력(골든셋·경계 케이스·메시지 입력과 고정 오류 코드별 재시도)을 실제 Java 조립 메시지 SHA 와
 * 검토된 렌더러·pin 된 공식 tokenizer 로 미리 센 표(count_input_audit_v2.py 산출)만 사용합니다. 표에 없는 메시지는 계수를 추정하지 않고 호출 전에 거부합니다.
 * 운영 Gateway 의 사전 토큰 계수기(P18)가 아닙니다.
 */
public final class InputAudit {
    public static final String SCHEMA = "f0-input-audit-v2";
    /** 하네스가 보낼 수 있는 chat 호출 하나(첫 시도 또는 고정 오류 코드 재시도). id 는 원문 없는 위치 표지입니다. */
    public record Planned(String id, List<Map<String,String>> messages, int inputLimit, int maxTokens) {}
    private final Map<String,Integer> tokens;
    private final String tokenizerSha256, templateSha256;

    private InputAudit(Map<String,Integer> tokens, String tokenizerSha256, String templateSha256) { this.tokens = tokens; this.tokenizerSha256 = tokenizerSha256; this.templateSha256 = templateSha256; }

    public static InputAudit load(Path file) {
        JsonNode root = Json.read(file);
        if (!root.path("schema").asText().equals(SCHEMA) || !root.path("tokenizerSha256").asText().matches("[0-9a-f]{64}") || !root.path("templateSha256").asText().matches("[0-9a-f]{64}") || !root.path("rows").isArray()) throw new EvalException(2, "INPUT_AUDIT_INVALID");
        Map<String,Integer> tokens = new HashMap<>();
        for (JsonNode row : root.path("rows")) {
            JsonNode count = row.path("inputTokens");
            if (!row.path("messagesSha256").asText().matches("[0-9a-f]{64}") || !count.isInt() || count.intValue() < 1) throw new EvalException(2, "INPUT_AUDIT_INVALID");
            Integer previous = tokens.put(row.path("messagesSha256").asText(), count.intValue());
            if (previous != null && previous != count.intValue()) throw new EvalException(2, "INPUT_AUDIT_INVALID");
        }
        return new InputAudit(Map.copyOf(tokens), root.path("tokenizerSha256").asText(), root.path("templateSha256").asText());
    }
    /** 계수에 쓴 tokenizer·chat template 이 측정 런타임과 같아야 표를 씁니다. */
    public void requireRuntime(MeasurementSession.Runtime runtime) {
        if (!tokenizerSha256.equals(runtime.tokenizerSha256()) || !templateSha256.equals(runtime.templateSha256())) throw new EvalException(2, "INPUT_AUDIT_RUNTIME_MISMATCH");
    }
    /** 사전 계수를 돌려줍니다. 없거나 입력 상한·입력+max_tokens ≤ context 를 어기면 송신하지 않습니다. */
    public int require(List<Map<String,String>> messages, int inputLimit, int maxTokens) {
        Integer counted = tokens.get(messagesSha256(messages));
        if (counted == null) throw new EvalException(2, "INPUT_AUDIT_MISSING");
        if (counted > inputLimit || counted + maxTokens > Prereg.CONTEXT_TOKENS) throw new EvalException(2, "INPUT_BUDGET_EXCEEDED");
        return counted;
    }
    public void requireAll(List<Planned> planned) { for (Planned call : planned) require(call.messages(), call.inputLimit(), call.maxTokens()); }
    public static String messagesSha256(List<Map<String,String>> messages) { return Json.sha(Json.canonical(Json.MAPPER.valueToTree(messages))); }

    /** 측정 명령별 전체 호출 목록. 계수 표 생성과 호출 전 검사가 같은 목록을 씁니다. */
    public static List<Planned> run(GoldenSet golden, MessageAssembler assembler, Prereg.Budget budget, String route) { return run(golden, assembler, budget, route, ClassificationJudge.RETRY_HINT_NONE); }
    /** retryHint 는 등록 parameters.retryHint 이며, 측정(ClassificationJudge.execute)과 같은 재시도 요지 목록(ClassificationJudge.retryCodes)으로 재시도 행을 만듭니다. */
    public static List<Planned> run(GoldenSet golden, MessageAssembler assembler, Prereg.Budget budget, String route, String retryHint) {
        List<Planned> planned = new ArrayList<>(); List<String> codes = ClassificationJudge.retryCodes(assembler.kind(), retryHint);
        for (GoldenSet.Question q : golden.questions())
            withRetries(planned, route + "/" + q.id(), assembler.messages(q.question(), q.context(), QRefContext.extract(q.question())), budget.input(assembler.kind()), budget.output(assembler.kind()), codes);
        return planned;
    }
    public static List<Planned> boundary(List<JsonNode> cases, MessageAssembler classify, Prereg.Budget budget) {
        List<Planned> planned = new ArrayList<>();
        for (JsonNode c : cases) {
            String question = c.path("question").asText();
            withRetries(planned, "boundary/" + c.path("caseId").asText(), classify.messages(question, c.path("context"), QRefContext.extract(question)), budget.classifyInput(), budget.classifyOutput(), ClassificationJudge.RETRY_ERROR_CODES);
        }
        return planned;
    }
    /** usage-probe·token-golden 은 max_tokens 1 의 계수 호출이므로 context 안(입력 + 1 ≤ 4096)만 요구합니다. */
    public static List<Planned> probes(String use, List<Map.Entry<String,List<Map<String,String>>>> rows) {
        List<Planned> planned = new ArrayList<>();
        for (var row : rows) planned.add(new Planned(use + "/" + row.getKey(), row.getValue(), Prereg.CONTEXT_TOKENS - 1, 1));
        return planned;
    }
    private static void withRetries(List<Planned> planned, String id, List<Map<String,String>> first, int inputLimit, int maxTokens, List<String> codes) {
        planned.add(new Planned(id + "/first", first, inputLimit, maxTokens));
        for (String code : codes) planned.add(new Planned(id + "/retry/" + code, MessageAssembler.retry(first, code), inputLimit, maxTokens));
    }
}
