package com.nori.ai.gateway.eval.ci;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.*;
import java.util.*;

/**
 * 같은 파이프라인 {@code package} job 이 만든 {@code build/package/build-identity.json} 과 시험 task 요약의 빌드 신원 대조.
 * {@code test-evidence verify --expect-build-identity}(runner 모드)와 {@code ci-evidence verify}(evidence 모드)가 같은 판정을 쓴다.
 */
public final class BuildIdentityManifest {
    private static final Set<String> FIELDS = Set.of("gitSha", "appJarSha256", "runtimeClasspathSha256");
    private final String gitSha;
    private final String runtimeClasspathSha256;

    private BuildIdentityManifest(String gitSha, String runtimeClasspathSha256) { this.gitSha = gitSha; this.runtimeClasspathSha256 = runtimeClasspathSha256; }

    /** 파일 부재·형식 위반은 exit 2 이며 추측값으로 대조하지 않는다. */
    public static BuildIdentityManifest load(Path file) {
        if (file == null || !Files.isRegularFile(file)) throw new EvalException(2, "BUILD_IDENTITY_MANIFEST_MISSING");
        JsonNode node;
        try { node = Json.read(file); } catch (RuntimeException e) { throw new EvalException(2, "BUILD_IDENTITY_MANIFEST_INVALID"); }
        Set<String> names = new HashSet<>(); node.fieldNames().forEachRemaining(names::add);
        if (!node.isObject() || !names.equals(FIELDS) || !node.path("gitSha").isTextual() || !node.path("gitSha").asText().matches("[0-9a-f]{40}")
            || !node.path("appJarSha256").isTextual() || !node.path("appJarSha256").asText().matches("[0-9a-f]{64}")
            || !node.path("runtimeClasspathSha256").isTextual() || !node.path("runtimeClasspathSha256").asText().matches("[0-9a-f]{64}"))
            throw new EvalException(2, "BUILD_IDENTITY_MANIFEST_INVALID");
        return new BuildIdentityManifest(node.path("gitSha").asText(), node.path("runtimeClasspathSha256").asText());
    }

    public String gitSha() { return gitSha; }

    /** 시험 레코드의 {@code buildIdentity} 가 기대 commit·package 의 runtime classpath 와 같지 않으면 exit 4. */
    public void requireTaskIdentity(JsonNode buildIdentity, String commitSha) {
        if (commitSha == null || !commitSha.equals(gitSha) || !buildIdentity.isObject()
            || !commitSha.equals(buildIdentity.path("gitSha").asText(null))
            || !runtimeClasspathSha256.equals(buildIdentity.path("runtimeClasspathSha256").asText(null)))
            throw new EvalException(4, "BUILD_IDENTITY_MISMATCH");
    }

    /**
     * runner 모드: 이 job 에서 실행한 각 필수 task 의 정제 요약과 레코드를 package manifest 와 대조한다.
     * {@code commitSha} 는 CI 의 {@code CI_COMMIT_SHA}(없으면 manifest 값 — 로컬 재현용)이다.
     */
    public void verifyTaskSummaries(Path root, List<String> tasks, String commitSha) {
        String expected = commitSha == null ? gitSha : commitSha;
        for (String task : tasks) {
            int split = task.lastIndexOf(':');
            Path file = root.resolve(task.substring(1, split).replace(':', '/')).resolve("build/aigw-evidence/" + task.substring(split + 1) + "/leaf-results.json");
            if (!Files.isRegularFile(file)) throw new EvalException(4, "NO_TESTS_EXECUTED");
            JsonNode evidence = Json.read(file);
            requireTaskIdentity(evidence.path("summary").path("buildIdentity"), expected);
            for (JsonNode record : evidence.path("records")) requireTaskIdentity(record.path("buildIdentity"), expected);
        }
    }
}
