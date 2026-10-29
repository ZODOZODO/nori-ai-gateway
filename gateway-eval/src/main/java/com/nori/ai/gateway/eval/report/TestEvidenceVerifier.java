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
            int split = task.lastIndexOf(':'); String module = task.substring(1, split).replace(':', '/'), name = task.substring(split + 1);
            Path summaryFile = root.resolve(module).resolve("build/aigw-evidence/" + name + "/leaf-results.json");
            
            Path fingerprintFile = root.resolve("build/aigw-fingerprint/" + task.replace(':', '_') + ".json");
            Path bindingFile = root.resolve(module).resolve("build/aigw-otr/" + name + "/run-binding.json");
            if (!Files.isRegularFile(fingerprintFile) || !Files.isRegularFile(bindingFile)) throw new EvalException(4, Files.isRegularFile(summaryFile) ? "STALE_TEST_EVIDENCE" : "NO_TESTS_EXECUTED");
            JsonNode fingerprint = Json.read(fingerprintFile), binding = Json.read(bindingFile);
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
}
