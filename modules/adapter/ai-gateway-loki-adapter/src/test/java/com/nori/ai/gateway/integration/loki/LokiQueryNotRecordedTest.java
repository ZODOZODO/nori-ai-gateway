package com.nori.ai.gateway.integration.loki;

import com.nori.ai.gateway.integration.source.*;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.integration.ConnectorTestSupport;
import io.micrometer.observation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.net.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

public class LokiQueryNotRecordedTest {
    @Test void successErrorAndMalformedBodyNeverRecordQueryUrlOrExceptionText() throws Exception {
        String sentinel = "SENSITIVE_LOKI_SENTINEL_한글";
        for (int status : List.of(200, 400, 500)) try (var stub = new ConnectorTestSupport(); var capture = new Capture()) {
            stub.responder = req -> new Response(status, status == 200 ? emptyLoki() : sentinel + stub.base());
            var connector = new LokiLogConnector(new SourceHttpClient(stub.base(), "loki", capture.observations), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
            var result = connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of(sentinel), 1000));
            assertThat(result.status()).isEqualTo(status == 200 ? ResultStatus.EMPTY : ResultStatus.SOURCE_UNAVAILABLE);
            capture.assertNoSecret(sentinel, stub.base().toString());
            assertThat(capture.spanAttributes).isNotEmpty();
        }
    }
    @Test void connectionFailureIsSanitizedBeforeObservationErrorHandlersReceiveIt() throws Exception {
        String sentinel = "SENSITIVE_CONNECTION_SENTINEL";
        URI unavailable;
        try (var socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) { unavailable = URI.create("http://127.0.0.1:" + socket.getLocalPort()); }
        try (var capture = new Capture()) {
            var connector = new LokiLogConnector(new SourceHttpClient(unavailable, "loki", capture.observations), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
            assertThat(connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of(sentinel), 1000)).status())
                .isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
            capture.assertNoSecret(sentinel, unavailable.toString());
            assertThat(capture.spanAttributes.stream().filter(value -> value.contains("Failure")).count()).isEqualTo(2);
        }
    }
    @Test void malformedSourceTextCannotEscapeThroughJsonParserException() throws Exception {
        String sentinel = "MALFORMED_SOURCE_SENTINEL";
        try (var stub = new ConnectorTestSupport(); var capture = new Capture()) {
            stub.responder = req -> new Response(200, "{\"" + sentinel + "\":");
            var connector = new LokiLogConnector(new SourceHttpClient(stub.base(), "loki", capture.observations), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
            assertThat(connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of(sentinel), 1000)).status())
                .isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
            capture.assertNoSecret(sentinel, stub.base().toString());
        }
    }
    @Test void debugWireLoggingFailsBeforeAnyRequest() throws Exception {
        var wire = java.util.logging.Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");
        var previous = wire.getLevel();
        try (var stub = new ConnectorTestSupport()) {
            var connector = new LokiLogConnector(stub.http("loki"), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
            for (var level : List.of(java.util.logging.Level.FINE, java.util.logging.Level.FINER, java.util.logging.Level.FINEST)) {
                wire.setLevel(level);
                assertThat(connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of("비밀"), 1000)).status())
                    .isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
            }
            assertThat(stub.requests).isEmpty();
        } finally { wire.setLevel(previous); }
    }
    @Test void jdkWireCaptureAndTlsDebugAreRejectedWithoutRecordingSourceTraffic() throws Exception {
        for (String key : List.of("sun.net.http.captureRules", "javax.net.debug", "jdk.httpclient.HttpClient.log", "jdk.internal.httpclient.debug")) {
            String previous = System.getProperty(key);
            try (var stub = new ConnectorTestSupport()) {
                System.setProperty(key, "stub-capture-enabled");
                var connector = new LokiLogConnector(stub.http("loki"), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
                assertThat(connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of("비밀"), 1000)).status())
                    .isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
                assertThat(stub.requests).isEmpty();
            } finally { if (previous == null) System.clearProperty(key); else System.setProperty(key, previous); }
        }
    }
    @Test void jdkClientSystemLoggerDebugIsBlockedBeforeAnyRequest() throws Exception {
        for (String name : List.of("jdk.internal.httpclient.debug", "jdk.internal.httpclient.hpack.debug")) {
            var logger = java.util.logging.Logger.getLogger(name); var previous = logger.getLevel();
            try (var stub = new ConnectorTestSupport()) {
                logger.setLevel(java.util.logging.Level.FINE);
                var connector = new LokiLogConnector(stub.http("loki"), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
                assertThat(connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of("비밀"), 1000)).status())
                    .isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
                assertThat(stub.requests).isEmpty();
            } finally { logger.setLevel(previous); }
        }
    }
    @Test void globalDefaultMeterHandlerAndParentScopeCannotAddTagsToTheDedicatedSourceRegistry() throws Exception {
        try (var stub = new ConnectorTestSupport(); var capture = new Capture()) {
            Observation parent = Observation.start("global.parent", capture.globalObservations);
            try (var scope = parent.openScope()) {
                var connector = new LokiLogConnector(new SourceHttpClient(stub.base(), "loki", capture.observations), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
                assertThat(connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of("SOURCE_PRIVATE_SENTINEL"), 1000)).status())
                    .isEqualTo(ResultStatus.EMPTY);
            } finally { parent.stop(); }
            assertThat(capture.globalMeters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getName()).startsWith("global.parent"));
            capture.assertNoSecret("SOURCE_PRIVATE_SENTINEL", stub.base().toString());
            assertThat(capture.meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags()).hasSize(3));
        }
    }
}
