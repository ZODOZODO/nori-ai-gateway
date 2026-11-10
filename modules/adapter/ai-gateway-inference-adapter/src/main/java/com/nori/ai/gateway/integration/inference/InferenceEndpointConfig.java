package com.nori.ai.gateway.integration.inference;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;

/**
 * 추론 서버의 기존 endpoint 한 곳(SPEC §26.1 임차 원칙 — proxy·토큰 없음)과 배포 설정 기대 생성 모델 digest 를 기동 시 검증한다.
 * 값은 {@code scheme://host:port} 만 허용하고, SDK 는 그 뒤에 {@code /v1} 을 정확히 한 번 붙인 주소를, readiness 는 원래 주소의
 * {@code /api/ps} 를 쓴다. 값이 없거나 형식이 다르면 기동을 거부한다(기본 api.openai.com·{@code OPENAI_*} 환경변수 채택 0).
 */
@ConditionalOnProperty(name = "ai.gateway.inference.mode", havingValue = "enabled")
public final class InferenceEndpointConfig {
    public static final String BASE_URL_PROPERTY = "ai.gateway.inference.baseUrl";
    public static final String GENERATION_MODEL_DIGEST_PROPERTY = "ai.gateway.inference.generationModelDigest";
    public static final String GENERATION_MODEL = "qwen3:4b";
    public static final String EMBEDDING_MODEL = "bge-m3";
    private static final String INVALID = "추론 endpoint 설정이 잘못되었습니다";
    private final URI baseUrl;
    private final String expectedGenerationDigest;

    @Autowired
    public InferenceEndpointConfig(Environment environment) {
        this(environment.getProperty(BASE_URL_PROPERTY), environment.getProperty(GENERATION_MODEL_DIGEST_PROPERTY));
    }

    InferenceEndpointConfig(String baseUrl, String expectedGenerationDigest) {
        this.baseUrl = parse(baseUrl);
        // /api/ps 의 digest 는 소문자 SHA-256 hex 이므로 같은 형식만 기대값으로 받는다.
        if (expectedGenerationDigest == null || !expectedGenerationDigest.matches("[0-9a-f]{64}")) throw new IllegalStateException(INVALID);
        this.expectedGenerationDigest = expectedGenerationDigest;
    }

    private static URI parse(String value) {
        if (value == null || value.isBlank()) throw new IllegalStateException(INVALID);
        final URI uri;
        try { uri = new URI(value); } catch (URISyntaxException invalid) { throw new IllegalStateException(INVALID); }
        if (uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1
            || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalStateException(INVALID);
        return uri;
    }

    public URI baseUrl() { return baseUrl; }
    public String sdkBaseUrl() { return baseUrl + "/v1"; }
    public URI readinessUri() { return URI.create(baseUrl + "/api/ps"); }
    public String expectedGenerationDigest() { return expectedGenerationDigest; }
    @Override public String toString() { return "InferenceEndpointConfig[보호됨]"; }
}
