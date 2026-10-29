package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.SpecBinding;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public final class EvidenceIngestor {
    private final EvidenceRepositoryFs repository;
    private final LeafCatalog catalog;
    public EvidenceIngestor(EvidenceRepositoryFs repository, LeafCatalog catalog) { this.repository = repository; this.catalog = catalog; }
    public int ingest(Path input, String source, String stage, String parent, SpecBinding binding) {
        if (!Set.of("junit-otr", "acceptance", "measurement", "user-run", "rubric", "ops", "perf", "f0-report", "spec-record").contains(source)) throw new EvalException(2, "EVIDENCE_SOURCE_INVALID");
        List<JsonNode> records = new ArrayList<>();
        if (source.equals("junit-otr")) return reference(input, stage, parent, binding);
        if (Files.isDirectory(input)) {
            try (var files = Files.walk(input)) { for (Path file : files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().toList()) records.addAll(readJson(file)); }
            catch (IOException e) { throw new EvalException(5, "EVIDENCE_READ_FAILED"); }
        } else if (source.equals("acceptance")) records.addAll(blocks(input, stage, parent, binding));
        else records.addAll(readJson(input));
        for (JsonNode record : records) {
            EvidenceRecord.validate(record, catalog);
            if (!record.path("stage").asText().equals(stage) || !record.path("parentRunId").asText().equals(parent)) throw new EvalException(2, "EVIDENCE_RUN_MISMATCH");
            String expected = Map.of("user-run", "USER_RUN", "rubric", "RUBRIC", "ops", "OPS", "perf", "PERF").get(source);
            if (expected != null && record.path("kind").asText().equals("LEAF") && !record.path("producer").asText().equals(expected)) throw new EvalException(2, "EVIDENCE_PRODUCER_MISMATCH");
            if (source.equals("measurement") && !record.path("kind").asText().equals("MEASUREMENT")) throw new EvalException(2, "MEASUREMENT_INVALID");
        }
        records.forEach(repository::store); return records.size();
    }
    private List<JsonNode> readJson(Path path) {
        JsonNode node = Json.read(path);
        if (node.isArray()) { List<JsonNode> nodes = new ArrayList<>(); node.forEach(nodes::add); return nodes; }
        if (node.has("records")) { List<JsonNode> nodes = new ArrayList<>(); node.path("records").forEach(nodes::add); if (node.has("summary")) nodes.add(node.get("summary")); return nodes; }
        return List.of(node);
    }
    private int reference(Path input, String stage, String parent, SpecBinding binding) {
        JsonNode original = Json.read(input), summary = original.path("summary");
        EvidenceRecord.validate(summary, catalog);
        for (JsonNode record : original.path("records")) EvidenceRecord.validate(record, catalog);
        JsonNode manifest = repository.readAll(stage).stream().filter(n -> n.path("kind").asText().equals("RUN") && n.path("status").asText().equals("PARENT") && n.path("parentRunId").asText().equals(parent)).findFirst().orElseThrow(() -> new EvalException(2, "PARENT_MANIFEST_MISSING"));
        if (!BuildIdentity.matches(manifest.path("buildIdentity"), summary.path("buildIdentity"), "JUNIT_OTR", stage)) throw new EvalException(3, "IDENTITY_MISMATCH");
        ObjectNode reference = EvidenceRecord.create(binding, Map.ofEntries(Map.entry("kind", "RUN"), Map.entry("status", "REFERENCE"), Map.entry("stage", stage),
            Map.entry("parentRunId", parent), Map.entry("runId", summary.path("runId").asText()), Map.entry("producer", "JUNIT_OTR"), Map.entry("taskPath", summary.path("taskPath").asText()),
            Map.entry("sourceFile", input.toAbsolutePath().normalize().toString()), Map.entry("sourceFileSha256", Json.sha(Json.bytes(input))), Map.entry("originalContentSha256", summary.path("contentSha256").asText()),
            Map.entry("otrSha256", summary.path("otrSha256").asText()), Map.entry("buildIdentity", summary.path("buildIdentity"))));
        EvidenceRecord.validate(reference, catalog); repository.store(reference); return 1;
    }
    private List<JsonNode> blocks(Path path, String stage, String parent, SpecBinding binding) {
        byte[] bytes = Json.bytes(path); Path hashFile = path.resolveSibling(path.getFileName() + ".sha256");
        String expected = Files.isRegularFile(hashFile) ? new String(Json.bytes(hashFile), StandardCharsets.UTF_8).strip().split("\\s+")[0] : "";
        if (!expected.equals(Json.sha(bytes))) throw new EvalException(5, "EVIDENCE_HASH_MISMATCH");
        String text = new String(bytes, StandardCharsets.UTF_8); List<JsonNode> records = new ArrayList<>();
        var matcher = Pattern.compile("(?m)^AIGW-EVIDENCE-BEGIN ([0-9a-f]{64})\\r?\\n([A-Za-z0-9+/=\\r\\n]+?)\\r?\\nAIGW-EVIDENCE-END(?:\\r?$)").matcher(text);
        boolean invalid = false;
        while (matcher.find()) {
            byte[] decoded;
            try { decoded = Base64.getMimeDecoder().decode(matcher.group(2)); } catch (IllegalArgumentException e) { invalid = true; break; }
            if (!Json.sha(decoded).equals(matcher.group(1))) { invalid = true; break; }
            JsonNode node = Json.parse(decoded);
            if (node.isArray()) node.forEach(records::add); else records.add(node);
        }
        if (records.isEmpty() || invalid) {
            records.clear(); records.add(EvidenceRecord.create(binding, Map.of("kind", "RUN", "status", "ABORTED", "stage", stage, "parentRunId", parent,
                "runId", UUID.randomUUID().toString(), "reasonCode", "ACCEPTANCE_EVIDENCE_MISSING", "producer", "ACCEPTANCE")));
        }
        return records;
    }
}
