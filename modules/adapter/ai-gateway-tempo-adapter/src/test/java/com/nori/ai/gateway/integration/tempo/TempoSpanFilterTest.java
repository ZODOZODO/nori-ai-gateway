package com.nori.ai.gateway.integration.tempo;

import com.nori.ai.gateway.integration.source.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.ConnectorTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

class TempoSpanFilterTest {
    private static final String TRACE_ID = "a".repeat(32);
    private static String batch(String service, String name) {
        return batch(service, name, SourceHttpClient.nanos(NOW.minusSeconds(1)), SourceHttpClient.nanos(NOW));
    }
    private static String batch(String service, String name, String start, String end) {
        return "{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"" + service + "\"}},{\"key\":\"service.version\",\"value\":{\"stringValue\":\"123\"}}]},"
            + "\"scopeSpans\":[{\"spans\":[{\"spanId\":\"a123\",\"name\":\"" + name + "\",\"startTimeUnixNano\":\"" + start + "\",\"endTimeUnixNano\":\"" + end + "\"}]}]}";
    }
    @Test void crossServiceTraceRemovesForeignSpansAndKeepsOnlyTcServiceVersion() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(200, "{\"batches\":[" + batch("tc-gateway", "허용") + "," + batch("wm-ui-backend", "비공개") + "," + batch("", "알수없음") + "]}");
            var result = new TempoTraceConnector(stub.http("tempo"), access()).getTrace(request(TempoTraceConnector.SOURCE_REF), TRACE_ID);
            assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS); assertThat(result.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
            assertThat(result.traces()).singleElement().satisfies(trace -> assertThat(trace.spans()).singleElement().satisfies(span -> {
                assertThat(span.service()).isEqualTo("tc-gateway"); assertThat(span.name()).isEqualTo("허용"); assertThat(span.serviceVersion()).isEqualTo("123");
            }));
        }
    }
    @Test void searchOnlyUsesApprovedExactServiceAndSanitizesEveryReturnedTrace() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(200, req.path().equals("/api/search") ? "{\"traces\":[{\"traceID\":\"" + TRACE_ID + "\",\"rootServiceName\":\"wm-ui-backend\"}]}"
                : "{\"resourceSpans\":[" + batch("tc-business", "조회") + "," + batch("wm-ui-front", "비공개") + "]}");
            var connector = new TempoTraceConnector(stub.http("tempo"), access());
            var result = connector.search(request(TempoTraceConnector.SOURCE_REF), "tc-business", new TimeWindow(NOW.minusSeconds(60), NOW));
            assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS); assertThat(stub.requests).hasSize(2);
            assertThat(stub.requests.getFirst().query()).containsEntry("q", "{resource.service.name=\"tc-business\"}");
            assertThat(result.traces().getFirst().spans()).extracting(TempoTraceConnector.Span::service).containsExactly("tc-business");
        }
    }
    @Test void foreignServiceTraceInjectionOrForeignSourceCannotStartARequest() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            var connector = new TempoTraceConnector(stub.http("tempo"), access());
            for (String service : List.of("wm-ui-backend", "tc-gateway\"} || true"))
                assertThat(connector.search(request(TempoTraceConnector.SOURCE_REF), service, new TimeWindow(NOW.minusSeconds(1), NOW)).status()).isEqualTo(ResultStatus.DENIED);
            assertThat(connector.getTrace(request(TempoTraceConnector.SOURCE_REF), "../secret").status()).isEqualTo(ResultStatus.DENIED);
            assertThat(connector.getTrace(request("sources://WM/trace"), TRACE_ID).status()).isEqualTo(ResultStatus.DENIED);
            assertThat(stub.requests).isEmpty();
        }
    }
    @Test void emptyFilteredTraceIsUnverifiedAndBackendErrorIsUnavailable() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            var connector = new TempoTraceConnector(stub.http("tempo"), access());
            stub.responder = req -> new Response(200, "{\"batches\":[" + batch("wm-ui-backend", "비공개") + "]}");
            assertThat(connector.getTrace(request(TempoTraceConnector.SOURCE_REF), TRACE_ID).status()).isEqualTo(ResultStatus.EMPTY);
            assertThat(connector.getTrace(request(TempoTraceConnector.SOURCE_REF), TRACE_ID).warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
            stub.responder = req -> new Response(503, "오류");
            assertThat(connector.getTrace(request(TempoTraceConnector.SOURCE_REF), TRACE_ID).status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        }
    }
    @Test void nanosecondWindowIsWidenedOnlyForTransportAndExactOriginalWindowFiltersSpans() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            var from = NOW.plusNanos(123); var to = NOW.plusNanos(125);
            String hit = batch("tc-gateway", "요청내부", SourceHttpClient.nanos(from), SourceHttpClient.nanos(from.plusNanos(1)));
            String before = batch("tc-gateway", "요청이전", SourceHttpClient.nanos(NOW), SourceHttpClient.nanos(from));
            String after = batch("tc-gateway", "요청이후", SourceHttpClient.nanos(to), SourceHttpClient.nanos(NOW.plusSeconds(1)));
            stub.responder = req -> new Response(200, req.path().equals("/api/search") ? "{\"traces\":[{\"traceID\":\"" + TRACE_ID + "\"}]}"
                : "{\"batches\":[" + before + "," + hit + "," + after + "]}");
            var result = new TempoTraceConnector(stub.http("tempo"), access()).search(request(TempoTraceConnector.SOURCE_REF), "tc-gateway", new TimeWindow(from, to));
            assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS);
            assertThat(stub.requests.getFirst().query()).containsEntry("start", Long.toString(NOW.getEpochSecond())).containsEntry("end", Long.toString(NOW.plusSeconds(1).getEpochSecond()));
            assertThat(result.traces().getFirst().spans()).extracting(TempoTraceConnector.Span::name).containsExactly("요청내부");
            assertThat(result.traces().getFirst().spans().getFirst().startTimeUnixNano()).isEqualTo(SourceHttpClient.nanos(from));
        }
    }
    @Test void exactSecondEndIsNotUnnecessarilyExpandedAndInvalidSpanTimeIsUnavailable() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(200, req.path().equals("/api/search") ? "{\"traces\":[]}" : "{\"batches\":[" + batch("tc-gateway", "오류", "20", "10") + "]}");
            var connector = new TempoTraceConnector(stub.http("tempo"), access());
            connector.search(request(TempoTraceConnector.SOURCE_REF), "tc-gateway", new TimeWindow(NOW.minusNanos(1), NOW));
            assertThat(stub.requests.getFirst().query()).containsEntry("start", Long.toString(NOW.minusSeconds(1).getEpochSecond())).containsEntry("end", Long.toString(NOW.getEpochSecond()));
            assertThat(connector.getTrace(request(TempoTraceConnector.SOURCE_REF), TRACE_ID).status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        }
    }
}
