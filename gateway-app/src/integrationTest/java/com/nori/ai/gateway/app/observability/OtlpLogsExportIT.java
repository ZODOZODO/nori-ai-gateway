package com.nori.ai.gateway.app.observability;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.core.audit.AuditEvent;
import com.nori.ai.gateway.core.scope.ActorRef;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.*;

/**
 * logs=true일 때만 만들어지는 전용 OTel appender → 명시 SDK → OTLP/HTTP `/v1/logs` 경로와 stdout encoder를 인프로세스 수신기·출력 캡처로 확인한다.
 * 제품 logger의 고정 제품 코드 5개만 나가고, 형식만 맞는 대문자 sentinel·비제품 logger·닫힌 값 밖 MDC·인자·예외는 나가지 않는다.
 */
@Tag("stage-F1") @Tag("label-MOCK_API")
@ExtendWith(OutputCaptureExtension.class)
class OtlpLogsExportIT extends StackContextSupport {
    private static final Set<String> CODES = Set.of("RESTORE_PATH_REQUIRED", "RETENTION_PROBE_FAILED", "RETENTION_PURGE_FAILED", "EXPIRY_CANCEL_FAILED", "AUDIT_WRITE_FAILED");
    private static final Set<String> MDC_KEYS = Set.of("requestId", "conversationId", "jobId", "traceId", "ownerSystem", "status", "errorCode", "decision");
    private static final Map<String,String> PRODUCT_LOGGERS = Map.of("RESTORE_PATH_REQUIRED", "com.nori.ai.gateway.app.job.recovery.ReadinessGate",
        "RETENTION_PROBE_FAILED", "com.nori.ai.gateway.app.ops.SourceRetentionProbeScheduler", "RETENTION_PURGE_FAILED", "com.nori.ai.gateway.app.ops.RetentionPurgeJob",
        "EXPIRY_CANCEL_FAILED", "com.nori.ai.gateway.app.ops.ExpiryEdgeJobCanceller");
    private OtlpCaptureServer receiver;
    private boolean logsEnabled;

    @Override protected Map<String,Object> extraApplicationProperties() {
        return Map.of("ai.gateway.otlp.endpoint", receiver.endpoint(), "ai.gateway.otlp.logs.enabled", logsEnabled,
            "ai.gateway.otlp.metrics.enabled", false, "ai.gateway.otlp.traces.enabled", false);
    }
    @AfterEach void stopReceiver() { if (receiver != null) receiver.close(); }

    private static List<OpenTelemetryAppender> otelAppenders() {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var found = new ArrayList<OpenTelemetryAppender>();
        root.iteratorForAppenders().forEachRemaining(appender -> { if (appender instanceof OpenTelemetryAppender otel) found.add(otel); });
        return found;
    }
    /** 제품 4개 코드는 실제 제품 코드와 같은 System.Logger 경로·logger 이름으로, AUDIT_WRITE_FAILED는 실제 AuditRecorder 실패로 남긴다. */
    private void emitProductCodes(String jobId) {
        MDC.put("jobId", jobId); MDC.put("status", "SUCCEEDED"); MDC.put("decision", "EXPIRY_EDGE");
        try {
            PRODUCT_LOGGERS.forEach((code, logger) -> System.getLogger(logger).log(System.Logger.Level.WARNING, code));
            var now = context.getBean(Clock.class).instant();
            var scope = new VerifiedSystemScope(OwnerSystem.TC, "service:TC", new ActorRef("actor"), "actor", Set.of(), "a".repeat(64), now.minusSeconds(1), now.plusSeconds(60), UUID.randomUUID().toString());
            assertThatThrownBy(() -> context.getBean(AuditRecorder.class).write(AuditEvent.apiRequest(scope, "0".repeat(32)))).isInstanceOf(IllegalArgumentException.class);
        } finally { MDC.clear(); }
    }
    private static void emitRejected(String jobId) {
        var product = LoggerFactory.getLogger("com.nori.ai.gateway.app.ops.RetentionPurgeJob");
        product.warn("SENTINEL_SECRET");
        LoggerFactory.getLogger("org.example.thirdparty.Client").warn("RETENTION_PURGE_FAILED");
        try {
            MDC.put("jobId", jobId); MDC.put("errorCode", "SENTINEL_SECRET"); product.warn("RETENTION_PURGE_FAILED");
            MDC.clear(); MDC.put("actorRef", "actor-SENTINEL"); product.warn("RETENTION_PURGE_FAILED");
            MDC.clear(); MDC.put("question", "질문 SENTINEL:question-raw"); product.warn("RETENTION_PURGE_FAILED");
        } finally { MDC.clear(); }
        product.warn("SELECT * FROM gw.job WHERE q = 'SENTINEL:sql-raw'");
        product.warn("RETENTION_PURGE_FAILED {}", "http://loki.monitoring.svc:3100/loki/api/v1/query_range?query=SENTINEL:url-raw");
        product.warn("RETENTION_PURGE_FAILED", new IllegalStateException("SENTINEL:exception-raw"));
    }
    private List<Map<String,Object>> records(List<String> resourceKeys) {
        var records = new ArrayList<Map<String,Object>>();
        for (byte[] body : receiver.bodies("/v1/logs")) {
            var request = OtlpCaptureServer.parse("io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest", body);
            for (var resource : OtlpCaptureServer.list(request, "resource_logs")) {
                resourceKeys.addAll(OtlpCaptureServer.attributes(OtlpCaptureServer.child(resource, "resource")).keySet());
                for (var scope : OtlpCaptureServer.list(resource, "scope_logs")) records.addAll(OtlpCaptureServer.list(scope, "log_records"));
            }
        }
        return records;
    }
    private static String body(Map<String,Object> record) { return String.valueOf(OtlpCaptureServer.child(record, "body").getOrDefault("string_value", "")); }

