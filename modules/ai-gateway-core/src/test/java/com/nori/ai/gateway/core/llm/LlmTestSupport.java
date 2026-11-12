package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.port.InferenceClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/** core.llm 단위 시험 대역. 전송 포트는 응답 순서만 고정하고 받은 메시지를 그대로 기록한다(예산 assert 는 P19 Client 시험 소관). */
final class LlmTestSupport {
    static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private LlmTestSupport() {}

    static final class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    record Sent(CallType type, List<ChatMessage> messages, int maxTokens) {}

    static final class ScriptedClient implements InferenceClient {
        final LinkedList<ChatOutcome> replies;
        final List<Sent> sent = new ArrayList<>();
        Consumer<Sent> onSend = sent -> {};
        ScriptedClient(ChatOutcome... replies) { this.replies = new LinkedList<>(List.of(replies)); }
        @Override public ChatOutcome chat(CallType type, List<ChatMessage> messages, int maxTokens) {
            var call = new Sent(type, List.copyOf(messages), maxTokens);
            sent.add(call); onSend.accept(call);
            if (replies.isEmpty()) throw new IllegalStateException("시험 응답이 소진되었습니다");
            return replies.removeFirst();
        }
        @Override public List<float[]> embed(List<String> inputs) { throw new UnsupportedOperationException("임베딩은 이 시험 대상이 아닙니다"); }
    }

    static InferenceClient.Completed reply(String content) { return new InferenceClient.Completed(content, "stop", 100, 50, Duration.ofMillis(5)); }
    static InferenceClient.Completed reply(String content, String finish) { return new InferenceClient.Completed(content, finish, 100, 50, Duration.ofMillis(5)); }

    /** 코드포인트 수로 세는 대역. 실제 pin tokenizer 와 같이 짝 없는 surrogate 는 셀 수 없어 거부한다. */
    static final AnswerTokenCounting CODEPOINTS = text -> {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) i++;
            else if (Character.isSurrogate(c)) throw new IllegalArgumentException("TOKENIZER_INPUT_UNPAIRED_SURROGATE");
        }
        return text.codePointCount(0, text.length());
    };

    static ExpiryCheckpoint expiry(Clock clock) { return new ExpiryCheckpoint(new ConversationExpiryGuard(), clock); }
}
