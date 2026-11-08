package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.ci.BuildIdentityManifest;
import com.nori.ai.gateway.eval.ci.TarGz;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.EvidenceRecord;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static com.nori.ai.gateway.eval.TestSupport.error;
import static org.assertj.core.api.Assertions.*;

class CiEvidenceTest {
    @TempDir Path directory;
    CiEvidenceFixture x;
    @BeforeEach void fixture() throws Exception { x = new CiEvidenceFixture(directory); }

    private void verify(Path bundle, String sha) { x.evidence().verify(bundle, sha, x.required, x.f.binding(), BuildIdentityManifest.load(x.identity)); }
    /** pack 을 우회해 정제 요약 하나를 바꾼 묶음을 만든다(manifest 의 파일 SHA 는 새 바이트로 맞춘다). */
    private Path rewrite(Path bundle, String name, byte[] bytes, boolean updateManifest) {
        var entries = new TreeMap<>(TarGz.read(bundle));
        entries.put(name, bytes);
        if (updateManifest) {
            ObjectNode manifest = (ObjectNode) Json.parse(entries.get("bundle.json"));
            ((ObjectNode) manifest.get("files")).put(name, Json.sha(bytes));
            entries.put("bundle.json", Json.canonical(manifest));
        }
        Path out = directory.resolve("rewritten-" + UUID.randomUUID() + ".tgz");
        TarGz.write(out, entries);
        return out;
    }
    private Path withSummary(Path bundle, Map<String,Object> changes) {
        ObjectNode summary = x.summary.deepCopy();
        changes.forEach((key, value) -> summary.set(key, Json.MAPPER.valueToTree(value)));
        EvidenceRecord.seal(summary);
        return rewrite(bundle, "sub/build/aigw-evidence/integrationTest/leaf-results.json", Json.canonical(Json.MAPPER.valueToTree(Map.of("summary", summary, "records", x.records))), true);
    }
    private static byte[] gunzip(Path file) throws IOException { try (var in = new GZIPInputStream(Files.newInputStream(file))) { return in.readAllBytes(); } }

    @Test void packedBundleHoldsOnlySanitizedSummariesAndVerifiesForSameCommitAndSpec() throws Exception {
        Path bundle = x.pack();
        assertThat(TarGz.read(bundle).keySet()).containsExactly("bundle.json", "sub/build/aigw-evidence/integrationTest/leaf-results.json");
        String content = new String(gunzip(bundle), StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("승인 fixture SPEC", "<?xml", "testcase", "stackTrace");
        var manifest = Json.parse(TarGz.read(bundle).get("bundle.json"));
        assertThat(manifest.path("gitSha").asText()).isEqualTo(CiEvidenceFixture.SHA);
        assertThat(manifest.path("worktreeClean").asBoolean()).isTrue();
        assertThat(manifest.path("dockerServerVersion").asText()).isEqualTo("29.9.2");
        assertThat(manifest.path("testcontainersVersion").asText()).isEqualTo("2.0.3");
        assertThat(manifest.path("specSha").asText()).isEqualTo(x.f.binding().specSha());
        verify(bundle, CiEvidenceFixture.SHA);
        // 미등록 모듈의 부재·카탈로그 leaf 부재는 실패가 아니다(부재 판정은 gate 몫).
        assertThat(x.f.cli("ci-evidence", "verify", "--sha", CiEvidenceFixture.SHA, "--bundle", "it-evidence.tgz", "--required", "ci/integration-required.txt",
            "--expect-build-identity", "build/package/build-identity.json")).isZero();
    }

    @Test void packMakesNoBundleWhenPrecheckFails() throws Exception {
        x.summary.put("skippedTestCount", 1); x.save();
        error(x::pack, 4, "TESTS_SKIPPED");
        assertThat(directory.resolve("it-evidence.tgz")).doesNotExist();
        x.summary.put("skippedTestCount", 0); x.summary.put("specShaSource", "BASELINE_DECLARED"); x.save();
        assertThatThrownBy(x::pack).isInstanceOf(EvalException.class);
        assertThat(directory.resolve("it-evidence.tgz")).doesNotExist();
    }

    @Test void packRefusesMissingOrMismatchedSpecInputBeforeAnyBundle() throws Exception {
        var noSpec = new EvalCli(Map.of(), new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), directory);
        assertThat(noSpec.execute("ci-evidence", "pack", "--out", "it-evidence.tgz")).isEqualTo(2);
        Files.writeString(x.f.spec, "변조된 SPEC\n");
        assertThat(x.f.cli("ci-evidence", "pack", "--out", "it-evidence.tgz")).isEqualTo(3);
        assertThat(directory.resolve("it-evidence.tgz")).doesNotExist();
    }

