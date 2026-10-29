package com.nori.ai.gateway.eval.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public record SpecBaseline(List<JsonNode> entries) {
    public static SpecBaseline parseChain(Path path) {
        String text = new String(Json.bytes(path), StandardCharsets.UTF_8);
        int start = text.indexOf("```json\n");
        if (start < 0) start = text.indexOf("```json\r\n");
        int end = start < 0 ? -1 : text.indexOf("```", start + 7);
        if (start < 0 || end < 0) throw new EvalException(2, "SPEC_BASELINE_INVALID");
        JsonNode nodes = Json.parse(text.substring(text.indexOf('\n', start) + 1, end).getBytes(StandardCharsets.UTF_8));
        if (!nodes.isArray() || nodes.isEmpty()) throw new EvalException(2, "SPEC_BASELINE_INVALID");
        List<JsonNode> entries = new ArrayList<>(); String previous = null; int seq = 1;
        for (JsonNode n : nodes) {
            String hash = n.path("sha256").asText();
            if (!n.path("seq").isIntegralNumber() || n.path("seq").asInt() != seq++ || !hash.matches("[0-9a-f]{64}") || !Objects.equals(previous, n.path("prevSha256").isNull() ? null : n.path("prevSha256").asText()) || !n.path("measurementAffecting").isBoolean() || !(n.path("affectedLeaves").isArray() || n.path("affectedLeaves").asText().equals("ALL")) || !n.path("changedSections").isArray() || n.path("approvedAt").asText().isBlank() || n.path("approvalRef").asText().isBlank()) throw new EvalException(2, "SPEC_BASELINE_INVALID");
            for (JsonNode leaf : n.path("affectedLeaves")) if (!leaf.isTextual() || !leaf.asText().matches("(?:AC|ISO)-[0-9]{2}(?:-[A-Z0-9]+)*")) throw new EvalException(2, "SPEC_BASELINE_INVALID");
            for (JsonNode section : n.path("changedSections")) if (!section.isTextual() || section.asText().isBlank()) throw new EvalException(2, "SPEC_BASELINE_INVALID");
            if (entries.stream().anyMatch(e -> e.path("sha256").asText().equals(hash))) throw new EvalException(2, "SPEC_BASELINE_INVALID");
            entries.add(n.deepCopy()); previous = hash;
        }
        return new SpecBaseline(List.copyOf(entries));
    }
    public String currentSha() { return entries.getLast().get("sha256").asText(); }
    public int seq() { return entries.size(); }
    public boolean eligible(String hash, String leaf) {
        int index = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).get("sha256").asText().equals(hash)) index = i;
        if (index < 0) return false;
        for (int i = index + 1; i < entries.size(); i++) {
            JsonNode affected = entries.get(i).get("affectedLeaves");
            if (affected.asText().equals("ALL")) return false;
            for (JsonNode id : affected) if (id.asText().equals(leaf)) return false;
        }
        return true;
    }
}
