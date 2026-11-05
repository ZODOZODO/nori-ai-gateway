package com.nori.ai.gateway.app;

import com.nori.ai.gateway.integration.IntegrationModuleConfiguration;
import com.nori.ai.gateway.persistence.PersistenceModuleConfiguration;
import com.nori.ai.gateway.tcmock.TcMockModuleConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ArchitectureRulesTest {
    static JavaClasses mainClasses() throws Exception {
        Set<Path> locations = new HashSet<>();
        for (Class<?> type : List.of(AiGatewayApplication.class, IntegrationModuleConfiguration.class,
                PersistenceModuleConfiguration.class, TcMockModuleConfiguration.class)) {
            locations.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        Path core = Path.of(System.getProperty("aigw.rootDir"), "gateway-core/build/classes/java/main");
        if (Files.isDirectory(core)) locations.add(core);
        Path eval = Path.of(System.getProperty("aigw.rootDir"), "gateway-eval/build/classes/java/main");
        assertThat(eval).isDirectory();
        locations.add(eval);
        return new ClassFileImporter().importPaths(locations);
    }

    @Test void enforcesModuleDirectionsAndScopeConstructorBoundary() throws Exception {
        JavaClasses classes = mainClasses();
        assertThat(classes).isNotEmpty();
        for (var type : classes) {
            String name = type.getName();
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getName();
                boolean invalid = name.startsWith("com.nori.ai.gateway.core.") &&
                    (target.startsWith("org.springframework.") || target.startsWith("java.sql.") || target.startsWith("javax.sql.") ||
                     target.startsWith("java.net.http.") || target.startsWith("com.nori.ai.gateway.") && !target.startsWith("com.nori.ai.gateway.core."));
                invalid |= name.startsWith("com.nori.ai.gateway.persistence.") && target.startsWith("com.nori.ai.gateway.integration.");
                invalid |= name.startsWith("com.nori.ai.gateway.integration.") && target.startsWith("com.nori.ai.gateway.persistence.");
                invalid |= name.startsWith("com.nori.ai.gateway.tcmock.") &&
                    (target.startsWith("com.nori.ai.gateway.persistence.") || target.startsWith("com.nori.ai.gateway.integration.") || target.startsWith("com.nori.ai.gateway.app."));
                invalid |= name.startsWith("com.nori.ai.gateway.eval.") && target.startsWith("com.nori.ai.gateway.") && !target.startsWith("com.nori.ai.gateway.eval.");
                assertThat(invalid).as("모듈 의존 방향: %s -> %s", name, target).isFalse();
            }
            for (var call : type.getConstructorCallsFromSelf()) {
                if (call.getTargetOwner().getName().equals("com.nori.ai.gateway.core.scope.VerifiedSystemScope")) {
                    assertThat(name).isEqualTo("com.nori.ai.gateway.app.security.ScopeAssembler");
                }
            }
            assertThat(type.getAnnotations().stream().map(a -> a.getRawType().getName()))
                .doesNotContain("org.springframework.context.annotation.ComponentScan", "org.springframework.boot.autoconfigure.SpringBootApplication");
        }
    }
}
