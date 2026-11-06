package com.nori.ai.gateway.core.result;

import java.util.*;
import com.nori.ai.gateway.core.result.ResultSourceDeps.DocKey;

/** P23의 정본 상태 조회가 합류하기 전에는 문서 이용 가능성을 추정하지 않습니다. */
public final class FailClosedKnowledgeDocumentStateReader implements KnowledgeDocumentStateReader {
    @Override public Map<DocKey,State> states(Set<String> perm, Set<DocKey> documents) {
        var result=new HashMap<DocKey,State>(); documents.forEach(key -> result.put(key,State.UNKNOWN)); return Map.copyOf(result);
    }
}
