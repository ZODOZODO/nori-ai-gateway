package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.*;
import java.time.Instant;
import java.util.*;
public record ToolResultEnvelope(String toolExecutionId, String capabilityId, String contractVersion, String schemaDigest,
        OwnerSystem ownerSystem, ResultStatus status, OwnedReference dataRef, List<OwnedReference> evidenceRefs,
        Instant observedAt, String sourceRevision, List<String> missingFields, List<Warning> warnings,
        ErrorCode errorCode, String traceId, ToolResultMetadata metadata, Clarification clarification,
        List<ResultBlock> blocks, List<Claim> claims, List<Evidence> evidence, List<OwnedReference> citations,
        List<OwnedReference> composerInputs) {
    public ToolResultEnvelope {
        evidenceRefs = List.copyOf(evidenceRefs); missingFields = List.copyOf(missingFields); warnings = List.copyOf(warnings);
        blocks = List.copyOf(blocks); claims = List.copyOf(claims); evidence = List.copyOf(evidence);
        citations = List.copyOf(citations); composerInputs = List.copyOf(composerInputs);
    }
}
