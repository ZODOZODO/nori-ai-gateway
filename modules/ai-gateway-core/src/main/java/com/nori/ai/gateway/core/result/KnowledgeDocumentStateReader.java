package com.nori.ai.gateway.core.result;

import java.util.*;
import com.nori.ai.gateway.core.result.ResultSourceDeps.DocKey;

public interface KnowledgeDocumentStateReader {
    enum State { AVAILABLE, UNAVAILABLE, UNKNOWN }
    Map<DocKey,State> states(Set<String> perm, Set<DocKey> documents);
}
