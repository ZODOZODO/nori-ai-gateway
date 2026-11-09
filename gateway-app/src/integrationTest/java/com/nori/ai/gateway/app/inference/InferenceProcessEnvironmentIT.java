package com.nori.ai.gateway.app.inference;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.app.harness.AppProcessHarness;
import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static com.nori.ai.gateway.app.inference.OllamaEndpointStub.EXPECTED_DIGEST;
import static org.assertj.core.api.Assertions.*;

/**
 * 현재 integrationTest classpath 의 자식 JVM(P09 AppProcessHarness, 실제 앱·SDK 빈)에 OPENAI_API_KEY·OPENAI_BASE_URL·OPENAI_LOG 를 명시 env 로 넣어도
 * 설정한 stub baseUrl 로만 연결하고 env 값을 채택·출력하지 않는지 확인한다.
 */
@Tag("stage-F2") @Tag("label-MOCK_API")
class InferenceProcessEnvironmentIT extends StackContextSupport {
    private final String hookToken = UUID.randomUUID().toString();
    private final String apiKeySentinel = "sk-env-sentinel-" + UUID.randomUUID();

    @Test void childProcessWithOpenAiEnvironmentConnectsOnlyToTheConfiguredEndpointAndNeverAdoptsOrPrintsIt() throws Exception {
        database = new PgTestDatabase(System.getProperty("aigw.pgTestImage"), Path.of(System.getProperty("aigw.rootDir"))).start();
        database.bootstrap(true); database.migrate(); prepareSecurityCredentials();
        Files.writeString(securityDirectory.resolve("hook.sha256"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(hookToken.getBytes(StandardCharsets.UTF_8))) + "\n");
        Path state = securityDirectory.resolve("state");
        var harness = new AppProcessHarness(apiKeySentinel);
        try (var stub = new OllamaEndpointStub(); var decoy = new OllamaEndpointStub()) {
            try {
                harness.watch(hookToken); harness.watch(serviceToken); harness.watch(database.passwordFor("aigw_app"));
                // SDK 가 요청 본문을 기록하면 나타나는 키다. OPENAI_LOG=debug 를 채택하지 않았다면 출력에 없다.
                harness.watch("encoding_format");
                var p = new LinkedHashMap<String,String>();
                securityProperties().forEach((key, value) -> p.put(key, value.toString()));
                p.putAll(Map.of("spring.datasource.url", database.jdbcUrl(), "spring.datasource.username", "aigw_app", "spring.datasource.password", database.passwordFor("aigw_app"),
                    "server.address", "127.0.0.1", "server.port", "0", "spring.flyway.enabled", "false"));
                p.putAll(Map.of("ai.gateway.capacity.max-running-jobs", "1", "ai.gateway.capacity.generation-concurrency", "1", "ai.gateway.capacity.queued-depth", "4",
                    "ai.gateway.capacity.queue-wait-limit", "60s", "ai.gateway.capacity.job-lifetime-limit", "180s"));
                p.putAll(Map.of("ai.gateway.inference.mode", "enabled", "ai.gateway.inference.baseUrl", stub.baseUrl(), "ai.gateway.inference.generationModelDigest", EXPECTED_DIGEST));
                p.putAll(Map.of("ai.gateway.test-hooks.enabled", "true", "ai.gateway.test-hooks.token-sha256-file", securityDirectory.resolve("hook.sha256").toString(), "ai.gateway.tcmock.state-dir", state.toString()));
                p.putAll(Map.of("spring.kafka.bootstrap-servers", "127.0.0.1:1", "spring.kafka.listener.auto-startup", "false", "nori.storage.s3.enabled", "true", "nori.storage.s3.endpoint", "http://127.0.0.1:1",
                    "nori.storage.s3.bucket", "p19-fixture", "nori.storage.s3.access-key", "p19-dummy", "nori.storage.s3.secret-key", "p19-dummy", "nori.storage.s3.region", "us-east-1"));
                harness.launch(state, p, null, Map.of("OPENAI_API_KEY", apiKeySentinel, "OPENAI_BASE_URL", decoy.baseUrl() + "/v1", "OPENAI_LOG", "debug"));
                var ready = harness.awaitReady(state);
                assertThat(ready.get("envKeys").toString()).as("자식 JVM 에 env 가 실제로 주입됨").contains("OPENAI_API_KEY", "OPENAI_BASE_URL", "OPENAI_LOG");
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                while (stub.count("/api/ps") == 0 && System.nanoTime() < deadline) Thread.sleep(20);
                assertThat(stub.count("/api/ps")).as("readiness 는 설정 baseUrl 의 /api/ps 만 읽는다").isPositive();
                var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(harness.uri("/__test/embedding-probe")).timeout(Duration.ofSeconds(30))
                    .header("X-AIGW-Hook-Token", hookToken).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(JsonMapper.builder().build().readTree(response.body()).get("dimension").asInt()).isEqualTo(3);
                assertThat(stub.count("/v1/embeddings")).isOne();
                var embedRequest = stub.received().stream().filter(request -> request.path().equals("/v1/embeddings")).findFirst().orElseThrow();
                assertThat(embedRequest.header("Authorization")).isEqualTo("Bearer aigw-no-auth").doesNotContain(apiKeySentinel);
                assertThat(stub.received()).extracting(OllamaEndpointStub.Received::path).containsOnly("/api/ps", "/v1/embeddings");
                assertThat(decoy.received()).as("OPENAI_BASE_URL·기본 api.openai.com 연결 0").isEmpty();
            } finally { harness.close(); }
            assertThat(harness.sentinelCount()).as("env 키·SDK 본문 로그·자격 출력 잔류").isZero();
        }
    }
}
