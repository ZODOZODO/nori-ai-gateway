package com.nori.ai.gateway.app;

import com.nori.objectstorage.ObjectStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;
import org.flywaydb.core.Flyway;
import java.net.ServerSocket;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.Tag("stage-F1")
@org.junit.jupiter.api.Tag("label-MOCK_API")
class StackSmokeIT extends StackContextSupport {
    @Test void acceptsStockSdkTransportWithZeroRetriesAndSeparateConnectTimeoutWithoutRequest() {
        var timeout = com.openai.core.Timeout.builder().connect(Duration.ofSeconds(1)).request(Duration.ofSeconds(2)).build();
        var options = com.openai.core.ClientOptions.builder()
            .httpClient(com.openai.client.okhttp.OkHttpClient.builder().timeout(timeout).build())
            .baseUrl("http://127.0.0.1:1/v1").apiKey("p04-dummy").maxRetries(0).timeout(timeout)
            .logLevel(com.openai.core.LogLevel.OFF).build();
        assertThat(options.maxRetries()).isZero();
        assertThat(options.timeout().connect()).isEqualTo(Duration.ofSeconds(1));
        assertThat(options.logLevel()).isEqualTo(com.openai.core.LogLevel.OFF);
        var client = new com.openai.client.OpenAIClientImpl(options);
        try {
            assertThat(OpenAiChatModel.builder().openAiClient(client).openAiClientAsync(client.async()).options(OpenAiChatOptions.builder()
                .baseUrl("http://127.0.0.1:1/v1").apiKey("p04-dummy").model("qwen3:4b").maxRetries(0).build()).build()).isNotNull();
            assertThat(OpenAiEmbeddingModel.builder().openAiClient(client).options(OpenAiEmbeddingOptions.builder()
                .baseUrl("http://127.0.0.1:1/v1").apiKey("p04-dummy").model("bge-m3").maxRetries(0).build()).build()).isNotNull();
        } finally { client.close(); }
    }

    @Test void pinnedStackStartsWithManualModelsAndNoInference() throws Exception {
        start(Models.class);
        exerciseCompatibilityLibraries();
        assertThat(context.getBean(OpenAiChatModel.class)).isNotNull();
        assertThat(context.getBean(OpenAiEmbeddingModel.class)).isNotNull();
        assertThat(context.getBean(JdbcClient.class).sql("select 1").query(Integer.class).single()).isEqualTo(1);
        assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
        var jdbc = context.getBean(JdbcClient.class);
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo(database.user());
        var versions = jdbc.sql("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL")
            .query(String.class).list();
        assertThat(versions.stream().map(org.flywaydb.core.api.MigrationVersion::fromVersion)
            .max(org.flywaydb.core.api.MigrationVersion::compareTo).orElseThrow())
            .isEqualTo(com.nori.ai.gateway.persistence.schema.SchemaVersionGuard.latestClasspathVersion());
        // 앱 리소스 사본(db/migration/gateway)은 루트 flyway/sql 과 파일 집합·바이트가 같고 최신 V 도 같다.
        var source = java.nio.file.Path.of(System.getProperty("aigw.rootDir")).resolve(System.getProperty("aigw.flyway.sql.dir"));
        var copies = new java.util.TreeMap<String, byte[]>();
        for (var resource : new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/gateway/V*__*.sql"))
            try (var in = resource.getInputStream()) { copies.put(resource.getFilename(), in.readAllBytes()); }
        var originals = new java.util.TreeMap<String, byte[]>();
        try (var files = java.nio.file.Files.list(source)) {
            for (var file : files.filter(path -> path.getFileName().toString().matches("V.+__.+\\.sql")).toList())
                originals.put(file.getFileName().toString(), java.nio.file.Files.readAllBytes(file));
        }
        assertThat(copies.keySet()).isEqualTo(originals.keySet()).hasSize(7);
        originals.forEach((name, bytes) -> assertThat(copies.get(name)).as(name).isEqualTo(bytes));
        assertThat(originals.keySet().stream().map(name -> org.flywaydb.core.api.MigrationVersion.fromVersion(name.substring(1, name.indexOf("__")).replace('_', '.')))
            .max(org.flywaydb.core.api.MigrationVersion::compareTo).orElseThrow())
            .isEqualTo(com.nori.ai.gateway.persistence.schema.SchemaVersionGuard.latestClasspathVersion());
        assertThat(jdbc.sql("SELECT count(*) FROM information_schema.schemata WHERE schema_name IN ('gw','kn','gw_flyway')")
            .query(Integer.class).single()).isEqualTo(3);
        assertThat(context.getBean(ConsumerFactory.class)).isNotNull();
        assertThat(context.getBean(ObjectStorageClient.class)).isNotNull();
        for (String name : new String[]{"io.opentelemetry.sdk.logs.SdkLoggerProvider",
                "io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter",
                "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"}) {
            assertThat(Class.forName(name)).isNotNull();
        }
        // P14: BOM 관리 registry-otlp의 실제 artifact·명시 registry·OTel 조합과 logging 경계 회귀입니다.
        assertLoadedArtifact(io.micrometer.registry.otlp.OtlpMeterRegistry.class, "micrometer-registry-otlp-1.16.6.jar");
        assertLoadedArtifact(Class.forName("io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest"), "opentelemetry-proto-1.8.0-alpha.jar");
        // 공용 fixture 기본값은 export off이므로 OTLP registry는 없고 SDK provider는 유지됩니다(전용 수신 시험만 loopback으로 켭니다).
        assertThat(context.getBeansOfType(io.micrometer.core.instrument.MeterRegistry.class).values()).singleElement().isNotInstanceOf(io.micrometer.registry.otlp.OtlpMeterRegistry.class);
        assertThat(context.getBeansOfType(io.opentelemetry.api.OpenTelemetry.class).values()).singleElement().isInstanceOf(com.nori.ai.gateway.app.observability.GatewayOpenTelemetry.class);
        assertThat(context.getBeansOfType(com.nori.ai.gateway.app.observability.OtlpLogAppenderInstaller.class)).isEmpty();
        var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        assertThat(((ch.qos.logback.core.ConsoleAppender<?>) root.getAppender("STDOUT")).getEncoder()).isInstanceOf(com.nori.ai.gateway.app.observability.AllowlistJsonEncoder.class);
        // logs=false이면 OTel appender를 만들지 않으므로 root에는 STDOUT만 있습니다.
        var appenders = new java.util.ArrayList<ch.qos.logback.core.Appender<?>>(); root.iteratorForAppenders().forEachRemaining(appenders::add);
        assertThat(appenders).noneMatch(io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.class::isInstance).anyMatch(appender -> appender.getName().equals("STDOUT"));
        for (String name : new String[]{"org.springframework.ai.openai.OpenAiChatModel", "org.springframework.ai.openai.OpenAiEmbeddingModel", "org.springframework.ai.openai.setup.OpenAiSetup", "com.openai"})
            assertThat(org.slf4j.LoggerFactory.getLogger(name).isErrorEnabled()).as(name).isFalse();
    }

    private void exerciseCompatibilityLibraries() throws Exception {
        assertLoadedArtifact(com.nimbusds.jwt.SignedJWT.class, "nimbus-jose-jwt-10.5.jar");
        assertLoadedArtifact(com.google.protobuf.StringValue.class, "protobuf-java-4.33.2.jar");
        assertLoadedArtifact(com.networknt.schema.SchemaRegistry.class, "json-schema-validator-3.0.9.jar");

        // 로컬 더미 서명만 왕복하며 제품 인증 정책이나 외부 요청은 구성하지 않습니다.
        byte[] dummyKey = new byte[32];
        var jwt = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
            new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("p04-smoke").build());
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner(dummyKey));
        var parsed = com.nimbusds.jwt.SignedJWT.parse(jwt.serialize());
        assertThat(parsed.verify(new com.nimbusds.jose.crypto.MACVerifier(dummyKey))).isTrue();
        assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo("p04-smoke");
        byte[] wrongKey = new byte[32];
        wrongKey[0] = 1;
        assertThat(parsed.verify(new com.nimbusds.jose.crypto.MACVerifier(wrongKey))).isFalse();

