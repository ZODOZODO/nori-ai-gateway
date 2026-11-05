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
        assertThat(context.getBean(Flyway.class).info().all()).isEmpty();
        assertThat(context.getBean(ConsumerFactory.class)).isNotNull();
        assertThat(context.getBean(ObjectStorageClient.class)).isNotNull();
        for (String name : new String[]{"io.opentelemetry.sdk.logs.SdkLoggerProvider",
                "io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter",
                "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"}) {
            assertThat(Class.forName(name)).isNotNull();
        }
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
