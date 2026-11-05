package com.nori.ai.gateway.core.plan;

import com.nori.ai.gateway.core.registry.CapabilityManifest;
import com.nori.ai.gateway.core.scope.*;
import java.time.ZoneId;
import java.util.Set;

/** 실제 Backend 조회·인가는 어댑터가 수행하고 현재 요청에 결속된 결과만 전달합니다. */
public interface ResourceResolver {
    ResourceState resolve(ResourceRef resource, VerifiedSystemScope scope);
    SourceBinding source(CapabilityManifest manifest, VerifiedSystemScope scope);
    CurrentAuthorization authorize(CapabilityManifest manifest, PlanStep step, VerifiedSystemScope scope);
    ZoneId timezone(VerifiedSystemScope scope);
    record ResourceState(OwnerSystem ownerSystem, String type, boolean exists) {}
    record SourceBinding(OwnerSystem ownerSystem, String sourceAllowlistRef, boolean allowed) {}
    record CurrentAuthorization(OwnerSystem ownerSystem, String sourceAllowlistRef, ActorRef actorRef,
            String permissionDigest, boolean sessionValid, Set<String> permissions, Set<String> actorScopes) {
        public CurrentAuthorization { permissions = Set.copyOf(permissions); actorScopes = Set.copyOf(actorScopes); }
    }
}
