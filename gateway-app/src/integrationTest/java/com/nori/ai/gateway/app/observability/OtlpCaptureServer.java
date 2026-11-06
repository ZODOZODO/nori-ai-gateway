package com.nori.ai.gateway.app.observability;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 인프로세스 OTLP/HTTP 수신기다. 받은 요청 경로와 원본 바이트만 보관한다. */
final class OtlpCaptureServer implements AutoCloseable {
    record Captured(String path, byte[] body) {}
    private final HttpServer server;
    private final List<Captured> requests = new CopyOnWriteArrayList<>();
    OtlpCaptureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(new Captured(exchange.getRequestURI().getPath(), exchange.getRequestBody().readAllBytes()));
                exchange.sendResponseHeaders(200, -1);
            }
        });
        server.start();
    }
    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    List<byte[]> bodies(String path) { return requests.stream().filter(request -> request.path().equals(path)).map(Captured::body).toList(); }
    List<Captured> requests() { return List.copyOf(requests); }
    /** sentinel 잔류 검사용으로 모든 본문을 UTF-8 문자열로 합친다(protobuf 문자열 필드는 UTF-8 바이트 그대로 포함된다). */
    String allBytesAsText() {
        var text = new StringBuilder();
        for (var request : requests) text.append(request.path()).append('\n').append(new String(request.body(), StandardCharsets.UTF_8)).append('\n');
        return text.toString();
    }
    @Override public void close() { server.stop(0); }

    /** runtime의 OTLP proto 메시지로 본문을 해석해 필드 이름 기준 중첩 Map으로 돌려준다(proto 클래스는 runtime 전용 의존이다). */
    static java.util.Map<String,Object> parse(String messageClass, byte[] body) {
        try { return fields((com.google.protobuf.Message) Class.forName(messageClass).getMethod("parseFrom", byte[].class).invoke(null, (Object) body)); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("OTLP 메시지를 해석할 수 없습니다", failure); }
    }
    private static java.util.Map<String,Object> fields(com.google.protobuf.MessageOrBuilder message) {
        var map = new java.util.LinkedHashMap<String,Object>();
        message.getAllFields().forEach((field, value) -> map.put(field.getName(), value(value)));
        return map;
    }
    private static Object value(Object value) {
        if (value instanceof com.google.protobuf.MessageOrBuilder message) return fields(message);
        if (value instanceof List<?> values) return values.stream().map(OtlpCaptureServer::value).toList();
        // trace·span ID 같은 binary 필드는 비교 가능한 소문자 hex로 둡니다.
        if (value instanceof com.google.protobuf.ByteString bytes) return java.util.HexFormat.of().formatHex(bytes.toByteArray());
        return value;
    }
    @SuppressWarnings("unchecked")
    static List<java.util.Map<String,Object>> list(java.util.Map<String,Object> message, String field) {
        return (List<java.util.Map<String,Object>>) message.getOrDefault(field, List.of());
    }
    @SuppressWarnings("unchecked")
    static java.util.Map<String,Object> child(java.util.Map<String,Object> message, String field) {
        return (java.util.Map<String,Object>) message.getOrDefault(field, java.util.Map.of());
    }
    /** OTLP KeyValue 목록을 key → string_value Map으로 바꾼다. */
    static java.util.Map<String,String> attributes(java.util.Map<String,Object> owner) {
        var map = new java.util.TreeMap<String,String>();
        for (var attribute : list(owner, "attributes")) map.put((String) attribute.get("key"), String.valueOf(child(attribute, "value").getOrDefault("string_value", "")));
        return map;
    }
}
