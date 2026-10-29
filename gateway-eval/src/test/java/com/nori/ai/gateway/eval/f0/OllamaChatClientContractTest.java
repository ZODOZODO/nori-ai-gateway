package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

class OllamaChatClientContractTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    @TempDir Path directory;

    static void noKeyAnywhere(JsonNode node, String key) {
        if (node == null) return;
        if (node.isObject()) { assertThat(node.has(key)).as(key).isFalse(); node.forEach(child -> noKeyAnywhere(child, key)); }
        else if (node.isArray()) node.forEach(child -> noKeyAnywhere(child, key));
    }

    @Test void bothPathsSendOnlyAllowedPathsAndExactBodyKeysWithoutNumCtx() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0RunTest.runB(f, stub, "R1")).isZero();
            assertThat(F0RunTest.run(f, stub, "R1", "A", "eval/f0/prompts/plan-v1.txt", "2")).isZero();
            f.env.put("AIGW_PARENT_RUN_ID", "R1");
            assertThat(f.exec("gguf-count", "--endpoint", stub.endpoint(), "--cases", "eval/f0/output-boundary-cases.jsonl", "--out", "eval/f0/results/R1/gguf")).isZero();
            assertThat(stub.requests).extracting(OllamaStub.Request::path).isSubsetOf(OllamaChatClient.PATHS).doesNotContain("/v1/embeddings", "/api/embed", "/api/embeddings");
            Set<Integer> maxTokens = new HashSet<>();
            for (OllamaStub.Request r : stub.requests) {
                noKeyAnywhere(r.body(), "num_ctx");
                switch (r.path()) {
                    case "/v1/chat/completions" -> {
                        assertThat(r.method()).isEqualTo("POST"); assertThat(GoldenSet.keys(r.body())).isEqualTo(Set.of("model", "messages", "temperature", "max_tokens", "stream"));
                        assertThat(r.body().path("model").asText()).isEqualTo("qwen3:4b-instruct"); assertThat(r.body().path("temperature").asDouble()).isZero(); assertThat(r.body().path("stream").asBoolean()).isFalse();
                        maxTokens.add(r.body().path("max_tokens").asInt());
                        r.body().path("messages").forEach(m -> assertThat(GoldenSet.keys(m)).isEqualTo(Set.of("role", "content")));
                    }
                    case "/api/generate" -> {
                        assertThat(GoldenSet.keys(r.body())).isEqualTo(Set.of("model", "prompt", "raw", "stream", "options")); assertThat(GoldenSet.keys(r.body().path("options"))).isEqualTo(Set.of("num_predict"));
                        assertThat(r.body().path("raw").asBoolean()).isTrue(); assertThat(r.body().path("options").path("num_predict").asInt()).isEqualTo(1);
                    }
                    case "/api/show" -> assertThat(GoldenSet.keys(r.body())).isEqualTo(Set.of("model"));
                    default -> { assertThat(r.method()).isEqualTo("GET"); assertThat(r.body()).isNull(); }
                }
            }
            assertThat(maxTokens).containsExactlyInAnyOrder(128, 160);
            // gguf-count: 각 계수 직전 flush-n(첫 토큰부터 다른 raw 프롬프트)
            List<String> prompts = stub.requests.stream().filter(r -> r.path().equals("/api/generate")).map(r -> r.body().path("prompt").asText()).toList();
            for (int i = 0; i < prompts.size(); i += 2) assertThat(prompts.get(i)).isEqualTo("flush-" + (i / 2 + 1));
            assertThat(prompts.get(1)).startsWith("{\"t\":\"INC\",\"eqp\":\"PHOTO_001\"");
        }
    }
    @Test void clientRejectsNonLoopbackEndpointsAndInferenceWithoutPreflightToken() throws Exception {
        for (String endpoint : List.of("http://10.0.0.1:11434", "https://127.0.0.1:11434", "http://127.0.0.1:11434/api", "http://user@127.0.0.1:11434", "http://127.0.0.1", "ftp://127.0.0.1:11434", "http://example.com:11434"))
            assertThatThrownBy(() -> new OllamaChatClient(endpoint, Duration.ofSeconds(1))).as(endpoint).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("ENDPOINT_INVALID"));
        try (OllamaStub stub = new OllamaStub()) {
            OllamaChatClient client = new OllamaChatClient(stub.endpoint(), Duration.ofSeconds(2));
            assertThatThrownBy(() -> client.chat(null, "qwen3:4b-instruct", List.of(Map.of("role", "user", "content", "x")), 1)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> client.generateRawPromptEvalCount(null, "qwen3:4b-instruct", "x")).isInstanceOf(NullPointerException.class);
            assertThat(stub.requests).isEmpty();
            stub.ps = com.nori.ai.gateway.eval.io.Json.MAPPER.createObjectNode().put("models", "not-array");
            assertThat(Preflight.run(client, "qwen3:4b-instruct", "bge-m3").summary().reason()).isEqualTo("PREFLIGHT_RESPONSE_INVALID");
        }
    }
    @Test void harnessSourcesContainNoManagementOrEmbeddingEndpointAndNoRawOutputPrinting() throws Exception {
        Pattern literal = Pattern.compile("\"(/(?:api|v1)/[A-Za-z0-9/_-]*)\"");
        List<Path> sources = new ArrayList<>();
        for (String dir : List.of("gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0", "gateway-eval/src/main/java/com/nori/ai/gateway/eval/f3"))
            try (var walk = Files.walk(ROOT.resolve(dir))) { walk.filter(p -> p.toString().endsWith(".java")).forEach(sources::add); }
        assertThat(sources).hasSizeGreaterThan(10);
        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            for (String forbidden : List.of("/api/pull", "/api/create", "/api/delete", "/api/copy", "/api/push", "/api/embed", "/v1/embeddings", "/api/chat\"", "num_ctx", "keep_alive", "printStackTrace", "System.out", "System.err"))
                assertThat(text).as(source + " " + forbidden).doesNotContain(forbidden);
            var matcher = literal.matcher(text);
            while (matcher.find()) assertThat(OllamaChatClient.PATHS).as(source.toString()).contains(matcher.group(1));
            if (source.toString().contains("f3")) assertThat(text).doesNotContain("HttpClient").doesNotContain("java.net");
        }
    }
}
