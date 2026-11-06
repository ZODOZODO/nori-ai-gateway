package com.nori.ai.gateway.app.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.Objects;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * `ai.gateway.otlp.logs.enabled=true`일 때만 이 컨텍스트 전용 OTel appender 하나를 root logger에 연결한다.
 * SDK를 start 전에 instance setter로 넣어 연결 전 보관 이벤트가 없고, stop은 자기 appender만 떼어 낸다.
 */
public final class OtlpLogAppenderInstaller implements SmartLifecycle {
    static final String APPENDER_NAME = "AIGW_OTLP_LOGS";
    private final OpenTelemetry openTelemetry;
    private OpenTelemetryAppender appender;

    public OtlpLogAppenderInstaller(OpenTelemetry openTelemetry) { this.openTelemetry = Objects.requireNonNull(openTelemetry); }

    @Override public synchronized void start() {
        if (appender != null) return;
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var created = new OpenTelemetryAppender();
        created.setName(APPENDER_NAME);
        created.setContext(context);
        // 2.33.1-alpha start()는 numLogsCapturedBeforeOtelInstall로 보관 queue를 다시 만들므로 그 값은 건드리지 않고,
        // start 전에 SDK를 넣어 append가 보관 없이 바로 emit하게 합니다.
        created.setOpenTelemetry(openTelemetry);
        created.setCaptureExperimentalAttributes(false);
        created.setCaptureCodeAttributes(false);
        created.setCaptureMarkerAttribute(false);
        created.setCaptureKeyValuePairAttributes(false);
        created.setCaptureLoggerContext(false);
        created.setCaptureTemplate(false);
        created.setCaptureArguments(false);
        created.setCaptureLogstashMarkerAttributes(false);
        created.setCaptureLogstashStructuredArguments(false);
        created.setCaptureMdcAttributes(AllowlistLogFilter.MDC_KEYS);
        var filter = new AllowlistLogFilter();
        filter.setContext(context);
        filter.start();
        created.addFilter(filter);
        created.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(created);
        appender = created;
    }
    @Override public synchronized void stop() {
        if (appender == null) return;
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        appender.stop();
        appender = null;
    }
    @Override public synchronized boolean isRunning() { return appender != null; }
}
