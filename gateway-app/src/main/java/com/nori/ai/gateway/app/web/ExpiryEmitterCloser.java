package com.nori.ai.gateway.app.web;

import java.time.*;
import java.util.concurrent.*;

/** 예약 지연과 무관하게 실제 쓰기는 GuardedSseWriter의 동기 guard를 통과합니다. */
public final class ExpiryEmitterCloser implements AutoCloseable {
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("sse-expiry").factory());
    private final Clock clock;
    public ExpiryEmitterCloser(Clock clock) { this.clock=clock; }
    public ScheduledFuture<?> schedule(Instant expiresAt,GuardedSseWriter writer) {
        return scheduler.schedule(writer::complete,Math.max(0,Duration.between(clock.instant(),expiresAt).toMillis()),TimeUnit.MILLISECONDS);
    }
    @Override public void close() { scheduler.shutdownNow(); }
}
