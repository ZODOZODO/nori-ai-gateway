package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** F0 하네스의 유일한 Ollama 호출 경로. 관리 API·embedding 경로는 만들 수 없고, 추론 호출은 통과한 preflight 증표가 있어야만 보낸다. */
public final class OllamaChatClient {
    public static final Set<String> PATHS = Set.of("/v1/chat/completions", "/api/ps", "/api/tags", "/api/generate", "/api/show");
    private static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    /** 응답 본문(content)은 판정용으로 메모리에만 두며 toString 으로도 노출하지 않는다. thinking 은 /v1/chat/completions 응답에서만 만들고 그 밖 경로는 null 이다. */
    public record ChatResult(String content, String finishReason, int promptTokens, int completionTokens, ThinkingObservation thinking) {
        public ChatResult(String content, String finishReason, int promptTokens, int completionTokens) { this(content, finishReason, promptTokens, completionTokens, null); }
        @Override public String toString() { return "ChatResult[finishReason=" + finishReason + ", completionTokens=" + completionTokens + "]"; }
    }
    /** 사고문을 따로 담을 수 있는 message 필드 후보. Ollama 0.40.13 의 실제 응답 형태는 미확인이라 어느 것이 오는지 이름만 기록한다. */
    public static final List<String> THINKING_FIELDS = List.of("reasoning", "reasoning_content", "thinking");
    /**
     * 사고(thinking) 출력 관측: 원문 없이 존재/부재와 수치만 담는다(사고문 전문 비저장, SPEC §12.3).
     * separateFields = message 에 null 이 아닌 값으로 있는 THINKING_FIELDS 이름(정렬), reasoningTokens = usage.completion_tokens_details.reasoning_tokens(정수가 아니거나 없으면 null),
     * contentThinkOpen/Close = 답변 content 에 `<think>`/`</think>` 표지가 있는지(사고문의 content 혼입 판별용).
     */
    public record ThinkingObservation(List<String> separateFields, Integer reasoningTokens, boolean contentThinkOpen, boolean contentThinkClose) {
        static ThinkingObservation of(JsonNode message, JsonNode usage, String content) {
            List<String> fields = new ArrayList<>();
            for (String name : THINKING_FIELDS) if (message.has(name) && !message.get(name).isNull()) fields.add(name);
            JsonNode reasoning = usage.path("completion_tokens_details").path("reasoning_tokens");
            return new ThinkingObservation(List.copyOf(fields), isCount(reasoning) ? reasoning.intValue() : null, content.contains("<think>"), content.contains("</think>"));
        }
    }
    private final URI endpoint;
    private final HttpClient http;
    private final Duration timeout;

