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

/** 하네스 경계: stdout 은 블록뿐, 입력 오류는 고정 사유, skip·abort·무효 OTR 은 성공(exit 0)이 아니다. */
class AcceptanceMainTest {
    static final String SENTINEL = "ACCEPTANCE_NARRATIVE_SENTINEL";
    static final byte[] SPEC = "# 승인 SPEC 바이트\n".getBytes(StandardCharsets.UTF_8);
    @TempDir Path directory;
    final AtomicInteger launches = new AtomicInteger();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    static String otr(String... results) {
        var xml = new StringBuilder("<e:events xmlns:e=\"https://schemas.opentest4j.org/reporting/events/0.2.0\" xmlns:c=\"https://schemas.opentest4j.org/reporting/core/0.2.0\""
            + " xmlns:j=\"https://schemas.junit.org/open-test-reporting\">");
        int id = 0;
        for (String result : results) {
            if (result.startsWith("finished:")) { xml.append("<e:finished id=\"").append(result.substring(9, result.indexOf('='))).append("\" time=\"2026-11-07T00:00:00Z\"><c:result status=\"")
                .append(result.substring(result.indexOf('=') + 1)).append("\"/></e:finished>"); continue; }
            id++;
            xml.append("<e:started id=\"").append(id).append("\" name=\"t").append(id).append("\" time=\"2026-11-07T00:00:00Z\"><c:metadata><c:tags><c:tag>AC-24-JOB</c:tag>")
                .append("<c:tag>label-MOCK_API</c:tag></c:tags></c:metadata><j:type>TEST</j:type></e:started>")
                .append("<e:finished id=\"").append(id).append("\" time=\"2026-11-07T00:00:00Z\"><c:result status=\"").append(result).append("\"/></e:finished>");
        }
        return xml.append("</e:events>").toString();
    }
    private int run(Map<String,String> env, String otrXml) throws IOException {
        Path gzip = directory.resolve("spec.md.gz");
        try (var stream = new GZIPOutputStream(Files.newOutputStream(gzip))) { stream.write(SPEC); }
        var base = new HashMap<>(Map.of("AIGW_PARENT_RUN_ID", "F1-20261107T000000Z-0a1b2c3d", "AIGW_SPEC_GZIP_FILE", gzip.toString(),
            "AIGW_EXPECTED_SPEC_SHA256", CanonicalJson.sha256(SPEC), "AIGW_BUILD_IDENTITY_JSON", "{\"appImageDigest\":\"sha256:" + "b".repeat(64) + "\"}"));
        base.putAll(env);
        Path temporary = Files.createDirectories(directory.resolve("tmp"));
        return new AcceptanceMain(new AcceptanceConfig(base, Map.of("aigw.acceptance.suite", "F1")), new PrintStream(out, true, StandardCharsets.UTF_8), (suite, otr) -> {
            launches.incrementAndGet();
            try { Files.writeString(otr.resolve("junit-platform-events.xml"), otrXml); } catch (IOException e) { throw new UncheckedIOException(e); }
        }, temporary).runSuite();
    }
    private List<JsonNode> records() { return EvidenceBlockWriterTest.decode(out.toString(StandardCharsets.UTF_8)); }
    private JsonNode last() { var all = records(); return all.getLast(); }

    @Test void onlyPassedTestsExitZero() throws Exception {
        assertThat(run(Map.of(), otr("SUCCESSFUL", "SUCCESSFUL"))).isZero();
        assertThat(last().path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test void abortedOrSkippedTestsAreNotSuccess() throws Exception {
        assertThat(run(Map.of(), otr("SUCCESSFUL", "ABORTED"))).isEqualTo(1);
        assertThat(records()).filteredOn(r -> r.path("kind").asText().equals("LEAF")).singleElement()
            .satisfies(leaf -> assertThat(leaf.path("result").asText()).isEqualTo("NOT_RUN"));
        out.reset();
        assertThat(run(Map.of(), otr("SUCCESSFUL", "SKIPPED"))).isEqualTo(1);
    }

    @Test void duplicateFinishedCannotTurnFailureIntoSuccess() throws Exception {
        assertThat(run(Map.of(), otr("FAILED", "finished:1=SUCCESSFUL"))).isEqualTo(1);
        assertThat(last().path("status").asText()).isEqualTo("ABORTED");
        assertThat(last().path("reasonCode").asText()).isEqualTo("OTR_INPUT_INVALID");
        assertThat(records()).noneMatch(r -> r.path("kind").asText().equals("LEAF"));
        out.reset();
        assertThat(run(Map.of(), otr("SUCCESSFUL", "finished:9=FAILED"))).isEqualTo(1);
        assertThat(last().path("reasonCode").asText()).isEqualTo("OTR_INPUT_INVALID");
    }

    @Test void unusableSpecPathOrOversizedInputIsSpecInputMissingBeforeLauncher() throws Exception {
        assertThat(run(Map.of("AIGW_SPEC_GZIP_FILE", "bad\u0000path"), otr("SUCCESSFUL"))).isEqualTo(3);
        assertThat(launches).hasValue(0);
        assertThat(records()).singleElement().satisfies(r -> assertThat(r.path("reasonCode").asText()).isEqualTo("SPEC_INPUT_MISSING"));
        // 해제 4 MiB 초과(압축은 작다)·압축 2 MiB 초과는 끝까지 풀지 않고 같은 사유로 끝난다.
        Path bomb = directory.resolve("bomb.gz");
        try (var stream = new GZIPOutputStream(Files.newOutputStream(bomb))) { stream.write(new byte[4_194_305]); }
        assertThatThrownBy(() -> SpecInputVerifier.verify(bomb, "0".repeat(64))).isInstanceOfSatisfying(SpecInputVerifier.Failure.class, f -> assertThat(f.code()).isEqualTo("SPEC_INPUT_MISSING"));
        Path large = directory.resolve("large.gz");
        byte[] noise = new byte[2_200_000]; new Random(7).nextBytes(noise);
        try (var stream = new GZIPOutputStream(Files.newOutputStream(large))) { stream.write(noise); }
        assertThat(Files.size(large)).isGreaterThan(2_097_152L);
        assertThatThrownBy(() -> SpecInputVerifier.verify(large, "0".repeat(64))).isInstanceOfSatisfying(SpecInputVerifier.Failure.class, f -> assertThat(f.code()).isEqualTo("SPEC_INPUT_MISSING"));
    }

    /** 시험·라이브러리가 System.out/err 에 쓴 서술과 예외 원문은 프로세스 출력에 0. 증거 스트림에는 블록 1개만. */
    @Test void isolatedRunDiscardsNarrativeOutputAndMapsUnexpectedFailureToFixedCode() {
        PrintStream originalOut = System.out, originalErr = System.err;
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8)); System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try {
            int exit = AcceptanceMain.isolated(new PrintStream(out, true, StandardCharsets.UTF_8), evidence -> {
                System.out.println(SENTINEL); System.err.println(SENTINEL);
                throw new IllegalStateException(SENTINEL);
            });
            assertThat(exit).isEqualTo(1);
            assertThat(stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8)).doesNotContain(SENTINEL);
        } finally { System.setOut(originalOut); System.setErr(originalErr); }
        String evidence = out.toString(StandardCharsets.UTF_8);
        assertThat(evidence).doesNotContain(SENTINEL);
        assertThat(evidence.lines().filter(line -> !line.startsWith("AIGW-EVIDENCE-")).count()).isEqualTo(1);
        assertThat(records()).singleElement().satisfies(r -> assertThat(r.path("reasonCode").asText()).isEqualTo("ACCEPTANCE_HARNESS_FAILED"));
    }
}
