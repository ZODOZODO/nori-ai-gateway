package com.nori.ai.gateway.core.budget;

import java.util.List;

/**
 * 생성 모델의 실제 chat template 으로 조립한 최종 입력의 토큰 수를 센다(SPEC §14.5 사전 토큰 계수).
 * 구현이 렌더할 수 없는 메시지 구성은 추정하지 않고 {@link IllegalArgumentException} 으로 거부한다.
 */
public interface PromptTokenCounting {
    int count(List<ChatMessage> messages);
}