    @Test void onlyTheFiveProductCodesReachStdoutAndOtlpAndNothingIsSentBeforeInstallOrAfterClose(CapturedOutput output) throws Exception {
        receiver = new OtlpCaptureServer(); logsEnabled = true;
        String beforeInstall = UUID.randomUUID().toString(), installed = UUID.randomUUID().toString(), afterClose = UUID.randomUUID().toString();
        MDC.put("jobId", beforeInstall); LoggerFactory.getLogger("com.nori.ai.gateway.app.ops.RetentionPurgeJob").warn("RETENTION_PURGE_FAILED"); MDC.clear();
        start();
        assertThat(context.getBeansOfType(OtlpLogAppenderInstaller.class)).hasSize(1);
        assertThat(otelAppenders()).singleElement().satisfies(appender -> {
            assertThat(appender.isStarted()).isTrue();
            assertThat(appender.getCopyOfAttachedFiltersList()).singleElement().isInstanceOf(AllowlistLogFilter.class);
        });
        emitProductCodes(installed);
        emitRejected(installed);
        // 컨텍스트 종료가 자기 appender를 떼고 BatchLogRecordProcessor를 flush합니다.
        context.close(); context = null;
        assertThat(otelAppenders()).isEmpty();
        MDC.put("jobId", afterClose); LoggerFactory.getLogger("com.nori.ai.gateway.app.ops.RetentionPurgeJob").warn("RETENTION_PURGE_FAILED"); MDC.clear();

        var resourceKeys = new ArrayList<String>();
        var records = records(resourceKeys);
        assertThat(records.stream().map(OtlpLogsExportIT::body).toList()).containsExactlyInAnyOrderElementsOf(CODES);
        assertThat(records).allSatisfy(record -> {
            assertThat(OtlpCaptureServer.attributes(record).keySet()).isSubsetOf(MDC_KEYS);
            assertThat(OtlpCaptureServer.attributes(record)).containsEntry("jobId", installed);
        });
        assertThat(Set.copyOf(resourceKeys)).isSubsetOf(Set.of("service.name", "service.version")).contains("service.name");
        assertThat(receiver.allBytesAsText()).doesNotContain("SENTINEL", "SELECT ", "query_range", "질문", beforeInstall, afterClose, "thirdparty");
        for (String code : CODES) assertThat(output.getAll()).contains("\"message\":\"" + code + "\"");
        assertThat(output.getAll()).contains("\"message\":\"UNLISTED_MESSAGE\"").doesNotContain("SENTINEL", "SELECT ", "query_range", "질문");
    }

    @Test void disabledLogsCreateNoAppenderAndSendNothing() throws Exception {
        receiver = new OtlpCaptureServer(); logsEnabled = false;
        start();
        assertThat(context.getBeansOfType(OtlpLogAppenderInstaller.class)).isEmpty();
        assertThat(otelAppenders()).isEmpty();
        emitProductCodes(UUID.randomUUID().toString());
        context.close(); context = null;
        assertThat(receiver.requests()).isEmpty();
    }

    @Test void eachContextRemovesOnlyItsOwnAppender() throws Exception {
        receiver = new OtlpCaptureServer(); logsEnabled = true;
        start();
        var first = context; var firstAppender = otelAppenders().getFirst();
        context = null;
        startApplication();
        var second = context;
        assertThat(otelAppenders()).hasSize(2).contains(firstAppender);
        first.close();
        assertThat(otelAppenders()).singleElement().satisfies(remaining -> { assertThat(remaining).isNotSameAs(firstAppender); assertThat(remaining.isStarted()).isTrue(); });
        assertThat(firstAppender.isStarted()).isFalse();
        second.close(); context = null;
        assertThat(otelAppenders()).isEmpty();
    }
}
