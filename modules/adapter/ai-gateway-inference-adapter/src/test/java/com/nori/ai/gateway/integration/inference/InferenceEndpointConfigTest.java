package com.nori.ai.gateway.integration.inference;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InferenceEndpointConfigTest {
    private static final String DIGEST = "a".repeat(64);

    @Test void acceptsOnlySchemeHostPortAndDerivesSdkAndReadinessUrlsExactlyOnce() {
        var config = new InferenceEndpointConfig("http://192.168.0.160:11434", DIGEST);
        assertThat(config.sdkBaseUrl()).isEqualTo("http://192.168.0.160:11434/v1");
        assertThat(config.readinessUri()).hasToString("http://192.168.0.160:11434/api/ps");
        assertThat(config.expectedGenerationDigest()).isEqualTo(DIGEST);
        assertThat(config.toString()).doesNotContain("192.168.0.160", DIGEST);
        assertThat(new InferenceEndpointConfig("https://inference.local:8443", DIGEST).sdkBaseUrl()).isEqualTo("https://inference.local:8443/v1");
    }

    @Test void rejectsMissingPathTrailingSlashV1PortlessAndOtherFormsWithoutEchoingTheValue() {
        List<String> invalid = Arrays.asList(null, "", "  ", "http://192.168.0.160:11434/", "http://192.168.0.160:11434/v1", "http://192.168.0.160:11434/v1/",
            "http://192.168.0.160", "192.168.0.160:11434", "ftp://192.168.0.160:11434", "http://user@192.168.0.160:11434",
            "http://192.168.0.160:11434?x=1", "http://192.168.0.160:11434#f", "http://exa mple:1", "http://192.168.0.160:11434/api");
        for (String value : invalid)
            assertThatThrownBy(() -> new InferenceEndpointConfig(value, DIGEST)).as(String.valueOf(value))
                .isInstanceOf(IllegalStateException.class).hasMessage("추론 endpoint 설정이 잘못되었습니다");
    }

    @Test void requiresTheDeploymentExpectedGenerationDigestInApiPsFormat() {
        for (String digest : Arrays.asList(null, "", "A".repeat(64), "a".repeat(63), "sha256:" + "a".repeat(64), "g".repeat(64)))
            assertThatThrownBy(() -> new InferenceEndpointConfig("http://127.0.0.1:1", digest)).as(String.valueOf(digest))
                .isInstanceOf(IllegalStateException.class).hasMessage("추론 endpoint 설정이 잘못되었습니다");
    }
}
