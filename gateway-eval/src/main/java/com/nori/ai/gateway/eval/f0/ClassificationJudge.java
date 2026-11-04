package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.util.*;

/** 압축 출력 판정: JSON 파싱 → 스키마(필수 키·enum·타입·형식) → finish_reason·usage 검사. 실패하면 오류 요지를 붙여 1회만 재시도한다. */
public final class ClassificationJudge {
    /** judge 가 재시도 요지로 내는 고정 오류 코드 전체. 호출 전 입력 audit 은 코드마다 재시도 메시지를 계수합니다. */
    public static final List<String> RETRY_ERROR_CODES = List.of("FINISH_LENGTH", "FINISH_REASON_INVALID", "USAGE_EXCEEDS_LIMIT", "JSON_PARSE_FAILED", "SCHEMA_KEYS_INVALID",
        "ENUM_INVALID", "QREF_INVALID", "TYPE_INVALID", "FORMAT_INVALID", "ARG_LENGTH_EXCEEDED", "STEPS_INVALID");
    /**
     * 재시도 힌트. NONE = 기존 등록과 같은 재시도(판정 errorCode 그대로). STEPS_DETAIL_V1 = v3 등록에서만 opt-in 하며, 계획 판정이 STEPS_INVALID 일 때
     * 같은 검사가 찾은 세부 원인(STEPS_DETAIL_CODES)을 재시도 요지로 보냅니다. 판정(valid·errorCode·결과 행 errorCodes)은 바꾸지 않습니다.
     */
    public static final String RETRY_HINT_NONE = "NONE", STEPS_DETAIL_V1 = "STEPS_DETAIL_V1";
    /** STEPS_INVALID 의 세부 원인 중 재시도 요지로 보내는 것(이름은 attemptDiagnostics.steps 의 shape·toolConsistency 값과 같음). 배열 아님·10개 초과·문자열 아닌 항목은 STEPS_INVALID 그대로입니다. */
    public static final List<String> STEPS_DETAIL_CODES = List.of("ITEM_FORMAT_INVALID", "EMPTY_FOR_TOOL_TASK", "NONEMPTY_FOR_NO_TOOL_TASK");
    /**
     * v4(thinking 전용 모델) 답변 상한 판정 코드(SPEC §14.5 "답변 상한 초과 = 실패", 2026-11-04 사용자 결정). v1~v3 판정에는 나오지 않으며,
     * 재시도 요지는 기존 USAGE_EXCEEDS_LIMIT 로 보냅니다(retryCode) — 사전 입력 audit 이 계수하는 재시도 메시지 집합을 v2·v3 과 같게 두기 위해서입니다.
     */
    public static final String ANSWER_LIMIT_EXCEEDED = "ANSWER_LIMIT_EXCEEDED", ANSWER_TOKENS_UNCOUNTABLE = "ANSWER_TOKENS_UNCOUNTABLE";
    private static final Set<String> ANSWER_CODES = Set.of(ANSWER_LIMIT_EXCEEDED, ANSWER_TOKENS_UNCOUNTABLE);
    static final String TIME ="\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:\\d{2})?";
    /** 판정 결과. 인자 값은 메모리 판정에만 쓰고 결과 파일에는 SHA 만 남긴다. stepsDetail 은 STEPS_INVALID 의 세부 원인(STEPS_DETAIL_CODES 중 하나, 없으면 null)입니다. */
    public record Verdict(boolean valid, String errorCode, String t, Map<String,String> args, List<String> steps, String stepsDetail) {
        public Verdict(boolean valid, String errorCode, String t, Map<String,String> args, List<String> steps) { this(valid, errorCode, t, args, steps, null); }
        @Override public String toString() { return "Verdict[valid=" + valid + ", errorCode=" + errorCode + ", t=" + t + "]"; }
    }
    public record Outcome(String status, boolean retried, boolean firstValid, List<String> finishReasons, List<Integer> promptTokens, List<Integer> completionTokens,
                          List<String> outputSha256, List<String> errorCodes, long latencyMs, Verdict verdict) {}
    public interface Caller { OllamaChatClient.ChatResult call(List<Map<String,String>> messages); }

