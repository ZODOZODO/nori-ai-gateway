package com.nori.ai.gateway.eval.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.*;
import java.util.Map;

public final class SpecInputResolver {
    private final Map<String,String> env;
    public SpecInputResolver(Map<String,String> env) { this.env = Map.copyOf(env); }
    public Path resolve(String explicit, String property) {
        String value = explicit != null ? explicit : property != null ? property : env.get("AIGW_SPEC_FILE");
        try {
            if (value == null || value.isBlank()) throw new EvalException(2, "SPEC_INPUT_MISSING");
            Path path = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new EvalException(2, "SPEC_INPUT_MISSING");
            return path;
        } catch (InvalidPathException e) { throw new EvalException(2, "SPEC_INPUT_MISSING"); }
    }
    public String origin(Path path, String actualSha) {
        boolean ci = "true".equalsIgnoreCase(env.get("CI"));
        Path receipt = path.resolveSibling("fetch-receipt.json");
        if (Files.isRegularFile(receipt)) {
            JsonNode node = Json.read(receipt);
            if (!node.path("compressedSha256").asText().matches("[0-9a-f]{64}") || !node.path("compressedBytes").isIntegralNumber() || node.path("compressedBytes").asLong() < 1 || node.path("compressedBytes").asLong() > SpecInputPackage.MAX_COMPRESSED || !node.path("expandedBytes").isIntegralNumber() || node.path("expandedBytes").asLong() != Json.bytes(path).length || node.path("expandedBytes").asLong() > SpecInputPackage.MAX_EXPANDED) throw new EvalException(2, "SPEC_INPUT_UNVERIFIED_SOURCE");
            if (!node.path("sha256").asText().equals(actualSha) || !node.path("version").asText().equals(actualSha) || !node.path("package").asText().equals("aigw-spec-input") || !node.path("apiHost").asText().equals("gitlab.noricloud.org") || !actualSha.equals(env.getOrDefault("AIGW_APPROVED_SPEC_SHA", actualSha))) throw new EvalException(2, "SPEC_INPUT_UNVERIFIED_SOURCE");
            return ci ? "CI_VERIFIED_PACKAGE" : "OPERATOR_VERIFIED_PACKAGE";
        }
        if (ci) throw new EvalException(2, "SPEC_INPUT_UNVERIFIED_SOURCE");
        return "LOCAL_FILE";
    }
}
