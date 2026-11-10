package com.nori.ai.gateway.integration.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.PreCallBudgetAssert;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.budget.TruncationDetector;
import com.nori.ai.gateway.core.job.GenerationSemaphore;
import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.integration.inference.OllamaStubServer.Disconnect;
import com.nori.ai.gateway.integration.inference.OllamaStubServer.Json;
import com.openai.client.OpenAIClient;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 실제 설정 메서드로 만든 SDK·Spring AI 모델을 JDK HttpServer stub 에 붙여 wire 계약과 재시도 0 을 확인한다. */
class InferenceTransportContractTest {
    static final String DIGEST = "a".repeat(64);
    static final int CLASSIFY_MAX = TokenBudgetPolicy.current().budget(CallType.CLASSIFY).requestMaxTokens();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private OllamaStubServer stub;
    private OpenAIClient sdk;
    private InferenceClient client;

    static String completion(String content, int promptTokens) {
        return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"qwen3:4b\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
            + content + "\",\"reasoning\":\"사고문\"},\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":" + promptTokens + ",\"completion_tokens\":5,\"total_tokens\":" + (promptTokens + 5) + "}}";
    }
    static final String EMPTY_CHOICES = "{\"id\":\"chatcmpl-2\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"qwen3:4b\",\"choices\":[],\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":0,\"total_tokens\":50}}";
    static final String EMBEDDING = "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.25,0.5,0.75]}],\"model\":\"bge-m3\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";

    private void start(Function<OllamaStubServer.Received,OllamaStubServer.Reply> responder) {
        stub = new OllamaStubServer(responder);
        var configuration = new InferenceModelConfiguration();
        var endpoint = new InferenceEndpointConfig(stub.baseUrl(), DIGEST);
        sdk = configuration.inferenceOpenAiClient(endpoint);
        var chat = configuration.openAiChatModel(sdk, endpoint, TokenBudgetPolicy.current());
        client = new SpringAiOpenAiInferenceClient(configuration.springAiChatTransport(chat), configuration.openAiEmbeddingModel(sdk, endpoint),
            new PreCallBudgetAssert(messages -> 50, TokenBudgetPolicy.current()), new TruncationDetector(), new GenerationSemaphore(1));
    }
    @AfterEach void close() { if (sdk != null) sdk.close(); if (stub != null) stub.close(); }

    private InferenceClient.ChatOutcome chat() {
        return client.chat(CallType.CLASSIFY, List.of(new ChatMessage(ChatMessage.Role.SYSTEM, "지시"), new ChatMessage(ChatMessage.Role.USER, "질문")), CLASSIFY_MAX);
    }

    @Test void chatAndEmbeddingUseOnlyTheV1PathsWithAllowedBodyKeysNoNumCtxAndThePlaceholderAuthorization() throws Exception {
        start(request -> request.path().equals("/v1/embeddings") ? new Json(200, EMBEDDING) : new Json(200, completion("답변", 50)));
        assertThat(chat()).isInstanceOfSatisfying(InferenceClient.Completed.class, done -> {
            assertThat(done.content()).isEqualTo("답변").doesNotContain("사고문"); assertThat(done.finishReason()).isEqualTo("length");
            assertThat(done.promptTokens()).isEqualTo(50); assertThat(done.completionTokens()).isEqualTo(5);
        });
        assertThat(client.embed(List.of("probe"))).singleElement().satisfies(vector -> assertThat(vector).containsExactly(0.25f, 0.5f, 0.75f));
        assertThat(stub.received()).extracting(OllamaStubServer.Received::path).containsExactly("/v1/chat/completions", "/v1/embeddings");
        var chatRequest = stub.received().getFirst();
        assertThat(chatRequest.method()).isEqualTo("POST");
        JsonNode body = MAPPER.readTree(chatRequest.body());
        assertThat(Set.copyOf(iterator(body))).isEqualTo(Set.of("model", "messages", "max_tokens", "temperature"));
        assertThat(body.get("model").asText()).isEqualTo("qwen3:4b");
        assertThat(body.get("max_tokens").asInt()).isEqualTo(CLASSIFY_MAX);
        assertThat(body.get("temperature").asDouble()).isZero();
        assertThat(body.get("messages")).extracting(node -> node.get("role").asText()).containsExactly("system", "user");
        assertThat(chatRequest.body()).doesNotContain("num_ctx", "\"options\"", "\"stream\"");
        JsonNode embedding = MAPPER.readTree(stub.received().get(1).body());
        assertThat(Set.copyOf(iterator(embedding))).isEqualTo(Set.of("model", "input", "encoding_format"));
        assertThat(embedding.get("model").asText()).isEqualTo("bge-m3");
        for (var request : stub.received()) assertThat(request.header("Authorization")).isEqualTo("Bearer " + InferenceModelConfiguration.NO_AUTH_PLACEHOLDER);
    }

