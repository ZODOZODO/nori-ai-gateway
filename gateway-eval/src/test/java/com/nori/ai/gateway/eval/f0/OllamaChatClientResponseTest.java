package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;

/** 응답 본문·usage 수치 검증: 실제 localhost JDK HttpServer 로 본문 지연·미완료·과대 응답과 비정수·음수 usage 를 보냅니다(실제 Ollama 호출 0). */
class OllamaChatClientResponseTest {
    static final String MODEL = "qwen3:4b-instruct";
    static final List<Map<String,String>> MESSAGES = List.of(Map.of("role", "user", "content", "x"));
    static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    @TempDir Path directory;

    /** 헤더 송신 뒤 본문을 직접 제어하는 서버. close 시 대기 중 handler 를 풀고 executor 를 정리합니다. */
    static final class RawServer implements AutoCloseable {
        final HttpServer server;
        final ExecutorService executor = Executors.newCachedThreadPool();
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger requests = new AtomicInteger();
        RawServer(IoHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                int n = requests.incrementAndGet();
                try { exchange.getRequestBody().readAllBytes(); handler.handle(this, exchange, n); } catch (IOException ignored) { } finally { exchange.close(); }
            });
            server.start();
        }
        String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        /** 시험 종료(close)까지 또는 지정 시간까지 대기합니다. 풀리면 true. */
        boolean hold(long millis) { try { return release.await(millis, TimeUnit.MILLISECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return true; } }
        @Override public void close() { release.countDown(); server.stop(0); executor.shutdownNow(); }
    }
    interface IoHandler { void handle(RawServer server, HttpExchange exchange, int n) throws IOException; }
    static byte[] bytes(String json) { return json.getBytes(StandardCharsets.UTF_8); }
    static EvalException failure(Runnable call) {
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).isInstanceOf(EvalException.class);
        return (EvalException) thrown;
    }
    static long since(long start) { return Duration.ofNanos(System.nanoTime() - start).toMillis(); }

    // 3. 헤더는 즉시, 본문은 Content-Length 의 일부만 보내고 멈춤 — 헤더~본문 끝 단일 총 기한 안에 ENDPOINT_TIMEOUT.
    @Test void stalledFixedLengthBodyTimesOutWithinTotalDeadline() throws Exception {
        byte[] full = bytes("{\"models\":[],\"pad\":\"" + "a".repeat(64) + "\"}");
        try (RawServer server = new RawServer((s, exchange, n) -> {
            exchange.sendResponseHeaders(200, full.length);
            OutputStream out = exchange.getResponseBody();
            if (n == 1) { out.write(full, 0, 10); out.flush(); s.hold(8000); out.write(full, 10, full.length - 10); }
            else out.write(full);
        })) {
            OllamaChatClient client = new OllamaChatClient(server.endpoint(), Duration.ofMillis(1000));
            long start = System.nanoTime();
            EvalException e = failure(client::ps);
            long elapsed = since(start);
            assertThat(e.getMessage()).isEqualTo("ENDPOINT_TIMEOUT"); assertThat(e.exitCode()).isEqualTo(5);
            assertThat(elapsed).isGreaterThanOrEqualTo(900).isLessThan(4000);
            // 시간 초과 뒤에도 같은 client 의 다음 요청은 정상(멈춘 교환이 남아 막지 않음)
            assertThat(client.ps().path("models").isArray()).isTrue();
        }
    }
    @Test void neverEndingChunkedBodyTimesOutWithinTotalDeadline() throws Exception {
        try (RawServer server = new RawServer((s, exchange, n) -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(bytes("{\"models\":[")); exchange.getResponseBody().flush(); s.hold(8000);
        })) {
            OllamaChatClient client = new OllamaChatClient(server.endpoint(), Duration.ofMillis(1000));
            long start = System.nanoTime();
            assertThat(failure(client::tags).getMessage()).isEqualTo("ENDPOINT_TIMEOUT");
            assertThat(since(start)).isLessThan(4000);
        }
    }
    @Test void slowTrickleBodyCannotExtendTheDeadline() throws Exception {
        try (RawServer server = new RawServer((s, exchange, n) -> {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            out.write(bytes("{\"models\":[],\"pad\":\"")); out.flush();
            // 300ms 마다 1바이트 — 바이트 사이 간격은 기한보다 짧지만 전체는 기한을 넘김
            for (int i = 0; i < 30; i++) { if (s.hold(300)) return; out.write('a'); out.flush(); }
            out.write(bytes("\"}"));
        })) {
            OllamaChatClient client = new OllamaChatClient(server.endpoint(), Duration.ofMillis(1000));
            long start = System.nanoTime();
            assertThat(failure(client::ps).getMessage()).isEqualTo("ENDPOINT_TIMEOUT");
            assertThat(since(start)).isLessThan(4000);
        }
    }
    @Test void interruptDuringBodyWaitIsPropagated() throws Exception {
        try (RawServer server = new RawServer((s, exchange, n) -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(bytes("{\"models\":[")); exchange.getResponseBody().flush(); s.hold(8000);
        })) {
            OllamaChatClient client = new OllamaChatClient(server.endpoint(), Duration.ofSeconds(30));
            AtomicReference<Throwable> thrown = new AtomicReference<>(); AtomicReference<Boolean> flag = new AtomicReference<>();
            Thread worker = new Thread(() -> { try { client.ps(); } catch (Throwable t) { thrown.set(t); } flag.set(Thread.currentThread().isInterrupted()); });
            worker.start();
            while (server.requests.get() == 0) Thread.sleep(20);
            Thread.sleep(300);
            long start = System.nanoTime();
            worker.interrupt(); worker.join(4000);
            assertThat(worker.isAlive()).isFalse();
            assertThat(since(start)).isLessThan(3000);
            assertThat(thrown.get()).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("ENDPOINT_INTERRUPTED"));
            assertThat(flag.get()).isTrue();
        }
    }
    @Test void responseCapStaysAt8MiBIncludingEndlessStreams() throws Exception {
        int cap = 8 * 1024 * 1024;
        String prefix = "{\"template\":\"", suffix = "\"}";
        byte[] exact = bytes(prefix + "a".repeat(cap - prefix.length() - suffix.length()) + suffix);
        byte[] over = bytes(prefix + "a".repeat(cap - prefix.length() - suffix.length() + 1) + suffix);
        assertThat(exact).hasSize(cap); assertThat(over).hasSize(cap + 1);
        try (RawServer server = new RawServer((s, exchange, n) -> {
            switch (n) {
                case 1 -> { exchange.sendResponseHeaders(200, exact.length); exchange.getResponseBody().write(exact); }
                case 2 -> { exchange.sendResponseHeaders(200, over.length); exchange.getResponseBody().write(over); }
                default -> {
                    exchange.sendResponseHeaders(200, 0); OutputStream out = exchange.getResponseBody(); out.write(bytes(prefix));
                    byte[] chunk = new byte[64 * 1024]; Arrays.fill(chunk, (byte) 'a');
                    while (s.release.getCount() > 0) out.write(chunk);
                }
            }
        })) {
            OllamaChatClient client = new OllamaChatClient(server.endpoint(), Duration.ofSeconds(30));
            assertThat(client.showTemplate(MODEL)).hasSize(cap - prefix.length() - suffix.length());
            assertThat(failure(() -> client.showTemplate(MODEL)).getMessage()).isEqualTo("ENDPOINT_RESPONSE_TOO_LARGE");
            long start = System.nanoTime();
            assertThat(failure(() -> client.showTemplate(MODEL)).getMessage()).isEqualTo("ENDPOINT_RESPONSE_TOO_LARGE");
            assertThat(since(start)).isLessThan(20_000);
        }
    }

    // 2. usage·prompt_eval_count·context_length 는 JSON 정수·0 이상·int 표현 가능일 때만 받습니다.
    static ObjectNode chatWithUsage(Function<ObjectNode,ObjectNode> usage) {
        ObjectNode root = (ObjectNode) OllamaStub.chatResponse("{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"q\":null}", "stop", 50, 20);
        usage.apply((ObjectNode) root.get("usage")); return root;
    }
    @Test void malformedUsageNumbersAreRejectedWithFixedErrorAndNoRawText() throws Exception {
        try (OllamaStub stub = new OllamaStub()) {
            OllamaChatClient client = new OllamaChatClient(stub.endpoint(), Duration.ofSeconds(5));
            Preflight.Verified gate = Preflight.run(client, MODEL, "bge-m3").verified();
            assertThat(gate).isNotNull();
            Map<String,Function<ObjectNode,ObjectNode>> bad = new LinkedHashMap<>();
            bad.put("completion 128.9", u -> u.put("completion_tokens", 128.9));
            bad.put("completion 128.0", u -> u.put("completion_tokens", 128.0));
            bad.put("prompt 50.5", u -> u.put("prompt_tokens", 50.5));
            bad.put("prompt -1", u -> u.put("prompt_tokens", -1));
            bad.put("completion -1", u -> u.put("completion_tokens", -1));
            bad.put("completion overflow", u -> u.put("completion_tokens", 1L << 40));
            bad.put("completion text", u -> u.put("completion_tokens", "12"));
            bad.put("prompt missing", u -> { u.remove("prompt_tokens"); return u; });
            for (var entry : bad.entrySet()) {
                stub.chat = body -> chatWithUsage(entry.getValue());
                EvalException e = failure(() -> client.chat(gate, MODEL, MESSAGES, 128));
                assertThat(e.getMessage()).as(entry.getKey()).isEqualTo("ENDPOINT_RESPONSE_INVALID"); assertThat(e.exitCode()).isEqualTo(5);
                assertThat(e.toString()).doesNotContain(OllamaStub.SENTINEL);
            }
            stub.chat = body -> chatWithUsage(u -> u.put("prompt_tokens", 0).put("completion_tokens", 128));
            OllamaChatClient.ChatResult ok = client.chat(gate, MODEL, MESSAGES, 128);
            assertThat(ok.promptTokens()).isZero(); assertThat(ok.completionTokens()).isEqualTo(128);
            for (JsonNode value : List.of(NODES.numberNode(7.5), NODES.numberNode(7.0), NODES.numberNode(-1), NODES.numberNode(1L << 40), NODES.textNode("7"))) {
                stub.generate = body -> { ObjectNode r = (ObjectNode) OllamaStub.generateResponse(7); r.set("prompt_eval_count", value); return r; };
                assertThat(failure(() -> client.generateRawPromptEvalCount(gate, MODEL, "p")).getMessage()).as(value.toString()).isEqualTo("ENDPOINT_RESPONSE_INVALID");
            }
            stub.generate = body -> OllamaStub.generateResponse(0);
            assertThat(client.generateRawPromptEvalCount(gate, MODEL, "p")).isZero();
        }
    }
    @Test void fractionalOrNegativeContextLengthFailsPreflight() throws Exception {
        try (OllamaStub stub = new OllamaStub()) {
            OllamaChatClient client = new OllamaChatClient(stub.endpoint(), Duration.ofSeconds(5));
            for (JsonNode value : List.of(NODES.numberNode(4096.9), NODES.numberNode(4096.0), NODES.numberNode(-4096), NODES.numberNode(1L << 40), NODES.textNode("4096"))) {
                ObjectNode ps = (ObjectNode) OllamaStub.models(4096, 8192, OllamaStub.GEN_DIGEST);
                ((ObjectNode) ps.path("models").get(0)).set("context_length", value);
                stub.ps = ps;
                Preflight.Result result = Preflight.run(client, MODEL, "bge-m3");
                assertThat(result.verified()).as(value.toString()).isNull();
                assertThat(result.summary().ok()).isFalse(); assertThat(result.summary().reason()).as(value.toString()).isEqualTo("PREFLIGHT_RESPONSE_INVALID");
            }
            // bge-m3 값도 형식이 틀리면 응답 자체가 잘못된 것(4096 강제는 여전히 없음)
            ObjectNode ps = (ObjectNode) OllamaStub.models(4096, 8192, OllamaStub.GEN_DIGEST);
            ((ObjectNode) ps.path("models").get(1)).put("context_length", 8192.5);
            stub.ps = ps;
            assertThat(Preflight.run(client, MODEL, "bge-m3").summary().reason()).isEqualTo("PREFLIGHT_RESPONSE_INVALID");
            stub.ps = OllamaStub.models(4096, 8192, OllamaStub.GEN_DIGEST);
            assertThat(Preflight.run(client, MODEL, "bge-m3").summary().ok()).isTrue();
            stub.ps = OllamaStub.models(4096, null, OllamaStub.GEN_DIGEST);
            assertThat(Preflight.run(client, MODEL, "bge-m3").summary().ok()).isTrue();
        }
    }
    @Test void fractionalCompletionTokensCannotProduceABoundaryPass() throws Exception {
        F0TestRoot f = BoundaryGenJudgeTest.confirmedRoot(directory);
        Function<JsonNode,JsonNode> correct = StubAnswers.correct(f.root);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = body -> { ObjectNode r = (ObjectNode) correct.apply(body); ((ObjectNode) r.get("usage")).put("completion_tokens", 128.9); return r; };
            int exit = f.exec("boundary-gen", "--endpoint", stub.endpoint(), "--cases", "eval/f0/output-boundary-cases.jsonl", "--fixture", "eval/golden/tc-registry-fixture-v1.json",
                "--prompt", "eval/f0/prompts/classify-v1.txt", "--repeats", "2", "--run-id", "R9", "--out", "eval/f0/results/R9/boundary");
            assertThat(exit).isEqualTo(5); assertThat(f.lastError).isEqualTo("ENDPOINT_RESPONSE_INVALID");
            JsonNode partial = f.json("eval/f0/results/R9/boundary/boundary-results.json");
            assertThat(partial.path("complete").asBoolean()).isFalse(); assertThat(partial.path("abortReason").asText()).isEqualTo("ENDPOINT_RESPONSE_INVALID");
            partial.path("cases").forEach(c -> { assertThat(c.path("passRounds").asInt()).isZero(); assertThat(c.path("attempts")).isEmpty(); });
            assertThat(f.allOutput("eval/f0/results/R9")).doesNotContain(OllamaStub.SENTINEL).doesNotContain("128.9");
        }
    }
    // 3. 본문 지연으로 인한 시간 초과는 실패 표본(TIMEOUT)으로 남고 측정은 계속됩니다.
    @Test void stalledChatBodyIsRecordedAsTimeoutSampleAndRunContinues() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root); stub.stallChatBody = n -> n == 2;
            assertThat(F0RunTest.runB(f, stub, "R5")).isZero();
            JsonNode result = f.json("eval/f0/results/R5/B/f0-results.json");
            JsonNode timedOut = result.path("attempts").get(1);
            assertThat(timedOut.path("status").asText()).isEqualTo("TIMEOUT"); assertThat(timedOut.path("predicted").isNull()).isTrue();
            assertThat(timedOut.path("latencyMs").asLong()).isGreaterThanOrEqualTo(1900).isLessThan(6000);
            assertThat(result.path("complete").asBoolean()).isTrue(); assertThat(result.path("attempts")).hasSize(44);
            assertThat(f.allOutput("eval/f0/results/R5")).doesNotContain(OllamaStub.SENTINEL);
        }
    }
}
