package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.authz.SourcePermissionMap;
import com.nori.ai.gateway.core.dispatch.DispatchTarget;
import com.nori.ai.gateway.core.plan.ResourceResolver;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import java.util.*;
import tools.jackson.databind.node.ObjectNode;

public final class SeamRegistry implements PlanInjectionSeam, DispatchInjectionSeam {
    public enum Kind { PLANNED, DISABLED, PHASE_INACTIVE, SIDE_EFFECTS, OWNER_OVERRIDE, RESOURCE_OWNER_OVERRIDE,
        SOURCE_ALLOWLIST_REF_OVERRIDE, PERMISSION_MAP_DROP }
    public record Directive(Kind kind, String capabilityId, String sourceId, OwnerSystem ownerSystem) {
        public Directive { Objects.requireNonNull(kind); }
    }
    private final TestHookRegistry hooks;
    private final Set<String> capabilities;
    private final SourcePermissionMap permissions;
    public SeamRegistry(TestHookRegistry hooks, ManifestLoader loader, SourcePermissionMap permissions) {
        this.hooks = hooks; this.permissions = permissions;
        capabilities = Set.copyOf(loader.loadClasspath("manifests/tc/index.json").stream().map(CapabilityManifest::capabilityId).toList());
    }
    public void validate(Directive directive) {
        if (directive.kind() == Kind.PERMISSION_MAP_DROP) {
            if (directive.capabilityId() != null || directive.ownerSystem() != null || directive.sourceId() == null || permissions.find(directive.sourceId()).isEmpty())
                throw new IllegalArgumentException("주입 대상이 잘못되었습니다");
        } else if (directive.capabilityId() == null || !capabilities.contains(directive.capabilityId()) || directive.sourceId() != null
                || ((directive.kind() == Kind.OWNER_OVERRIDE || directive.kind() == Kind.RESOURCE_OWNER_OVERRIDE
                     || directive.kind() == Kind.SOURCE_ALLOWLIST_REF_OVERRIDE) ? directive.ownerSystem() != OwnerSystem.WM : directive.ownerSystem() != null))
            throw new IllegalArgumentException("주입 대상이 잘못되었습니다");
    }
    private Optional<Directive> directive(VerifiedSystemScope scope) {
        return hooks.binding(scope.requestId()).flatMap(TestHookRegistry.Scenario::seam).map(value -> { validate(value); return value; });
    }
    @Override public CapabilityManifest inspect(CapabilityManifest manifest) { return manifest; }
    @Override public DispatchTarget inspect(DispatchTarget target) { return target; }
    @Override public CapabilityManifest inspect(CapabilityManifest manifest, VerifiedSystemScope scope) {
        var directive = directive(scope).filter(d -> manifest.capabilityId().equals(d.capabilityId()));
        if (directive.isEmpty()) return manifest;
        ObjectNode copy = SchemaContracts.JSON.valueToTree(manifest);
        switch (directive.get().kind()) {
            case PLANNED -> copy.put("availability", "PLANNED");
            case DISABLED -> copy.put("availability", "DISABLED");
            case PHASE_INACTIVE -> copy.put("implementationPhase", "PHASE_2");
            case SIDE_EFFECTS -> copy.put("requiresApproval", true);
            case OWNER_OVERRIDE -> copy.put("ownerSystem", "WM").put("toolId", manifest.toolId().replaceFirst("^tc\\.", "wm."))
                .put("capabilityId", manifest.capabilityId().replaceFirst("^tc\\.", "wm."))
                .put("sourceAllowlistRef", manifest.sourceAllowlistRef().replace("sources://TC/", "sources://WM/"));
            default -> { return manifest; }
        }
        return SchemaContracts.JSON.treeToValue(copy, CapabilityManifest.class);
    }
    @Override public DispatchTarget inspect(DispatchTarget target, VerifiedSystemScope scope) {
        var directive = directive(scope).filter(d -> target.tool().capabilityId().equals(d.capabilityId()));
        if (directive.isEmpty()) return target;
        return switch (directive.get().kind()) {
            case SOURCE_ALLOWLIST_REF_OVERRIDE -> new DispatchTarget(target.tool(), target.resources(), new ResourceResolver.SourceBinding(
                OwnerSystem.WM, target.tool().sourceAllowlistRef().replace("sources://TC/", "sources://WM/"), true));
            case RESOURCE_OWNER_OVERRIDE -> new DispatchTarget(target.tool(), target.resources().stream()
                .map(resource -> resource == null ? null : new ResourceResolver.ResourceState(OwnerSystem.WM, resource.type(), resource.exists())).toList(), target.source());
            case OWNER_OVERRIDE -> new DispatchTarget(inspect(target.tool(), scope), target.resources(), target.source());
            default -> target;
        };
    }
    public boolean permissionMappingExcluded(String sourceId, VerifiedSystemScope scope) {
        return directive(scope).filter(d -> d.kind() == Kind.PERMISSION_MAP_DROP && sourceId.equals(d.sourceId())).isPresent();
    }
}
