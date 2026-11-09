package com.nori.ai.gateway.integration.inference;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.LogLevel;
import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 모델을 starter·자동설정 없이 직접 만든다(Plan P19, P04 ⓐ~ⓕ). SDK client 는 이 설정이 명시한 값만 쓰며
 * {@code OPENAI_*} 환경변수·기본 api.openai.com·SDK 로그(OPENAI_LOG)를 채택하지 않는다.
 * 재시도 차단: ①SDK {@code maxRetries(0)} ②Spring retry wrapper 없음(P04 ⓒ) ③전송 계층 {@link SingleAttemptHttpClient}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
public class InferenceModelConfiguration {
    /**
     * Ollama endpoint 는 무인증이지만(SPEC §26.1) SDK {@code ClientOptions.Builder} 는 credential 이 없으면 생성을 거부한다
     * ("At least one credential source must be specified"). 비밀이 아닌 고정 값이며 Secret·env 에서 읽지 않는다.
     */
    static final String NO_AUTH_PLACEHOLDER = "aigw-no-auth";
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    // P04 ⓔ: 별도 connect timeout 수단이 있으므로 Source 전송기(SourceHttpClient)와 같은 3s 를 쓴다.
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Timeout TIMEOUT = Timeout.builder().connect(CONNECT_TIMEOUT).read(REQUEST_TIMEOUT).write(REQUEST_TIMEOUT).request(REQUEST_TIMEOUT).build();

    @Bean OpenAIClient inferenceOpenAiClient(InferenceEndpointConfig endpoint) {
        // pool 을 두지 않아 끊긴 유휴 연결을 재사용하다 실패하는 경로를 없앤다(재전송 불가 본문과 함께 쓴다).
        var transport = SpringAiOpenAiHttpClient.builder().timeout(TIMEOUT).maxIdleConnections(0).keepAliveDuration(Duration.ofSeconds(1)).build();
        return new OpenAIClientImpl(ClientOptions.builder().httpClient(new SingleAttemptHttpClient(transport)).baseUrl(endpoint.sdkBaseUrl())
            .apiKey(NO_AUTH_PLACEHOLDER).maxRetries(0).timeout(TIMEOUT).logLevel(LogLevel.OFF).build());
    }

    @Bean OpenAiChatModel openAiChatModel(OpenAIClient inferenceOpenAiClient, InferenceEndpointConfig endpoint, TokenBudgetPolicy policy) {
        // temperature 0 은 진단 이력의 구현 기본값이다(Plan P19 v4.1 대체). 요청별 max_tokens 는 SpringAiChatTransport 가 덮어쓴다.
        return OpenAiChatModel.builder().openAiClient(inferenceOpenAiClient).openAiClientAsync(inferenceOpenAiClient.async())
            .options(OpenAiChatOptions.builder().baseUrl(endpoint.sdkBaseUrl()).apiKey(NO_AUTH_PLACEHOLDER).model(InferenceEndpointConfig.GENERATION_MODEL)
                .temperature(0.0).maxTokens(policy.budget(CallType.CLASSIFY).requestMaxTokens()).maxRetries(0).timeout(REQUEST_TIMEOUT).build())
            .build();
    }

    @Bean OpenAiEmbeddingModel openAiEmbeddingModel(OpenAIClient inferenceOpenAiClient, InferenceEndpointConfig endpoint) {
        return OpenAiEmbeddingModel.builder().openAiClient(inferenceOpenAiClient)
            .options(OpenAiEmbeddingOptions.builder().encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT).baseUrl(endpoint.sdkBaseUrl())
                .apiKey(NO_AUTH_PLACEHOLDER).model(InferenceEndpointConfig.EMBEDDING_MODEL).maxRetries(0).timeout(REQUEST_TIMEOUT).build())
            .build();
    }

    @Bean @Qualifier("liveChatTransport") SpringAiChatTransport springAiChatTransport(OpenAiChatModel openAiChatModel) {
        return new SpringAiChatTransport(openAiChatModel);
    }

    /**
     * Spring AI 전송기는 OkHttp {@code retryOnConnectionFailure(true)} 를 해제할 수단이 없다(P04 ⓓ). 요청 본문을 재전송 불가로 표시하면
     * OkHttp 는 본문 송신이 시작된 요청을 다시 보내지 않는다. 모델 옵션의 요청별 timeout(Duration) 은 connect 값을 비우므로 고정 timeout 으로 되돌린다.
     */
    static final class SingleAttemptHttpClient implements HttpClient {
        private final HttpClient delegate;
        SingleAttemptHttpClient(HttpClient delegate) { this.delegate = delegate; }
        @Override public HttpResponse execute(HttpRequest request, RequestOptions options) { return delegate.execute(oneShot(request), fixed(options)); }
        @Override public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) { return delegate.executeAsync(oneShot(request), fixed(options)); }
        @Override public void close() { delegate.close(); }

        private static RequestOptions fixed(RequestOptions options) {
            var builder = RequestOptions.builder().timeout(TIMEOUT);
            if (options.getResponseValidation() != null) builder.responseValidation(options.getResponseValidation());
            return builder.build();
        }
        private static HttpRequest oneShot(HttpRequest request) {
            HttpRequestBody body = request.body();
            if (body == null) return request;
            return request.toBuilder().body(new HttpRequestBody() {
                @Override public void writeTo(OutputStream output) { body.writeTo(output); }
                @Override public String contentType() { return body.contentType(); }
                @Override public long contentLength() { return body.contentLength(); }
                @Override public boolean repeatable() { return false; }
                @Override public void close() { body.close(); }
            }).build();
        }
    }
}
