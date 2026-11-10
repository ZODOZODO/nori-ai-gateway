package com.nori.ai.gateway.core.dispatch;

import com.nori.ai.gateway.core.registry.CapabilityManifest;
import com.nori.ai.gateway.core.plan.ResourceResolver;
import java.util.*;
public record DispatchTarget(CapabilityManifest tool, List<ResourceResolver.ResourceState> resources,
        ResourceResolver.SourceBinding source) {
    public DispatchTarget { resources = Collections.unmodifiableList(new ArrayList<>(resources)); }
}