    public static Verdict judge(OllamaChatClient.ChatResult result, int maxTokens, MessageAssembler.Kind kind) {
        return judge(result, maxTokens, kind, false, null);
    }

    public static Verdict judge(OllamaChatClient.ChatResult result, int maxTokens, MessageAssembler.Kind kind, boolean versioned, String timezone) {
        return judge(result, maxTokens, kind, versioned, timezone, null);
    }
    public static Verdict judge(OllamaChatClient.ChatResult result, int maxTokens, MessageAssembler.Kind kind, boolean versioned, String timezone, QRefContext references) {
        return judge(result, maxTokens, kind, versioned, timezone, references, null);
    }
    /** answerLimit = v4 등록 답변(사고 제외) 상한(분류 192·계획 256). null 이면 v1~v3 판정 그대로입니다(완료 토큰을 요청 max_tokens 로만 검사). */
    public static Verdict judge(OllamaChatClient.ChatResult result, int maxTokens, MessageAssembler.Kind kind, boolean versioned, String timezone, QRefContext references, Integer answerLimit) {
        if (versioned && references == null) return invalid("QREF_REQUEST_MISSING");
        if (result.finishReason().equals("length")) return invalid("FINISH_LENGTH");
        if (!result.finishReason().equals("stop")) return invalid("FINISH_REASON_INVALID");
        if (result.completionTokens() > maxTokens || result.completionTokens() < 0) return invalid("USAGE_EXCEEDS_LIMIT");
        if (answerLimit != null) {
            Integer answer = answerTokens(result);
            if (answer == null) return invalid(ANSWER_TOKENS_UNCOUNTABLE);
            if (answer > answerLimit) return invalid(ANSWER_LIMIT_EXCEEDED);
        }
        JsonNode node;
        try { node = Json.MAPPER.readTree(result.content()); } catch (IOException | RuntimeException e) { return invalid("JSON_PARSE_FAILED"); }
        if (node == null || !node.isObject()) return invalid("JSON_PARSE_FAILED");
        Set<String> expectedKeys = kind == MessageAssembler.Kind.PLAN ? Set.of("t", "eqp", "from", "to", "q", "steps") : Set.of("t", "eqp", "from", "to", "q");
        if (references != null) {
            expectedKeys = new HashSet<>(expectedKeys); expectedKeys.remove("q"); expectedKeys.add("qRef");
        }
        if (!GoldenSet.keys(node).equals(expectedKeys)) return invalid("SCHEMA_KEYS_INVALID");
        if (!node.path("t").isTextual() || !RegistryFixture.TASK_TYPES.contains(node.path("t").textValue())) return invalid("ENUM_INVALID");
        if (references != null && !references.accepts(node.path("qRef"))) return invalid("QREF_INVALID");
        Map<String,String> args = new LinkedHashMap<>();
        for (String field : GoldenSet.ARG_FIELDS) {
            if (field.equals("q") && references != null) { args.put(field, references.resolve(node.path("qRef"))); continue; }
            JsonNode value = node.path(field);
            if (value.isNull()) { args.put(field, null); continue; }
            if (!value.isTextual() || value.textValue().isEmpty()) return invalid("TYPE_INVALID");
            if ((field.equals("from") || field.equals("to")) && !value.textValue().matches(TIME)) return invalid("FORMAT_INVALID");
            if (versioned) {
                String text = value.textValue(); int length = text.codePointCount(0, text.length());
                if (text.codePoints().anyMatch(c -> c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)) return invalid("TYPE_INVALID");
                if ((field.equals("eqp") && length > ArgLimits.V2_EQP_MAX_CODEPOINTS) || (field.equals("q") && length > ArgLimits.V2_QUERY_MAX_CODEPOINTS)) return invalid("ARG_LENGTH_EXCEEDED");
                if (field.equals("from") || field.equals("to")) {
                    // F0 정확 일치는 모델 원문으로 판정하며 시각 자동 보정으로 정답을 만들지 않습니다.
                    try { ArgLimits.normalizeTime(text, timezone); } catch (EvalException e) { return invalid("FORMAT_INVALID"); }
                }
            }
            args.put(field, value.textValue());
        }
        List<String> steps = new ArrayList<>();
        if (kind == MessageAssembler.Kind.PLAN) {
            JsonNode array = node.path("steps");
            if (!array.isArray() || array.size() > 10) return invalid("STEPS_INVALID");
            for (JsonNode step : array) {
                if (!step.isTextual()) return invalid("STEPS_INVALID");
                if (!step.textValue().matches("[a-z]+(?:\\.[A-Za-z]+){1,4}")) return invalidSteps("ITEM_FORMAT_INVALID");
                steps.add(step.textValue());
            }
            boolean noTool = Set.of("GQ", "UNS").contains(node.path("t").textValue());
            if (noTool != steps.isEmpty()) return invalidSteps(noTool ? "NONEMPTY_FOR_NO_TOOL_TASK" : "EMPTY_FOR_TOOL_TASK");
        }
        return new Verdict(true, null, node.path("t").textValue(), Collections.unmodifiableMap(args), List.copyOf(steps));
    }
    /**
     * 답변(사고 제외) 토큰 수 = usage.completion_tokens − usage.completion_tokens_details.reasoning_tokens. 사고 토큰이 completion 에 포함된다는
     * OpenAI 호환 정의를 전제하며, Ollama 0.40.13 의 실제 보고 의미는 F0 진단 확인 항목입니다(SPEC §14.5 열린 항목 ⓒ — 확인 전 GATE 등록은 Prereg 가 거부).
     * 사고 토큰 수 보고가 없거나, content 에 사고 표지가 섞였거나, 사고 토큰 수가 completion 보다 크면 셀 수 없으므로 null 입니다(호출자는 실패로 판정, fail-closed).
     */
    static Integer answerTokens(OllamaChatClient.ChatResult result) {
        OllamaChatClient.ThinkingObservation thinking = result.thinking();
        if (thinking == null || thinking.reasoningTokens() == null || thinking.contentThinkOpen() || thinking.contentThinkClose() || thinking.reasoningTokens() > result.completionTokens()) return null;
        return result.completionTokens() - thinking.reasoningTokens();
    }
    private static Verdict invalid(String code) { return new Verdict(false, code, null, Map.of(), List.of()); }
    private static Verdict invalidSteps(String detail) { return new Verdict(false, "STEPS_INVALID", null, Map.of(), List.of(), detail); }

