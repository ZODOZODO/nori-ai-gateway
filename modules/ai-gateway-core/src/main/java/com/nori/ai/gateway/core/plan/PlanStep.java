package com.nori.ai.gateway.core.plan;

import tools.jackson.databind.JsonNode;
import java.util.*;
public record PlanStep(String stepId, String capabilityId, List<String> dependsOn, List<ResourceRef> resources, JsonNode arguments) {
    public PlanStep {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        resources = resources == null ? List.of() : List.copyOf(resources);
        arguments = arguments == null ? null : arguments.deepCopy();
    }
    @Override public JsonNode arguments() { return arguments == null ? null : arguments.deepCopy(); }
}
