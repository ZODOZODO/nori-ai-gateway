package com.nori.ai.gateway.core.plan;

import com.nori.ai.gateway.core.registry.TaskType;
import java.util.*;
public record PlanEnvelope(String requestId, String conversationId, String userRequest, List<ResourceRef> targetResources,
        TaskType taskType, List<PlanStep> plan) {
    public PlanEnvelope {
        targetResources = targetResources == null ? List.of() : List.copyOf(targetResources);
        plan = plan == null ? List.of() : List.copyOf(plan);
    }
}
