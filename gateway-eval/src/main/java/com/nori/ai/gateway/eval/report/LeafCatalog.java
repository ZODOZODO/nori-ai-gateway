package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class LeafCatalog {
    private final JsonNode data;
    private final Map<String,JsonNode> leaves = new LinkedHashMap<>();
    public LeafCatalog(JsonNode data) {
        this.data = data;
        for (String group : List.of("leaves", "atoms")) for (JsonNode n : data.path(group)) {
            String id = Json.required(n, "id");
            if (!id.matches("(?:AC|ISO)-[0-9]{2}(?:-[A-Z0-9]+)*") || leaves.put(id, n) != null) throw new EvalException(2, "CATALOG_INVALID");
        }
    }
    public static LeafCatalog load(Path path) {
        if (path != null) return new LeafCatalog(Json.read(path));
        try (var input = LeafCatalog.class.getResourceAsStream("/com/nori/ai/gateway/eval/leaf-catalog-v2.json")) {
            if (input == null) throw new EvalException(2, "CATALOG_MISSING");
            return new LeafCatalog(Json.parse(input.readAllBytes()));
        } catch (IOException e) { throw new EvalException(2, "CATALOG_MISSING"); }
    }
    public JsonNode data() { return data; }
    public boolean contains(String id) { return leaves.containsKey(id); }
    public JsonNode leaf(String id) { JsonNode leaf = leaves.get(id); if (leaf == null) throw new EvalException(4, "UNKNOWN_LEAF_TAG"); return leaf; }
    public Collection<JsonNode> all() { return leaves.values(); }
    public List<JsonNode> required(String stage) { return leaves.values().stream().filter(n -> n.path("components").has(stage)).toList(); }
    public List<JsonNode> components(String stage, String leaf) { List<JsonNode> result = new ArrayList<>(); leaf(leaf).path("components").path(stage).forEach(result::add); return result; }
    public boolean measurement(String id) { for (JsonNode n : data.path("measurementIds")) if (n.asText().equals(id)) return true; return false; }
}
