package com.nori.ai.gateway.app.config;

import java.util.Arrays;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class NormalDatabaseConfiguration {
    // 일반 진입점의 잘못된 프로필/설정이 Flyway 초기화 단계까지 도달하기 전에 거부합니다.
    @Bean static BeanFactoryPostProcessor normalDatabasePolicy(Environment environment) {
        return beans -> {
            if (Arrays.asList(environment.getActiveProfiles()).contains("migrate")
                    || environment.getProperty("spring.flyway.enabled", Boolean.class, false))
                throw new IllegalStateException("일반 애플리케이션에서는 자동 마이그레이션을 사용할 수 없습니다");
        };
    }
}
