package com.nori.ai.gateway.integration.loki;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;
import java.time.Duration;

@ConfigurationProperties("ai.gateway.sources.loki")
public record LokiProperties(@DefaultValue("PT6H") Duration splitInterval,
        @Name("per-query-limit") @DefaultValue("500") int limitPerQuery,
        @DefaultValue("1000") int maxLinesTotal, @DefaultValue("PT30S") Duration stepDeadline) {
    public LokiProperties() { this(Duration.ofHours(6), 500, 1000, Duration.ofSeconds(30)); }
    // 기존 무인자 API와 함께 존재하므로 Binder가 값 생성자를 명시적으로 선택해야 합니다.
    @ConstructorBinding
    public LokiProperties {
        if (splitInterval == null || splitInterval.isNegative() || splitInterval.isZero() || limitPerQuery <= 0
                || maxLinesTotal <= 0 || stepDeadline == null || stepDeadline.isNegative() || stepDeadline.isZero())
            throw new IllegalArgumentException("로그 조회 설정이 잘못되었습니다");
    }
}
