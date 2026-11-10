package com.nori.ai.gateway.core.port;

import java.time.Instant;
import java.util.*;

public interface SourceRetentionReader {
    Optional<VerifiedOldest> find(String sourceId);
    record VerifiedOldest(Instant verifiedOldestAt, Instant probedAt) {
        public VerifiedOldest { Objects.requireNonNull(verifiedOldestAt); Objects.requireNonNull(probedAt); }
    }
}
