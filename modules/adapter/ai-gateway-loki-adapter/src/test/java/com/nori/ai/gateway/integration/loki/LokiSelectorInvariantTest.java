package com.nori.ai.gateway.integration.loki;

import com.nori.ai.gateway.integration.source.*;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.ConnectorTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

class LokiSelectorInvariantTest {
    @Test void injectedTermsAreExactEncodedLineFiltersAndNeverChangeTheFourSelectors() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            List<String> terms = List.of("app=\"wm-ui-backend\"", "{namespace=\"tc\"}", "a\\b\"\n\r\t +&?/%한글🙂");
            var connector = new LokiLogConnector(stub.http("loki"), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
            var result = connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), terms, 1000));
            assertThat(result.status()).isEqualTo(ResultStatus.EMPTY);
            assertThat(stub.requests).hasSize(4);
            for (int i = 0; i < 4; i++) {
                var received = stub.requests.get(i);
                assertThat(received.method()).isEqualTo("GET");
                assertThat(received.path()).isEqualTo("/loki/api/v1/query_range");
                assertThat(received.query()).containsEntry("query", LokiSourcePolicy.selector(TcSourcePolicy.APPS.get(i), terms))
                    .containsEntry("start", SourceHttpClient.nanos(NOW.minusSeconds(1))).containsEntry("end", SourceHttpClient.nanos(NOW))
                    .containsEntry("limit", "500").containsEntry("direction", "backward");
                assertThat(received.query().get("query")).contains("a\\\\b\\\"\\n\\r\\t +&?/%한글🙂");
                assertThat(received.rawQuery()).doesNotContain("한글", "{", "}", "\"", " +&?");
            }
        }
    }
    @Test void responseAppAndNamespaceAreRecheckedAndViolationsCountedWithoutReturningForeignLines() throws Exception {
        try (var stub = new ConnectorTestSupport()) {
            stub.responder = req -> new Response(200, loki("wm-ui-backend", "tc", List.of(NOW.minusNanos(1))));
            var connector = new LokiLogConnector(stub.http("loki"), access(), ignored -> Optional.empty(), new LokiProperties(), CLOCK);
            var result = connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of(), 1000));
            assertThat(result.items()).isEmpty(); assertThat(result.rejectedLabels()).isEqualTo(4);
            stub.responder = req -> new Response(200, loki(selectedApp(req), "wm", List.of(NOW.minusNanos(1))));
            assertThat(connector.search(request(LokiSourcePolicy.SOURCE_REF), new LokiLogConnector.LogQuery(new TimeWindow(NOW.minusSeconds(1), NOW), List.of(), 1000)).rejectedLabels()).isEqualTo(4);
        }
    }
    @Test void unapprovedAppCannotConstructASelector() {
        assertThatThrownBy(() -> LokiSourcePolicy.selector("wm-ui-backend", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LokiSourcePolicy.selector("", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
