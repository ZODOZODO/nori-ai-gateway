package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TestCountGuardFunctionalTest {
    @TempDir Path directory;
    @Test void rejectsNoSourceNoTestsAndZeroMatchingFilterButAllowsUnrequestedEmptyPlaceholder() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);
        assertThat(fixture.run(true,":sub:test").getOutput()).contains("NO_TESTS_EXECUTED");
        assertThat(fixture.run(true,":gateway-eval:run",fixture.verifyArgs(":sub:test")).getOutput()).contains("NO_TESTS_EXECUTED");
        fixture.write("sub/src/test/java/probe/EmptyTest.java","package probe; class EmptyTest { int value=1; }\n");
        String empty=fixture.run(true,":sub:test").getOutput();assertThat(empty).containsAnyOf("NO_TESTS_EXECUTED","No tests found");
        fixture.passingTest();assertThat(fixture.run(true,":sub:test","--tests","probe.NothingMatches").getOutput()).contains("NO_TESTS_EXECUTED");
        fixture.run(false,"build");
        fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        assertThat(fixture.run(false,":placeholder:testLeafEvidence").getOutput()).contains("BUILD SUCCESSFUL");
    }
    @Test void reusesSameBindingForUpToDateAndCacheButRemeasuresEveryDeclaredContentInput() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);fixture.passingTest();fixture.write("sub/src/test/resources/nested/fixture.txt","v1");
        fixture.run(false,":sub:test","--build-cache");fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));String initial=fixture.runId();
        byte[] originalSummary=Files.readAllBytes(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json"));
        assertThat(fixture.run(false,":sub:test","--build-cache").task(":sub:test").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);assertThat(fixture.runId()).isEqualTo(initial);fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        assertThat(Files.readAllBytes(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json"))).isEqualTo(originalSummary);
        fixture.deleteOutput("sub/build");assertThat(fixture.run(false,":sub:test","--build-cache").task(":sub:test").getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);assertThat(fixture.runId()).isEqualTo(initial);fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        for(String change:List.of("spec","resource","catalog","baseline","class")) {
            String before=fixture.runId();
            switch(change) {
                case "spec" -> {Files.writeString(fixture.fixture.spec,"revised-approved-spec");fixture.fixture.baseline(Files.readAllBytes(fixture.fixture.spec));}
                case "resource" -> fixture.write("sub/src/test/resources/nested/fixture.txt","v2");
                case "catalog" -> Files.writeString(directory.resolve("gateway-eval/src/main/resources/com/nori/ai/gateway/eval/leaf-catalog-v2.json"),Files.readString(directory.resolve("gateway-eval/src/main/resources/com/nori/ai/gateway/eval/leaf-catalog-v2.json"))+"\n");
                case "class" -> fixture.write("sub/src/test/java/probe/PassingTest.java","package probe; import org.junit.jupiter.api.*; class PassingTest { @Test void checksFixtureArithmetic(){ Assertions.assertEquals(9,3*3); } }\n");
                case "baseline" -> Files.writeString(fixture.fixture.baseline,Files.readString(fixture.fixture.baseline)+"\n");
                default -> throw new AssertionError();
            }
            assertThat(fixture.run(false,":sub:test","--build-cache").task(":sub:test").getOutcome()).as(change).isEqualTo(TaskOutcome.SUCCESS);
            assertThat(fixture.runId()).isNotEqualTo(before);assertThat(Json.read(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json")).path("summary").path("specSha").asText()).isEqualTo(fixture.fixture.binding().specSha());fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        }
    }
    @Test void staleOtrAndRecursivelyChangedCompiledResourceCannotBeRelabeledByFinalizer() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);fixture.passingTest();fixture.write("sub/src/test/resources/nested/deep/input.txt","one");fixture.run(false,":sub:test");fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        Path binding=directory.resolve("sub/build/aigw-otr/test/run-binding.json");byte[] preserved=Files.readAllBytes(binding);
        Files.writeString(fixture.fixture.spec,"new-approved-spec");fixture.fixture.baseline(Files.readAllBytes(fixture.fixture.spec));
        assertThat(fixture.run(true,":sub:testLeafEvidence").getOutput()).contains("STALE_TEST_EVIDENCE");assertThat(fixture.run(true,":gateway-eval:run",fixture.verifyArgs(":sub:test")).getOutput()).contains("STALE_TEST_EVIDENCE");assertThat(Files.readAllBytes(binding)).isEqualTo(preserved);
        fixture.run(false,":sub:test");fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        Files.writeString(directory.resolve("sub/build/resources/test/nested/deep/input.txt"),"changed-compiled-resource");
        assertThat(fixture.run(true,":sub:testLeafEvidence").getOutput()).contains("STALE_TEST_EVIDENCE");
        // 독립 verify는 시험 classpath를 먼저 갱신하므로 산출물 직접 변조는 원복됩니다. 원본 하위 리소스 변경이 재귀 해시로 STALE이 되는지 확인합니다.
        String bindingBefore=fixture.runId();fixture.write("sub/src/test/resources/nested/deep/input.txt","two");
        assertThat(fixture.run(true,":gateway-eval:run",fixture.verifyArgs(":sub:test")).getOutput()).contains("STALE_TEST_EVIDENCE");assertThat(fixture.runId()).isEqualTo(bindingBefore);
    }
    @Test void rootSelectorRequiresRegisteredIntegrationModuleOnly() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);fixture.write("ci/integration-required.txt",":sub\n");
        assertThat(fixture.run(true,"integrationTest").getOutput()).contains("NO_TESTS_EXECUTED");
        assertThat(fixture.run(true,":gateway-eval:run","--args=test-evidence verify --selector integrationTest --required ci/integration-required.txt").getOutput()).contains("NO_TESTS_EXECUTED");
        fixture.write("ci/integration-required.txt","# 미등록 placeholder는 필수가 아닙니다.\n");assertThat(fixture.run(false,"integrationTest").getOutput()).contains("BUILD SUCCESSFUL");
    }
    @Test void equivalentCheckoutsHaveSameInputFingerprintDespiteAbsoluteLocation() throws Exception {
        TestKitSupport first=new TestKitSupport(directory.resolve("first")),second=new TestKitSupport(directory.resolve("second"));first.passingTest();second.passingTest();
        first.run(false,":sub:test");first.run(false,":gateway-eval:run",first.verifyArgs(":sub:test"));second.run(false,":sub:test");second.run(false,":gateway-eval:run",second.verifyArgs(":sub:test"));
        assertThat(Json.read(first.root.resolve("sub/build/aigw-otr/test/run-binding.json")).path("inputFingerprint")).isEqualTo(Json.read(second.root.resolve("sub/build/aigw-otr/test/run-binding.json")).path("inputFingerprint"));
    }
}
