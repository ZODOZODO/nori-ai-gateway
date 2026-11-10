package com.nori.ai.gateway.app.observability;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTLP/HTTP 수집 경로 설정이다. logs는 G-OBS-READBACK에서 수신 파이프라인이 확인될 때만 켠다.
 * endpoint는 경로 없는 수집기 주소이며 신호별 경로(`/v1/metrics`·`/v1/traces`·`/v1/logs`)는 이 설정이 붙인다.
 */
@ConfigurationProperties("ai.gateway.otlp")
public record OtlpProperties(URI endpoint, Metrics metrics, Signal traces, Signal logs) {
    public record Metrics(boolean enabled, Duration step) {
        public Metrics { if (step == null || step.isNegative() || step.isZero()) throw new IllegalArgumentException("OTLP 지표 주기가 잘못되었습니다"); }
    }
    public record Signal(boolean enabled) {}
    public OtlpProperties {
        Objects.requireNonNull(metrics, "OTLP 지표 설정이 필요합니다"); Objects.requireNonNull(traces, "OTLP trace 설정이 필요합니다"); Objects.requireNonNull(logs, "OTLP log 설정이 필요합니다");
        if (endpoint == null || !Set.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getRawUserInfo() != null
                || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/")))
            throw new IllegalArgumentException("OTLP 수집 주소가 잘못되었습니다");
    }
    public String signal(String path) { return endpoint.resolve(path).toString(); }
}
