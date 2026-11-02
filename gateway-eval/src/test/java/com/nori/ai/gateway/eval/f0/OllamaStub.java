package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.IntPredicate;

/** localhost JDK HttpServer 로 만든 Ollama stub. 받은 요청(메서드·경로·본문)을 모두 기록하고, 응답에 sentinel 필드를 넣어 비노출을 시험한다. */
final class OllamaStub implements AutoCloseable {
    static final String SENTINEL = "SENTINEL_RAW_7c1d";
    static final String GEN_DIGEST = "1".repeat(64), EMB_DIGEST = "2".repeat(64);
    record Request(String method, String path, JsonNode body) {}
    final List<Request> requests = new CopyOnWriteArrayList<>();
    final HttpServer server;
    volatile JsonNode ps = models(4096, 8192, GEN_DIGEST);
    volatile JsonNode tags = tagModels(GEN_DIGEST);
    static final String DEFAULT_TEMPLATE = "{{- range .Messages }}<|im_start|>{{ .Role }}\n{{ .Content }}<|im_end|>\n{{ end }}<|im_start|>assistant\n";
    volatile String template = DEFAULT_TEMPLATE;
    volatile Function<JsonNode,JsonNode> chat = body -> chatResponse("{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"q\":null}", "stop", 50, 20);
    volatile Function<JsonNode,JsonNode> generate = body -> generateResponse(7);
    volatile int failChatAfter = -1;
    /** 참이면 그 chat 요청을 HTTP 400 으로 거부합니다(서버가 response_format 을 지원하지 않는 경우 재현). */
    volatile java.util.function.Predicate<JsonNode> rejectChat = body -> false;
    /** n 번째 chat 응답은 헤더·본문 절반만 보내고 close 까지(최대 10초) 멈춥니다(본문 시간 초과 시험). */
    volatile IntPredicate stallChatBody = n -> false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CountDownLatch released = new CountDownLatch(1);
    private int chatCount;

    OllamaStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] input = exchange.getRequestBody().readAllBytes();
            JsonNode body = input.length == 0 ? null : Json.MAPPER.readTree(input);
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(exchange.getRequestMethod(), path, body));
            JsonNode response; int status = 200; boolean stall = false;
            synchronized (this) {
                switch (path) {
                    case "/api/ps" -> response = ps;
                    case "/api/tags" -> response = tags;
                    case "/api/show" -> { ObjectNode show = Json.MAPPER.createObjectNode(); show.put("template", template); show.put("modelfile", SENTINEL); show.put("license", SENTINEL); show.putObject("model_info").put("general.secret", SENTINEL); response = show; }
                    case "/v1/chat/completions" -> {
                        chatCount++; stall = stallChatBody.test(chatCount);
                        if (failChatAfter >= 0 && chatCount > failChatAfter) { status = 500; response = Json.MAPPER.createObjectNode().put("error", SENTINEL); }
                        else if (rejectChat.test(body)) { status = 400; response = Json.MAPPER.createObjectNode().put("error", SENTINEL); }
                        else response = chat.apply(body);
                    }
                    case "/api/generate" -> response = generate.apply(body);
                    default -> { status = 404; response = Json.MAPPER.createObjectNode().put("error", "not found"); }
                }
            }
            byte[] bytes = Json.MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            if (stall) {
                try { exchange.getResponseBody().write(bytes, 0, bytes.length / 2); exchange.getResponseBody().flush(); released.await(10, TimeUnit.SECONDS); }
                catch (IOException | InterruptedException ignored) { }
                finally { exchange.close(); }
                return;
            }
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.setExecutor(executor);
        server.start();
    }
    String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    List<Request> inference() { return requests.stream().filter(r -> r.path().equals("/v1/chat/completions") || r.path().equals("/api/generate")).toList(); }
    static JsonNode models(Integer generationContext, Integer embeddingContext, String generationDigest) {
        ObjectNode root = Json.MAPPER.createObjectNode(); var models = root.putArray("models");
        ObjectNode gen = models.addObject(); gen.put("name", "qwen3:4b-instruct"); gen.put("model", "qwen3:4b-instruct"); gen.put("digest", generationDigest); gen.put("size", 1234); gen.put("expires_at", SENTINEL);
        if (generationContext != null) gen.put("context_length", generationContext);
        gen.putObject("details").put("family", SENTINEL);
        ObjectNode emb = models.addObject(); emb.put("name", "bge-m3:latest"); emb.put("digest", EMB_DIGEST); emb.put("context_length", embeddingContext); emb.put("size_vram", 99);
        return root;
    }
    static JsonNode tagModels(String generationDigest) {
        ObjectNode root = Json.MAPPER.createObjectNode(); var models = root.putArray("models");
        ObjectNode gen = models.addObject(); gen.put("name", "qwen3:4b-instruct"); gen.put("digest", generationDigest); gen.put("modified_at", SENTINEL);
        models.addObject().put("name", "bge-m3:latest").put("digest", EMB_DIGEST);
        return root;
    }
    static JsonNode chatResponse(String content, String finish, int promptTokens, int completionTokens) {
        ObjectNode root = Json.MAPPER.createObjectNode(); root.put("id", "chatcmpl-1"); root.put("system_fingerprint", SENTINEL);
        ObjectNode choice = root.putArray("choices").addObject(); choice.put("index", 0); choice.putObject("message").put("role", "assistant").put("content", content); choice.put("finish_reason", finish);
        root.putObject("usage").put("prompt_tokens", promptTokens).put("completion_tokens", completionTokens).put("total_tokens", promptTokens + completionTokens);
        return root;
    }
    static JsonNode generateResponse(int promptEvalCount) {
        ObjectNode root = Json.MAPPER.createObjectNode(); root.put("response", SENTINEL); root.put("done", true); root.put("prompt_eval_count", promptEvalCount); root.putArray("context").add(1).add(2); return root;
    }
    /** 사용자 메시지(JSON)의 question 필드 — stub 응답을 질문별로 정하기 위해서만 씀. */
    static String question(JsonNode body) {
        JsonNode messages = body.path("messages");
        for (int i = messages.size() - 1; i >= 0; i--) {
            String content = messages.get(i).path("content").asText();
            if (messages.get(i).path("role").asText().equals("user") && content.startsWith("{")) {
                try { return Json.MAPPER.readTree(content.getBytes(StandardCharsets.UTF_8)).path("question").asText(); } catch (IOException e) { return ""; }
            }
        }
        return "";
    }
    @Override public void close() { released.countDown(); server.stop(0); executor.shutdownNow(); }
}
