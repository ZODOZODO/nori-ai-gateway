package com.nori.ai.gateway.core.plan;

import com.nori.ai.gateway.core.registry.CapabilityManifest;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.List;
public final class OwnerBoundary {
    private OwnerBoundary() {}
    public static boolean toolMatches(CapabilityManifest manifest, VerifiedSystemScope scope) {
        return scope != null && manifest != null && manifest.ownerSystem() == scope.boundSystem();
    }
    public static boolean resourcesMatch(List<ResourceResolver.ResourceState> resources, VerifiedSystemScope scope) {
        return scope != null && resources.stream().allMatch(r -> r != null && (!r.exists() && r.ownerSystem() == null || r.ownerSystem() == scope.boundSystem()));
    }
}
