package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeClasspathFinalizerFunctionalTest {
    @TempDir Path directory;

    @Test void rootFinalizerBuildsAndHashesRuntimeFileBeforeExtractingEvidence() throws Exception {
        TestKitSupport fixture = new TestKitSupport(directory);
        fixture.passingTest();
        fixture.write("settings.gradle.kts", TestKitSupport.buildLogicSettings() + "rootProject.name=\"runtime-finalizer-fixture\"\ninclude(\"gateway-eval\",\"sub\",\":apps:ai-gateway-app\")\nproject(\":apps\").projectDir = file(\"modules/apps\")\nproject(\":apps:ai-gateway-app\").projectDir = file(\"modules/apps/ai-gateway-app\")\n");
        fixture.write("modules/apps/ai-gateway-app/build.gradle.kts", """
            val runtime = layout.buildDirectory.file("aigw/runtime-classpath.txt")
            tasks.register("writeRuntimeClasspath") {
                outputs.file(runtime)
                doLast {
                    runtime.get().asFile.apply { parentFile.mkdirs(); writeText("fixture:dependency:1 sha256=abc\\n", Charsets.UTF_8) }
                }
            }
            """);
        String head = git(Path.of(System.getProperty("aigw.rootDir")), "rev-parse", "HEAD");
        git(directory, "init");
        fixture.write(".git/HEAD", head + "\n");
        fixture.env.put("AIGW_GIT_SHA", "0".repeat(40));
        var build = fixture.run(false, ":sub:test");
        assertThat(build.task(":apps:ai-gateway-app:writeRuntimeClasspath")).isNotNull();
        assertThat(build.getOutput().indexOf(":apps:ai-gateway-app:writeRuntimeClasspath"))
            .isLessThan(build.getOutput().indexOf(":sub:testLeafEvidence"));
        Path runtime = directory.resolve("modules/apps/ai-gateway-app/build/aigw/runtime-classpath.txt");
        var evidence = Json.read(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json"));
        assertThat(evidence.path("summary").path("buildIdentity").path("gitSha").asText()).isEqualTo(head);
        assertThat(evidence.path("summary").path("buildIdentity").path("runtimeClasspathSha256").asText()).isEqualTo(Json.sha(Json.bytes(runtime)));
        fixture.run(false, ":gateway-eval:run", fixture.verifyArgs(":sub:test"));
        Files.delete(runtime);
        fixture.write("modules/apps/ai-gateway-app/build.gradle.kts", "tasks.register(\"writeRuntimeClasspath\")\n");
        assertThat(fixture.run(true, ":sub:testLeafEvidence").getOutput()).contains("INPUT_MISSING");
        assertThat(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json")).doesNotExist();
    }
    private String git(Path root, String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(java.util.List.of("git", "-C", root.toString()));
        command.addAll(java.util.List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertThat(process.waitFor()).as("Git 신원 확인").isZero();
        return output;
    }
}

