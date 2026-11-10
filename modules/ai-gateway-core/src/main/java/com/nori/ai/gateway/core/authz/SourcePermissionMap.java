package com.nori.ai.gateway.core.authz;

import com.nori.ai.gateway.core.registry.SchemaContracts;
import java.util.*;

public final class SourcePermissionMap {
    public record Entry(Set<String> requiredPermCodes, boolean mockOnly) {
        public Entry {
            requiredPermCodes = Set.copyOf(requiredPermCodes);
            if (requiredPermCodes.isEmpty() || requiredPermCodes.stream().anyMatch(p -> !validPermission(p)))
                throw new IllegalArgumentException("Source 권한 매핑이 잘못되었습니다");
        }
    }
    private final Map<String, Entry> entries;
    public SourcePermissionMap(Map<String, Entry> entries) { this.entries = Map.copyOf(entries); }
    public Optional<Entry> find(String sourceId) { return Optional.ofNullable(entries.get(sourceId)); }
    public static boolean validPermission(String value) { return value != null && value.matches("[A-Z0-9_]{1,64}"); }
    public static SourcePermissionMap fromJson(String json) {
        var node = SchemaContracts.parse(json);
        if (!node.isObject()) throw new IllegalArgumentException("Source 권한 매핑이 잘못되었습니다");
        Map<String, Entry> result = new HashMap<>();
        for (var item : node.properties()) {
            var entry = item.getValue();
            if (!entry.isObject() || entry.size() != 2 || !entry.has("requiredPermCodes") || !entry.get("requiredPermCodes").isArray()
                    || !entry.has("mockOnly") || !entry.get("mockOnly").isBoolean())
                throw new IllegalArgumentException("Source 권한 매핑이 잘못되었습니다");
            Set<String> permissions = new HashSet<>();
            for (var perm : entry.get("requiredPermCodes")) {
                if (!perm.isString() || !validPermission(perm.asString()) || !permissions.add(perm.asString()))
                    throw new IllegalArgumentException("Source 권한 매핑이 잘못되었습니다");
            }
            result.put(item.getKey(), new Entry(permissions, entry.get("mockOnly").asBoolean()));
        }
        return new SourcePermissionMap(result);
    }
}