    @Test void verifyRejectsEachBundleDefect() throws Exception {
        Path bundle = x.pack();
        error(() -> verify(directory.resolve("absent.tgz"), CiEvidenceFixture.SHA), 4, "CI_EVIDENCE_BUNDLE_MISSING");
        Files.write(directory.resolve("broken.tgz"), new byte[]{1, 2, 3});
        error(() -> verify(directory.resolve("broken.tgz"), CiEvidenceFixture.SHA), 4, "CI_EVIDENCE_BUNDLE_INVALID");
        error(() -> verify(bundle, "f".repeat(40)), 4, "CI_EVIDENCE_SHA_MISMATCH");
        error(() -> verify(withSummary(bundle, Map.of("skipped", 1, "skippedTestCount", 1)), CiEvidenceFixture.SHA), 4, "TESTS_SKIPPED");
        error(() -> verify(withSummary(bundle, Map.of("failed", 1)), CiEvidenceFixture.SHA), 4, "TESTS_FAILED");
        error(() -> verify(withSummary(bundle, Map.of("executedTestCount", 0)), CiEvidenceFixture.SHA), 4, "NO_TESTS_EXECUTED");
        error(() -> verify(withSummary(bundle, Map.of("buildIdentity", Map.of("gitSha", CiEvidenceFixture.SHA, "runtimeClasspathSha256", "9".repeat(64)))), CiEvidenceFixture.SHA), 4, "BUILD_IDENTITY_MISMATCH");
        // 요약 바이트를 바꾸고 contentSha256 을 다시 봉인하지 않으면 거부한다.
        byte[] tampered = new String(TarGz.read(bundle).get("sub/build/aigw-evidence/integrationTest/leaf-results.json"), StandardCharsets.UTF_8)
            .replace("\"executedTestCount\":1", "\"executedTestCount\":2").getBytes(StandardCharsets.UTF_8);
        error(() -> verify(rewrite(bundle, "sub/build/aigw-evidence/integrationTest/leaf-results.json", tampered, true), CiEvidenceFixture.SHA), 4, "CI_EVIDENCE_CONTENT_MISMATCH");
        error(() -> verify(rewrite(bundle, "sub/build/aigw-evidence/integrationTest/leaf-results.json", tampered, false), CiEvidenceFixture.SHA), 4, "CI_EVIDENCE_CONTENT_MISMATCH");
        error(() -> verify(rewrite(bundle, "sub/build/test-results/integrationTest/TEST-probe.RealIT.xml", "<testcase/>".getBytes(StandardCharsets.UTF_8), true), CiEvidenceFixture.SHA), 4, "CI_EVIDENCE_UNEXPECTED_ENTRY");
        Files.writeString(x.required, ":sub probe.RealIT,probe.OtherIT\n");
        error(() -> verify(bundle, CiEvidenceFixture.SHA), 4, "REQUIRED_CLASS_NOT_EXECUTED");
        // 등록 모듈의 요약이 없으면(NO-SOURCE·미실행) 거부한다.
        Files.writeString(x.required, ":sub probe.RealIT\n:other probe.MissingIT\n");
        error(() -> verify(bundle, CiEvidenceFixture.SHA), 4, "REQUIRED_TASK_SUMMARY_MISSING");
        Files.writeString(x.required, ":sub probe.RealIT\n");
        x.status = " M gateway-app/src/main/java/Changed.java\n";
        Path dirty = x.pack();
        error(() -> verify(dirty, CiEvidenceFixture.SHA), 4, "CI_EVIDENCE_DIRTY");
    }

    @Test void verifyRecomputesSpecShaFromTheCiInput() throws Exception {
        Path bundle = x.pack();
        var noSpec = new EvalCli(Map.of(), new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), directory);
        assertThat(noSpec.execute("ci-evidence", "verify", "--sha", CiEvidenceFixture.SHA, "--bundle", "it-evidence.tgz", "--required", "ci/integration-required.txt",
            "--expect-build-identity", "build/package/build-identity.json")).isEqualTo(2);
        error(() -> verify(withSummary(bundle, Map.of("specShaSource", "BASELINE_DECLARED")), CiEvidenceFixture.SHA), 3, "SPEC_SHA_SOURCE_INVALID");
        // 변조 SPEC 과 그에 맞춘 기준선으로 만든 묶음은 승인 바이트 CI 입력과 맞지 않는다.
        Path other = Files.createDirectory(directory.resolve("tampered"));
        var tampered = new CiEvidenceFixture(other);
        Files.writeString(tampered.f.spec, "변조 SPEC\n"); tampered.f.baseline("변조 SPEC\n".getBytes(StandardCharsets.UTF_8));
        tampered.summary = tampered.f.record(Map.ofEntries(Map.entry("kind", "RUN"), Map.entry("status", "COMPLETED"), Map.entry("stage", "F0"), Map.entry("runId", "r"),
            Map.entry("executedTestCount", 1), Map.entry("inputFingerprint", "a".repeat(64)), Map.entry("executedClasses", List.of("probe.RealIT")),
            Map.entry("skippedTestCount", 0), Map.entry("skipped", 0), Map.entry("failed", 0), Map.entry("aborted", 0),
            Map.entry("buildIdentity", Map.of("gitSha", CiEvidenceFixture.SHA, "runtimeClasspathSha256", CiEvidenceFixture.CLASSPATH))));
        tampered.records.clear(); tampered.save();
        Json.write(other.resolve("sub/build/aigw-otr/integrationTest/run-binding.json"), Map.of("runId", "r", "specSha", tampered.f.binding().specSha(), "inputFingerprint", "a".repeat(64)));
        Path forged = tampered.pack();
        error(() -> verify(forged, CiEvidenceFixture.SHA), 3, "SPEC_BASELINE_MISMATCH");
        // CI 입력 바이트가 바뀌면 같은 기준선에서 SpecGuard 가 먼저 거부한다.
        Files.writeString(x.f.spec, "CI 변조 입력\n");
        assertThat(x.f.cli("ci-evidence", "verify", "--sha", CiEvidenceFixture.SHA, "--bundle", "it-evidence.tgz", "--required", "ci/integration-required.txt",
            "--expect-build-identity", "build/package/build-identity.json")).isEqualTo(3);
    }
}
