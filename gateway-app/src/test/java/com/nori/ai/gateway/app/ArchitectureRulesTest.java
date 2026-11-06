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
        // 참조된 타입의 자동 해석에 기대지 않고 모든 제품 main 산출물을 직접 수집합니다.
        for (String module : List.of("gateway-app","gateway-persistence","gateway-core","gateway-integration","gateway-tc-mock","gateway-eval")) {
            Path output = Path.of(System.getProperty("aigw.rootDir"),module,"build/classes/java/main");
            assertThat(output).isDirectory(); locations.add(output);
        }
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

    @Test void restrictsRejectionAppendAndEveryVerifiedIdentityConstructorToTheirOwners() throws Exception {
        var expectedConstructors = Map.of(
            "com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository", "com.nori.ai.gateway.persistence.audit.auth.AuthRejectionPersistenceConfiguration",
            "com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppend", "com.nori.ai.gateway.app.security.AuthRejectionAuditor",
            "com.nori.ai.gateway.app.security.VerifiedServiceIdentity", "com.nori.ai.gateway.app.security.SecurityCredentialFiles",
            "com.nori.ai.gateway.app.security.ActorAssertionVerifier$VerifiedAssertion", "com.nori.ai.gateway.app.security.ActorAssertionVerifier");
        var constructorCallers = new HashMap<String,Set<String>>();
        var appendCallers = new HashSet<String>();
        var auditorCallers = new HashSet<String>();
        for (var type : mainClasses()) {
            assertThat(type.getName()).doesNotContain("StubAssertionSigner", "tcmock.testing", "tcmock.verify");
            for (var call : type.getConstructorCallsFromSelf()) {
                String target = call.getTargetOwner().getName();
                if (expectedConstructors.containsKey(target)) {
                    assertThat(type.getName()).as("신원 생성자 호출 경계").isEqualTo(expectedConstructors.get(target));
                    constructorCallers.computeIfAbsent(target,key -> new HashSet<>()).add(type.getName());
                }
            }
            for (var call : type.getMethodCallsFromSelf()) {
                if (call.getTargetOwner().getName().equals("com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository") && call.getName().equals("append"))
                    appendCallers.add(type.getName());
                if (call.getTargetOwner().getName().equals("com.nori.ai.gateway.app.security.AuthRejectionAuditor") && Set.of("unauthenticated","serviceVerified","actorVerified").contains(call.getName()))
                    auditorCallers.add(type.getName());
            }
        }
        assertThat(constructorCallers.keySet()).containsExactlyInAnyOrderElementsOf(expectedConstructors.keySet());
        assertThat(appendCallers).containsExactly("com.nori.ai.gateway.app.security.AuthRejectionAuditor");
        assertThat(auditorCallers).containsExactly("com.nori.ai.gateway.app.security.ServiceTokenAuthFilter");
    }
}
