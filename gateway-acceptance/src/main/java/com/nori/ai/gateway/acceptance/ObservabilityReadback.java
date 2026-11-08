package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** G-OBS-READBACK 확인 경로의 Tempo span 속성 키·metric label 이름 조회. 값은 돌려주지 않고 키 이름·건수만 낸다. */
public final class ObservabilityReadback {
    private final HttpClient http;
    private final URI tempo, metrics;

    public ObservabilityReadback(HttpClient http, URI tempo, URI metrics) { this.http = http; this.tempo = tempo; this.metrics = metrics; }

    /** trace 하나의 span 속성 키 집합. */
    public Set<String> spanAttributeKeys(String traceId) {
        if (traceId == null || !traceId.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("TRACE_ID_INVALID");
        var keys = new TreeSet<String>();
        collect(get(tempo.resolve("/api/traces/" + traceId)), keys);
        return keys;
    }
    /** metric 하나의 series label 이름 집합(값 제외). */
    public Set<String> metricLabelNames(String metricName) {
        if (metricName == null || !metricName.matches("[a-zA-Z_:][a-zA-Z0-9_:]{0,127}")) throw new IllegalArgumentException("METRIC_NAME_INVALID");
        JsonNode root = get(metrics.resolve("/api/v1/series?match[]=" + URLEncoder.encode(metricName, StandardCharsets.UTF_8)));
        var names = new TreeSet<String>();
        for (JsonNode series : root.path("data")) series.fieldNames().forEachRemaining(names::add);
        return names;
    }
    private static void collect(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            if (node.has("attributes") && node.get("attributes").isArray()) for (JsonNode attribute : node.get("attributes")) if (attribute.path("key").isTextual()) keys.add(attribute.path("key").asText());
            node.forEach(child -> collect(child, keys));
        } else if (node.isArray()) node.forEach(child -> collect(child, keys));
    }
    private JsonNode get(URI uri) {
        try {
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IllegalStateException("OBS_READBACK_FAILED:" + response.statusCode());
            return CanonicalJson.MAPPER.readTree(response.body());
        } catch (IOException e) { throw new IllegalStateException("OBS_READBACK_FAILED"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("OBS_READBACK_FAILED"); }
    }
}
