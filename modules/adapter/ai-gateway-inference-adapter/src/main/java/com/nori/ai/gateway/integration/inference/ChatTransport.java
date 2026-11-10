package com.nori.ai.gateway.integration.inference;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import java.util.List;

/**
 * 예산 assert 를 통과한 chat 호출 1회의 전송 계층. 운영 구현은 {@link SpringAiChatTransport} 하나이며,
 * verify 의 LLM_STUB 는 이 계층만 대체한다(Plan P19 N10 — 예산 guard·잘림 감지는 Client 에 남는다).
 */
public interface ChatTransport {
    /** 실패는 원인 예외 그대로 던진다. Client 가 클래스명만 남기고 변환한다. */
    Response send(CallType type, List<ChatMessage> messages, int maxTokens);

    /** 응답이 비었으면 {@code content} 가 null 이다. usage 가 없으면 토큰 수는 null 이다. */
    record Response(String content, String finishReason, Integer promptTokens, Integer completionTokens) {
        @Override public String toString() { return "Response[보호됨]"; }
    }
}
