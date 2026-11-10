package com.nori.ai.gateway.integration.tokenizer;

import com.nori.ai.gateway.integration.testing.DockerRequiredExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import java.io.File;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(DockerRequiredExtension.class)
class TokenizerNativeSmokeIT {
    @TempDir Path directory;

    @Test void loadsPinnedNativeLocallyAndInsideOfflineLinuxAmd64Glibc() throws Exception {
        assertThat(System.getProperty("ai.djl.offline")).isEqualTo("true");
        Path tokenizer = directory.resolve("tokenizer.json");
        Files.copy(Path.of(System.getProperty("aigw.tokenizerFile")), tokenizer);
        String expected = System.getProperty("aigw.tokenizerSha256");
        // Gradle worker의 classpath 대신 선언된 integrationTest 런타임 전체를 사용합니다.
        String classpath = System.getProperty("aigw.harness.classpath");
        assertThat(classpath).isNotBlank();
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Dai.djl.offline=true", "-cp", classpath, TokenizerOfflineProbe.class.getName(), tokenizer.toString(), expected);
        builder.environment().put("DJL_CACHE_DIR", directory.resolve("local-cache").toString());
        builder.environment().put("RUST_FLAVOR", "cpu");
        builder.environment().remove("RUST_LIBRARY_PATH");
        builder.redirectErrorStream(true);
        Path localLog = directory.resolve("local.log");
        builder.redirectOutput(localLog.toFile());
        Process process = builder.start();
        try {
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("로컬 native 검사 제한 시간").isTrue();
            assertThat(process.exitValue()).as(Files.readString(localLog)).isZero();
            assertThat(Files.readString(localLog)).contains("P04_TOKENIZER_OFFLINE_OK");
        } finally { if (process.isAlive()) process.destroyForcibly(); }

        List<String> mountedClasspath = new ArrayList<>();
        int index = 0;
        for (String entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path source = Path.of(entry);
            if (!Files.exists(source)) continue;
            Path target = directory.resolve("cp" + index++ + (Files.isDirectory(source) ? "" : ".jar"));
            if (Files.isDirectory(source)) {
                try (var paths = Files.walk(source)) {
                    for (Path path : paths.toList()) {
                        Path copy = target.resolve(source.relativize(path));
                        if (Files.isDirectory(path)) Files.createDirectories(copy);
                        else Files.copy(path, copy);
                    }
                }
            } else Files.copy(source, target);
            mountedClasspath.add("/smoke/" + target.getFileName());
        }
        try (GenericContainer<?> container = new GenericContainer<>("eclipse-temurin:21-jre-jammy")
                .withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"))
                .withFileSystemBind(directory.toString(), "/smoke", BindMode.READ_ONLY)
                .withNetworkMode("none").withEnv("DJL_CACHE_DIR", "/tmp/p04-djl").withEnv("RUST_FLAVOR", "cpu")
                .withCommand("java", "-Dai.djl.offline=true", "-cp", String.join(":", mountedClasspath),
                    TokenizerOfflineProbe.class.getName(), "/smoke/tokenizer.json", expected)
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(60)))
                .waitingFor(Wait.forLogMessage(".*P04_TOKENIZER_OFFLINE_OK.*", 1))) {
            container.start();
            assertThat(container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec().getHostConfig().getNetworkMode()).isEqualTo("none");
            assertThat(container.getLogs()).contains("P04_TOKENIZER_OFFLINE_OK");
            assertThat(container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec().getState().getExitCodeLong()).isZero();
        }
    }
}
