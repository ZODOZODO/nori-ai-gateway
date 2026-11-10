package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SpecInputVerifierTest {
    @TempDir Path directory;
    static final byte[] SPEC = "# 승인 SPEC 바이트\n본문 sentinel SPEC_BODY_SENTINEL\n".getBytes(StandardCharsets.UTF_8);
    final AtomicInteger launches = new AtomicInteger();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    Path gzip;

    @BeforeEach void fixture() throws Exception { gzip = gzip(directory.resolve("spec.md.gz"), SPEC); }
    static Path gzip(Path file, byte[] bytes) throws IOException {
        try (var stream = new GZIPOutputStream(Files.newOutputStream(file))) { stream.write(bytes); }
        return file;
    }
    private int run(Map<String,String> env) {
        var properties = Map.of("aigw.acceptance.suite", "F1");
        var base = new HashMap<>(Map.of("AIGW_PARENT_RUN_ID", "F1-20261107T000000Z-0a1b2c3d", "AIGW_BUILD_IDENTITY_JSON",
            "{\"appImageDigest\":\"sha256:" + "b".repeat(64) + "\",\"acceptanceImageDigest\":\"sha256:" + "c".repeat(64) + "\"}"));
        base.putAll(env);
        return new AcceptanceMain(new AcceptanceConfig(base, properties), new PrintStream(out, true, StandardCharsets.UTF_8),
            (suite, otr) -> launches.incrementAndGet(), directory).runSuite();
    }
    private List<JsonNode> records() { return EvidenceBlockWriterTest.decode(out.toString(StandardCharsets.UTF_8)); }

    @Test void missingMountAbortsBeforeLauncherWithOneRunRecord() {
        assertThat(run(Map.of("AIGW_EXPECTED_SPEC_SHA256", CanonicalJson.sha256(SPEC)))).isEqualTo(3);
        assertThat(launches).hasValue(0);
        assertThat(records()).singleElement().satisfies(record -> {
            assertThat(record.path("kind").asText()).isEqualTo("RUN"); assertThat(record.path("status").asText()).isEqualTo("ABORTED");
            assertThat(record.path("reasonCode").asText()).isEqualTo("SPEC_INPUT_MISSING"); assertThat(record.has("specSha")).isFalse();
        });
    }

    @Test void oneChangedByteIsBaselineMismatchAndRunsNothing() throws Exception {
        byte[] changed = SPEC.clone(); changed[changed.length - 2] ^= 1;
        gzip(gzip, changed);
        assertThat(run(Map.of("AIGW_SPEC_GZIP_FILE", gzip.toString(), "AIGW_EXPECTED_SPEC_SHA256", CanonicalJson.sha256(SPEC)))).isEqualTo(3);
        assertThat(launches).hasValue(0);
        assertThat(records()).singleElement().satisfies(record -> assertThat(record.path("reasonCode").asText()).isEqualTo("SPEC_BASELINE_MISMATCH"));
    }

    @Test void missingExpectedValueIsSpecInputMissing() {
        assertThat(run(Map.of("AIGW_SPEC_GZIP_FILE", gzip.toString()))).isEqualTo(3);
        assertThat(launches).hasValue(0);
        assertThat(records()).singleElement().satisfies(record -> assertThat(record.path("reasonCode").asText()).isEqualTo("SPEC_INPUT_MISSING"));
    }

    @Test void approvedBytesRunTheSuiteAndBindComputedSha() {
        // 시험 0건 suite 는 COMPLETED 가 아니라 ABORTED(NO_TESTS_EXECUTED) 다. Launcher 호출과 SPEC 결속만 확인한다.
        assertThat(run(Map.of("AIGW_SPEC_GZIP_FILE", gzip.toString(), "AIGW_EXPECTED_SPEC_SHA256", CanonicalJson.sha256(SPEC)))).isEqualTo(4);
        assertThat(launches).hasValue(1);
        assertThat(records()).extracting(r -> r.path("status").asText()).containsExactly("STARTED", "ABORTED");
        records().forEach(record -> {
            assertThat(record.path("specSha").asText()).isEqualTo(CanonicalJson.sha256(SPEC));
            assertThat(record.path("specShaSource").asText()).isEqualTo("FILE_HASHED");
            assertThat(record.path("specInputOrigin").asText()).isEqualTo("ACCEPTANCE_MOUNT");
        });
        assertThat(out.toString(StandardCharsets.UTF_8)).doesNotContain("SPEC_BODY_SENTINEL");
        assertThat(directory.toFile().list()).containsExactly("spec.md.gz");
    }
}
