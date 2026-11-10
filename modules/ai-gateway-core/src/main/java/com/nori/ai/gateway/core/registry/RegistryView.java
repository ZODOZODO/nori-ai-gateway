package com.nori.ai.gateway.core.registry;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.util.*;
public record RegistryView(OwnerSystem ownerSystem, List<CapabilityManifest> capabilities) {
    public RegistryView { Objects.requireNonNull(ownerSystem); capabilities = List.copyOf(capabilities); }
    public Optional<CapabilityManifest> find(String id) { return capabilities.stream().filter(m -> m.capabilityId().equals(id)).findFirst(); }
}
