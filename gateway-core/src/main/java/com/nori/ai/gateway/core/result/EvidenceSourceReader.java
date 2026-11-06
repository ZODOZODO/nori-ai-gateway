package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.Set;

public interface EvidenceSourceReader {
    sealed interface Outcome permits Found, Unavailable {}
    record Found(String maskedText) implements Outcome {}
    record Unavailable() implements Outcome {}
    Outcome read(VerifiedSystemScope scope, Set<String> perm, EvidenceSourceRef reference);
}
