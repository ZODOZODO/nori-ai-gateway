package com.nori.ai.gateway.acceptance.loki;

import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.loki.SourceHttpClient;
import com.nori.ai.gateway.integration.loki.SourceObservability;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LokiDirectQueryTest {
    @Test void fixedPositiveControlsReturnMetadataWithoutSourceLogText() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = new ArrayList<>();
        server.createContext("/loki/api/v1/query_range", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] body = "{\"status\":\"success\",\"data\":{\"resultType\":\"streams\",\"result\":[{\"stream\":{\"app\":\"wm-ui-backend\",\"namespace\":\"tc\"},\"values\":[[\"1000000000\",\"RAW_SOURCE_SENTINEL\"]]}]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            var query = new LokiDirectQuery(new SourceHttpClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "loki", SourceObservability.noop()));
            var result = query.measure("wm-ui-backend", new TimeWindow(Instant.EPOCH, Instant.ofEpochSecond(2)));
            assertThat(result.count()).isEqualTo(1); assertThat(result.apps()).containsExactly("wm-ui-backend");
            assertThat(result.oldestAt()).contains(Instant.ofEpochSecond(1)); assertThat(result.newestAt()).contains(Instant.ofEpochSecond(1));
            assertThat(result.toString()).doesNotContain("RAW_SOURCE_SENTINEL");
            assertThat(requests).singleElement().asString().contains("GET", "query={app=\"wm-ui-backend\", namespace=\"tc\"}", "limit=500", "direction=backward");
            assertThatThrownBy(() -> query.measure("unknown", new TimeWindow(Instant.EPOCH, Instant.ofEpochSecond(2)))).isInstanceOf(IllegalArgumentException.class);
            assertThat(requests).hasSize(1);
        } finally { server.stop(0); }
    }
}
