package com.nori.ai.gateway.app.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OTLP logs 명시 경로다(P04 확인: Boot OTLP logging 자동구성 모듈은 classpath에 없음).
 * `ai.gateway.otlp.logs.enabled=true`일 때만 등록하며, 그 밖에는 stdout JSON만 남고 OTLP logs 전송은 없다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.gateway.otlp.logs.enabled", havingValue = "true")
public class OtlpLogsConfiguration {
    @Bean(destroyMethod = "close")
    SdkLoggerProvider otlpSdkLoggerProvider(OtlpProperties properties) {
        return SdkLoggerProvider.builder().setResource(ObservabilityConfiguration.resource())
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(OtlpHttpLogRecordExporter.builder().setEndpoint(properties.signal("/v1/logs")).build()).build())
            .build();
    }
    @Bean OtlpLogAppenderInstaller otlpLogAppenderInstaller(OpenTelemetry openTelemetry) { return new OtlpLogAppenderInstaller(openTelemetry); }
}
