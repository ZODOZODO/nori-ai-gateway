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
import java.util.HashMap;
import java.util.List;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Base64;
import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;

@ExtendWith(DockerRequiredExtension.class)
public abstract class StackContextSupport {
    protected PgTestDatabase database;
    protected ConfigurableApplicationContext context;
    @org.junit.jupiter.api.io.TempDir protected Path securityDirectory;
    protected StubAssertionSigner signer;
    protected String serviceToken;

    protected void prepareSecurityCredentials() {
        try {
            if (signer == null) signer = StubAssertionSigner.ephemeral("test-eph-stack");
            if (serviceToken == null) { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); serviceToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serviceToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Files.writeString(securityDirectory.resolve("service-tokens.json"),"{\"" + digest + "\":\"TC\"}");
            Files.writeString(securityDirectory.resolve("jwks.json"),new JWKSet(trustedPublicKeys()).toString());
        } catch (Exception failure) { throw new IllegalStateException("시험 인입 자격을 준비할 수 없습니다"); }
    }
    protected List<JWK> trustedPublicKeys() { return List.of(signer.publicJwk()); }
    protected Map<String,Object> securityProperties() {
        return Map.of("ai.gateway.security.service-tokens-file",securityDirectory.resolve("service-tokens.json").toString(),
            "ai.gateway.security.actor-jwks-file",securityDirectory.resolve("jwks.json").toString());
    }
    protected WebApplicationType applicationType() { return WebApplicationType.NONE; }
    protected Map<String,Object> extraApplicationProperties() { return Map.of(); }

    protected void start(Class<?>... additionalSources) {
        database = new PgTestDatabase(System.getProperty("aigw.pgTestImage"), Path.of(System.getProperty("aigw.rootDir"))).start();
        database.bootstrap(true);
        database.migrate();
        startApplication(additionalSources);
    }

    protected void startApplication(Class<?>... additionalSources) {
        prepareSecurityCredentials();
        var properties = new HashMap<String,Object>(securityProperties());
        properties.putAll(Map.of("spring.datasource.url",database.jdbcUrl(),"spring.datasource.username","aigw_app","spring.datasource.password",database.passwordFor("aigw_app")));
        properties.putAll(extraApplicationProperties());
        if (applicationType() == WebApplicationType.SERVLET) { properties.put("server.address","127.0.0.1"); properties.put("server.port",0); }
        context = new SpringApplicationBuilder(AiGatewayApplication.class).sources(additionalSources)
            .properties(properties)
            .web(applicationType()).profiles("verify").run(
                "--spring.flyway.enabled=false",
                "--spring.kafka.bootstrap-servers=127.0.0.1:1",
                "--spring.kafka.listener.auto-startup=false",
                "--nori.storage.s3.enabled=true", "--nori.storage.s3.endpoint=http://127.0.0.1:1",
                "--nori.storage.s3.bucket=p04-smoke", "--nori.storage.s3.access-key=p04-dummy",
                "--nori.storage.s3.secret-key=p04-dummy", "--nori.storage.s3.region=us-east-1");
    }

    @AfterEach protected void close() {
        try { if (context != null) context.close(); }
        finally { if (database != null) database.close(); }
    }
}
