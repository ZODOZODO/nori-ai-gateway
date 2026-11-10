package com.nori.ai.gateway.app.hooks;

import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class MockStateDirectoryGuard {
    @Bean static BeanFactoryPostProcessor mockStateDirectoryPolicy(Environment environment) {
        return beans -> {
            if (!environment.containsProperty("ai.gateway.tcmock.state-dir")) return;
            try {
                var profiles = Arrays.asList(environment.getActiveProfiles());
                String directory = environment.getProperty("ai.gateway.tcmock.state-dir");
                if (!profiles.contains("verify") || profiles.contains("prod") || directory == null || directory.isBlank())
                    throw new IllegalArgumentException();
                Path.of(directory);
            } catch (RuntimeException invalid) { throw new IllegalStateException("Mock 상태 디렉터리 설정이 허용되지 않습니다"); }
        };
    }
}
