package com.nori.ai.gateway.app.observability;

import com.nori.ai.gateway.app.AiGatewayApplication;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * OTLP metrics·traces 연결이다. javaagent·tracing bridge·표준 HTTP handler는 쓰지 않는다.
 * Boot 4.0.7의 OTLP metrics 자동구성은 `spring-boot-opentelemetry` 모듈 클래스를 조건으로 요구하는데 그 모듈은 runtime에 없으므로,
 * 같은 `micrometer-registry-otlp` API로 registry를 명시 생성한다. 지표는 {@link MetricsCatalog} 목록만 내보낸다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OtlpProperties.class)
@Import(OtlpLogsConfiguration.class)
public class ObservabilityConfiguration {
    static final String SERVICE_NAME = "ai-gateway";

    @Bean MeterFilter metricsCatalogFilter(com.nori.ai.gateway.core.registry.CapabilityRegistry registry) { return MetricsCatalog.filter(registry); }
    @Bean ObservationFilter allowlistObservationFilter() { return new AllowlistObservationFilter(); }
    @Bean AuditRecorder auditRecorder(com.nori.ai.gateway.persistence.audit.AuditWriter writer, io.micrometer.core.instrument.MeterRegistry meters) { return new AuditRecorder(writer, meters); }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "ai.gateway.otlp.metrics.enabled", havingValue = "true")
    OtlpMeterRegistry otlpMeterRegistry(OtlpProperties properties, io.micrometer.core.instrument.Clock clock) {
        String url = properties.signal("/v1/metrics");
        Duration step = properties.metrics().step();
        Map<String,String> resource = resourceAttributes();
        // 환경변수(OTEL_*)의 header·resource가 섞이지 않도록 모든 값을 명시합니다.
        OtlpConfig config = new OtlpConfig() {
            @Override public String get(String key) { return null; }
            @Override public String url() { return url; }
            @Override public Duration step() { return step; }
            @Override public Map<String,String> resourceAttributes() { return resource; }
            @Override public Map<String,String> headers() { return Map.of(); }
        };
        return new OtlpMeterRegistry(config, clock);
    }

    @Bean(destroyMethod = "close")
    GatewayOpenTelemetry openTelemetry(OtlpProperties properties, ObjectProvider<SdkLoggerProvider> logs) {
        // SDK 1.67은 span processor가 0개면 noop tracer(무효 traceId)를 돌려준다. export off여도 SDK context를 만들도록 내보내지 않는 processor를 둔다.
        SpanProcessor processor = properties.traces().enabled()
            ? BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder().setEndpoint(properties.signal("/v1/traces")).build()).build()
            : CONTEXT_ONLY;
        var tracer = SdkTracerProvider.builder().setResource(resource()).addSpanProcessor(processor);
        return new GatewayOpenTelemetry(tracer.build(), java.util.Optional.ofNullable(logs.getIfAvailable()));
    }
    private static final SpanProcessor CONTEXT_ONLY = new SpanProcessor() {
        @Override public void onStart(Context parent, ReadWriteSpan span) {}
        @Override public boolean isStartRequired() { return false; }
        @Override public void onEnd(ReadableSpan span) {}
        @Override public boolean isEndRequired() { return false; }
    };

    /** service.name·service.version만 싣는다. 빌드 버전이 manifest에 없으면 버전을 만들지 않는다. */
    static Resource resource() {
        AttributesBuilder attributes = Attributes.builder();
        resourceAttributes().forEach((key, value) -> attributes.put(AttributeKey.stringKey(key), value));
        return Resource.create(attributes.build());
    }
    private static Map<String,String> resourceAttributes() {
        String version = AiGatewayApplication.class.getPackage().getImplementationVersion();
        return version == null ? Map.of("service.name", SERVICE_NAME) : Map.of("service.name", SERVICE_NAME, "service.version", version);
    }
}
