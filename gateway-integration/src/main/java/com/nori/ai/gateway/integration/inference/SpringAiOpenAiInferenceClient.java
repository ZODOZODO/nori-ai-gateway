package com.nori.ai.gateway.integration.inference;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.PreCallBudgetAssert;
import com.nori.ai.gateway.core.budget.TruncationDetector;
import com.nori.ai.gateway.core.job.GenerationSemaphore;
import com.nori.ai.gateway.core.port.InferenceClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * {@link InferenceClient} 의 유일한 구현. chat 은 매 호출(재시도 포함) 직전 {@link PreCallBudgetAssert} 를 거쳐 초과면 전송 0 으로 돌려주고,
 * 생성 동시성 슬롯 안에서 {@link ChatTransport} 를 1회 호출한 뒤 {@link TruncationDetector} 로 서버 잘림을 확인한다.
 * 예외는 클래스명만 남긴다(메시지·응답 본문을 결과·로그에 싣지 않음).
 */
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
public final class SpringAiOpenAiInferenceClient implements InferenceClient {
    private final ChatTransport transport;
    private final OpenAiEmbeddingModel embeddings;
    private final PreCallBudgetAssert budget;
    private final TruncationDetector truncation;
    private final GenerationSemaphore generation;

    public SpringAiOpenAiInferenceClient(ChatTransport transport, OpenAiEmbeddingModel embeddings, PreCallBudgetAssert budget,
            TruncationDetector truncation, GenerationSemaphore generation) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.embeddings = Objects.requireNonNull(embeddings, "embeddings");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.truncation = Objects.requireNonNull(truncation, "truncation");
        this.generation = Objects.requireNonNull(generation, "generation");
    }

    @Override public ChatOutcome chat(CallType type, List<ChatMessage> messages, int maxTokens) {
        var checked = budget.check(type, messages, maxTokens);
        if (checked instanceof PreCallBudgetAssert.Exceeded exceeded) return ChatOutcome.budgetExceeded(exceeded.count());
        long started = System.nanoTime();
        try { generation.acquire(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return failed(FailureReason.INTERRUPTED, null, started); }
        final ChatTransport.Response response;
        try { response = transport.send(type, List.copyOf(messages), maxTokens); }
        catch (RuntimeException failure) { return failed(FailureReason.TRANSPORT, failure.getClass().getName(), started); }
        finally { generation.release(); }
        Duration latency = Duration.ofNanos(System.nanoTime() - started);
        if (response == null || response.content() == null || response.finishReason() == null) return new CallFailed(FailureReason.EMPTY_RESPONSE, null, latency);
        // usage 가 없으면 잘림을 배제할 수 없으므로 잘림과 같이 실패로 본다.
        if (response.promptTokens() == null || response.completionTokens() == null
            || truncation.check(checked.count(), response.promptTokens()) == TruncationDetector.Result.TRUNCATED)
            return new CallFailed(FailureReason.TRUNCATED, null, latency);
        return new Completed(response.content(), response.finishReason(), response.promptTokens(), response.completionTokens(), latency);
    }

    @Override public List<float[]> embed(List<String> inputs) {
        var copy = List.copyOf(inputs);
        final List<float[]> vectors;
        try { vectors = embeddings.embed(copy); }
        catch (RuntimeException failure) { throw new EmbeddingFailed(FailureReason.TRANSPORT, failure.getClass().getName()); }
        if (vectors == null || vectors.size() != copy.size() || vectors.stream().anyMatch(vector -> vector == null || vector.length == 0))
            throw new EmbeddingFailed(FailureReason.EMPTY_RESPONSE, null);
        return List.copyOf(vectors);
    }

    private static CallFailed failed(FailureReason reason, String errorClass, long started) {
        return new CallFailed(reason, errorClass, Duration.ofNanos(System.nanoTime() - started));
    }
}
