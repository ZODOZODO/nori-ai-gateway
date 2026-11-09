package com.nori.ai.gateway.integration.inference;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.PreCallBudgetAssert;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.budget.TruncationDetector;
import com.nori.ai.gateway.core.job.GenerationSemaphore;
import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.core.port.InferenceClient.CallFailed;
import com.nori.ai.gateway.core.port.InferenceClient.Completed;
import com.nori.ai.gateway.core.port.InferenceClient.FailureReason;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import static org.assertj.core.api.Assertions.*;

/** Client 의 호출 순서(예산 assert → 생성 슬롯 → 전송 1회 → 잘림 감지)와 실패 변환을 전송 대역으로 확인한다. HTTP 계약은 InferenceRequestContractIT. */
class SpringAiOpenAiInferenceClientTest {
    private static final String SENTINEL = "client-sentinel-7f3a";
    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatMessage.Role.USER, "질문 " + SENTINEL));
    private static final int CLASSIFY_MAX = TokenBudgetPolicy.current().budget(CallType.CLASSIFY).requestMaxTokens();
    private final AtomicInteger counted = new AtomicInteger(100);
    private final AtomicInteger sends = new AtomicInteger();
    private final GenerationSemaphore generation = new GenerationSemaphore(1);
    private final OpenAiEmbeddingModel unusedEmbeddings = OpenAiEmbeddingModel.builder().openAiClient(
        new InferenceModelConfiguration().inferenceOpenAiClient(new InferenceEndpointConfig("http://127.0.0.1:1", "a".repeat(64))))
        .options(OpenAiEmbeddingOptions.builder().model("bge-m3").build()).build();

    private InferenceClient client(ChatTransport transport) {
        return new SpringAiOpenAiInferenceClient((type, messages, maxTokens) -> { sends.incrementAndGet(); return transport.send(type, messages, maxTokens); },
            unusedEmbeddings, new PreCallBudgetAssert(messages -> counted.get(), TokenBudgetPolicy.current()), new TruncationDetector(), generation);
    }

    @Test void budgetExceededReturnsWithoutSendingAndPolicyMismatchIsRejectedBeforeSending() {
        counted.set(3_073);
        assertThat(client((type, messages, max) -> fail("전송 금지")).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX)).isEqualTo(InferenceClient.ChatOutcome.budgetExceeded(3_073));
        counted.set(10);
        assertThatThrownBy(() -> client((type, messages, max) -> fail("전송 금지")).chat(CallType.CLASSIFY, MESSAGES, 128)).isInstanceOf(IllegalArgumentException.class);
        assertThat(sends).hasValue(0);
    }

    @Test void completedCarriesAnswerUsageAndHoldsTheGenerationSlotOnlyDuringTheSend() {
        var outcome = client((type, messages, max) -> {
            assertThat(generation.availablePermits()).as("전송 중 생성 슬롯 점유").isZero();
            assertThat(max).isEqualTo(CLASSIFY_MAX);
            return new ChatTransport.Response("{\"t\":\"GQ\"}", "stop", 100, 12);
        }).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX);
        assertThat(outcome).isInstanceOfSatisfying(Completed.class, done -> {
            assertThat(done.content()).isEqualTo("{\"t\":\"GQ\"}"); assertThat(done.finishReason()).isEqualTo("stop");
            assertThat(done.promptTokens()).isEqualTo(100); assertThat(done.completionTokens()).isEqualTo(12); assertThat(done.latency().isNegative()).isFalse();
            assertThat(done.toString()).doesNotContain("GQ");
        });
        assertThat(generation.availablePermits()).isOne();
        assertThat(sends).hasValue(1);
    }

    @Test void truncatedMissingUsageEmptyAndTransportFailuresAreCallFailuresWithClassNameOnly() {
        assertThat(client((t, m, x) -> new ChatTransport.Response("답", "stop", 99, 1)).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX))
            .isInstanceOfSatisfying(CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(FailureReason.TRUNCATED));
        assertThat(client((t, m, x) -> new ChatTransport.Response("답", "stop", 101, 1)).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX)).isInstanceOf(Completed.class);
        assertThat(client((t, m, x) -> new ChatTransport.Response("답", "stop", null, null)).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX))
            .isInstanceOfSatisfying(CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(FailureReason.TRUNCATED));
        assertThat(client((t, m, x) -> new ChatTransport.Response(null, null, 100, 0)).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX))
            .isInstanceOfSatisfying(CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(FailureReason.EMPTY_RESPONSE));
        var failure = client((t, m, x) -> { throw new IllegalStateException("응답 " + SENTINEL); }).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX);
        assertThat(failure).isInstanceOfSatisfying(CallFailed.class, failed -> {
            assertThat(failed.reason()).isEqualTo(FailureReason.TRANSPORT); assertThat(failed.errorClass()).isEqualTo(IllegalStateException.class.getName());
        });
        assertThat(failure.toString()).doesNotContain(SENTINEL);
        assertThat(generation.availablePermits()).isOne();
        assertThat(sends).hasValue(5);
    }

    @Test void interruptedWhileWaitingForTheGenerationSlotDoesNotSend() throws Exception {
        generation.acquire();
        try {
            Thread.currentThread().interrupt();
            assertThat(client((t, m, x) -> fail("전송 금지")).chat(CallType.CLASSIFY, MESSAGES, CLASSIFY_MAX))
                .isInstanceOfSatisfying(CallFailed.class, failed -> assertThat(failed.reason()).isEqualTo(FailureReason.INTERRUPTED));
            assertThat(Thread.interrupted()).isTrue();
        } finally { generation.release(); }
        assertThat(sends).hasValue(0);
    }
}
