package com.nori.ai.gateway.core.seam;

import com.nori.ai.gateway.core.registry.CapabilityManifest;
@FunctionalInterface
public interface PlanInjectionSeam {
    CapabilityManifest inspect(CapabilityManifest manifest);
    final class NoOp implements PlanInjectionSeam {
        @Override public CapabilityManifest inspect(CapabilityManifest manifest) { return manifest; }
    }
}
