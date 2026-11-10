package com.nori.ai.gateway.integration.source;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.*;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class SourceHttpTerminationTest {
    private static final class Fixture implements AutoCloseable {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger requests = new AtomicInteger(), stops = new AtomicInteger();
        final SimpleMeterRegistry meters = new SimpleMeterRegistry();
        final HttpServer server;
        final SourceHttpClient http;
        Fixture(boolean sendHeaders) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/stalled", exchange -> {
                requests.incrementAndGet();
                try {
                    if (sendHeaders) { exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush(); }
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("지연 응답 해제가 누락되었습니다"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                } finally { exchange.close(); }
            });
            server.start();
            var observations = SourceObservability.create(meters, new ObservationHandler<ClientRequestObservationContext>() {
                @Override public boolean supportsContext(Observation.Context context) { return context instanceof ClientRequestObservationContext; }
                @Override public void onStop(ClientRequestObservationContext context) { stops.incrementAndGet(); }
            });
            http = new SourceHttpClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "loki", observations);
        }
        URI uri() { return http.uri(List.of("stalled"), Map.of()); }
        @Override public void close() { release.countDown(); server.stop(0); meters.close(); }
    }
    @Test void deadlineDuringBodyReadReturnsAfterCallbackExitAndObservationStop() throws Exception {
        try (var fixture = new Fixture(true)) {
            AtomicBoolean callbackExited = new AtomicBoolean();
            long start = System.nanoTime();
            assertThatThrownBy(() -> fixture.http.stream(fixture.uri(), null, fixture.http.deadline(Duration.ofMillis(300)), stream -> {
                try { while (stream.read() != -1) { /* 마감에 의해 body가 닫힐 때까지 읽습니다. */ } return true; }
                finally { callbackExited.set(true); }
            })).isInstanceOf(SourceHttpClient.Failure.class);
            assertThat(callbackExited).isTrue(); assertThat(fixture.stops).hasValue(1); assertThat(fixture.requests).hasValue(1);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(fixture.meters.get("aigw.source.requests").timer().count()).isEqualTo(1);
        }
    }
    @Test void interruptedBodyReadReturnsOnlyAfterCallbackAndObservationHaveFinished() throws Exception {
        try (var fixture = new Fixture(true)) {
            CountDownLatch callbackEntered = new CountDownLatch(1), done = new CountDownLatch(1);
            AtomicBoolean callbackExited = new AtomicBoolean(), interruptPreserved = new AtomicBoolean();
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread caller = Thread.ofPlatform().start(() -> {
                try {
                    fixture.http.stream(fixture.uri(), null, fixture.http.deadline(Duration.ofSeconds(30)), stream -> {
                        callbackEntered.countDown();
                        try { while (stream.read() != -1) { /* 실제 응답 읽기를 중단시킵니다. */ } return true; }
                        finally { callbackExited.set(true); }
                    });
                } catch (Throwable failure) { outcome.set(failure); }
                finally { interruptPreserved.set(Thread.currentThread().isInterrupted()); done.countDown(); }
            });
            try {
                assertThat(callbackEntered.await(3, TimeUnit.SECONDS)).isTrue(); caller.interrupt();
                assertThat(done.await(3, TimeUnit.SECONDS)).isTrue(); caller.join();
                assertThat(outcome.get()).isInstanceOf(SourceHttpClient.Failure.class);
                assertThat(callbackExited).isTrue(); assertThat(interruptPreserved).isTrue();
                assertThat(fixture.stops).hasValue(1); assertThat(fixture.requests).hasValue(1);
            } finally { fixture.release.countDown(); caller.interrupt(); caller.join(3000); }
        }
    }
    @Test void interruptedHeaderReadCompletesItsObservationAndDoesNotRetry() throws Exception {
        try (var fixture = new Fixture(false)) {
            CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread caller = Thread.ofPlatform().start(() -> {
                try { fixture.http.json(fixture.uri(), null, fixture.http.deadline(Duration.ofSeconds(30))); }
                catch (Throwable failure) { outcome.set(failure); }
                finally { done.countDown(); }
            });
            try {
                assertThat(fixture.entered.await(3, TimeUnit.SECONDS)).isTrue(); caller.interrupt();
                assertThat(done.await(3, TimeUnit.SECONDS)).isTrue(); caller.join();
                assertThat(outcome.get()).isInstanceOf(SourceHttpClient.Failure.class);
                assertThat(fixture.stops).hasValue(1); assertThat(fixture.requests).hasValue(1);
            } finally { fixture.release.countDown(); caller.interrupt(); caller.join(3000); }
        }
    }
}
