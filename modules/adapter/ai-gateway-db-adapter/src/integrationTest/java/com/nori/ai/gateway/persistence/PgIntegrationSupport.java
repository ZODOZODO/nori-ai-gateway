package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.integration.testing.DockerRequiredExtension;
import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@Tag("stage-F1") @Tag("label-MOCK_API") @ExtendWith(DockerRequiredExtension.class)
abstract class PgIntegrationSupport {
    protected PgTestDatabase rawDatabase() {
        return new PgTestDatabase(System.getProperty("aigw.pgTestImage"), Path.of(System.getProperty("aigw.rootDir"))).start();
    }
    protected ConfigurableApplicationContext application(PgTestDatabase database, Class<?>... sources) {
        return new SpringApplicationBuilder(PersistenceApplication.class).sources(sources).web(WebApplicationType.NONE)
            .properties(Map.of("spring.datasource.url", database.jdbcUrl(), "spring.datasource.username", database.user(),
                "spring.datasource.password", database.passwordFor(database.user()), "spring.flyway.enabled", "false"))
            .run();
    }
    @EnableAutoConfiguration @Import(PersistenceModuleConfiguration.class)
    static class PersistenceApplication {
        // 앱에서는 CoreBeansConfiguration이 등록하는 core 감사 허용 목록을 persistence 단독 컨텍스트에도 같은 생성 방식으로 공급합니다.
        // 이 classpath에는 Tool Manifest가 없으므로 등록 Tool이 0개인 실제 Registry를 씁니다(Tool 감사 값은 모두 거부됨).
        @org.springframework.context.annotation.Bean com.nori.ai.gateway.core.audit.AuditFieldAllowlist auditFieldAllowlist() {
            return new com.nori.ai.gateway.core.audit.AuditFieldAllowlist(new com.nori.ai.gateway.core.sanitize.ContentSanitizer(),
                new com.nori.ai.gateway.core.registry.CapabilityRegistry(java.util.List.of(), java.util.List.of(),
                    java.util.Set.of(com.nori.ai.gateway.core.registry.ImplementationPhase.PHASE_1), new com.nori.ai.gateway.core.registry.ContractVersionPolicy()));
        }
    }
}
