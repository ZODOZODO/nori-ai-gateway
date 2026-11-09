package com.nori.ai.gateway.integration.tokenizer;

import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.ChatMessage.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** docs/gates/f0/template-review-v4.md 의 렌더 공식(실제 Go 엔진 대조) 그대로의 문자열인지 확인한다. 정확성의 최종 기준은 golden 이다. */
class QwenChatTemplateRendererTest {
    private final QwenChatTemplateRenderer renderer = new QwenChatTemplateRenderer();
    private static ChatMessage m(Role role, String content) { return new ChatMessage(role, content); }

    @Test void rendersSystemUserWithBlankLineAfterSystemAndOpenThinkPrefix() {
        assertThat(renderer.render(List.of(m(Role.SYSTEM, "S"), m(Role.USER, "U"))))
            .isEqualTo("<|im_start|>system\nS\n\n<|im_end|>\n<|im_start|>user\nU<|im_end|>\n<|im_start|>assistant\n<think>\n");
    }

    @Test void omitsMissingOrEmptySystemAndMergesConsecutiveSameRoleMessages() {
        String expected = "<|im_start|>user\nU\n\nR<|im_end|>\n<|im_start|>assistant\n<think>\n";
        assertThat(renderer.render(List.of(m(Role.USER, "U"), m(Role.USER, "R")))).isEqualTo(expected);
        assertThat(renderer.render(List.of(m(Role.SYSTEM, ""), m(Role.USER, "U"), m(Role.USER, "R")))).isEqualTo(expected);
    }

    @Test void rendersPriorAssistantTurnWithoutThinkingBlock() {
        assertThat(renderer.render(List.of(m(Role.SYSTEM, "S"), m(Role.USER, "U1"), m(Role.ASSISTANT, "A1"), m(Role.USER, "U2"))))
            .isEqualTo("<|im_start|>system\nS\n\n<|im_end|>\n<|im_start|>user\nU1<|im_end|>\n<|im_start|>assistant\nA1<|im_end|>\n"
                + "<|im_start|>user\nU2<|im_end|>\n<|im_start|>assistant\n<think>\n");
    }

    @Test void refusesInputsOutsideTheReviewedTemplateRange() {
        for (List<ChatMessage> messages : List.of(
                List.<ChatMessage>of(),
                List.of(m(Role.SYSTEM, "S")),
                List.of(m(Role.USER, "U"), m(Role.SYSTEM, "S"), m(Role.USER, "U2")),
                List.of(m(Role.USER, "U"), m(Role.ASSISTANT, "A")),
                List.of(m(Role.USER, "U"), m(Role.ASSISTANT, "<think>x"), m(Role.USER, "U2")),
                List.of(m(Role.USER, "U"), m(Role.ASSISTANT, " A"), m(Role.USER, "U2")),
                List.of(m(Role.USER, "U"), m(Role.ASSISTANT, ""), m(Role.USER, "U2"))))
            assertThatThrownBy(() -> renderer.render(messages)).as(messages.toString()).hasMessage("CHAT_TEMPLATE_INPUT_UNSUPPORTED");
    }
}
