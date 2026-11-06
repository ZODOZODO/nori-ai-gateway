package com.nori.ai.gateway.app;

import com.nori.ai.gateway.integration.testing.DockerRequiredExtension;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.nio.file.Path;
import java.util.Map;

@ExtendWith(DockerRequiredExtension.class)
public abstract class StackContextSupport {
    protected PgTestDatabase database;
    protected ConfigurableApplicationContext context;

    protected void start(Class<?>... additionalSources) {
        database = new PgTestDatabase(System.getProperty("aigw.pgTestImage"), Path.of(System.getProperty("aigw.rootDir"))).start();
        database.bootstrap(true);
        database.migrate();
        startApplication(additionalSources);
    }

    protected void startApplication(Class<?>... additionalSources) {
        context = new SpringApplicationBuilder(AiGatewayApplication.class).sources(additionalSources)
            .properties(Map.of("spring.datasource.url", database.jdbcUrl(), "spring.datasource.username", "aigw_app",
                "spring.datasource.password", database.passwordFor("aigw_app")))
            .web(WebApplicationType.NONE).profiles("verify").run(
                "--spring.flyway.enabled=false",
                "--spring.kafka.bootstrap-servers=127.0.0.1:1",
                "--spring.kafka.listener.auto-startup=false",
                "--nori.storage.s3.enabled=true", "--nori.storage.s3.endpoint=http://127.0.0.1:1",
                "--nori.storage.s3.bucket=p04-smoke", "--nori.storage.s3.access-key=p04-dummy",
                "--nori.storage.s3.secret-key=p04-dummy", "--nori.storage.s3.region=us-east-1");
    }

    @AfterEach void close() {
        try { if (context != null) context.close(); }
        finally { if (database != null) database.close(); }
    }
}
