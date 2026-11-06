package com.nori.ai.gateway.acceptance.loki;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.loki.*;
import java.time.*;
import java.util.*;
import java.math.BigInteger;

/** 수용 환경의 양성 제어에 필요한 건수·라벨·시각만 보존합니다. */
public final class LokiDirectQuery {
    private static final Set<String> CONTROL_APPS = Set.of("tc-gateway", "tc-business", "tc-ui-backend", "tc-ui-front", "wm-ui-backend", "wm-ui-front");
    public record Observation(int count, Set<String> apps, Set<String> namespaces, Optional<Instant> oldestAt, Optional<Instant> newestAt, boolean limited) {
        public Observation { apps = Set.copyOf(apps); namespaces = Set.copyOf(namespaces); }
    }
    private final SourceHttpClient http;
    public LokiDirectQuery(SourceHttpClient http) { this.http = Objects.requireNonNull(http); }
    public Observation measure(String app, TimeWindow window) {
        if (!CONTROL_APPS.contains(app)) throw new IllegalArgumentException("허용되지 않은 양성 제어 앱입니다");
        JsonNode root = http.json(http.uri(List.of("loki", "api", "v1", "query_range"), Map.of("query", "{app=\"" + app + "\", namespace=\"tc\"}",
            "start", LokiLogConnector.nanos(window.startAt()), "end", LokiLogConnector.nanos(window.endAt()), "limit", 500, "direction", "backward")), null, http.deadline(Duration.ofSeconds(30))).body();
        if (!"success".equals(root.path("status").asText()) || !"streams".equals(root.path("data").path("resultType").asText())
                || !root.path("data").path("result").isArray()) throw new SourceHttpClient.Failure(false, false);
        int count = 0; Set<String> apps = new HashSet<>(), namespaces = new HashSet<>(); Instant oldest = null, newest = null;
        for (JsonNode stream : root.path("data").path("result")) {
            if (!stream.path("values").isArray()) throw new SourceHttpClient.Failure(false, false);
            for (JsonNode row : stream.path("values")) {
                if (!row.isArray() || row.size() < 2 || !row.get(0).isTextual()) throw new SourceHttpClient.Failure(false, false);
                BigInteger[] parts = new BigInteger(row.get(0).asText()).divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
                Instant time = Instant.ofEpochSecond(parts[0].longValueExact(), parts[1].longValueExact());
                if (time.isBefore(window.startAt()) || !time.isBefore(window.endAt())) throw new SourceHttpClient.Failure(false, false);
                count++; apps.add(stream.path("stream").path("app").asText()); namespaces.add(stream.path("stream").path("namespace").asText());
                if (oldest == null || time.isBefore(oldest)) oldest = time;
                if (newest == null || time.isAfter(newest)) newest = time;
            }
        }
        if (count > 500) throw new SourceHttpClient.Failure(false, false);
        return new Observation(count, apps, namespaces, Optional.ofNullable(oldest), Optional.ofNullable(newest), count == 500);
    }
}
