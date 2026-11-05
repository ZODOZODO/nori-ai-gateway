package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.OwnedReference;
import java.util.List;
public record Claim(String claimId, Verdict verdict, String text, List<OwnedReference> evidenceRefs) {
    public Claim { evidenceRefs = List.copyOf(evidenceRefs); }
}
