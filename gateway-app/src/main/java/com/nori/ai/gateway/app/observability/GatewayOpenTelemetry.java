package com.nori.ai.gateway.app.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.Objects;
import java.util.Optional;

/**
 * 이 앱의 명시 SDK provider만 노출하는 OpenTelemetry다. 전역 등록·propagation·자동 계측은 없다.
 * (`opentelemetry-sdk` 집계 artifact는 compile classpath에 없어 API 인터페이스로 직접 조합한다.)
 */
public final class GatewayOpenTelemetry implements OpenTelemetry, AutoCloseable {
    private final SdkTracerProvider tracers;
    private final Optional<SdkLoggerProvider> logs;
    public GatewayOpenTelemetry(SdkTracerProvider tracers, Optional<SdkLoggerProvider> logs) {
        this.tracers = Objects.requireNonNull(tracers); this.logs = Objects.requireNonNull(logs);
    }
    @Override public TracerProvider getTracerProvider() { return tracers; }
    @Override public LoggerProvider getLogsBridge() { return logs.<LoggerProvider>map(provider -> provider).orElse(LoggerProvider.noop()); }
    @Override public ContextPropagators getPropagators() { return ContextPropagators.noop(); }
    @Override public void close() { tracers.close(); }
}
