package com.nori.ai.gateway.integration.loki;

import com.nori.ai.gateway.integration.ConnectorTestSupport;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

class SourceHttpClientTest {
    @Test void redirectCannotForwardSecretOrCreateASecondSourceRead() throws Exception {
        try (var source = new ConnectorTestSupport(); var destination = new ConnectorTestSupport()) {
            source.responder = req -> new Response(302, "비공개", Map.of("Location", destination.base() + "/redirected"));
            SourceHttpClient http = source.http("gitlab");
            assertThatThrownBy(() -> http.json(http.uri(List.of("api", "test"), Map.of()), "stub-read-token", http.deadline(Duration.ofSeconds(30))))
                .isInstanceOf(SourceHttpClient.Failure.class).hasMessage("Source 조회를 완료하지 못했습니다").hasNoCause();
            assertThat(source.requests).hasSize(1); assertThat(destination.requests).isEmpty();
        }
    }
    @Test void anAbsoluteUriFromAnotherAuthorityIsRejectedBeforeAnyRead() throws Exception {
        try (var source = new ConnectorTestSupport(); var destination = new ConnectorTestSupport()) {
            SourceHttpClient http = source.http("loki");
            assertThatThrownBy(() -> http.json(destination.base(), null, http.deadline(Duration.ofSeconds(30))))
                .isInstanceOf(SourceHttpClient.Failure.class);
            assertThat(source.requests).isEmpty(); assertThat(destination.requests).isEmpty();
        }
    }
    @Test void transportDefaultsMatchApprovedConnectAndReadTimeouts() {
        assertThat(SourceHttpClient.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(3));
        assertThat(SourceHttpClient.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
        var loki = new LokiProperties();
        assertThat(loki.splitInterval()).isEqualTo(Duration.ofHours(6)); assertThat(loki.limitPerQuery()).isEqualTo(500);
        assertThat(loki.maxLinesTotal()).isEqualTo(1000); assertThat(loki.stepDeadline()).isEqualTo(Duration.ofSeconds(30));
    }
    @Test void aBlockedResponseCannotOverrunTheOverallStepDeadline() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (var source = new ConnectorTestSupport()) {
            source.responder = req -> {
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("응답 해제 신호가 누락되었습니다"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                return new Response(200, emptyLoki());
            };
            var http = source.http("loki");
            long start = System.nanoTime();
            try {
                assertThatThrownBy(() -> http.json(http.uri(List.of("test"), Map.of()), null, http.deadline(Duration.ofMillis(200))))
                    .isInstanceOfSatisfying(SourceHttpClient.Failure.class, failure -> assertThat(failure.deadline()).isTrue());
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
                assertThat(source.requests).hasSize(1);
            } finally { release.countDown(); }
        }
    }
}
