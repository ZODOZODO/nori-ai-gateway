package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.ci.CiEvidence;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.EvidenceRecord;
import java.nio.file.*;
import java.util.*;

/** CI 증거 시험 공용: 등록 모듈 하나(:sub)의 정제 통합 시험 요약·실행 결속·package manifest. git·docker 는 고정 응답이다. */
final class CiEvidenceFixture {
    static final String SHA = "c".repeat(40), CLASSPATH = "d".repeat(64);
    final TestSupport f;
    final Path root, output, required, identity;
    ObjectNode summary;
    List<ObjectNode> records = new ArrayList<>();
    String status = "";

    CiEvidenceFixture(Path root) throws Exception {
        this.root = root; f = new TestSupport(root);
        Files.createDirectories(root.resolve("gradle"));
        Files.writeString(root.resolve("gradle/libs.versions.toml"), "[versions]\ntestcontainers = \"2.0.3\"\n");
        summary = f.record(Map.ofEntries(Map.entry("kind", "RUN"), Map.entry("status", "COMPLETED"), Map.entry("stage", "F0"), Map.entry("runId", "r"),
            Map.entry("taskPath", ":sub:integrationTest"), Map.entry("module", ":sub"),
            Map.entry("executedTestCount", 1), Map.entry("inputFingerprint", "a".repeat(64)), Map.entry("executedClasses", List.of("probe.RealIT")),
            Map.entry("skippedTestCount", 0), Map.entry("skipped", 0), Map.entry("failed", 0), Map.entry("aborted", 0),
            Map.entry("buildIdentity", Map.of("gitSha", SHA, "runtimeClasspathSha256", CLASSPATH))));
        records.add(f.record(Map.of("kind", "LEAF", "stage", "F1", "leafId", "AC-24-AUDIT-ALLOW", "producer", "JUNIT_OTR", "componentId", "IT", "result", "PASS",
            "runId", "r", "labels", List.of("MOCK_API"), "buildIdentity", Map.of("gitSha", SHA, "runtimeClasspathSha256", CLASSPATH))));
        output = root.resolve("sub/build/aigw-evidence/integrationTest/leaf-results.json");
        save();
        Json.write(root.resolve("sub/build/aigw-otr/integrationTest/run-binding.json"), Map.of("runId", "r", "specSha", f.binding().specSha(), "inputFingerprint", "a".repeat(64)));
        Json.write(root.resolve("build/aigw-fingerprint/_sub_integrationTest.json"), Map.of("inputFingerprint", "a".repeat(64), "projectDir", "sub"));
        required = root.resolve("ci/integration-required.txt");
        Files.createDirectories(required.getParent());
        Files.writeString(required, "# 등록 모듈만 필수\n:sub probe.RealIT\n");
        identity = root.resolve("build/package/build-identity.json");
        identity(SHA, CLASSPATH);
    }
    void save() { EvidenceRecord.seal(summary); records.forEach(EvidenceRecord::seal); Json.write(output, Map.of("summary", summary, "records", records)); }
    void identity(String gitSha, String classpath) { Json.write(identity, Map.of("gitSha", gitSha, "appJarSha256", "e".repeat(64), "runtimeClasspathSha256", classpath)); }
    CiEvidence evidence() {
        return new CiEvidence(root, (directory, command) -> switch (command.get(0) + " " + command.get(1)) {
            case "git rev-parse" -> SHA + "\n";
            case "git status" -> status;
            case "docker version" -> "29.9.2\n";
            default -> throw new IllegalArgumentException();
        });
    }
    Path pack() { return evidence().pack(root.resolve("it-evidence.tgz"), required, f.binding()); }
}
