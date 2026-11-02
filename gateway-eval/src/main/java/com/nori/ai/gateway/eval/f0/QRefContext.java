package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 요청 원문과 참조 목록을 같은 요청 메모리에 결속합니다. */
public final class QRefContext {
    private final List<String> candidates;
    private final String questionSha256;
    private QRefContext(String question, List<String> candidates) {
        this.candidates = List.copyOf(candidates); this.questionSha256 = Json.sha(strictUtf8(question, "QUERY_LITERAL_INVALID"));
    }
    /** String.getBytes는 짝 없는 surrogate를 '?'로 바꾸므로 손실 변환 대신 고정 코드로 거부합니다(원문은 오류에 싣지 않음). */
    static byte[] strictUtf8(String text, String errorCode) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[encoded.remaining()]; encoded.get(bytes); return bytes;
        } catch (CharacterCodingException e) { throw new EvalException(2, errorCode); }
    }
    public static QRefContext extract(String question) {
        // 인용구 밖의 짝 없는 surrogate도 요청 SHA 결속을 흐리므로 스캔 전에 질문 전체를 검증합니다.
        strictUtf8(question, "QUERY_LITERAL_INVALID");
        List<String> values = new ArrayList<>();
        for (int at = 0; at < question.length(); at++) {
            if (question.charAt(at) != '"') continue;
            boolean jsonLiteral = at >= 5 && question.substring(at - 5, at).equals("json:");
            int start = at++; boolean closed = false;
            for (; at < question.length(); at++) {
                char c = question.charAt(at);
                if (jsonLiteral && c == '\\') { at++; continue; }
                if (c == '"') { closed = true; break; }
            }
            if (!closed) throw new EvalException(2, "QUERY_LITERAL_INVALID");
            String value = question.substring(start + 1, at);
            if (jsonLiteral) {
                try { value = Json.parse(strictUtf8(question.substring(start, at + 1), "QUERY_LITERAL_INVALID")).textValue(); }
                catch (EvalException e) { throw new EvalException(2, "QUERY_LITERAL_INVALID"); }
            }
            // 일반 인용구의 역슬래시는 원문이며 명시된 json: 표기만 디코드합니다.
            if (value == null || value.isEmpty() || value.codePoints().anyMatch(c -> c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)) throw new EvalException(2, "QUERY_LITERAL_INVALID");
            if (value.codePointCount(0, value.length()) > ArgLimits.V2_QUERY_MAX_CODEPOINTS) throw new EvalException(2, "ARG_LENGTH_EXCEEDED");
            values.add(value);
        }
        return new QRefContext(question, values);
    }
    public void requireQuestionMatch(String question) {
        if (!questionSha256.equals(Json.sha(strictUtf8(question, "QREF_REQUEST_MISMATCH")))) throw new EvalException(2, "QREF_REQUEST_MISMATCH");
    }
    /** 다른 요청 메시지에 이전 바인딩을 잘못 주입하는 개발 오류도 모델 호출 전에 차단합니다. */
    public void requireMessagesMatch(List<Map<String,String>> messages) {
        if (messages.isEmpty() || !"user".equals(messages.getLast().get("role"))) throw new EvalException(2, "QREF_REQUEST_MISMATCH");
        JsonNode user;
        try { user = Json.parse(strictUtf8(messages.getLast().getOrDefault("content", ""), "QREF_REQUEST_MISMATCH")); }
        catch (EvalException e) { throw new EvalException(2, "QREF_REQUEST_MISMATCH"); }
        if (!user.path("question").isTextual() || !user.path("qRefCatalog").equals(catalog())) throw new EvalException(2, "QREF_REQUEST_MISMATCH");
        requireQuestionMatch(user.path("question").textValue());
    }
    public ArrayNode catalog() {
        ArrayNode catalog = Json.MAPPER.createArrayNode();
        for (int i = 0; i < candidates.size(); i++) catalog.add("Q" + (i + 1));
        return catalog;
    }
    public boolean accepts(JsonNode reference) {
        if (reference.isNull()) return candidates.isEmpty();
        if (!reference.isTextual()) return false;
        String ref = reference.textValue();
        if (!ref.matches("Q[1-9][0-9]*")) return false;
        try { return Integer.parseInt(ref.substring(1)) <= candidates.size(); }
        catch (NumberFormatException e) { return false; }
    }
    /** 진단 전용: 값이 이 요청의 후보 원문과 같은지만 알려 줍니다(후보 값 자체는 내보내지 않음). */
    boolean isCandidate(String text) { return candidates.contains(text); }
    public String resolve(JsonNode reference) {
        if (!accepts(reference)) throw new EvalException(2, "QREF_INVALID");
        return reference.isNull() ? null : candidates.get(Integer.parseInt(reference.textValue().substring(1)) - 1);
    }
    /** 기대값을 원문 복원 oracle과 wire 토큰 계수로 나눕니다. */
    public ObjectNode wireExpected(JsonNode resolved) {
        ObjectNode wire = resolved.deepCopy(); JsonNode query = wire.remove("q");
        if (query == null || (!query.isNull() && !query.isTextual())) throw new EvalException(2, "CASES_INVALID");
        if (query.isNull()) {
            if (!candidates.isEmpty()) throw new EvalException(2, "CASES_INVALID");
            wire.putNull("qRef");
        } else {
            int index = candidates.indexOf(query.textValue());
            if (index < 0) throw new EvalException(2, "CASES_INVALID");
            wire.put("qRef", "Q" + (index + 1));
        }
        return wire;
    }
    @Override public String toString() { return "QRefContext[request-bound]"; }
}