    /** 재시도 힌트가 보낼 수 있는 재시도 요지 전체. 호출 전 입력 audit 과 측정이 같은 목록을 씁니다(분류 재시도는 힌트와 무관하게 기존 목록). */
    public static List<String> retryCodes(MessageAssembler.Kind kind, String retryHint) {
        requireRetryHint(retryHint);
        if (kind != MessageAssembler.Kind.PLAN || retryHint.equals(RETRY_HINT_NONE)) return RETRY_ERROR_CODES;
        List<String> codes = new ArrayList<>(RETRY_ERROR_CODES); codes.addAll(STEPS_DETAIL_CODES);
        return List.copyOf(codes);
    }
    /** 무효 판정 하나의 재시도 요지. STEPS_DETAIL_V1 에서 세부 원인이 있으면 그 코드, 아니면 판정 errorCode 입니다. */
    static String retryCode(Verdict verdict, String retryHint) {
        if (ANSWER_CODES.contains(verdict.errorCode())) return "USAGE_EXCEEDS_LIMIT";
        return retryHint.equals(STEPS_DETAIL_V1) && verdict.stepsDetail() != null ? verdict.stepsDetail() : verdict.errorCode();
    }
    private static void requireRetryHint(String retryHint) {
        if (!RETRY_HINT_NONE.equals(retryHint) && !STEPS_DETAIL_V1.equals(retryHint)) throw new EvalException(2, "RETRY_HINT_INVALID");
    }

