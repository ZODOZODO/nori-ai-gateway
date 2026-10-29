package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class BuildIdentity {
    private BuildIdentity() {}
    public static boolean matches(JsonNode current, JsonNode observed, String producer, String stage) {
        if (!current.isObject() || !observed.isObject()) return false;
        if (!stage.equals("F0") && !producer.equals("JUNIT_OTR")) {
            String app = digest(current, "app"), acceptance = digest(current, "acceptance");
            return validDigest(app) && validDigest(acceptance) && app.equals(digest(observed, "app")) && acceptance.equals(digest(observed, "acceptance"));
        }
        List<String> keys = stage.equals("F0") ? List.of("harnessDigest", "runtimeManifestSha256", "parameterManifestSha256") : List.of("gitSha", "runtimeClasspathSha256");
        if (keys.isEmpty()) return false;
        for (String key : keys) if (!current.hasNonNull(key) || !observed.hasNonNull(key) || !current.get(key).equals(observed.get(key))) return false;
        return true;
    }
    private static String digest(JsonNode identity, String kind) {
        if (identity.has(kind + "ImageDigest")) return identity.path(kind + "ImageDigest").asText();
        if (identity.has(kind)) return identity.path(kind).asText();
        return identity.path("imageDigests").path(kind).asText();
    }
    private static boolean validDigest(String value) { return value.matches("(?:[^\\s]+@)?sha256:[0-9a-f]{64}"); }
}
