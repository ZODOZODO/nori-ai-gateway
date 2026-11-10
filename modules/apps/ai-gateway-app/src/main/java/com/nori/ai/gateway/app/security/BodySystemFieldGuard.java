package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

public final class BodySystemFieldGuard {
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().disable(tools.jackson.core.TokenStreamFactory.Feature.CHARSET_DETECTION)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    ReplayableRequest read(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        JsonNode json = null;
        if (body.length > 0) {
            try { json = JSON.readTree(body); }
            catch (RuntimeException failure) { throw new ActorAssertionVerifier.Rejected(com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason.ACTOR_ASSERTION_MISSING); }
        }
        return new ReplayableRequest(request,body,json);
    }
    String assertion(ReplayableRequest request) {
        String header = request.getHeader("X-Actor-Assertion");
        JsonNode node = request.json == null ? null : request.json.get("contextRef");
        if (header != null && node != null) throw new ActorAssertionVerifier.Rejected(com.nori.ai.gateway.persistence.audit.auth.AuthRejectionReason.ACTOR_ASSERTION_MISSING);
        return header != null ? header : node != null && node.isString() ? node.asString() : null;
    }
    boolean check(ReplayableRequest request, OwnerSystem bound) {
        if (request.json == null) return true;
        for (String field : List.of("system","originSystem","targetSystem")) {
            var value = request.json.get(field);
            if (value != null && (!value.isString() || !bound.name().equals(value.asString()))) return false;
        }
        var systems = request.json.get("authorizedSystems");
        if (systems != null) {
            if (!systems.isArray() || systems.isEmpty()) return false;
            for (var system : systems) if (!system.isString() || !bound.name().equals(system.asString())) return false;
        }
        return true;
    }
    static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final JsonNode json;
        ReplayableRequest(HttpServletRequest request, byte[] body, JsonNode json) { super(request); this.body = body; this.json = json; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] buffer,int offset,int length) { return input.read(buffer,offset,length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("비동기 본문 읽기는 지원하지 않습니다");
                }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
    }
}
