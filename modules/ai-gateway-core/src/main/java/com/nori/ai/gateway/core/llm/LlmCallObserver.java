package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import java.util.List;

/**
 * 분류·Composer 호출 1회(재시도 포함)를 감싸는 관찰 지점. 조립을 마친 메시지(PromptReducer 뒤·PreCallBudgetAssert 직전)를 받는다.
 * 운영은 {@link #NONE} 이고 verify 시험 hook 만 구현한다(Plan P20 PromptWatchHook·ScriptedChatTransport 결속).
 */
@FunctionalInterface
public interface LlmCallObserver {
    LlmCallObserver NONE = (jobId, type, messages) -> () -> {};

    /** 반환한 범위는 그 호출이 끝나면 닫는다. */
    Scope open(String jobId, CallType type, List<ChatMessage> messages);

    interface Scope extends AutoCloseable { @Override void close(); }
}
