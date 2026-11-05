package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.OtrLeafExtractor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeClasspathFileTest {
    @TempDir Path directory;
    TestSupport fixture;
    Path otr, output, runtime;
    Map<String,String> env;
    ByteArrayOutputStream errors;
    static final String FINGERPRINT = "a".repeat(64);

    @BeforeEach void prepare() throws Exception {
        fixture = new TestSupport(directory);
        otr = Files.createDirectories(directory.resolve("otr"));
        output = directory.resolve("leaf-results.json");
        runtime = directory.resolve("runtime classpath.txt");
        Files.writeString(runtime, "com.example:library:1.0 sha256=012345\n", StandardCharsets.UTF_8);
        Json.write(otr.resolve("run-binding.json"), Map.of("runId", "runtime-file-test", "inputFingerprint", FINGERPRINT,
            "specSha", fixture.binding().specSha(), "recordedAt", "2026-11-05T00:00:00Z"));
        Files.writeString(otr.resolve("events.xml"), """
            <e:events xmlns:e="https://schemas.opentest4j.org/reporting/events/0.2.0"
                      xmlns:j="https://schemas.junit.org/open-test-reporting"
                      xmlns:c="https://schemas.opentest4j.org/reporting/core/0.2.0"
                      xmlns:java="https://schemas.opentest4j.org/reporting/java/0.2.0">
              <e:started id="1"><j:type>TEST</j:type><c:tag>AC-24-JOB</c:tag>
                <java:methodSource className="probe.RuntimeTest"/></e:started>
              <e:finished id="1"><c:result status="SUCCESSFUL"/></e:finished>
            </e:events>
            """);
        env = new HashMap<>(Map.of("AIGW_SPEC_FILE", fixture.spec.toString()));
        errors = new ByteArrayOutputStream();
    }

    int cli(String... extra) {
        List<String> args = new ArrayList<>(List.of("leaf-extract", "--otr-dir", "otr", "--task", ":probe:test",
            "--module", ":probe", "--out", "leaf-results.json", "--current-fingerprint", FINGERPRINT, "--require-executed"));
        args.addAll(List.of(extra));
        return new EvalCli(env, new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors), directory).execute(args.toArray(String[]::new));
    }

    void assertIdentity(String expected) {
        JsonNode evidence = Json.read(output);
        assertThat(evidence.path("summary").path("executedTestCount").asInt()).isEqualTo(1);
        assertThat(evidence.path("records").size()).isEqualTo(1);
        List<JsonNode> records = List.of(evidence.path("summary"), evidence.path("records").get(0));
        for (JsonNode record : records) {
            JsonNode hash = record.path("buildIdentity").path("runtimeClasspathSha256");
            if (expected == null) assertThat(hash.isNull()).isTrue();
            else assertThat(hash.asText()).isEqualTo(expected);
            assertThat(record.path("buildIdentity").path("inputFingerprint").asText()).isEqualTo(FINGERPRINT);
        }
    }

    @Test void cliHashesActualFileAndRecomputesAfterBytesChange() throws Exception {
        assertThat(cli("--runtime-classpath-file", runtime.getFileName().toString())).isZero();
        assertIdentity(Json.sha(Files.readAllBytes(runtime)));
        Files.writeString(runtime, "changed dependency bytes\n");
        assertThat(cli("--runtime-classpath-file", runtime.toString())).isZero();
        assertIdentity(Json.sha(Files.readAllBytes(runtime)));
        assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test void explicitFileOverridesMisleadingEnvironmentHash() {
        env.put("AIGW_RUNTIME_CLASSPATH_SHA256", "f".repeat(64));
        assertThat(cli("--runtime-classpath-file", runtime.toString())).isZero();
        assertIdentity(Json.sha(Json.bytes(runtime)));
    }

    @Test void missingExplicitFileDeletesOldOutputWithoutEnvironmentFallback() throws Exception {
        env.put("AIGW_RUNTIME_CLASSPATH_SHA256", "f".repeat(64));
        Files.writeString(output, "stale output");
        assertThat(cli("--runtime-classpath-file", "missing.txt")).isEqualTo(2);
        assertThat(errors.toString(StandardCharsets.UTF_8).strip()).isEqualTo("INPUT_MISSING");
        assertThat(output).doesNotExist();
    }

    @Test void nonregularExplicitFileDeletesOldOutputWithoutEnvironmentFallback() throws Exception {
        env.put("AIGW_RUNTIME_CLASSPATH_SHA256", "f".repeat(64));
        Files.writeString(output, "stale output");
        assertThat(cli("--runtime-classpath-file", otr.toString())).isEqualTo(2);
        assertThat(errors.toString(StandardCharsets.UTF_8).strip()).isEqualTo("INPUT_MISSING");
        assertThat(output).doesNotExist();
    }

    @Test void explicitFileStillFailsWhenNoTestsWereRun() throws Exception {
        Files.delete(otr.resolve("run-binding.json"));
        Files.writeString(output, "stale output");
        assertThat(cli("--runtime-classpath-file", runtime.toString())).isEqualTo(4);
        assertThat(errors.toString(StandardCharsets.UTF_8).strip()).isEqualTo("NO_TESTS_EXECUTED");
        assertThat(output).doesNotExist();
    }

    @Test void noFileKeepsNullValidAndInvalidEnvironmentBehavior() {
        assertThat(cli()).isZero(); assertIdentity(null);
        env.put("AIGW_RUNTIME_CLASSPATH_SHA256", "f".repeat(64));
        assertThat(cli()).isZero(); assertIdentity("f".repeat(64));
        env.put("AIGW_RUNTIME_CLASSPATH_SHA256", "invalid");
        assertThat(cli()).isZero(); assertIdentity(null);
    }

    @Test void existingExtractorSignatureRemainsUsableWithoutFile() {
        new OtrLeafExtractor().extract(otr, ":probe:test", ":probe", fixture.baseline, fixture.spec.toString(),
            output, FINGERPRINT, true, fixture.catalog, env);
        assertIdentity(null);
    }
}
