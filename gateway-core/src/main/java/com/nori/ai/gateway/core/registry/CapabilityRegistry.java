package com.nori.ai.gateway.core.registry;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.*;

public final class CapabilityRegistry {
    private final Map<String, CapabilityManifest> manifests;
    private final List<WorkflowDefinition> workflows;
    private final Set<ImplementationPhase> activePhases;
    public CapabilityRegistry(List<CapabilityManifest> candidates, List<WorkflowDefinition> workflows,
            Set<ImplementationPhase> activePhases, ContractVersionPolicy versions) {
        this.activePhases = Set.copyOf(activePhases); this.workflows = List.copyOf(workflows);
        Map<String, CapabilityManifest> registered = new LinkedHashMap<>();
        Set<String> ids = new HashSet<>();
        for (CapabilityManifest manifest : candidates) {
            if (!ids.add(manifest.capabilityId())) throw new IllegalArgumentException("Capability가 중복되었습니다");
            SchemaContracts.validateSchema(manifest.inputSchema()); SchemaContracts.validateSchema(manifest.outputSchema());
            if (!SchemaContracts.digest(manifest.inputSchema(), manifest.outputSchema()).equals(manifest.schemaDigest()))
                throw new IllegalArgumentException("Schema digest가 일치하지 않습니다");
            if (!versions.supports(manifest.contractVersion())) continue;
            registered.put(manifest.capabilityId(), manifest); versions.register(manifest);
        }
        Set<String> workflowKeys = new HashSet<>();
        for (WorkflowDefinition workflow : workflows) {
            if (!workflowKeys.add(workflow.ownerSystem().name() + "/" + workflow.taskType().name())) throw new IllegalArgumentException("Workflow가 중복되었습니다");
        }
        this.manifests = Collections.unmodifiableMap(registered);
    }
    public Optional<CapabilityManifest> find(String id) { return Optional.ofNullable(manifests.get(id)); }
    public boolean phaseActive(ImplementationPhase phase) { return activePhases.contains(phase); }
    public Optional<WorkflowDefinition> workflow(VerifiedSystemScope scope, TaskType task) {
        return workflows.stream().filter(w -> w.ownerSystem() == scope.boundSystem() && w.taskType() == task).findFirst();
    }
    public RegistryView view(VerifiedSystemScope scope) {
        return new RegistryView(scope.boundSystem(), manifests.values().stream().filter(m -> m.ownerSystem() == scope.boundSystem()
            && m.availability() == Availability.IMPLEMENTED && phaseActive(m.implementationPhase())
            && m.requiredPermission().stream().anyMatch(scope.perm()::contains)
            && m.requiredActorScope().equals("READ")).toList());
    }
}