    /** 송신~최종 응답(재시도 포함) 지연을 재고, timeout 은 그 시각까지의 경과로 남긴다. timeout 외 endpoint 오류는 호출자에게 전파(측정 중단). */
    public static Outcome execute(Caller caller, List<Map<String,String>> messages, int maxTokens, MessageAssembler.Kind kind) {
        return execute(caller, messages, maxTokens, kind, false, null);
    }

    public static Outcome execute(Caller caller, List<Map<String,String>> messages, int maxTokens, MessageAssembler.Kind kind, boolean versioned, String timezone) {
        return execute(caller, messages, maxTokens, kind, versioned, timezone, null);
    }
    public static Outcome execute(Caller caller, List<Map<String,String>> messages, int maxTokens, MessageAssembler.Kind kind, boolean versioned, String timezone, QRefContext references) {
        return execute(caller, messages, maxTokens, kind, versioned, timezone, references, RETRY_HINT_NONE);
    }
    /** retryHint 는 재시도 메시지의 요지만 정합니다. errorCodes(결과 행)는 힌트와 무관하게 판정 errorCode 입니다. */
    public static Outcome execute(Caller caller, List<Map<String,String>> messages, int maxTokens, MessageAssembler.Kind kind, boolean versioned, String timezone, QRefContext references, String retryHint) {
        return execute(caller, messages, maxTokens, kind, versioned, timezone, references, retryHint, null);
    }
    /** answerLimit 은 judge 와 같습니다(v4 등록 답변 상한, v1~v3 은 null). 초과·계수 불가도 다른 판정 실패처럼 1회만 재시도합니다. */
    public static Outcome execute(Caller caller, List<Map<String,String>> messages, int maxTokens, MessageAssembler.Kind kind, boolean versioned, String timezone, QRefContext references, String retryHint, Integer answerLimit) {
        requireRetryHint(retryHint);
        if (versioned && references == null) throw new EvalException(2, "QREF_REQUEST_MISSING");
        if (references != null) references.requireMessagesMatch(messages);
        List<String> finish = new ArrayList<>(), shas = new ArrayList<>(), errors = new ArrayList<>(); List<Integer> prompt = new ArrayList<>(), completion = new ArrayList<>();
        long start = System.nanoTime(); boolean retried = false, firstValid = false; Verdict verdict = null; String retryCode = null;
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                OllamaChatClient.ChatResult result = caller.call(attempt == 0 ? messages : MessageAssembler.retry(messages, retryCode));
                finish.add(sanitize(result.finishReason())); prompt.add(result.promptTokens()); completion.add(result.completionTokens());
                shas.add(Json.sha(result.content().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                verdict = judge(result, maxTokens, kind, versioned, timezone, references, answerLimit);
                if (attempt == 0) firstValid = verdict.valid();
                if (verdict.valid()) break;
                errors.add(verdict.errorCode()); retryCode = retryCode(verdict, retryHint); if (attempt == 0) retried = true;
            }
        } catch (EvalException e) {
            if (!e.getMessage().equals("ENDPOINT_TIMEOUT")) throw e;
            errors.add("ENDPOINT_TIMEOUT");
            return new Outcome("TIMEOUT", !finish.isEmpty(), firstValid, finish, prompt, completion, shas, errors, elapsed(start), null);
        }
        return new Outcome(verdict.valid() ? "OK" : "INVALID", retried, firstValid, finish, prompt, completion, shas, errors, elapsed(start), verdict);
    }
    static String sanitize(String finishReason) { return Set.of("stop", "length").contains(finishReason) ? finishReason : "other"; }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
