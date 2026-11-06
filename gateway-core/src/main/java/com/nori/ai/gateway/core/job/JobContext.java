package com.nori.ai.gateway.core.job;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.time.Instant;
import java.util.Map;

public record JobContext(String jobId, String conversationId, VerifiedSystemScope scope, String delegation,
        Instant conversationExpiresAt, CancellationToken cancellation, InferenceMode inferenceMode,
        Map<String,Object> classification) {
    public JobContext { classification = Map.copyOf(classification); }
    @Override public String toString() { return "JobContext[jobId=" + jobId + "]"; }
}
