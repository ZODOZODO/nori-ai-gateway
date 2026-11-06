package com.nori.ai.gateway.persistence.queue;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.time.Instant;
import java.util.Set;

public record JobScopeRecord(OwnerSystem ownerSystem, String actorRef, Set<String> requestPerm,
        String requestPdg, String jobId, Instant createdAt, String conversationId, Instant conversationExpiresAt, String status) {
    public JobScopeRecord { requestPerm = Set.copyOf(requestPerm); }
}
