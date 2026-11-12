package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.PromptTokenCounting;
import com.nori.ai.gateway.integration.inference.ChatTransport;
import java.util.List;
import java.util.Objects;

/**
 * verify 전용 {@link ChatTransport}(Plan P20 N10, 라벨 LLM_STUB — 기존 ScriptedInferenceClient 대체). 무장이 결속된 Job 의 호출만
 * 순번대로 고정 응답을 돌려주고, 결속·무장이 없는 Job 은 운영 전송({@code liveChatTransport})으로 위임한다. Client 의 예산 assert·잘림 감지는
 * 이 계층 앞뒤에 그대로 남는다. 고정 응답의 usage.promptTokens 가 없으면 실제 Gateway 계수(같은 계수기)를 쓴다.
 */
public final class ScriptedChatTransport implements ChatTransport {
    private final ChatTransport live;
    private final LlmScriptRegistry scripts;
    private final PromptTokenCounting counter;

    public ScriptedChatTransport(ChatTransport live, LlmScriptRegistry scripts, PromptTokenCounting counter) {
        this.live = Objects.requireNonNull(live, "live");
        if (live instanceof ScriptedChatTransport) throw new IllegalArgumentException("고정 응답 전송은 운영 전송에만 위임합니다");
        this.scripts = Objects.requireNonNull(scripts, "scripts");
        this.counter = Objects.requireNonNull(counter, "counter");
    }

    @Override public Response send(CallType type, List<ChatMessage> messages, int maxTokens) {
        var reply = scripts.next(type);
        if (reply.isEmpty()) return live.send(type, messages, maxTokens);
        var value = reply.get();
        int promptTokens = value.promptTokens() == null ? counter.count(messages) : value.promptTokens();
        return new Response(value.content(), value.finishReason(), promptTokens, value.completionTokens());
    }

    public ChatTransport delegate() { return live; }
}
