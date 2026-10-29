package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class EvidenceRepositoryFs {
    private final Path root;
    public EvidenceRepositoryFs(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public List<JsonNode> readAll(String stage) {
        Path directory = root.resolve("evidence").resolve(stage);
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.walk(directory)) { return files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().map(Json::read).toList(); }
        catch (IOException e) { throw new EvalException(5, "EVIDENCE_READ_FAILED"); }
    }
    public Path store(JsonNode record) {
        String stage = Json.required(record, "stage");
        String kind = Json.required(record, "kind");
        String group = kind.equals("LEAF") ? Json.required(record, "leafId") : kind.equals("MEASUREMENT") ? "measurements/" + Json.required(record, "measurementId").replace(':', '_') : "runs";
        if (!stage.matches("F[0-4]") || !group.matches("[A-Za-z0-9_/-]+") || group.contains("..")) throw new EvalException(2, "EVIDENCE_PATH_INVALID");
        String hash = Json.required(record, "contentSha256"); if (!hash.matches("[0-9a-f]{64}")) throw new EvalException(2, "EVIDENCE_HASH_MISMATCH");
        Path output = root.resolve("evidence/" + stage + "/" + group + "/" + hash + ".json").normalize();
        if (!output.startsWith(root)) throw new EvalException(2, "EVIDENCE_PATH_INVALID");
        if (Files.exists(output)) { if (!Arrays.equals(Json.bytes(output), Json.canonical(record))) throw new EvalException(5, "EVIDENCE_IMMUTABLE"); return output; }
        Json.write(output, record); return output;
    }
}
