package com.nori.ai.gateway.integration.tokenizer;

import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.ChatMessage.Role;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * P03 v4 에서 캡처한 Ollama Go template(qwen3:4b = Qwen3-4B-Thinking-2507, {@link #TEMPLATE_SHA256})이 만드는 렌더 문자열을 재현한다.
 * 검토 근거는 docs/gates/f0/template-review-v4.md 이며 정확성은 P03 golden(서버 prompt_tokens)으로만 확인한다.
 * Ollama collate 는 연속한 같은 역할을 "\n\n" 으로 합치고, system 내용 뒤에 빈 줄을 두며, 생성 접두부가 "&lt;think&gt;\n" 을 연다.
 * 검토 범위 밖(첫 위치가 아닌 system, 마지막이 user 가 아님, Ollama think 필터가 바꿀 수 있는 assistant 내용)은 렌더하지 않는다.
 */
@Component
public class QwenChatTemplateRenderer {
    public static final String TEMPLATE_SHA256 = "2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9";

    public String render(List<ChatMessage> messages) {
        int first = !messages.isEmpty() && messages.getFirst().role() == Role.SYSTEM ? 1 : 0;
        List<Role> roles = new ArrayList<>();
        List<StringBuilder> contents = new ArrayList<>();
        for (ChatMessage message : messages.subList(first, messages.size())) {
            boolean supported = message.role() == Role.USER || message.role() == Role.ASSISTANT && thinkFilterKeeps(message.content());
            if (!supported) throw new IllegalArgumentException("CHAT_TEMPLATE_INPUT_UNSUPPORTED");
            if (!roles.isEmpty() && roles.getLast() == message.role()) contents.getLast().append("\n\n").append(message.content());
            else { roles.add(message.role()); contents.add(new StringBuilder(message.content())); }
        }
        if (roles.isEmpty() || roles.getLast() != Role.USER) throw new IllegalArgumentException("CHAT_TEMPLATE_INPUT_UNSUPPORTED");
        StringBuilder text = new StringBuilder();
        if (first == 1 && !messages.getFirst().content().isEmpty()) text.append("<|im_start|>system\n").append(messages.getFirst().content()).append("\n\n<|im_end|>\n");
        for (int i = 0; i < roles.size(); i++)
            text.append("<|im_start|>").append(roles.get(i) == Role.USER ? "user" : "assistant").append('\n').append(contents.get(i)).append("<|im_end|>\n");
        return text.append("<|im_start|>assistant\n<think>\n").toString();
    }

    /** Ollama 의 qwen3 think 필터는 첫 글자가 공백류도 '&lt;' 도 아닌 비어 있지 않은 assistant 내용만 원문 그대로 둔다. */
    private static boolean thinkFilterKeeps(String content) {
        if (content.isEmpty()) return false;
        int c = content.codePointAt(0);
        return c != '<' && !Character.isWhitespace(c) && !Character.isSpaceChar(c);
    }
}