    @Test void serverErrorDisconnectAndEmptyChoicesProduceExactlyOneRequestPerAttempt() {
        start(request -> new Json(200, completion("답변", 50)));
        assertThat(chat()).isInstanceOf(InferenceClient.Completed.class);
        assertThat(chat()).isInstanceOf(InferenceClient.Completed.class);
        assertThat(stub.count("/v1/chat/completions")).isEqualTo(2);
        stub.respond(request -> new Json(500, "{\"error\":{\"message\":\"boom\"}}"));
        assertThat(chat()).isInstanceOfSatisfying(InferenceClient.CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(InferenceClient.FailureReason.TRANSPORT));
        assertThat(stub.count("/v1/chat/completions")).isEqualTo(3);
        stub.respond(request -> new Disconnect());
        assertThat(chat()).isInstanceOfSatisfying(InferenceClient.CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(InferenceClient.FailureReason.TRANSPORT));
        assertThat(stub.count("/v1/chat/completions")).isEqualTo(4);
        stub.respond(request -> new Json(200, EMPTY_CHOICES));
        assertThat(chat()).isInstanceOfSatisfying(InferenceClient.CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(InferenceClient.FailureReason.EMPTY_RESPONSE));
        assertThat(stub.count("/v1/chat/completions")).isEqualTo(5);
        stub.respond(request -> new Json(200, completion("답변", 49)));
        assertThat(chat()).isInstanceOfSatisfying(InferenceClient.CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(InferenceClient.FailureReason.TRUNCATED));
        assertThat(stub.count("/v1/chat/completions")).isEqualTo(6);
        stub.respond(request -> new Disconnect());
        assertThatThrownBy(() -> client.embed(List.of("probe"))).isInstanceOfSatisfying(InferenceClient.EmbeddingFailed.class,
            failed -> assertThat(failed.reason()).isEqualTo(InferenceClient.FailureReason.TRANSPORT));
        assertThat(stub.count("/v1/embeddings")).isEqualTo(1);
        assertThat(stub.received()).extracting(OllamaStubServer.Received::path).containsOnly("/v1/chat/completions", "/v1/embeddings");
    }

    /** pool 을 쓰는 Spring AI 전송기에서 재전송 불가 본문이 OkHttp 연결 실패 자동 재시도를 막는지 대조군과 함께 확인한다. */
    @Test void singleAttemptBodyStopsOkHttpConnectionRetryOnAReusedPooledConnection() {
        start(request -> new Json(200, "{}"));
        try (var pooled = org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient.builder().timeout(InferenceModelConfiguration.TIMEOUT).build()) {
            var guarded = new InferenceModelConfiguration.SingleAttemptHttpClient(pooled);
            send(guarded);
            stub.respond(request -> new Disconnect());
            assertThatThrownBy(() -> send(guarded)).isInstanceOf(RuntimeException.class);
            assertThat(stub.count("/v1/chat/completions")).as("보호된 전송: 시도당 수신 1").isEqualTo(2);
            stub.respond(request -> new Json(200, "{}"));
            send(pooled);
            stub.respond(request -> new Disconnect());
            assertThatThrownBy(() -> send(pooled)).isInstanceOf(RuntimeException.class);
            assertThat(stub.count("/v1/chat/completions")).as("대조군: 재사용 연결 실패를 OkHttp 가 다시 보낸다").isEqualTo(5);
        }
    }
    private void send(com.openai.core.http.HttpClient http) {
        byte[] json = "{\"model\":\"qwen3:4b\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var body = new com.openai.core.http.HttpRequestBody() {
            @Override public void writeTo(java.io.OutputStream output) { try { output.write(json); } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); } }
            @Override public String contentType() { return "application/json"; }
            @Override public long contentLength() { return json.length; }
            @Override public boolean repeatable() { return true; }
            @Override public void close() {}
        };
        var request = com.openai.core.http.HttpRequest.builder().method(com.openai.core.http.HttpMethod.POST).baseUrl(stub.baseUrl())
            .addPathSegments("v1", "chat", "completions").body(body).build();
        try (var response = http.execute(request, com.openai.core.RequestOptions.none())) { assertThat(response.statusCode()).isEqualTo(200); }
    }

    private static List<String> iterator(JsonNode node) { var names = new java.util.ArrayList<String>(); node.fieldNames().forEachRemaining(names::add); return names; }
}
