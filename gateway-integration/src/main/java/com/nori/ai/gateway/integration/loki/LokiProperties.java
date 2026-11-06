package com.nori.ai.gateway.integration.loki;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("ai.gateway.sources.loki")
public record LokiProperties(Duration splitInterval, int limitPerQuery, int maxLinesTotal, Duration stepDeadline) {
    public LokiProperties() { this(Duration.ofHours(6), 500, 1000, Duration.ofSeconds(30)); }
    public LokiProperties {
        if (splitInterval == null || splitInterval.isNegative() || splitInterval.isZero() || limitPerQuery <= 0
                || maxLinesTotal <= 0 || stepDeadline == null || stepDeadline.isNegative() || stepDeadline.isZero())
            throw new IllegalArgumentException("로그 조회 설정이 잘못되었습니다");
    }
}
