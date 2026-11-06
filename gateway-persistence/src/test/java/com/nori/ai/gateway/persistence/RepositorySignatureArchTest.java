package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("stage-F1") @Tag("label-MOCK_API")
class RepositorySignatureArchTest {
    @Test void nineRepositoriesRequireScopeAndMaintenanceCallersAreRestricted() throws Exception {
        var root = Path.of(System.getProperty("aigw.rootDir"));
        var classes = new ClassFileImporter().importPath(root.resolve("gateway-persistence/build/classes/java/main"));
        var allRepositories = classes.stream().filter(type -> type.getSimpleName().endsWith("Repository")).toList();
        var expected = new HashSet<String>();
        for (String name : List.of("Conversation", "Message", "Job", "JobEvent", "Result", "Evidence", "ToolExecution", "Cache", "Audit"))
            expected.add("com.nori.ai.gateway.persistence.repository." + name + "Repository");
        for (String name : List.of("Queue", "Retention", "Recovery"))
            expected.add("com.nori.ai.gateway.persistence.maintenance." + name + "MaintenanceRepository");
        expected.add("com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository");
        assertThat(allRepositories.stream().map(type -> type.getName())).containsExactlyInAnyOrderElementsOf(expected);
        var repositories = allRepositories.stream().filter(type -> type.getPackageName().endsWith(".repository")).toList();
        assertThat(repositories).hasSize(9);
        for (var type : repositories) for (var method : type.reflect().getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
                assertThat(method.getParameterCount()).as(method.toString()).isGreaterThan(0);
                assertThat(method.getParameterTypes()[0]).as(method.toString()).isEqualTo(VerifiedSystemScope.class);
            }
        }
        // 실제 제품 클래스 전체에서 유지보수 저장소의 실행 호출만 검사합니다. 빈 등록 의존은 호출이 아닙니다.
        var paths = new ArrayList<Path>();
        for (String module : List.of("gateway-persistence", "gateway-app", "gateway-core", "gateway-integration", "gateway-tc-mock")) {
            var path = root.resolve(module + "/build/classes/java/main"); if (Files.isDirectory(path)) paths.add(path);
        }
        for (var type : new ClassFileImporter().importPaths(paths)) for (var call : type.getMethodCallsFromSelf()) {
            String target = call.getTargetOwner().getName();
            if (!target.startsWith("com.nori.ai.gateway.persistence.maintenance.")) continue;
            String caller = type.getName();
            if (caller.equals(target)) continue;
            boolean allowed = target.endsWith("QueueMaintenanceRepository")
                ? caller.equals("com.nori.ai.gateway.app.job.JobDispatcher")
                : target.endsWith("RetentionMaintenanceRepository")
                    ? caller.equals("com.nori.ai.gateway.app.ops.RetentionPurgeJob")
                    : caller.startsWith("com.nori.ai.gateway.app.job.recovery.");
            assertThat(allowed).as("유지보수 호출자: %s -> %s", caller, target).isTrue();
        }
    }

    @Test void rejectionAppendHasOnlyTypedAppendAndClosedFields() throws Exception {
        var type = com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository.class;
        assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
        var methods = Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers())).toList();
        assertThat(methods).hasSize(1);
        assertThat(methods.getFirst().getName()).isEqualTo("append");
        assertThat(methods.getFirst().getParameterTypes()).containsExactly(com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppend.class);
        assertThat(Arrays.stream(com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppend.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
            .containsExactly("requestId", "ownerSystem", "actorRef", "reason");
        var source = Files.readString(Path.of(System.getProperty("aigw.rootDir"), "gateway-persistence/src/main/java/com/nori/ai/gateway/persistence/audit/auth/AuthRejectionAppendRepository.java"));
        assertThat(source.split("INSERT INTO", -1)).hasSize(2);
        assertThat(source).doesNotContain("SELECT ", "DELETE ", "UPDATE ", "catch (", "Logger", "new VerifiedSystemScope");
    }

    @Test void jobQueriesNeverSelectWildcardOrReadDelegationOutsideDedicatedReader() throws Exception {
        Path source = Path.of(System.getProperty("aigw.rootDir"), "gateway-persistence/src/main/java");
        try (var files = Files.walk(source)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file);
                assertThat(content).as(file.toString()).doesNotContainPattern("(?is)SELECT\\s+(?:[a-z]+\\.)?\\*");
                if (content.contains("delegation_id") && !file.getFileName().toString().equals("JobDelegationReader.java")
                        && !file.getFileName().toString().equals("JobTerminalCommitter.java")) {
                    assertThat(file.getFileName().toString()).isEqualTo("JobRepository.java");
                    assertThat(content).contains("INSERT INTO gw.job");
                    assertThat(content).doesNotContainPattern("(?is)SELECT[^;]*delegation_id[^;]*FROM\\s+gw\\.job");
                    assertThat(content).doesNotContainPattern("(?is)UPDATE[^;]*delegation_id");
                }
            }
        }
        assertThat(Arrays.stream(com.nori.ai.gateway.persistence.repository.JobRepository.Job.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
            .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("delegation"));
    }
}
