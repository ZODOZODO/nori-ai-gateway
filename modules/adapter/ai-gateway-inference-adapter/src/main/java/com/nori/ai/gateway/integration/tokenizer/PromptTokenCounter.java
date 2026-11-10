package com.nori.ai.gateway.integration.tokenizer;

import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.PromptTokenCounting;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 생성 모델 chat template 렌더 결과 전체를 pin tokenizer 로 센다(사전 토큰 계수 core 포트 구현). */
@Component
public class PromptTokenCounter implements PromptTokenCounting {
    private final QwenChatTemplateRenderer renderer;
    private final HfTokenizerCounter tokenizer;

    public PromptTokenCounter(QwenChatTemplateRenderer renderer, HfTokenizerCounter tokenizer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
    }

    @Override public int count(List<ChatMessage> messages) { return tokenizer.countText(renderer.render(messages)); }
}
