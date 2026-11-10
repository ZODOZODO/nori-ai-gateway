package com.nori.ai.gateway.integration.inference;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Ollama endpoint 를 흉내 내는 JDK HttpServer. 받은 요청(메서드·경로·헤더·본문)을 모두 기록한다. */
final class OllamaStubServer implements AutoCloseable {
    record Received(String method, String path, Map<String,List<String>> headers, String body) {
        String header(String name) { var values = headers.get(name.toLowerCase(Locale.ROOT)); return values == null ? null : values.getFirst(); }
    }
    sealed interface Reply permits Json, Disconnect {}
    record Json(int status, String body) implements Reply {}
    /** 요청을 다 읽은 뒤 응답 없이 연결을 닫는다. */
    record Disconnect() implements Reply {}

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile Function<Received,Reply> responder;

    OllamaStubServer(Function<Received,Reply> responder) {
        this.responder = responder;
        try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        server.createContext("/", exchange -> {
            var headers = new TreeMap<String,List<String>>();
            exchange.getRequestHeaders().forEach((key, values) -> headers.put(key.toLowerCase(Locale.ROOT), List.copyOf(values)));
            var request = new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), Map.copyOf(headers),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            received.add(request);
            // JDK HttpServer 는 handler 예외에서 응답 없이 연결을 닫는다.
            if (this.responder.apply(request) instanceof Json(int status, String body)) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            } else throw new IllegalStateException("stub disconnect");
        });
        server.start();
    }

    void respond(Function<Received,Reply> next) { this.responder = next; }
    String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    List<Received> received() { return List.copyOf(received); }
    long count(String path) { return received.stream().filter(request -> request.path().equals(path)).count(); }
    @Override public void close() { server.stop(0); }
}
