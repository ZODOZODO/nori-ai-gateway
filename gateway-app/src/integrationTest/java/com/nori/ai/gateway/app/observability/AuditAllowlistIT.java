package com.nori.ai.gateway.app.observability;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.app.config.SourceConnectorConfiguration;
import com.nori.ai.gateway.core.audit.AuditEvent;
import com.nori.ai.gateway.core.audit.AuditEventType;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.ActorRef;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.integration.loki.SourceHttpClient;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.assertj.core.api.Assertions.*;

/**
 * AC-24-AUDIT-ALLOW 구성 요소의 로컬 확인이다(배포 판정은 F4·G-OBS-READBACK). 실제 인증 요청의 감사 생산자·감사 기록기와 실제 metrics registry·
 * 인입 요청 span·직접 Source span handler의 OTLP export를 인프로세스 수신기로 받아 허용 값만 나가고 민감 값은 나가지 않는지,
 * 비활성 설정에서는 전송이 없어도 SDK trace가 유효한지 본다.
 */
@Tag("stage-F1") @Tag("label-MOCK_API")
@ExtendWith(OutputCaptureExtension.class)
class AuditAllowlistIT extends StackContextSupport {
    private static final Set<String> SOURCE_TAGS = Set.of("source_id", "method", "status_class");
    private OtlpCaptureServer receiver;
    private HttpServer source;
    private final AtomicInteger sourceRequests = new AtomicInteger();
    private boolean exportEnabled;

    @Override protected WebApplicationType applicationType() { return WebApplicationType.SERVLET; }
    @Override protected Map<String,Object> extraApplicationProperties() {
        return Map.of("ai.gateway.otlp.endpoint", receiver.endpoint(), "ai.gateway.otlp.metrics.enabled", exportEnabled, "ai.gateway.otlp.metrics.step", "PT1S",
            "ai.gateway.otlp.traces.enabled", exportEnabled, "ai.gateway.otlp.logs.enabled", false);
    }
    @AfterEach void stopServers() { if (receiver != null) receiver.close(); if (source != null) source.stop(0); }

