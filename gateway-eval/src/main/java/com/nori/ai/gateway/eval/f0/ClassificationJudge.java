package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.util.*;

/** 압축 출력 판정: JSON 파싱 → 스키마(필수 키·enum·타입·형식) → finish_reason·usage 검사. 실패하면 오류 요지를 붙여 1회만 재시도한다. */
public final class ClassificationJudge {
    static final String TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:\\d{2})?";
    /** 판정 결과. 인자 값은 메모리 판정에만 쓰고 결과 파일에는 SHA 만 남긴다. */
    public record Verdict(boolean valid, String errorCode, String t, Map<String,String> args, List<String> steps) {
        @Override public String toString() { return "Verdict[valid=" + valid + ", errorCode=" + errorCode + ", t=" + t + "]"; }
    }
    public record Outcome(String status, boolean retried, boolean firstValid, List<String> finishReasons, List<Integer> promptTokens, List<Integer> completionTokens,
                          List<String> outputSha256, List<String> errorCodes, long latencyMs, Verdict verdict) {}
    public interface Caller { OllamaChatClient.ChatResult call(List<Map<String,String>> messages); }

    public static Verdict judge(OllamaChatClient.ChatResult result, int maxTokens, MessageAssembler.Kind kind) {
        if (result.finishReason().equals("length")) return invalid("FINISH_LENGTH");
        if (!result.finishReason().equals("stop")) return invalid("FINISH_REASON_INVALID");
        if (result.completionTokens() > maxTokens || result.completionTokens() < 0) return invalid("USAGE_EXCEEDS_LIMIT");
        JsonNode node;
        try { node = Json.MAPPER.readTree(result.content()); } catch (IOException | RuntimeException e) { return invalid("JSON_PARSE_FAILED"); }
        if (node == null || !node.isObject()) return invalid("JSON_PARSE_FAILED");
        Set<String> expectedKeys = kind == MessageAssembler.Kind.PLAN ? Set.of("t", "eqp", "from", "to", "q", "steps") : Set.of("t", "eqp", "from", "to", "q");
        if (!GoldenSet.keys(node).equals(expectedKeys)) return invalid("SCHEMA_KEYS_INVALID");
        if (!node.path("t").isTextual() || !RegistryFixture.TASK_TYPES.contains(node.path("t").textValue())) return invalid("ENUM_INVALID");
        Map<String,String> args = new LinkedHashMap<>();
        for (String field : GoldenSet.ARG_FIELDS) {
            JsonNode value = node.path(field);
            if (value.isNull()) { args.put(field, null); continue; }
            if (!value.isTextual() || value.textValue().isEmpty()) return invalid("TYPE_INVALID");
            if ((field.equals("from") || field.equals("to")) && !value.textValue().matches(TIME)) return invalid("FORMAT_INVALID");
            args.put(field, value.textValue());
        }
        List<String> steps = new ArrayList<>();
        if (kind == MessageAssembler.Kind.PLAN) {
            JsonNode array = node.path("steps");
            if (!array.isArray() || array.size() > 10) return invalid("STEPS_INVALID");
            for (JsonNode step : array) { if (!step.isTextual() || !step.textValue().matches("[a-z]+(?:\\.[A-Za-z]+){1,4}")) return invalid("STEPS_INVALID"); steps.add(step.textValue()); }
            boolean noTool = Set.of("GQ", "UNS").contains(node.path("t").textValue());
            if (noTool != steps.isEmpty()) return invalid("STEPS_INVALID");
        }
        return new Verdict(true, null, node.path("t").textValue(), Collections.unmodifiableMap(args), List.copyOf(steps));
    }
    private static Verdict invalid(String code) { return new Verdict(false, code, null, Map.of(), List.of()); }

    /** 송신~최종 응답(재시도 포함) 지연을 재고, timeout 은 그 시각까지의 경과로 남긴다. timeout 외 endpoint 오류는 호출자에게 전파(측정 중단). */
    public static Outcome execute(Caller caller, List<Map<String,String>> messages, int maxTokens, MessageAssembler.Kind kind) {
        List<String> finish = new ArrayList<>(), shas = new ArrayList<>(), errors = new ArrayList<>(); List<Integer> prompt = new ArrayList<>(), completion = new ArrayList<>();
        long start = System.nanoTime(); boolean retried = false, firstValid = false; Verdict verdict = null;
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                OllamaChatClient.ChatResult result = caller.call(attempt == 0 ? messages : MessageAssembler.retry(messages, errors.getLast()));
                finish.add(sanitize(result.finishReason())); prompt.add(result.promptTokens()); completion.add(result.completionTokens());
                shas.add(Json.sha(result.content().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                verdict = judge(result, maxTokens, kind);
                if (attempt == 0) firstValid = verdict.valid();
                if (verdict.valid()) break;
                errors.add(verdict.errorCode()); if (attempt == 0) retried = true;
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
