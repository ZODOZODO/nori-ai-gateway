package com.nori.ai.gateway.app.inference;

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

/** 추론 서버의 기존 Ollama endpoint(/api/ps·/v1/chat/completions·/v1/embeddings)를 흉내 내는 JDK HttpServer. 받은 요청을 모두 기록한다. */
public final class OllamaEndpointStub implements AutoCloseable {
    public static final String EXPECTED_DIGEST = "1".repeat(64);
    public static final String OTHER_DIGEST = "2".repeat(64);
    public static final String EMBEDDING_DIGEST = "3".repeat(64);
    public record Received(String method, String path, Map<String,List<String>> headers, String body) {
        public String header(String name) { var values = headers.get(name.toLowerCase(Locale.ROOT)); return values == null ? null : values.getFirst(); }
    }
    public sealed interface Reply permits Json, Disconnect {}
    public record Json(int status, String body) implements Reply {}
    /** 요청을 다 읽은 뒤 응답 없이 연결을 닫는다(JDK HttpServer 는 handler 예외에서 연결을 닫는다). */
    public record Disconnect() implements Reply {}

    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile Function<Received,Reply> responder;

    public OllamaEndpointStub() { this(OllamaEndpointStub::healthy); }
    public OllamaEndpointStub(Function<Received,Reply> responder) {
        this.responder = responder;
        try { server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        server.createContext("/", exchange -> {
            var headers = new TreeMap<String,List<String>>();
            exchange.getRequestHeaders().forEach((key, values) -> headers.put(key.toLowerCase(Locale.ROOT), List.copyOf(values)));
            var request = new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), Map.copyOf(headers),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            received.add(request);
            if (this.responder.apply(request) instanceof Json(int status, String body)) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            } else throw new IllegalStateException("stub disconnect");
        });
        server.start();
    }

    /** 두 모델 상주·생성 모델 8192·기대 digest 인 /api/ps 와 정상 chat·embedding 응답. */
    public static Reply healthy(Received request) {
        return switch (request.path()) {
            case "/api/ps" -> new Json(200, ps(generation(8192, EXPECTED_DIGEST), embedding(8192)));
            case "/v1/embeddings" -> new Json(200, embeddingResponse(3));
            case "/v1/chat/completions" -> new Json(200, completion("답변", 40));
            default -> new Json(404, "{}");
        };
    }
    public static String generation(int contextLength, String digest) {
        return "{\"name\":\"qwen3:4b\",\"model\":\"qwen3:4b\",\"digest\":\"" + digest + "\",\"context_length\":" + contextLength + "}";
    }
    public static String embedding(int contextLength) {
        return "{\"name\":\"bge-m3:latest\",\"model\":\"bge-m3:latest\",\"digest\":\"" + EMBEDDING_DIGEST + "\",\"context_length\":" + contextLength + "}";
    }
    public static String ps(String... models) { return "{\"models\":[" + String.join(",", models) + "]}"; }
    public static String completion(String content, int promptTokens) {
        return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"qwen3:4b\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
            + content + "\",\"reasoning\":\"사고문\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":" + promptTokens + ",\"completion_tokens\":5,\"total_tokens\":" + (promptTokens + 5) + "}}";
    }
    public static final String EMPTY_CHOICES = "{\"id\":\"chatcmpl-2\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"qwen3:4b\",\"choices\":[],\"usage\":{\"prompt_tokens\":40,\"completion_tokens\":0,\"total_tokens\":40}}";
    public static String embeddingResponse(int dimension) {
        var values = new StringBuilder();
        for (int i = 0; i < dimension; i++) values.append(i == 0 ? "" : ",").append("0.5");
        return "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[" + values + "]}],\"model\":\"bge-m3\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
    }

    public void respond(Function<Received,Reply> next) { this.responder = next; }
    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public List<Received> received() { return List.copyOf(received); }
    public long count(String path) { return received.stream().filter(request -> request.path().equals(path)).count(); }
    public void clear() { received.clear(); }
    @Override public void close() { server.stop(0); }
}
