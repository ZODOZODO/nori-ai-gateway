package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.result.*;
import java.util.*;

/** 실제 기여 자료의 서버 의존성만 합칩니다. */
public final class ResultSourceDepsRecorder {
    private final Map<String,Set<String>> sources=new LinkedHashMap<>();
    private final Set<ResultSourceDeps.DocKey> documents=new LinkedHashSet<>();
    public void inherit(ResultSourceDeps deps) {
        for(var source:deps.sources())sources.computeIfAbsent(source.sourceId(),ignored->new LinkedHashSet<>()).addAll(source.permCodes());documents.addAll(deps.knowledgeDocuments());
    }
    public void record(String source,Collection<String> permissions) {inherit(new ResultSourceDeps(List.of(new ResultSourceDeps.Source(source,Set.copyOf(permissions))),List.of()));}
    public ResultSourceDeps snapshot(){return new ResultSourceDeps(sources.entrySet().stream().map(entry->new ResultSourceDeps.Source(entry.getKey(),entry.getValue())).toList(),List.copyOf(documents));}
}
