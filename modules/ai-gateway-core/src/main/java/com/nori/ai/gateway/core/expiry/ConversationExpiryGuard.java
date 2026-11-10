package com.nori.ai.gateway.core.expiry;

import java.time.Instant;
import java.util.Objects;

public final class ConversationExpiryGuard {
    public void assertLive(Instant expiresAt, Instant now) {
        if (!Objects.requireNonNull(now).isBefore(Objects.requireNonNull(expiresAt))) throw new ConversationExpired();
    }
    public static final class ConversationExpired extends RuntimeException {
        public ConversationExpired() { super("대화가 만료되었습니다"); }
    }
}
