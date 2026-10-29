package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** F0 시험이 직접 읽는 레포·워크스페이스 자료 바이트가 Test 선언 입력과 run-binding·verify fingerprint 에 같은 값으로 결속되는지 실제 생산 스크립트로 확인합니다. */
class F0InputBindingFunctionalTest {
    static final Path ACTUAL = Path.of(System.getProperty("aigw.rootDir"));
    static final String SCRIPT = "gateway-eval/f0-test-inputs.gradle.kts";
    @TempDir Path directory;
    interface Change { void apply() throws IOException; }

    @Test void productionModuleAppliesTheSharedSnapshotScript() throws Exception {
        assertThat(Files.readString(ACTUAL.resolve("gateway-eval/build.gradle.kts"), StandardCharsets.UTF_8)).contains("apply(from = \"f0-test-inputs.gradle.kts\")");
    }

    @Test void everyReadInputByteChangeForcesRerunAndStaleVerifyUntilRerun() throws Exception {
        Path repo = directory.resolve("repo"); TestKitSupport fixture = new TestKitSupport(repo); fixture.passingTest();
        Files.copy(ACTUAL.resolve(SCRIPT), repo.resolve(SCRIPT));
        Files.writeString(repo.resolve("sub/build.gradle.kts"), "apply(from = rootProject.file(\"" + SCRIPT + "\"))\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        fixture.write("eval/golden/tc-f0-v1.jsonl", "{\"caseId\":\"G1\"}\n");
        fixture.write("eval/f0/prompts/classify-v1.txt", "A");
        fixture.write("eval/f0/extra-a.txt", "extra");
        fixture.write("eval/f0/arg-limits-v1.json", "{\"fields\":[{\"field\":\"eqp\",\"evidence\":[{\"kind\":\"CODE\",\"ref\":\"code-evidence/Entity.java:1\"}]}]}\n");
        fixture.write("eval/f0/results/raw.json", "{}");
        fixture.write("docs/gates/f0/arg-limits.md", "| eqp | CONFIRMED |\n");
        Path evidence = directory.resolve("code-evidence/Entity.java"); Files.createDirectories(evidence.getParent()); Files.writeString(evidence, "length = 64\n", StandardCharsets.UTF_8);

        assertThat(fixture.run(false, ":sub:test", "--build-cache").task(":sub:test").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        fixture.run(false, ":gateway-eval:run", fixture.verifyArgs(":sub:test"));

        Map<String,Change> changes = new LinkedHashMap<>();
        changes.put("golden prompt 1 byte", () -> fixture.write("eval/f0/prompts/classify-v1.txt", "B"));
        changes.put("arg-limits document 1 byte", () -> fixture.write("docs/gates/f0/arg-limits.md", "| eqp | CONFIRMEd |\n"));
        changes.put("new eval file", () -> fixture.write("eval/golden/new-case.jsonl", "{}\n"));
        changes.put("deleted eval file", () -> Files.delete(repo.resolve("eval/f0/extra-a.txt")));
        changes.put("workspace CODE evidence 1 byte", () -> Files.writeString(evidence, "length = 65\n", StandardCharsets.UTF_8));
        changes.put("workspace CODE evidence missing", () -> Files.delete(evidence));
        Set<String> runIds = new HashSet<>(Set.of(fixture.runId())), fingerprints = new HashSet<>(Set.of(fingerprint(repo)));
        for (var change : changes.entrySet()) {
            change.getValue().apply();
            assertThat(fixture.run(true, ":gateway-eval:run", fixture.verifyArgs(":sub:test")).getOutput()).as(change.getKey()).contains("STALE_TEST_EVIDENCE");
            assertThat(fixture.run(false, ":sub:test", "--build-cache").task(":sub:test").getOutcome()).as(change.getKey()).isEqualTo(TaskOutcome.SUCCESS);
            assertThat(runIds.add(fixture.runId())).as(change.getKey()).isTrue(); assertThat(fingerprints.add(fingerprint(repo))).as(change.getKey()).isTrue();
            fixture.run(false, ":gateway-eval:run", fixture.verifyArgs(":sub:test"));
        }

        // 기존 제외 경로(측정 결과)는 입력이 아니므로 재실행·증거 거부를 만들지 않습니다.
        String runId = fixture.runId();
        fixture.write("eval/f0/results/raw.json", "{\"changed\":true}");
        assertThat(fixture.run(false, ":sub:test", "--build-cache").task(":sub:test").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(fixture.runId()).isEqualTo(runId);
        fixture.run(false, ":gateway-eval:run", fixture.verifyArgs(":sub:test"));
        // 결속 값은 실제 내용 해시이며 기계마다 다른 절대 경로 문자열을 담지 않습니다.
        JsonNode binding = Json.read(repo.resolve("sub/build/aigw-otr/test/run-binding.json"));
        assertThat(binding.path("components").path("systemProperties").path("aigw.f0InputsSha256").asText()).matches("[0-9a-f]{64}");
        assertThat(binding.toString()).doesNotContain(directory.toString().replace('\\', '/')).doesNotContain(directory.toString().replace("\\", "\\\\"));
    }

    static String fingerprint(Path repo) { return Json.read(repo.resolve("sub/build/aigw-otr/test/run-binding.json")).path("inputFingerprint").asText(); }
}
