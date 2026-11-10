package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.OwnedReference;
import java.time.Instant;
import java.util.List;
public record ToolResultMetadata(OwnedReference sourceRef, String sourceFreshnessPolicy, boolean truncated, List<MissingRange> missingRanges) {
    public record MissingRange(Instant from, Instant to) {}
    public ToolResultMetadata { missingRanges = List.copyOf(missingRanges); }
}
