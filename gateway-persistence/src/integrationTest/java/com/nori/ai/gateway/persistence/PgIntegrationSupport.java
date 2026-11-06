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
            .properties(Map.of("spring.datasource.url", database.jdbcUrl(), "spring.datasource.username", "aigw_app",
                "spring.datasource.password", database.passwordFor("aigw_app"), "spring.flyway.enabled", "false"))
            .run();
    }
    @EnableAutoConfiguration @Import(PersistenceModuleConfiguration.class)
    static class PersistenceApplication {}
}
