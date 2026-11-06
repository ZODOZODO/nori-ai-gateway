package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.util.Objects;

public record EvidenceSourceRef(OwnerSystem ownerSystem, String sourceRef, String chunkId, String expectedRevision) {
    public EvidenceSourceRef { Objects.requireNonNull(ownerSystem); Objects.requireNonNull(sourceRef); Objects.requireNonNull(chunkId); Objects.requireNonNull(expectedRevision); }
}
