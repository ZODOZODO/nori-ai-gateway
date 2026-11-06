package com.nori.ai.gateway.core.retention;

import java.time.Instant;

public record TimeWindow(Instant startAt, Instant endAt) {
    public TimeWindow {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt))
            throw new IllegalArgumentException("조회 시간 범위가 잘못되었습니다");
    }
}
