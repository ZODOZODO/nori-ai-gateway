package com.nori.ai.gateway.integration.loki;

import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.port.SourceRetentionReader;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.ConnectorTestSupport;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

class LokiWindowBatchingTest {
    private LokiLogConnector connector(ConnectorTestSupport stub) { return new LokiLogConnector(stub.http("loki"), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK); }
    private LokiLogConnector.LogQuery query(TimeWindow window) { return new LokiLogConnector.LogQuery(window, List.of("검색"), 1000); }
    @Test void thirtyOneDayWindowIsNotClampedAndEverySubwindowIsNewestFirstWithFourInvariantSelectors() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            Instant start = NOW.minus(Duration.ofDays(31));
            var result = connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(start, NOW)));
            assertThat(result.status()).isEqualTo(ResultStatus.EMPTY); assertThat(result.missingRanges()).isEmpty();
            assertThat(result.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
            assertThat(stub.requests).hasSize(31 * 4 * 4);
            for (int i = 0; i < 124; i++) {
                Instant end = NOW.minus(Duration.ofHours(6L * i)); Instant from = end.minus(Duration.ofHours(6));
                for (int app = 0; app < 4; app++) assertThat(stub.requests.get(i * 4 + app).query())
                    .containsEntry("start", LokiLogConnector.nanos(from)).containsEntry("end", LokiLogConnector.nanos(end))
                    .containsEntry("query", LokiSourcePolicy.selector(LokiSourcePolicy.APPS.get(app), List.of("검색")));
            }
            assertThat(stub.requests.getLast().query().get("start")).isEqualTo(LokiLogConnector.nanos(start));
        }
    }
    @Test void totalCapReportsExactlyTheUnqueriedOlderWindows() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(200, loki(selectedApp(req), "tc", IntStream.range(0, 250).mapToObj(i -> NOW.minusSeconds(i + 1)).toList()));
            Instant start = NOW.minus(Duration.ofDays(31));
            var result = connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(start, NOW)));
            assertThat(result.status()).isEqualTo(ResultStatus.PARTIAL); assertThat(result.items()).hasSize(1000);
            assertThat(stub.requests).hasSize(4);
            assertThat(result.missingRanges()).containsExactly(new TimeWindow(start, NOW.minus(Duration.ofHours(6))));
        }
    }
    @Test void aLimitedSubqueryReportsStartToOldestReturnedTimestamp() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(200, selectedApp(req).equals("tc-gateway")
                ? loki("tc-gateway", "tc", IntStream.range(0, 500).mapToObj(i -> NOW.minusSeconds(i + 1)).toList()) : emptyLoki());
            Instant start = NOW.minus(Duration.ofHours(6));
            var result = connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(start, NOW)));
            assertThat(result.status()).isEqualTo(ResultStatus.PARTIAL); assertThat(result.items()).hasSize(500);
            assertThat(result.missingRanges()).containsExactly(new TimeWindow(start, NOW.minusSeconds(500)));
        }
    }
    @Test void deadlineNeverStartsAnOlderUnqueriedWindowAndMarksItsExactRange() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            AtomicLong elapsed = new AtomicLong();
            var guarded = access((call, required) -> {
                if (stub.requests.size() == 4) elapsed.set(Duration.ofSeconds(31).toNanos());
                return new com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.Allowed(required, "b".repeat(64));
            });
            var connector = new LokiLogConnector(new SourceHttpClient(stub.base(), "loki", SourceObservability.noop(), elapsed::get), guarded, ignored -> Optional.empty(), new LokiProperties(), CLOCK);
            Instant start = NOW.minus(Duration.ofDays(31));
            var result = connector.search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(start, NOW)));
            assertThat(result.status()).isEqualTo(ResultStatus.PARTIAL); assertThat(stub.requests).hasSize(4);
            assertThat(result.missingRanges()).containsExactly(new TimeWindow(start, NOW.minus(Duration.ofHours(6))));
        }
    }
    @Test void verifiedRetentionExcludesOutsideRangeWithoutNetworkAndPreservesOverlapMissingRanges() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            Instant boundary = NOW.minusSeconds(3600);
            var connector = new LokiLogConnector(stub.http("loki"), access(), source -> Optional.of(new SourceRetentionReader.VerifiedOldest(boundary, NOW)), new LokiProperties(), CLOCK);
            var outside = connector.search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(boundary.minusSeconds(10), boundary)));
            assertThat(outside.status()).isEqualTo(ResultStatus.OUT_OF_RETENTION); assertThat(stub.requests).isEmpty();
            assertThat(outside.missingRanges()).containsExactly(new TimeWindow(boundary.minusSeconds(10), boundary));
            var overlap = connector.search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(boundary.minusNanos(1), NOW)));
            assertThat(overlap.status()).isEqualTo(ResultStatus.PARTIAL);
            assertThat(overlap.missingRanges()).containsExactly(new TimeWindow(boundary.minusNanos(1), boundary));
            assertThat(overlap.warnings()).isEmpty();
            assertThat(stub.requests).allSatisfy(req -> assertThat(req.query().get("start")).isEqualTo(LokiLogConnector.nanos(boundary)));
        }
    }
    @Test void knownRetentionEmptyAndUnknownRetentionHitStayDistinct() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            var verified = new LokiLogConnector(stub.http("loki"), access(), source -> Optional.of(new SourceRetentionReader.VerifiedOldest(NOW.minusSeconds(1), NOW)), new LokiProperties(), CLOCK);
            var empty = verified.search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(NOW.minusSeconds(1), NOW)));
            assertThat(empty.status()).isEqualTo(ResultStatus.EMPTY); assertThat(empty.warnings()).isEmpty();
            stub.responder = req -> new Response(200, loki(selectedApp(req), "tc", List.of(NOW.minusNanos(1))));
            var hit = connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(NOW.minusSeconds(1), NOW)));
            assertThat(hit.status()).isEqualTo(ResultStatus.SUCCESS); assertThat(hit.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
        }
    }
    @Test void unapprovedLimitIsRejectedInsteadOfClamped() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            assertThatThrownBy(() -> connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of(), 1001)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(stub.requests).isEmpty();
        }
    }
    @Test void appSpecificProbeKeysClassifyEachAppWithoutAnAggregateBoundary() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            List<String> keys = new ArrayList<>();
            Instant start = NOW.minusSeconds(60);
            var connector = new LokiLogConnector(stub.http("loki"), access(), source -> {
                keys.add(source);
                if (source.equals(LokiSourcePolicy.retentionSourceId("tc-gateway"))) return Optional.of(new SourceRetentionReader.VerifiedOldest(NOW, NOW));
                if (source.equals(LokiSourcePolicy.retentionSourceId("tc-business"))) return Optional.of(new SourceRetentionReader.VerifiedOldest(NOW.minusSeconds(30), NOW));
                return Optional.empty();
            }, new LokiProperties(), CLOCK);
            var result = connector.search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(start, NOW)));
            assertThat(keys).containsExactlyElementsOf(LokiSourcePolicy.APPS.stream().map(LokiSourcePolicy::retentionSourceId).toList());
            assertThat(keys).doesNotContain(LokiSourcePolicy.SOURCE_REF);
            assertThat(result.status()).isEqualTo(ResultStatus.PARTIAL);
            assertThat(result.missingRanges()).containsExactly(new TimeWindow(start, NOW));
            assertThat(result.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
            assertThat(stub.requests).hasSize(3);
            assertThat(stub.requests.getFirst().query()).containsEntry("start", LokiLogConnector.nanos(NOW.minusSeconds(30)))
                .containsEntry("query", LokiSourcePolicy.selector("tc-business", List.of("검색")));
        }
    }
    @Test void finalSourceFailureNeverMasqueradesAsEmptyAndRetriesOnlyFiveHundreds() throws Exception {
        for (int status : List.of(400, 401, 403, 404, 429, 500, 503)) try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(status, "오류");
            var result = connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(NOW.minusSeconds(1), NOW)));
            assertThat(result.status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE); assertThat(result.items()).isEmpty();
            assertThat(stub.requests).hasSize(status >= 500 ? 2 : 1);
        }
    }
    @Test void transientFailureRetriesTheSameEncodedRequestOnce() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(stub.requests.size() == 1 ? 503 : 200, emptyLoki());
            assertThat(connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(NOW.minusSeconds(1), NOW))).status()).isEqualTo(ResultStatus.EMPTY);
            assertThat(stub.requests).hasSize(5); assertThat(stub.requests.get(0)).isEqualTo(stub.requests.get(1));
        }
    }
    @Test void malformedOrOutOfWindowSourcePayloadIsUnavailable() throws Exception {
        for (String body : List.of("{", "{}", loki("tc-gateway", "tc", List.of(NOW)), loki("tc-gateway", "tc", List.of(NOW.minusSeconds(2)))))
            try (var stub = new ConnectorTestSupport()) {
                stub.responder = req -> new Response(200, body);
                assertThat(connector(stub).search(request(LokiSourcePolicy.SOURCE_REF), query(new TimeWindow(NOW.minusSeconds(1), NOW))).status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
            }
    }
}
