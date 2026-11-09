package com.nori.ai.gateway.core.budget;

import java.util.Objects;

/** 추론 서버로 보낼 chat 메시지 한 건. 계수·전송은 chat template 렌더 결과를 기준으로 한다. */
public record ChatMessage(Role role, String content) {
    public enum Role { SYSTEM, USER, ASSISTANT }
    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
    }
}
