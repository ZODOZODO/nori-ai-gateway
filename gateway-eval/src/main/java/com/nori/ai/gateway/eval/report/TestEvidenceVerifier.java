package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.*;
import java.util.*;

public final class TestEvidenceVerifier {
    public void verify(Path root, List<String> tasks, boolean requireDocker, Map<String,List<String>> requiredClasses) {
        if (tasks.isEmpty()) throw new EvalException(4, "NO_TESTS_EXECUTED");
        for (String task : tasks) {
            if (!task.matches("(?::[A-Za-z0-9_.-]+)+")) throw new EvalException(2, "INVALID_TEST_TASK");
            String name = task.substring(task.lastIndexOf(':') + 1);
            Path fingerprintFile = fingerprintFile(root, task);
            if (!Files.isRegularFile(fingerprintFile)) throw new EvalException(4, "STALE_TEST_EVIDENCE");
            JsonNode fingerprint = Json.read(fingerprintFile);
            Path module = projectDir(root, fingerprint), summaryFile = module.resolve("build/aigw-evidence/" + name + "/leaf-results.json");
            Path bindingFile = module.resolve("build/aigw-otr/" + name + "/run-binding.json");
            if (!Files.isRegularFile(bindingFile)) throw new EvalException(4, Files.isRegularFile(summaryFile) ? "STALE_TEST_EVIDENCE" : "NO_TESTS_EXECUTED");
            JsonNode binding = Json.read(bindingFile);
            if (!binding.path("inputFingerprint").equals(fingerprint.path("inputFingerprint"))) throw new EvalException(4, "STALE_TEST_EVIDENCE");
            if (!Files.isRegularFile(summaryFile)) throw new EvalException(4, "NO_TESTS_EXECUTED");
            JsonNode summary = Json.read(summaryFile).path("summary");
            if (summary.path("executedTestCount").asInt() < 1) throw new EvalException(4, "NO_TESTS_EXECUTED");
            if (!summary.path("inputFingerprint").equals(fingerprint.path("inputFingerprint")) || !summary.path("inputFingerprint").equals(binding.path("inputFingerprint")) || !summary.path("runId").equals(binding.path("runId")) || !summary.path("specSha").equals(binding.path("specSha"))) throw new EvalException(4, "STALE_TEST_EVIDENCE");
            if (requireDocker && summary.path("skippedTestCount").asInt() != 0) throw new EvalException(4, "TESTS_SKIPPED");
            Set<String> executed = new HashSet<>(); summary.path("executedClasses").forEach(n -> executed.add(n.asText()));
            for (String required : requiredClasses.getOrDefault(task, List.of())) if (!executed.contains(required)) throw new EvalException(4, "REQUIRED_CLASS_NOT_EXECUTED");
            try {
                LeafCatalog catalog = LeafCatalog.load(null); EvidenceRecord.validate(summary, catalog);
                for (JsonNode record : Json.read(summaryFile).path("records")) EvidenceRecord.validate(record, catalog);
            } catch (EvalException e) { throw new EvalException(4, "STALE_TEST_EVIDENCE"); }
        }
    }
    /** 루트 build 가 verify 직전에 task 마다 쓰는 현재 fingerprint 파일. */
    private static Path fingerprintFile(Path root, String task) { return root.resolve("build/aigw-fingerprint/" + task.replace(':', '_') + ".json"); }

    /** task 의 정제 요약 파일. project 디렉터리는 {@link #projectDir(Path, JsonNode)} 규칙을 따른다. */
    public static Path summaryFile(Path root, String task) {
        Path fingerprintFile = fingerprintFile(root, task);
        if (!Files.isRegularFile(fingerprintFile)) throw new EvalException(4, "STALE_TEST_EVIDENCE");
        return projectDir(root, Json.read(fingerprintFile)).resolve("build/aigw-evidence/" + task.substring(task.lastIndexOf(':') + 1) + "/leaf-results.json");
    }

    // settings 의 projectDir 매핑(modules/…)이 정본이므로 task 경로에서 디렉터리를 유도하지 않는다. 루트 build 가 기록한 루트 기준 상대 경로만 쓰고 루트 밖은 거부한다.
    private static Path projectDir(Path root, JsonNode fingerprint) {
        String relative = fingerprint.path("projectDir").asText("");
        Path base = root.toAbsolutePath().normalize();
        if (relative.isEmpty() || Path.of(relative).isAbsolute()) throw new EvalException(4, "TEST_PROJECT_DIR_INVALID");
        Path directory = base.resolve(relative).normalize();
        if (!directory.startsWith(base)) throw new EvalException(4, "TEST_PROJECT_DIR_INVALID");
        return directory;
    }
}
