package com.nori.ai.gateway.app;

import com.nori.ai.gateway.integration.testing.DockerRequiredExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ExtendWith(DockerRequiredExtension.class)
public abstract class StackContextSupport {
    private PostgreSQLContainer database;
    protected ConfigurableApplicationContext context;

    protected void start(Class<?>... additionalSources) {
        database = new PostgreSQLContainer("postgres:17.6-alpine");
        database.start();
        context = new SpringApplicationBuilder(AiGatewayApplication.class).sources(additionalSources)
            .web(WebApplicationType.NONE).profiles("verify").run(
                "--spring.datasource.url=" + database.getJdbcUrl(),
                "--spring.datasource.username=" + database.getUsername(),
                "--spring.datasource.password=" + database.getPassword(),
                "--spring.flyway.fail-on-missing-locations=false",
                "--spring.kafka.bootstrap-servers=127.0.0.1:1",
                "--spring.kafka.listener.auto-startup=false",
                "--nori.storage.s3.enabled=true", "--nori.storage.s3.endpoint=http://127.0.0.1:1",
                "--nori.storage.s3.bucket=p04-smoke", "--nori.storage.s3.access-key=p04-dummy",
                "--nori.storage.s3.secret-key=p04-dummy", "--nori.storage.s3.region=us-east-1");
    }

    @AfterEach void close() {
        try { if (context != null) context.close(); }
        finally { if (database != null) database.stop(); }
    }
}
