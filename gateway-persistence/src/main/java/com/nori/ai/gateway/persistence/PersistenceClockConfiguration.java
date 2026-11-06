package com.nori.ai.gateway.persistence;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

@Import(PersistenceClockConfiguration.Defaults.class)
public class PersistenceClockConfiguration implements DeferredImportSelector {
    // 사용자 Clock의 정의가 모두 등록된 뒤 기본 빈 조건을 평가하여 등록 순서에 의존하지 않습니다.
    @Override public String[] selectImports(AnnotationMetadata metadata) {
        return java.util.Arrays.stream(getClass().getAnnotation(Import.class).value()).map(Class::getName).toArray(String[]::new);
    }
    @Configuration(proxyBeanMethods = false)
    public static class Defaults {
        @Bean @ConditionalOnMissingBean(Clock.class)
        Clock persistenceClock() { return Clock.systemUTC(); }
    }
}
