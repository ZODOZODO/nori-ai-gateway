package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RunWorkingDirFunctionalTest {
    @TempDir Path directory;
    @Test void resolvesSubprojectRunAndGradlePropertyPathsAgainstRepositoryRoot() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);fixture.passingTest();fixture.env.remove("AIGW_SPEC_FILE");
        fixture.run(false,":sub:run","--args=spec-input pack --spec spec.md --baseline docs/gates/spec-baseline.md --out-dir build/x");
        assertThat(directory.resolve("build/x/"+fixture.fixture.binding().specSha()+"/spec.md.gz")).exists();assertThat(directory.resolve("sub/build/x")).doesNotExist();
        fixture.run(false,":sub:test","-Paigw.specPath=spec.md");fixture.run(false,":gateway-eval:run","--args=test-evidence verify --tasks :sub:test --spec spec.md");
        assertThat(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json")).exists();
    }
    @Test void sanitizedEnvironmentSupportsExplicitCiPositiveAndNegativeFixtures() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);fixture.passingTest();assertThat(fixture.env.keySet()).noneMatch(k->k.equals("CI")||k.startsWith("CI_")||k.startsWith("GITLAB_"));fixture.env.put("CI","true");fixture.env.put("AIGW_APPROVED_SPEC_SHA",fixture.fixture.binding().specSha());
        assertThat(fixture.run(true,":sub:test").getOutput()).contains("SPEC_INPUT_UNVERIFIED_SOURCE");
        String sha=fixture.fixture.binding().specSha();Json.write(fixture.fixture.spec.resolveSibling("fetch-receipt.json"),TestSupport.receipt(fixture.fixture.spec,sha));
        fixture.run(false,":sub:test","--rerun-tasks");fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        assertThat(Json.read(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json")).path("summary").path("specInputOrigin").asText()).isEqualTo("CI_VERIFIED_PACKAGE");
    }
}
