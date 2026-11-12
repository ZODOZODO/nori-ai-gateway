package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.result.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 요청 하나의 검색어 후보 원문과 참조 ID 목록을 그 요청 메모리에만 결속한다(SPEC §14.5 "검색어 원문과 참조"·"원문 추출 문법").
 * 분류 호출마다 새로 만들고, 메시지 조립·1회 재시도·응답 검증·원문 복원에 같은 객체를 쓴다. 다른 요청의 같은 ID 와 바인딩을 공유하지 않는다.
 * 원문은 오류·toString 에 싣지 않는다.
 */
public final class QRefContext {
    /** SPEC §14.5 q ≤ 256 CODEPOINT(2026-10-30 Gateway 제품 계약, arg-limits v2 CONFIRMED). */
    public static final int QUERY_MAX_CODEPOINTS = 256;
    private static final JsonMapper STRICT = JsonMapper.builder().build();

    /** 분류 전 거부. {@code code} 는 {@code PLAN_INVALID}(명확한 원문을 만들 수 없음) 또는 {@code INPUT_BUDGET_EXCEEDED}(256 초과)다. */
    public static final class Rejected extends RuntimeException {
        private final ErrorCode code;
        Rejected(ErrorCode code) { super("검색어 원문을 확정할 수 없습니다", null, false, false); this.code = code; }
        public ErrorCode code() { return code; }
    }

    private final List<String> candidates;
    private final String question;

    private QRefContext(String question, List<String> candidates) { this.question = question; this.candidates = List.copyOf(candidates); }

    /** 질문을 왼쪽부터 한 번 스캔해 겹치지 않는 인용구만 후보로 만든다. 일반 인용구는 escape 를 해석하지 않고, {@code json:"…"} 만 JSON 문자열로 한 번 디코드한다. */
    public static QRefContext extract(String question) {
        Objects.requireNonNull(question, "question");
        // 인용구 밖의 짝 없는 surrogate 도 요청 결속을 흐리므로 스캔 전에 거부한다.
        if (hasUnpairedSurrogate(question)) throw new Rejected(ErrorCode.PLAN_INVALID);
        List<String> values = new ArrayList<>();
        for (int at = 0; at < question.length(); at++) {
            if (question.charAt(at) != '"') continue;
            boolean jsonLiteral = at >= 5 && question.startsWith("json:", at - 5);
            int start = at++;
            boolean closed = false;
            for (; at < question.length(); at++) {
                char c = question.charAt(at);
                if (jsonLiteral && c == '\\') { at++; continue; }
                if (c == '"') { closed = true; break; }
            }
            if (!closed) throw new Rejected(ErrorCode.PLAN_INVALID);
            String value = jsonLiteral ? decode(question.substring(start, at + 1)) : question.substring(start + 1, at);
            if (value.isEmpty() || hasUnpairedSurrogate(value)) throw new Rejected(ErrorCode.PLAN_INVALID);
            if (value.codePointCount(0, value.length()) > QUERY_MAX_CODEPOINTS) throw new Rejected(ErrorCode.INPUT_BUDGET_EXCEEDED);
            values.add(value);
        }
        return new QRefContext(question, values);
    }

    private static String decode(String literal) {
        final JsonNode node;
        try { node = STRICT.readTree(literal); }
        catch (RuntimeException invalid) { throw new Rejected(ErrorCode.PLAN_INVALID); }
        if (node == null || !node.isString()) throw new Rejected(ErrorCode.PLAN_INVALID);
        return node.asString();
    }

    private static boolean hasUnpairedSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) i++;
            else if (Character.isSurrogate(c)) return true;
        }
        return false;
    }

    /** 출현 순서대로 발급한 참조 ID 목록 {@code ["Q1","Q2",…]}. 원문은 담지 않는다. */
    public List<String> catalog() {
        List<String> catalog = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) catalog.add("Q" + (i + 1));
        return List.copyOf(catalog);
    }

    /** 후보가 없으면 null 만, 있으면 이 요청이 발급한 ID 문자열만 허용한다. */
    public boolean accepts(JsonNode reference) {
        Objects.requireNonNull(reference, "reference");
        if (reference.isNull()) return candidates.isEmpty();
        if (!reference.isString()) return false;
        String ref = reference.asString();
        if (!ref.matches("Q[1-9][0-9]{0,8}")) return false;
        return Integer.parseInt(ref.substring(1)) <= candidates.size();
    }

    /** 검증한 참조의 원문을 그대로 돌려준다(null 참조는 null). 허용되지 않는 참조는 호출자 결함이다. */
    public String resolve(JsonNode reference) {
        if (!accepts(reference)) throw new IllegalArgumentException("이 요청이 발급하지 않은 참조입니다");
        return reference.isNull() ? null : candidates.get(Integer.parseInt(reference.asString().substring(1)) - 1);
    }

    /** 메시지에 실은 질문이 이 바인딩을 만든 질문과 같은지(다른 요청 메시지에 바인딩을 잘못 쓰는 개발 오류 차단). */
    public boolean boundTo(String candidateQuestion) { return question.equals(candidateQuestion); }

    @Override public String toString() { return "QRefContext[request-bound]"; }
}
