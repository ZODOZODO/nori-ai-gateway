package com.nori.ai.gateway.core.llm;

/**
 * 답변(message.content) 토큰 수 — SPEC §14.5 방법 (b) {@code LOCAL_TOKENIZER_PIN_V1}(pin tokenizer, add_special_tokens=false).
 * 셀 수 없는 입력(짝 없는 surrogate 등)은 {@link IllegalArgumentException} 으로 거부하며 호출자는 그 시도를 실패로 본다.
 */
@FunctionalInterface
public interface AnswerTokenCounting {
    int count(String answer);
}
