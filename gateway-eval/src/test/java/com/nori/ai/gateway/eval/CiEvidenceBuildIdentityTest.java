package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** runner 모드(test-evidence verify)와 evidence 모드(ci-evidence verify)가 같은 manifest 판정을 쓰는지 같은 입력으로 확인한다. */
class CiEvidenceBuildIdentityTest {
    @TempDir Path directory;
    CiEvidenceFixture x;
    @BeforeEach void fixture() throws Exception { x = new CiEvidenceFixture(directory); x.pack(); }

    private int runner() { return x.f.cli("test-evidence", "verify", "--tasks", ":sub:integrationTest", "--require-docker", "--expect-build-identity", "build/package/build-identity.json"); }
    private int evidence() {
        return x.f.cli("ci-evidence", "verify", "--sha", CiEvidenceFixture.SHA, "--bundle", "it-evidence.tgz", "--required", "ci/integration-required.txt",
            "--expect-build-identity", "build/package/build-identity.json");
    }

    @Test void sameManifestPassesBothModes() { assertThat(runner()).isZero(); assertThat(evidence()).isZero(); }

    @Test void missingOrMalformedManifestIsExitTwoInBothModes() throws Exception {
        Files.delete(x.identity);
        assertThat(runner()).isEqualTo(2); assertThat(evidence()).isEqualTo(2);
        Json.write(x.identity, Map.of("gitSha", CiEvidenceFixture.SHA, "runtimeClasspathSha256", CiEvidenceFixture.CLASSPATH));
        assertThat(runner()).isEqualTo(2); assertThat(evidence()).isEqualTo(2);
        Json.write(x.identity, Map.of("gitSha", "HEAD", "appJarSha256", "e".repeat(64), "runtimeClasspathSha256", CiEvidenceFixture.CLASSPATH));
        assertThat(runner()).isEqualTo(2); assertThat(evidence()).isEqualTo(2);
    }

    @Test void differentGitShaOrRuntimeClasspathIsRejectedInBothModes() {
        x.identity("f".repeat(40), CiEvidenceFixture.CLASSPATH);
        assertThat(runner()).isEqualTo(4); assertThat(evidence()).isEqualTo(4);
        x.identity(CiEvidenceFixture.SHA, "9".repeat(64));
        assertThat(runner()).isEqualTo(4); assertThat(evidence()).isEqualTo(4);
        x.identity(CiEvidenceFixture.SHA, CiEvidenceFixture.CLASSPATH);
        assertThat(runner()).isZero(); assertThat(evidence()).isZero();
    }

    @Test void runnerModeComparesAgainstTheCiCommitWhenPresent() {
        var cli = new EvalCli(Map.of("AIGW_SPEC_FILE", x.f.spec.toString(), "CI_COMMIT_SHA", "f".repeat(40)),
            new java.io.PrintStream(new java.io.ByteArrayOutputStream()), new java.io.PrintStream(new java.io.ByteArrayOutputStream()), directory);
        assertThat(cli.execute("test-evidence", "verify", "--tasks", ":sub:integrationTest", "--expect-build-identity", "build/package/build-identity.json")).isEqualTo(4);
    }
}
