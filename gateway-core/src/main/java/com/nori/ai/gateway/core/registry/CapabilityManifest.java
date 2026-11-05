package com.nori.ai.gateway.core.registry;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.scope.OwnedReference;
import tools.jackson.databind.JsonNode;
import java.util.*;

public record CapabilityManifest(String toolId, String capabilityId, String contractVersion, OwnerSystem ownerSystem,
        ToolKind toolKind, String sourceAllowlistRef, Availability availability, ImplementationPhase implementationPhase,
        String description, List<String> exampleQuestions, RiskLevel riskLevel, String operation, SideEffects sideEffects,
        String resourceScope, Set<String> resourceTypes, List<String> requiredPermission, String requiredActorScope,
        boolean requiresApproval, boolean supportsAsync, boolean supportsCancel, boolean idempotent, RetryPolicy retryPolicy,
        String inputSchemaRef, String outputSchemaRef, JsonNode inputSchema, JsonNode outputSchema, String schemaDigest,
        long timeoutMs, int maxResourcesPerCall, Map<String, InputLimit> inputLimits, String auditPolicy,
        String adapterVersion, String sourceFreshnessPolicy, String outputRefType, int cost) {
    public CapabilityManifest {
        Objects.requireNonNull(ownerSystem, "소유 시스템이 필요합니다");
        Objects.requireNonNull(toolKind); Objects.requireNonNull(availability); Objects.requireNonNull(implementationPhase);
        Objects.requireNonNull(riskLevel); Objects.requireNonNull(sideEffects); Objects.requireNonNull(retryPolicy);
        for (String value : new String[]{toolId, capabilityId, contractVersion, sourceAllowlistRef, description, operation,
                resourceScope, requiredActorScope, inputSchemaRef, outputSchemaRef, schemaDigest, auditPolicy,
                adapterVersion, sourceFreshnessPolicy, outputRefType}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Manifest 필수 필드가 누락되었습니다");
        }
        if (!toolId.equals(capabilityId) || !capabilityId.matches(ownerSystem.name().toLowerCase(Locale.ROOT) + "\\.[a-z][a-zA-Z0-9]*\\.[a-z][a-zA-Z0-9]*"))
            throw new IllegalArgumentException("Capability 식별자가 잘못되었습니다");
        if (!new OwnedReference(ownerSystem, sourceAllowlistRef).belongsTo(ownerSystem) || !sourceAllowlistRef.startsWith("sources://"))
            throw new IllegalArgumentException("Source 소유 범위가 잘못되었습니다");
        if (!schemaDigest.matches("[0-9a-f]{64}") || timeoutMs < 1 || maxResourcesPerCall < 1 || cost < 0)
            throw new IllegalArgumentException("Manifest 한도가 잘못되었습니다");
        resourceTypes = Set.copyOf(resourceTypes); requiredPermission = List.copyOf(requiredPermission);
        exampleQuestions = List.copyOf(exampleQuestions); inputLimits = Map.copyOf(inputLimits);
        if (resourceTypes.isEmpty() || resourceTypes.stream().anyMatch(String::isBlank) || requiredPermission.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Manifest 범위가 잘못되었습니다");
        inputSchema = Objects.requireNonNull(inputSchema).deepCopy(); outputSchema = Objects.requireNonNull(outputSchema).deepCopy();
    }
    @Override public JsonNode inputSchema() { return inputSchema.deepCopy(); }
    @Override public JsonNode outputSchema() { return outputSchema.deepCopy(); }
}
