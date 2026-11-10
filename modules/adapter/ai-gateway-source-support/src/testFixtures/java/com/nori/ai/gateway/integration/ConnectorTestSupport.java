package com.nori.ai.gateway.integration;

import com.nori.ai.gateway.integration.source.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.scope.*;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import static org.assertj.core.api.Assertions.*;

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
            var values = times.stream().map(at -> List.of(SourceHttpClient.nanos(at), "조회 자료")).toList();
            return new ObjectMapper().writeValueAsString(Map.of("status", "success", "data", Map.of("resultType", "streams", "result",
                List.of(Map.of("stream", Map.of("app", app, "namespace", namespace), "values", values)))));
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    public static String selectedApp(Request request) {
        return TcSourcePolicy.APPS.stream().filter(app -> request.query().getOrDefault("query", "").startsWith("{app=\"" + app + "\", namespace=\"tc\"}")).findFirst().orElseThrow();
    }
    // Source 전송 관측(로그·span·meter)에 조회 원문이 남지 않는지 확인하는 수집기. Loki·GitLab 시험이 함께 쓴다.
    public static final class Capture implements AutoCloseable {
        public final ObservationRegistry globalObservations = ObservationRegistry.create();
        public final SourceObservability observations;
        public final SimpleMeterRegistry meters = new SimpleMeterRegistry();
        public final SimpleMeterRegistry globalMeters = new SimpleMeterRegistry();
        public final List<String> spanAttributes = new ArrayList<>();
        private final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        private final Level previous = logger.getLevel();
        private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
        public Capture() {
            logs.start(); logger.addAppender(logs); logger.setLevel(Level.TRACE);
            globalObservations.observationConfig().observationHandler(new DefaultMeterObservationHandler(globalMeters));
            observations = SourceObservability.create(meters, new ObservationHandler<ClientRequestObservationContext>() {
                @Override public boolean supportsContext(Observation.Context context) { return true; }
                @Override public void onError(ClientRequestObservationContext context) { spanAttributes.add(String.valueOf(context.getError())); }
                @Override public void onStop(ClientRequestObservationContext context) {
                    spanAttributes.add(context.getAllKeyValues().toString());
                    spanAttributes.add(context.getContextualName());
                }
            });
        }
        public void assertNoSecret(String sentinel, String fullUrl) {
            assertThat(logs.list.stream().map(event -> event.getFormattedMessage() + " " + event.getThrowableProxy()).toList().toString())
                .doesNotContain(sentinel, fullUrl, "query=", "http.url", "url.full");
            assertThat(spanAttributes.toString()).doesNotContain(sentinel, fullUrl, "http.url", "url.full", "uri=");
            assertThat(meters.getMeters()).isNotEmpty().allSatisfy(meter -> {
                assertThat(meter.getId().getTags()).allSatisfy(tag -> assertThat(tag.getKey()).isIn("source_id", "method", "status_class"));
                assertThat(meter.getId().toString()).doesNotContain(sentinel, fullUrl);
            });
        }
        @Override public void close() { logger.detachAppender(logs); logger.setLevel(previous); logs.stop(); meters.close(); globalMeters.close(); }
    }
    @Override public void close() { server.stop(0); }
}
