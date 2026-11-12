package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.budget.PromptTokenCounting;
import com.nori.ai.gateway.integration.inference.ChatTransport;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/** LLM_STUB 전송·Prompt 관찰 hook(Plan P20 N10). 조건 = verify AND test-hooks.enabled AND inference.mode=enabled. */
@Configuration(proxyBeanMethods = false)
@Profile("verify")
@ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "true")
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
@Import(PromptWatchHook.class)
public class LlmScriptHooksConfiguration {
    @Bean LlmScriptRegistry llmScriptRegistry(TestHookRegistry hooks, Clock clock) { return new LlmScriptRegistry(hooks, clock); }

    /** P19 의 운영 전송 빈만 위임 대상으로 받는다(자기 자신 주입 0). */
    @Bean @Primary ScriptedChatTransport scriptedChatTransport(@Qualifier("liveChatTransport") ChatTransport live, LlmScriptRegistry scripts, PromptTokenCounting counter) {
        return new ScriptedChatTransport(live, scripts, counter);
    }
}
