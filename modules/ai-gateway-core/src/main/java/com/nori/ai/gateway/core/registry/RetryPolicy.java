package com.nori.ai.gateway.core.registry;

public record RetryPolicy(int maxAttempts, long backoffMs) {
    public RetryPolicy { if (maxAttempts < 1 || backoffMs < 0) throw new IllegalArgumentException("재시도 정책이 잘못되었습니다"); }
}