    private void prepare(boolean enabled) throws Exception {
        receiver = new OtlpCaptureServer(); exportEnabled = enabled;
        source = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/", exchange -> {
            try (exchange) {
                sourceRequests.incrementAndGet();
                byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            }
        });
        source.start();
        start();
    }
    /** 실제 인입 filter를 지나는 정상 인증 요청 한 건이다. 원격 traceparent와 민감 header·body를 함께 싣는다. */
    private HttpResponse<String> authenticatedRequest() throws Exception {
        var claims = new HashMap<String,Object>(Map.of("iss", "tc-ui-backend", "aud", "ai-gateway", "sys", "TC", "sub", "actor", "scope", "READ", "perm", List.of("EQP_READ"), "pdg", "a".repeat(64)));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort() + "/v1/conversations"))
            .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + serviceToken).header("X-Actor-Assertion", signer.sign(claims, context.getBean(Clock.class).instant()))
            .header("Content-Type", "application/json").header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").header("X-Debug", "SENTINEL:header-raw")
            .POST(HttpRequest.BodyPublishers.ofString("{\"note\":\"SENTINEL:body-raw\"}")).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    private Map<String,Object> apiRequestRow() {
        return context.getBean(JdbcClient.class).sql("SELECT request_id, trace_id, service_ref, owner_system, actor_ref FROM gw.audit_event WHERE event_type='API_REQUEST'").query().singleRow();
    }
    /** 같은 운영 factory로 만든 Source 관측·전송기로 URL 경로·query에 sentinel을 실은 실제 HTTP 요청을 보낸다. */
    private void sourceRequestWithSentinelInUrl() {
        var observability = new SourceConnectorConfiguration.FactoryConfiguration().sourceObservability(context.getBean(MeterRegistry.class), context.getBean(OpenTelemetry.class));
        var http = new SourceHttpClient(URI.create("http://127.0.0.1:" + source.getAddress().getPort()), "loki", observability);
        http.json(http.uri(List.of("loki", "api", "v1", "SENTINEL:url-path"), Map.of("query", "{app=\"tc-gateway\"} |= \"SENTINEL:query-raw\"")), null, http.deadline(Duration.ofSeconds(5)));
        assertThat(sourceRequests).hasValue(1);
    }

    @Test @Tag("AC-24-AUDIT-ALLOW")
    void requestAuditMetricsAndSpansExportOnlyAllowedValuesAndRejectedValuesAreCountedNotStored(CapturedOutput output) throws Exception {
        prepare(true);
        assertThat(authenticatedRequest().statusCode()).isEqualTo(201);
        var row = apiRequestRow();
        assertThat((String) row.get("trace_id")).matches("[0-9a-f]{32}").isNotEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(row).containsEntry("service_ref", "service:TC").containsEntry("owner_system", "TC").containsEntry("actor_ref", "actor");

        // 허용 목록 밖 값은 저장 전에 거부되고 기록기의 무라벨 counter·고정 코드로만 관측됩니다.
        var recorder = context.getBean(AuditRecorder.class);
        var failures = context.getBean(MeterRegistry.class).get(MetricsCatalog.AUDIT_WRITE_FAILURES).counter();
        double before = failures.count();
        var now = context.getBean(Clock.class).instant();
        var scope = new VerifiedSystemScope(OwnerSystem.TC, "service:TC", new ActorRef("actor"), "actor", Set.of(), "a".repeat(64), now.minusSeconds(1), now.plusSeconds(60), UUID.randomUUID().toString());
        String conversation = UUID.randomUUID().toString(), job = UUID.randomUUID().toString();
        assertThatThrownBy(() -> recorder.write(new AuditEvent(scope, AuditEventType.AUTH_REJECTED, null, null, null, null, null, null, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.writeForJob(AuditEvent.toolCall(scope, conversation, job, "tc.SENTINEL.audit", null, ResultStatus.SUCCESS, null, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recorder.writeForJob(AuditEvent.toolCall(scope, conversation, job, "tc.logs.search", "sources://TC/logs/SENTINEL-audit-ref@example.com", ResultStatus.SUCCESS, null, 1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(failures.count()).isEqualTo(before + 3);
        var jdbc = context.getBean(JdbcClient.class);
        assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isOne();
        assertThat(jdbc.sql("SELECT string_agg(a::text, ' ') FROM gw.audit_event a").query(String.class).single()).doesNotContain("SENTINEL", "example.com", "4bf92f3577b34da6a3ce929d0e0e4736");

        assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(OtlpMeterRegistry.class);
        sourceRequestWithSentinelInUrl();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while ((sourceMetricAttributes().isEmpty() || spans("aigw.source.request").isEmpty() || spans("aigw.request").isEmpty() || !metricNames().contains(MetricsCatalog.AUDIT_WRITE_FAILURES))
            && System.nanoTime() < deadline) Thread.sleep(200);
        context.close(); context = null;
        assertThat(sourceMetricAttributes()).isNotEmpty().allSatisfy(attributes -> assertThat(attributes).isEqualTo(Map.of("source_id", "loki", "method", "GET", "status_class", "2xx")));
        assertThat(spans("aigw.source.request")).isNotEmpty().allSatisfy(span -> assertThat(OtlpCaptureServer.attributes(span).keySet()).isSubsetOf(SOURCE_TAGS).contains("source_id"));
        // 인입 span은 원격 부모 없이 Gateway SDK trace이며 속성이 없습니다.
        assertThat(spans("aigw.request")).singleElement().satisfies(span -> {
            assertThat(OtlpCaptureServer.attributes(span)).isEmpty();
            assertThat(span.get("trace_id")).isEqualTo(row.get("trace_id"));
            assertThat(span).doesNotContainKey("parent_span_id");
        });
        assertThat(metricNames()).contains(MetricsCatalog.AUDIT_WRITE_FAILURES).allSatisfy(name -> assertThat(MetricsCatalog.specs().containsKey(name) || name.startsWith("hikaricp.")).as(name).isTrue());
        assertThat(receiver.allBytesAsText()).doesNotContain("SENTINEL", "loki/api", "127.0.0.1", "query", "/v1/conversations", "4bf92f3577b34da6a3ce929d0e0e4736", "example.com");
        assertThat(output.getAll()).contains("\"message\":\"AUDIT_WRITE_FAILED\"").doesNotContain("SENTINEL");
    }

    @Test void disabledMetricAndTraceExportSendNothingButTheRequestStillGetsAValidSdkTrace() throws Exception {
        prepare(false);
        assertThat(context.getBean(MeterRegistry.class)).isNotInstanceOf(OtlpMeterRegistry.class);
        assertThat(authenticatedRequest().statusCode()).isEqualTo(201);
        assertThat((String) apiRequestRow().get("trace_id")).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32)).isNotEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        sourceRequestWithSentinelInUrl();
        Thread.sleep(2500);
        context.close(); context = null;
        assertThat(receiver.requests()).isEmpty();
    }

    private List<Map<String,Object>> metrics() {
        var metrics = new ArrayList<Map<String,Object>>();
        for (byte[] body : receiver.bodies("/v1/metrics"))
            for (var resource : OtlpCaptureServer.list(OtlpCaptureServer.parse("io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest", body), "resource_metrics"))
                for (var scope : OtlpCaptureServer.list(resource, "scope_metrics")) metrics.addAll(OtlpCaptureServer.list(scope, "metrics"));
        return metrics;
    }
    private List<String> metricNames() { return metrics().stream().map(metric -> (String) metric.get("name")).toList(); }
    private List<Map<String,String>> sourceMetricAttributes() {
        var values = new ArrayList<Map<String,String>>();
        for (var metric : metrics()) {
            if (!MetricsCatalog.SOURCE_REQUESTS.equals(metric.get("name"))) continue;
            for (String kind : List.of("histogram", "sum", "gauge"))
                for (var point : OtlpCaptureServer.list(OtlpCaptureServer.child(metric, kind), "data_points")) values.add(OtlpCaptureServer.attributes(point));
        }
        return values;
    }
    private List<Map<String,Object>> spans(String name) {
        var values = new ArrayList<Map<String,Object>>();
        for (byte[] body : receiver.bodies("/v1/traces"))
            for (var resource : OtlpCaptureServer.list(OtlpCaptureServer.parse("io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest", body), "resource_spans"))
                for (var scope : OtlpCaptureServer.list(resource, "scope_spans"))
                    for (var span : OtlpCaptureServer.list(scope, "spans")) {
                        assertThat(span.get("name")).isIn("aigw.source.request", "aigw.request");
                        if (name.equals(span.get("name"))) values.add(span);
                    }
        return values;
    }
}
