package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Gateway 자신의 로그 readback(H-2). Loki 정확 selector {@code {namespace="tc", app="<release>"}} 와 시험 시간창으로만 조회하고,
 * 결과 줄은 메모리에서 sentinel 포함 여부만 세어 건수만 돌려준다.
 */
public final class GatewayLogReadback {
    public record Count(int lines, int sentinelLines, boolean limited) {}
    static final int LIMIT = 5000;
    private final HttpClient http;
    private final URI loki;

    public GatewayLogReadback(HttpClient http, URI loki) { this.http = http; this.loki = loki; }

    public Count count(String release, Instant start, Instant end, Set<String> sentinels) {
        if (!Set.of("ai-gateway", "ai-gateway-restore").contains(release) || !start.isBefore(end)) throw new IllegalArgumentException("READBACK_INPUT_INVALID");
        String query = "{namespace=\"tc\", app=\"" + release + "\"}";
        URI uri = loki.resolve("/loki/api/v1/query_range?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&start=" + nanos(start) + "&end=" + nanos(end) + "&limit=" + LIMIT + "&direction=forward");
        JsonNode root;
        try {
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IllegalStateException("LOKI_READBACK_FAILED:" + response.statusCode());
            root = CanonicalJson.MAPPER.readTree(response.body());
        } catch (IOException e) { throw new IllegalStateException("LOKI_READBACK_FAILED"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("LOKI_READBACK_FAILED"); }
        if (!"success".equals(root.path("status").asText()) || !root.path("data").path("result").isArray()) throw new IllegalStateException("LOKI_READBACK_INVALID");
        int lines = 0, hits = 0;
        for (JsonNode stream : root.path("data").path("result")) for (JsonNode row : stream.path("values")) {
            lines++;
            String line = row.path(1).asText();
            if (sentinels.stream().anyMatch(line::contains)) hits++;
        }
        return new Count(lines, hits, lines >= LIMIT);
    }
    private static String nanos(Instant value) { return value.getEpochSecond() + String.format("%09d", value.getNano()); }
}
