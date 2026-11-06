package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.Set;

public final class FailClosedEvidenceSourceReader implements EvidenceSourceReader {
    @Override public Outcome read(VerifiedSystemScope scope, Set<String> perm, EvidenceSourceRef reference) { return new Unavailable(); }
}