    public OllamaChatClient(String endpoint, Duration timeout) {
        this.endpoint = validate(endpoint);
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY).build();
    }
    // SSH 터널의 로컬 끝점만 허용합니다(원격 주소 직접 지정 0).
    static URI validate(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            if (!"http".equals(uri.getScheme()) || host == null || !Set.of("127.0.0.1", "localhost", "[::1]").contains(host) || uri.getPort() < 1
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/")) || uri.getQuery() != null || uri.getUserInfo() != null || uri.getFragment() != null) throw new EvalException(2, "ENDPOINT_INVALID");
            return new URI("http", null, host.equals("[::1]") ? "::1" : host, uri.getPort(), null, null, null);
        } catch (URISyntaxException e) { throw new EvalException(2, "ENDPOINT_INVALID"); }
    }
    public JsonNode ps() { return send("GET", "/api/ps", null); }
    public JsonNode tags() { return send("GET", "/api/tags", null); }
    public String showTemplate(String model) {
        JsonNode node = send("POST", "/api/show", Map.of("model", model)).path("template");
        if (!node.isTextual()) throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID");
        return node.textValue();
    }
    public ChatResult chat(Preflight.Verified gate, String model, List<Map<String,String>> messages, int maxTokens) {
        return chat(gate, model, messages, maxTokens, null);
    }
    /**
     * responseFormat 이 null 이면 v1/v2 와 같은 body 입니다. 있으면 OpenAI 호환 response_format 만 더하며, 서버가 거부해도(HTTP 오류)
     * JSON 모드·비제약 호출로 다시 보내지 않고 고정 오류로 측정을 멈춥니다. response_format 값은 Format.bytes() 와 같은 바이트로 송신합니다.
     */
    public ChatResult chat(Preflight.Verified gate, String model, List<Map<String,String>> messages, int maxTokens, ResponseSchema.Format responseFormat) {
        return chat(gate, model, messages, maxTokens, responseFormat, IntNode.valueOf(0));
    }
    /** temperature 는 v1~v3 고정 0, v4 는 등록값(Prereg.Document.temperature) 입니다. 그 밖 body 는 위와 같습니다. */
    public ChatResult chat(Preflight.Verified gate, String model, List<Map<String,String>> messages, int maxTokens, ResponseSchema.Format responseFormat, JsonNode temperature) {
        Objects.requireNonNull(gate, "preflight");
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", model); body.put("messages", messages); body.put("temperature", temperature); body.put("max_tokens", maxTokens); body.put("stream", false);
        Set<List<String>> keepOrder = new HashSet<>();
        if (responseFormat != null) {
            body.put("response_format", responseFormat.node());
            for (List<String> path : responseFormat.orderedPaths()) { List<String> full = new ArrayList<>(List.of("response_format")); full.addAll(path); keepOrder.add(full); }
        }
        JsonNode response = send("POST", "/v1/chat/completions", body, keepOrder);
        JsonNode choice = response.path("choices").path(0), usage = response.path("usage");
        if (!choice.path("message").path("content").isTextual() || !choice.path("finish_reason").isTextual() || !isCount(usage.path("completion_tokens")) || !isCount(usage.path("prompt_tokens")) || response.path("choices").size() != 1) throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID");
        String content = choice.path("message").path("content").textValue();
        return new ChatResult(content, choice.path("finish_reason").textValue(), usage.path("prompt_tokens").intValue(), usage.path("completion_tokens").intValue(), ThinkingObservation.of(choice.path("message"), usage, content));
    }
    /** 토큰 수·context 길이: JSON 정수 리터럴이고 int 로 손실 없이 표현되며 0 이상일 때만(128.9·128.0·음수·overflow·문자열 거부). */
    static boolean isCount(JsonNode node) { return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 0; }
    public int generateRawPromptEvalCount(Preflight.Verified gate, String model, String prompt) {
        Objects.requireNonNull(gate, "preflight");
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", model); body.put("prompt", prompt); body.put("raw", true); body.put("stream", false); body.put("options", Map.of("num_predict", 1));
        JsonNode count = send("POST", "/api/generate", body).path("prompt_eval_count");
        if (!isCount(count)) throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID");
        return count.intValue();
    }
    /** 생성 토큰 하나의 후보(문자열은 판정용 메모리에만 두며 toString 으로도 노출하지 않는다). */
    public record TokenLogprob(String token, double logprob) {
        @Override public String toString() { return "TokenLogprob[logprob=" + logprob + "]"; }
    }
    /** 생성 위치 하나: 실제 선택 토큰과 top_logprobs 후보. */
    public record Position(TokenLogprob chosen, List<TokenLogprob> top) {
        @Override public String toString() { return "Position[top=" + top.size() + "]"; }
    }
    /** /api/generate 응답의 판정용 필드. logprobs 는 응답에 필드가 없으면 null 입니다. 응답 본문은 toString 으로 노출하지 않는다. */
    public record GenerateResult(String content, String doneReason, int promptEvalCount, int evalCount, List<Position> logprobs) {
        @Override public String toString() { return "GenerateResult[doneReason=" + doneReason + ", evalCount=" + evalCount + "]"; }
        public ChatResult asChatResult() { return new ChatResult(content, doneReason, promptEvalCount, evalCount); }
    }
    /**
     * /api/generate raw 생성(G08 원인 확정 시험 전용). format 은 JSON schema 객체 자체이며 formatOrderedPaths 에 든 경로(format 기준)의 객체만 노드 순서로,
     * 나머지는 키 정렬로 송신합니다. temperature 0·num_predict=maxTokens 외 options 는 보내지 않습니다(context 길이 등 모델 재적재를 일으키는 옵션 0).
     */
    public GenerateResult generateRawStructured(Preflight.Verified gate, String model, String prompt, JsonNode format, Set<List<String>> formatOrderedPaths, int maxTokens, int topLogprobs) {
        Objects.requireNonNull(gate, "preflight");
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", model); body.put("prompt", prompt); body.put("raw", true); body.put("stream", false); body.put("format", format);
        Map<String,Object> options = new LinkedHashMap<>(); options.put("temperature", 0); options.put("num_predict", maxTokens); body.put("options", options);
        body.put("logprobs", true); body.put("top_logprobs", topLogprobs);
        Set<List<String>> keepOrder = new HashSet<>();
        for (List<String> path : formatOrderedPaths) { List<String> full = new ArrayList<>(List.of("format")); full.addAll(path); keepOrder.add(full); }
        JsonNode response = send("POST", "/api/generate", body, keepOrder);
        if (!response.path("response").isTextual() || !response.path("done_reason").isTextual() || !isCount(response.path("prompt_eval_count")) || !isCount(response.path("eval_count"))) throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID");
        return new GenerateResult(response.path("response").textValue(), response.path("done_reason").textValue(), response.path("prompt_eval_count").intValue(), response.path("eval_count").intValue(), logprobs(response.get("logprobs")));
    }
    private static List<Position> logprobs(JsonNode node) {
        if (node == null) return null;
        if (!node.isArray()) throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID");
        List<Position> positions = new ArrayList<>();
        for (JsonNode entry : node) {
            List<TokenLogprob> top = new ArrayList<>();
            JsonNode candidates = entry.path("top_logprobs");
            if (!candidates.isMissingNode() && !candidates.isArray()) throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID");
            for (JsonNode candidate : candidates) top.add(tokenLogprob(candidate));
            positions.add(new Position(tokenLogprob(entry), List.copyOf(top)));
        }
        return List.copyOf(positions);
    }
    private static TokenLogprob tokenLogprob(JsonNode node) {
        if (!node.path("token").isTextual() || !node.path("logprob").isNumber()) throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID");
        return new TokenLogprob(node.path("token").textValue(), node.path("logprob").doubleValue());
    }
    /**
     * 요청 송신~응답 헤더~본문 끝을 하나의 총 기한(timeout)으로 묶습니다. HttpRequest.timeout 은 헤더 수신까지만 적용되므로
     * 본문은 상한 있는 BodySubscriber 로 받고 sendAsync 결과를 남은 기한만큼만 기다립니다. 기한 초과·interrupt 면 구독과 교환을 취소합니다.
     */
    private JsonNode send(String method, String path, Object body) { return send(method, path, body, Set.of()); }
    /** 본문은 키 정렬 canonical JSON 이며, keepOrder 경로의 객체만 노드 순서로 보냅니다(비어 있으면 전체 정렬 = 기존 송신 바이트). */
    private JsonNode send(String method, String path, Object body, Set<List<String>> keepOrder) {
        if (!PATHS.contains(path)) throw new EvalException(2, "ENDPOINT_PATH_FORBIDDEN");
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint.resolve(path)).timeout(timeout).header("Accept", "application/json");
        if (body == null) request.GET();
        else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(ResponseSchema.canonicalKeepingOrder(Json.MAPPER.valueToTree(body), keepOrder)));
        long deadline = System.nanoTime() + timeout.toNanos();
        AtomicReference<CappedBody> subscriber = new AtomicReference<>();
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request.build(), info -> { CappedBody capped = new CappedBody(info.statusCode() == 200); subscriber.set(capped); return capped; });
        byte[] bytes;
        try { bytes = future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS).body(); }
        catch (TimeoutException e) { abort(future, subscriber); throw new EvalException(5, "ENDPOINT_TIMEOUT"); }
        catch (InterruptedException e) { abort(future, subscriber); Thread.currentThread().interrupt(); throw new EvalException(5, "ENDPOINT_INTERRUPTED"); }
        catch (ExecutionException e) {
            abort(future, subscriber);
            Throwable cause = e.getCause();
            while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof EvalException fixed) throw fixed;
            if (cause instanceof HttpTimeoutException) throw new EvalException(5, "ENDPOINT_TIMEOUT");
            throw new EvalException(5, "ENDPOINT_UNREACHABLE");
        }
        try { return Json.MAPPER.readTree(new String(bytes, StandardCharsets.UTF_8)); }
        catch (IOException e) { throw new EvalException(5, "ENDPOINT_RESPONSE_INVALID"); }
    }
    private static void abort(CompletableFuture<?> future, AtomicReference<CappedBody> subscriber) {
        CappedBody capped = subscriber.get();
        if (capped != null) capped.cancel();
        future.cancel(true);
    }
    /** 본문을 MAX_RESPONSE_BYTES 까지만 모읍니다. 초과·200 아님이면 즉시 구독을 취소하고 고정 오류로 끝냅니다(본문 원문 보관·노출 0). */
    private static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final boolean accepted;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        CappedBody(boolean accepted) { this.accepted = accepted; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public synchronized void onSubscribe(Flow.Subscription s) {
            subscription = s;
            if (result.isDone()) { s.cancel(); return; }
            if (!accepted) { fail("ENDPOINT_HTTP_ERROR"); return; }
            s.request(Long.MAX_VALUE);
        }
        @Override public synchronized void onNext(List<ByteBuffer> items) {
            if (result.isDone()) return;
            for (ByteBuffer item : items) {
                if (item.remaining() > MAX_RESPONSE_BYTES - buffer.size()) { fail("ENDPOINT_RESPONSE_TOO_LARGE"); return; }
                byte[] chunk = new byte[item.remaining()]; item.get(chunk); buffer.writeBytes(chunk);
            }
        }
        @Override public synchronized void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public synchronized void onComplete() { result.complete(buffer.toByteArray()); }
        private void fail(String code) { result.completeExceptionally(new EvalException(5, code)); cancel(); }
        synchronized void cancel() {
            if (!result.isDone()) result.completeExceptionally(new CancellationException());
            if (subscription != null) subscription.cancel();
            buffer.reset();
        }
    }
}
