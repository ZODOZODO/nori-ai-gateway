package com.nori.ai.gateway.app;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.zip.ZipFile;
import com.nori.ai.gateway.tcmock.TcMockModuleConfiguration;
import static org.assertj.core.api.Assertions.*;

class RuntimeClasspathCleanTest {
    @Test void generatedProductRuntimeExcludesTestInfrastructure() throws Exception {
        String input = System.getProperty("aigw.runtimeClasspathFile");
        assertThat(input).isNotBlank();
        Path file = Path.of(input);
        assertThat(file).isRegularFile();
        assertThat(file.toString().replace('\\', '/')).endsWith("modules/apps/ai-gateway-app/build/aigw/runtime-classpath.txt");
        String runtime = Files.readString(file);
        assertThat(runtime).isNotBlank().contains("org.springframework.boot:spring-boot:", "ai.djl.huggingface:tokenizers:",
            "native/lib/linux-x86_64/cpu/libtokenizers.so", "native/lib/win-x86_64/cpu/tokenizers.dll",
            "io.micrometer:micrometer-registry-otlp:1.16.6 ", "io.opentelemetry.proto:opentelemetry-proto:1.8.0-alpha ");
        assertThat(runtime).doesNotContain("junit-platform-reporting", "junit-platform-launcher", ":gateway-eval:", "test-fixtures", "testFixtures", "org.testcontainers:");
        assertThat(runtime).doesNotContain("\r");
        // migration 은 앱 밖(루트 Gradle Flyway)이다. 앱 런타임에는 Spring Flyway 자동 설정·DB 방언 모듈이 없고 guard 용 flyway-core 만 남는다.
        assertThat(runtime).doesNotContain("org.springframework.boot:spring-boot-flyway:", "org.springframework.boot:spring-boot-starter-flyway:",
            "org.flywaydb:flyway-database-postgresql:").contains("org.flywaydb:flyway-core:");
        assertThat(runtime.lines()).allMatch(line -> line.matches("\\S+ sha256=[0-9a-f]{64} nativeResources=.*"));
        Path mockLocation = Path.of(TcMockModuleConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        // Mock의 운영 구성과 시험 보조 패키지가 같은 모듈 이름에 가려지지 않도록 실제 산출물도 검사합니다.
        if (Files.isDirectory(mockLocation)) {
            try (var entries = Files.walk(mockLocation)) {
                assertThat(entries.map(mockLocation::relativize).map(Path::toString).map(name -> name.replace('\\', '/')))
                    .noneMatch(this::testOnlyMockPackage);
            }
        } else {
            try (ZipFile jar = new ZipFile(mockLocation.toFile())) {
                assertThat(jar.stream().map(entry -> entry.getName())).noneMatch(this::testOnlyMockPackage);
            }
        }
    }
    private boolean testOnlyMockPackage(String name) {
        return name.startsWith("com/nori/ai/gateway/tcmock/testing/") || name.startsWith("com/nori/ai/gateway/tcmock/verify/");
    }
}
