package com.nori.ai.gateway.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.integration.loki.*;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;

public final class ConnectorTestSupport implements AutoCloseable {
    public static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public record Request(String method, String path, String rawQuery, Map<String, String> query, String token) {}
    public record Response(int status, String body, Map<String, String> headers) {
        public Response(int status, String body) { this(status, body, Map.of()); }
    }
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    public Function<Request, Response> responder = request -> new Response(200, emptyLoki());
    private final HttpServer server;
    public ConnectorTestSupport() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Map<String, String> query = new LinkedHashMap<>();
            String raw = exchange.getRequestURI().getRawQuery();
            if (raw != null) for (String item : raw.split("&")) {
                String[] parts = item.split("=", 2);
                query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts.length == 1 ? "" : URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
            Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), raw, Map.copyOf(query), exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            requests.add(request);
            Response response = responder.apply(request);
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Connection", "close");
            response.headers().forEach(exchange.getResponseHeaders()::set);
            exchange.sendResponseHeaders(response.status(), body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
    }
    public URI base() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
    public SourceHttpClient http(String source) { return new SourceHttpClient(base(), source, SourceObservability.noop()); }
    public static SourceReadAccess access() { return access((call, required) -> new Allowed(required, "b".repeat(64))); }
    public static SourceReadAccess access(com.nori.ai.gateway.core.port.TcSourceAuthorizationPort authorization) {
        Map<String, SourcePermissionMap.Entry> entries = Map.of("sources://TC/logs", new SourcePermissionMap.Entry(Set.of("AI_LOG_READ"), true),
            "sources://TC/trace", new SourcePermissionMap.Entry(Set.of("AI_TRACE_READ"), true), "sources://TC/git", new SourcePermissionMap.Entry(Set.of("AI_CODE_READ"), true));
        return new SourceReadAccess(new SourceAuthorizationStep(new SourcePermissionMap(entries), authorization), CLOCK);
    }
    public static SourceReadAccess.Request request(String source) { return request(source, OwnerSystem.TC, NOW.plusSeconds(60)); }
    public static SourceReadAccess.Request request(String source, OwnerSystem owner, Instant expiresAt) {
        var scope = new VerifiedSystemScope(owner, "service", new ActorRef("actor"), "actor", Set.of(), "a".repeat(64), NOW.minusSeconds(60), expiresAt, "req");
        return new SourceReadAccess.Request(scope, new DelegatedCall("actor", "d".repeat(22)), source);
    }
    public static String emptyLoki() { return "{\"status\":\"success\",\"data\":{\"resultType\":\"streams\",\"result\":[]}}"; }
    public static String loki(String app, String namespace, List<Instant> times) {
        try {
            var values = times.stream().map(at -> List.of(LokiLogConnector.nanos(at), "조회 자료")).toList();
            return new ObjectMapper().writeValueAsString(Map.of("status", "success", "data", Map.of("resultType", "streams", "result",
                List.of(Map.of("stream", Map.of("app", app, "namespace", namespace), "values", values)))));
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    public static String selectedApp(Request request) {
        return LokiSourcePolicy.APPS.stream().filter(app -> request.query().getOrDefault("query", "").startsWith("{app=\"" + app + "\", namespace=\"tc\"}")).findFirst().orElseThrow();
    }
    @Override public void close() { server.stop(0); }
}
