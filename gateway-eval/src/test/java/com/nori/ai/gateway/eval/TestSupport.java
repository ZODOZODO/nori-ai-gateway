package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.*;
import com.nori.ai.gateway.eval.spec.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

final class TestSupport {
    final Path root, spec, baseline;
    final LeafCatalog catalog = LeafCatalog.load(null);
    TestSupport(Path root) throws IOException { this(root, "승인 fixture SPEC\n".getBytes(StandardCharsets.UTF_8)); }
    TestSupport(Path root, byte[] bytes) throws IOException {
        this.root = root; Files.createDirectories(root); spec = root.resolve("spec.md"); Files.write(spec, bytes);
        baseline = root.resolve("docs/gates/spec-baseline.md"); baseline(bytes);
    }
    void baseline(byte[] bytes) throws IOException { baselineChain(List.of(entry(1, Json.sha(bytes), null, "ALL", true))); }
    Map<String,Object> entry(int seq, String sha, String previous, Object leaves, boolean affecting) {
        Map<String,Object> entry = new LinkedHashMap<>(); entry.put("seq", seq); entry.put("sha256", sha); entry.put("prevSha256", previous); entry.put("affectedLeaves", leaves); entry.put("measurementAffecting", affecting);
        entry.put("approvedAt", "2026-10-29"); entry.put("approvalRef", "fixture-user-approval"); entry.put("changedSections", List.of("fixture")); return entry;
    }
    void baselineChain(List<Map<String,Object>> entries) throws IOException {
        Files.createDirectories(baseline.getParent()); Files.writeString(baseline, "# 기준선\n\n```json\n" + Json.canonicalString(Json.MAPPER.valueToTree(entries)) + "\n```\n", StandardCharsets.UTF_8);
    }
    static Map<String,Object> receipt(Path file, String sha) { byte[] bytes=Json.bytes(file), compressed=SpecInputPackage.compress(bytes); return Map.of("package","aigw-spec-input","version",sha,"sha256",sha,"apiHost","gitlab.noricloud.org","compressedSha256",Json.sha(compressed),"compressedBytes",compressed.length,"expandedBytes",bytes.length); }
    SpecBinding binding() { return new SpecGuard(Map.of()).check(spec.toString(), null, baseline); }
    ObjectNode record(Map<String,?> input) { return EvidenceRecord.create(binding(), input); }
    ObjectNode parent(String stage, String id) {
        return record(Map.of("kind", "RUN", "status", "PARENT", "stage", stage, "runId", id, "parentRunId", id, "recordedAt", "2026-10-29T00:00:00Z", "buildIdentity", identity(stage)));
    }
    Map<String,Object> identity(String stage) {
        if (stage.equals("F0")) return Map.of("harnessDigest", "a".repeat(64), "runtimeManifestSha256", "b".repeat(64), "parameterManifestSha256", "c".repeat(64));
        return Map.of("appImageDigest", "sha256:" + "a".repeat(64), "acceptanceImageDigest", "sha256:" + "b".repeat(64), "gitSha", "c".repeat(40), "runtimeClasspathSha256", "d".repeat(64));
    }
    ObjectNode run(String stage, String parent, String id, String suite, String status, String time) {
        return record(Map.of("kind", "RUN", "status", status, "stage", stage, "runId", id, "parentRunId", parent, "suite", suite, "recordedAt", time, "buildIdentity", identity(stage)));
    }
    ObjectNode leaf(String stage, String parent, String run, String id, int componentIndex, String verdict, String time) {
        JsonNode component = catalog.components(stage, id).get(componentIndex);
        Map<String,Object> fields = new LinkedHashMap<>(); fields.put("kind", "LEAF"); fields.put("stage", stage); fields.put("parentRunId", parent); fields.put("runId", run); fields.put("leafId", id);
        fields.put("producer", component.path("producer").asText()); fields.put("componentId", component.path("componentId").asText()); fields.put("result", verdict); fields.put("recordedAt", time); fields.put("buildIdentity", identity(stage));
        fields.put("labels", catalog.leaf(id).path("labelsByStage").path(stage));
        if (component.has("suite")) fields.put("suite", component.get("suite").asText()); if (component.has("outagePhase")) fields.put("outagePhase", component.get("outagePhase").asText());
        return record(fields);
    }
    int cli(String... args) { return new EvalCli(Map.of("AIGW_SPEC_FILE", spec.toString()), new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), root).execute(args); }
    static void error(Runnable action, int exit, String code) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run).isInstanceOfSatisfying(EvalException.class, e -> { org.assertj.core.api.Assertions.assertThat(e.exitCode()).isEqualTo(exit); org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo(code); });
    }
}
