package com.nori.ai.gateway.core.expiry;

import com.nori.ai.gateway.core.job.CancellationToken;
import java.time.*;

/** 후속 실행기가 외부 호출과 캐시 경계 바로 전에 사용하는 동기 차단 지점입니다. */
public final class ExpiryCheckpoint {
    private final ConversationExpiryGuard guard;
    private final Clock clock;
    public ExpiryCheckpoint(ConversationExpiryGuard guard, Clock clock) { this.guard=guard; this.clock=clock; }
    public void assertLive(Instant expiresAt) { guard.assertLive(expiresAt,clock.instant()); }
    public void assertLive(Instant expiresAt, CancellationToken token) {
        try { assertLive(expiresAt); }
        catch (ConversationExpiryGuard.ConversationExpired expired) { token.signal(CancellationToken.Reason.EXPIRED); throw expired; }
        if (token.reason().isPresent()) throw new IllegalStateException("작업이 취소되었습니다");
    }
}
