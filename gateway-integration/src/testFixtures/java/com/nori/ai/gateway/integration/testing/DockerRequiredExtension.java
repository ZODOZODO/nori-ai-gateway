package com.nori.ai.gateway.integration.testing;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

public final class DockerRequiredExtension implements BeforeAllCallback {
    @Override public void beforeAll(ExtensionContext context) {
        // 필수 통합 검증은 Docker 부재를 skip이나 성공으로 처리하지 않습니다.
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException(Boolean.getBoolean("aigw.requireDocker")
                ? "필수 Docker 환경에 접근할 수 없습니다." : "통합 테스트에 Docker 환경이 필요합니다.");
        }
    }
}
