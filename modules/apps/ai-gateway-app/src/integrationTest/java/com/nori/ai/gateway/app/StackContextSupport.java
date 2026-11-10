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
    // 공용 fixture는 운영 기본 OTLP endpoint로 보내지 않습니다. 전용 인프로세스 수신 시험만 loopback endpoint와 함께 켭니다.
    private static final Map<String,Object> OTLP_TEST_DEFAULTS = Map.of("ai.gateway.otlp.metrics.enabled", false,
        "ai.gateway.otlp.traces.enabled", false, "ai.gateway.otlp.logs.enabled", false);
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
        database.bootstrap();
        database.migrate();
        startApplication(additionalSources);
    }

    protected void startApplication(Class<?>... additionalSources) {
        prepareSecurityCredentials();
        var properties = new HashMap<String,Object>(securityProperties());
        properties.putAll(Map.of("ai.gateway.capacity.max-running-jobs",1,"ai.gateway.capacity.generation-concurrency",1,
            "ai.gateway.capacity.queued-depth",4,"ai.gateway.capacity.queue-wait-limit","60s","ai.gateway.capacity.job-lifetime-limit","180s",
            "ai.gateway.inference.mode","disabled"));
        properties.putAll(Map.of("spring.datasource.url",database.jdbcUrl(),"spring.datasource.username",database.user(),"spring.datasource.password",database.passwordFor(database.user())));
        properties.putAll(extraApplicationProperties());
        if (applicationType() == WebApplicationType.SERVLET) { properties.put("server.address","127.0.0.1"); properties.put("server.port",0); }
        var arguments = new java.util.ArrayList<>(List.of(
                "--spring.flyway.enabled=false",
                "--ai.gateway.recovery.restore-mode=" + properties.getOrDefault("ai.gateway.recovery.restore-mode",false),
                "--ai.gateway.recovery.restore-handler.enabled=" + properties.getOrDefault("ai.gateway.recovery.restore-handler.enabled",true),
                "--spring.kafka.bootstrap-servers=127.0.0.1:1",
                "--spring.kafka.listener.auto-startup=false",
                "--nori.storage.s3.enabled=true", "--nori.storage.s3.endpoint=http://127.0.0.1:1",
                "--nori.storage.s3.bucket=p04-smoke", "--nori.storage.s3.access-key=p04-dummy",
                "--nori.storage.s3.secret-key=p04-dummy", "--nori.storage.s3.region=us-east-1"));
        // application.yaml에 있는 OTLP 키는 기본 속성보다 우선하므로 명령행 인자로 넘깁니다.
        var otlp = new java.util.TreeMap<String,Object>(OTLP_TEST_DEFAULTS);
        properties.forEach((key, value) -> { if (key.startsWith("ai.gateway.otlp.")) otlp.put(key, value); });
        otlp.forEach((key, value) -> arguments.add("--" + key + "=" + value));
        context = new SpringApplicationBuilder(AiGatewayApplication.class)
            .sources(com.nori.ai.gateway.app.job.P09TestPorts.class).sources(additionalSources)
            .properties(properties)
            .web(applicationType()).profiles("verify").run(arguments.toArray(String[]::new));
    }

    /** 접수 서비스를 직접 부르는 시험이 인입 filter와 같은 앱 OpenTelemetry Bean의 실제 SDK 서버 span 안에서 호출하게 한다. */
    protected <T> T inRequestSpan(java.util.function.Supplier<T> action) {
        var span = context.getBean(io.opentelemetry.api.OpenTelemetry.class).getTracer("com.nori.ai.gateway.test").spanBuilder("aigw.request")
            .setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER).setNoParent().startSpan();
        try (var current = span.makeCurrent()) { return action.get(); }
        finally { span.end(); }
    }

    @AfterEach protected void close() {
        try { if (context != null) context.close(); }
        finally { if (database != null) database.close(); }
    }
}
