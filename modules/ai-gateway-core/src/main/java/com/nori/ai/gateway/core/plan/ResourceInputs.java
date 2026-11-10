package com.nori.ai.gateway.core.plan;

import com.nori.ai.gateway.core.registry.CapabilityManifest;
import java.util.*;

public final class ResourceInputs {
    private ResourceInputs() {}
    public static List<ResourceRef> bound(PlanStep step, CapabilityManifest tool) {
        Set<ResourceRef> refs = new LinkedHashSet<>(step.resources());
        var args = step.arguments(); var properties = tool.inputSchema().get("properties");
        if (args != null && args.isObject() && properties != null && properties.isObject()) {
            for (var property : properties.properties()) {
                var type = property.getValue().get("x-resourceType"); var value = args.get(property.getKey());
                if (type != null && type.isString() && value != null && value.isString()) refs.add(new ResourceRef(type.asString(), value.asString()));
            }
        }
        return List.copyOf(refs);
    }
}
