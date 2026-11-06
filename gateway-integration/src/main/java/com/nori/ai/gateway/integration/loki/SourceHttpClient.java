package com.nori.ai.gateway.integration.loki;

import com.fasterxml.jackson.databind.*;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.logging.*;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpConnectTimeoutException;

/** 허용 Source 전용 전송기입니다. URL을 포함하는 저수준 예외는 관측 전에 제거합니다. */
public final class SourceHttpClient {
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_JSON_BYTES = 16 * 1024 * 1024;
    private final URI base;
    private final String sourceId;
    private final ObservationRegistry observations;
    private final LongSupplier nanoTime;
    private final ObjectMapper mapper = new ObjectMapper();
    public static final class Failure extends RuntimeException {
        private final boolean retryable;
        private final boolean deadline;
        private final boolean httpNotFound;
        public Failure(boolean retryable, boolean deadline) {
            this(retryable,deadline,false);
        }
        private Failure(boolean retryable,boolean deadline,boolean httpNotFound) {
            super(deadline ? "Source 조회 마감에 도달했습니다" : "Source 조회를 완료하지 못했습니다", null, false, false);
            this.retryable = retryable; this.deadline = deadline;this.httpNotFound=httpNotFound;
        }
        public boolean deadline() { return deadline; }
        public boolean httpNotFound(){return httpNotFound;}
    }
    public record Page(JsonNode body, String nextPage) {}
    @FunctionalInterface public interface StreamReader<T> { T read(InputStream stream) throws IOException; }
    public SourceHttpClient(URI base, String sourceId, SourceObservability observations) {
        this(base, sourceId, observations, System::nanoTime);
    }
    public SourceHttpClient(URI base, String sourceId, SourceObservability observations, LongSupplier nanoTime) {
        if (base == null || !Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                || base.getRawUserInfo() != null || base.getRawQuery() != null || base.getRawFragment() != null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))) throw new IllegalArgumentException("Source 주소가 잘못되었습니다");
        new AllowlistClientRequestObservationConvention(sourceId);
        this.base = base; this.sourceId = sourceId; this.observations = Objects.requireNonNull(observations).registry();
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }
    public long nowNanos() { return nanoTime.getAsLong(); }
    public long deadline(Duration budget) { return Math.addExact(nowNanos(), budget.toNanos()); }
    public boolean expired(long deadline) { return deadline - nowNanos() <= 0; }
    public URI uri(List<String> segments, Map<String, ?> query) {
        var builder = UriComponentsBuilder.fromUri(base).replacePath("");
        var values = new LinkedHashMap<String, Object>();
        for (int i = 0; i < segments.size(); i++) { String key = "p" + i; builder.pathSegment("{" + key + "}"); values.put(key, segments.get(i)); }
        int index = 0;
        for (var entry : query.entrySet()) { String key = "q" + index++; builder.queryParam(entry.getKey(), "{" + key + "}"); values.put(key, entry.getValue()); }
        return builder.encode().buildAndExpand(values).toUri();
    }
    public Page json(URI uri, String token, long deadline) {
        return json(uri,token,deadline,()->{});
    }
    public Page json(URI uri,String token,long deadline,Runnable beforeAttempt) {
        return read(uri, token, deadline, (stream, headers) -> {
            byte[] bytes = stream.readNBytes(MAX_JSON_BYTES + 1);
            if (bytes.length > MAX_JSON_BYTES) throw new Failure(false, false);
            JsonNode body = mapper.readTree(bytes);
            if (body == null) throw new Failure(false, false);
            return new Page(body, headers.getFirst("X-Next-Page"));
        },beforeAttempt);
    }
    public <T> T stream(URI uri, String token, long deadline, StreamReader<T> reader) {
        return stream(uri,token,deadline,reader,()->{});
    }
    public <T> T stream(URI uri,String token,long deadline,StreamReader<T> reader,Runnable beforeAttempt) {
        return read(uri, token, deadline, (body, headers) -> reader.read(body),beforeAttempt);
    }
    @FunctionalInterface private interface ResponseReader<T> { T read(InputStream stream, HttpHeaders headers) throws IOException; }
    private <T> T read(URI uri, String token, long deadline, ResponseReader<T> reader,Runnable beforeAttempt) {
        Objects.requireNonNull(beforeAttempt);
        if (!Objects.equals(base.getAuthority(), uri.getAuthority()) || !base.getScheme().equals(uri.getScheme())) throw new Failure(false, false);
        // JVM의 wire 로깅이 활성화된 환경은 요청 전에 거부하여 검색어 노출을 막습니다.
        if (Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection").isLoggable(Level.FINE)
                || Logger.getLogger("jdk.internal.httpclient.debug").isLoggable(Level.FINE)
                || Logger.getLogger("jdk.internal.httpclient.hpack.debug").isLoggable(Level.FINE)
                || !System.getProperty("sun.net.http.captureRules", "").isBlank()
                || !System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank()
                || !System.getProperty("jdk.internal.httpclient.debug", "").isBlank()
                || !System.getProperty("javax.net.debug", "").isBlank()) throw new Failure(false, false);
        for (int attempt = 0; attempt < 2; attempt++) {
            if (Thread.currentThread().isInterrupted()) throw new Failure(false, false);
            if (expired(deadline)) throw new Failure(false, true);
            // guard의 취소·만료·현재 인가 오류는 전송 오류나 재시도로 바꾸지 않습니다.
            beforeAttempt.run();
            if (Thread.currentThread().isInterrupted()) throw new Failure(false,false);
            if (expired(deadline)) throw new Failure(false,true);
            long remainingMs = Math.max(1, (deadline - nowNanos()) / 1_000_000 + 1);
            boolean deadlineLimited = remainingMs <= READ_TIMEOUT.toMillis();
            // 응답 stream·관측 handler와 JDK client의 종료까지 마쳐야 상위 실행 슬롯을 반환할 수 있습니다.
            try (var transportClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(CONNECT_TIMEOUT.toMillis(), remainingMs)))
                    .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build()) {
                var transport = new JdkClientHttpRequestFactory(transportClient);
                transport.setReadTimeout(Duration.ofMillis(Math.min(READ_TIMEOUT.toMillis(), remainingMs)));
                transport.enableCompression(false);
                ClientHttpRequestFactory shield = (target, method) -> {
                    final ClientHttpRequest delegate;
                    try { delegate = transport.createRequest(target, method); }
                    catch (IOException | RuntimeException failure) { throw sanitized(failure, deadlineLimited); }
                    return new ClientHttpRequest() {
                        @Override public HttpMethod getMethod() { return delegate.getMethod(); }
                        @Override public URI getURI() { return delegate.getURI(); }
                        @Override public HttpHeaders getHeaders() { return delegate.getHeaders(); }
                        @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }
                        @Override public OutputStream getBody() { throw new Failure(false, false); }
                        @Override public ClientHttpResponse execute() {
                            try { return delegate.execute(); }
                            catch (IOException | RuntimeException failure) { throw sanitized(failure, deadlineLimited); }
                        }
                    };
                };
                var client = RestClient.builder().requestFactory(shield).observationRegistry(observations)
                    .observationConvention(new AllowlistClientRequestObservationConvention(sourceId)).build();
                var request = client.get().uri(uri).accept(MediaType.APPLICATION_JSON);
                if (token != null) request.header("PRIVATE-TOKEN", token);
                return request.exchange((req, response) -> {
                    try {
                        int status = response.getStatusCode().value();
                        if (status < 200 || status >= 300) throw new Failure(status >= 500 && status <= 599, false,status==404);
                        InputStream bounded = new FilterInputStream(response.getBody()) {
                            private void check() { if (expired(deadline)) throw new Failure(false, true); }
                            @Override public int read() throws IOException { check(); int value = super.read(); check(); return value; }
                            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                                check(); int count = in.read(bytes, offset, length); check(); return count;
                            }
                        };
                        return reader.read(bounded, response.getHeaders());
                    } catch (Failure failure) { throw failure; }
                    catch (IOException | RuntimeException failure) { throw new Failure(false, deadlineLimited && expired(deadline)); }
                });
            } catch (Failure failure) {
                if (failure.deadline || !failure.retryable || attempt == 1) throw failure;
            } catch (RuntimeException failure) { throw new Failure(false, false); }
        }
        throw new Failure(false, false);
    }
    private static Failure sanitized(Exception failure, boolean deadlineLimited) {
        boolean timeout = failure instanceof HttpTimeoutException;
        boolean connection = failure instanceof ConnectException || failure instanceof UnknownHostException || failure instanceof HttpConnectTimeoutException;
        return new Failure(connection && !Thread.currentThread().isInterrupted(), timeout && deadlineLimited);
    }
}
