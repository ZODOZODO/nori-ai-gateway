package com.nori.ai.gateway.integration.loki;

import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.ConnectorTestSupport;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

class LokiRetentionProbeTest {
    @Test void measuresOldestAndActualProbeLimitUsingForwardInvariantSelector() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            Instant limit = NOW.minus(Duration.ofDays(31)); Instant oldest = limit.plusNanos(123);
            stub.responder = req -> new Response(200, loki("tc-gateway", "tc", List.of(oldest)));
            var probe = new LokiRetentionProbe(stub.http("loki"), new TimeWindow(limit, NOW));
            assertThat(probe.measure("tc-gateway")).contains(new LokiRetentionProbe.ProbeObservation(oldest, limit));
            assertThat(stub.requests).singleElement().satisfies(req -> {
                assertThat(req.method()).isEqualTo("GET");
                assertThat(req.query()).containsEntry("direction", "forward").containsEntry("limit", "1")
                    .containsEntry("start", LokiLogConnector.nanos(limit)).containsEntry("query", "{app=\"tc-gateway\", namespace=\"tc\"}");
            });
        }
    }
    @Test void zeroRecordsProduceNoObservationAndUnapprovedAppHasNoNetworkRead() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            var probe = new LokiRetentionProbe(stub.http("loki"), new TimeWindow(NOW.minusSeconds(60), NOW));
            assertThat(probe.measure("tc-ui-front")).isEmpty();
            assertThatThrownBy(() -> probe.measure("wm-ui-front")).isInstanceOf(IllegalArgumentException.class);
            assertThat(stub.requests).hasSize(1);
        }
    }
    @Test void failureOrForeignLabelCannotCreateVerifiedOldestObservation() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            var probe = new LokiRetentionProbe(stub.http("loki"), new TimeWindow(NOW.minusSeconds(60), NOW));
            stub.responder = req -> new Response(503, "오류");
            assertThatThrownBy(() -> probe.measure("tc-gateway")).isInstanceOf(SourceHttpClient.Failure.class);
            stub.responder = req -> new Response(200, loki("wm-ui-front", "tc", List.of(NOW.minusSeconds(1))));
            assertThatThrownBy(() -> probe.measure("tc-gateway")).isInstanceOf(SourceHttpClient.Failure.class);
        }
    }
    @Test void probeClassHasNoJdbcOrPersistenceDependency() throws Exception {
        try (var stream = LokiRetentionProbe.class.getResourceAsStream("LokiRetentionProbe.class")) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1)).doesNotContain("JdbcClient", "gateway/persistence", "java/sql", "SourceRetentionReader");
        }
    }
}
