package com.nori.ai.gateway.core.result;

import java.util.*;
import com.nori.ai.gateway.core.result.ResultSourceDeps.DocKey;
import com.nori.ai.gateway.core.result.KnowledgeDocumentStateReader.State;

public final class ResultAccessEvaluator {
    public enum Decision { ALLOW, DENY }
    public Decision evaluate(Set<String> perm, ResultSourceDeps deps, Map<DocKey,State> states) {
        if (deps.sources().stream().anyMatch(source -> Collections.disjoint(perm,source.permCodes()))) return Decision.DENY;
        return deps.knowledgeDocuments().stream().allMatch(key -> states.get(key)==State.AVAILABLE) ? Decision.ALLOW : Decision.DENY;
    }
}
