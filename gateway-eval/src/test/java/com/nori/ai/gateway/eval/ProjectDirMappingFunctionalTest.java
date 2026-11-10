package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class ProjectDirMappingFunctionalTest {
    @TempDir Path directory;

    // 제품 모듈은 settings 의 projectDir 로 modules/ 아래에 둔다. 실제 루트 build 가 그 위치를 fingerprint 에 기록하고 verify 가 그 위치의 증거를 찾아야 한다.
    @Test void verifyFindsEvidenceOfModuleMappedUnderModulesDirectory() throws Exception {
        TestKitSupport fixture = new TestKitSupport(directory);
        fixture.passingTest();
        Files.createDirectories(directory.resolve("modules/apps"));
        Files.move(directory.resolve("sub"), directory.resolve("modules/apps/sub"));
        fixture.write("settings.gradle.kts", TestKitSupport.buildLogicSettings() + "rootProject.name=\"project-dir-fixture\"\ninclude(\"gateway-eval\",\":apps:sub\")\n"
            + "project(\":apps\").projectDir = file(\"modules/apps\")\nproject(\":apps:sub\").projectDir = file(\"modules/apps/sub\")\n");
        fixture.run(false, ":apps:sub:test");
        fixture.run(false, ":gateway-eval:run", fixture.verifyArgs(":apps:sub:test"));
        assertThat(Json.read(directory.resolve("build/aigw-fingerprint/_apps_sub_test.json")).path("projectDir").asText()).isEqualTo("modules/apps/sub");
    }
}