        var message = com.google.protobuf.StringValue.newBuilder().setValue("p04-smoke").build();
        assertThat(com.google.protobuf.StringValue.parseFrom(message.toByteArray())).isEqualTo(message);
        var schema = com.networknt.schema.SchemaRegistry.withDefaultDialect(com.networknt.schema.SpecificationVersion.DRAFT_2020_12)
            .getSchema("{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}");
        assertThat(schema.validate("{\"name\":\"p04-smoke\"}", com.networknt.schema.InputFormat.JSON)).isEmpty();
        assertThat(schema.validate("{\"name\":42}", com.networknt.schema.InputFormat.JSON)).isNotEmpty();
    }

    private void assertLoadedArtifact(Class<?> type, String expectedFile) throws Exception {
        String actual = java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getFileName().toString();
        assertThat(actual).isEqualTo(expectedFile);
        System.out.println("P04_STACK_LOADED " + type.getName() + " " + actual);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Models {
        private String endpoint() throws Exception {
            // 포트를 열어 선택만 하고 닫습니다. 모델 메서드를 호출하지 않습니다.
            try (ServerSocket socket = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
                return "http://127.0.0.1:" + socket.getLocalPort() + "/v1";
            }
        }
        @Bean OpenAiChatModel chatModel() throws Exception {
            return OpenAiChatModel.builder().options(OpenAiChatOptions.builder().baseUrl(endpoint()).apiKey("p04-dummy")
                .model("qwen3:4b").temperature(0.0).maxTokens(128).maxRetries(0).timeout(Duration.ofSeconds(2)).build()).build();
        }
        @Bean OpenAiEmbeddingModel embeddingModel() throws Exception {
            return OpenAiEmbeddingModel.builder().options(OpenAiEmbeddingOptions.builder().baseUrl(endpoint()).apiKey("p04-dummy")
                .model("bge-m3").maxRetries(0).timeout(Duration.ofSeconds(2)).build()).build();
        }
    }
}
