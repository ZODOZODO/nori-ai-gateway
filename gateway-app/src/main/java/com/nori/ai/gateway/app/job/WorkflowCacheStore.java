package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.cache.ToolResultCache.*;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.result.ResultSourceDeps;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.repository.CacheRepository;
import java.util.*;

public final class WorkflowCacheStore implements Store {
    private final CacheRepository cache;
    public WorkflowCacheStore(CacheRepository cache){this.cache=cache;}
    @Override public Optional<CachedValue> find(VerifiedSystemScope scope,String conversationId,String keyDigest,String currentStagePdg) {
        return cache.find(scope,conversationId,keyDigest,currentStagePdg).map(value->new CachedValue(value.capabilityId(),SchemaContracts.parse(value.value()),SchemaContracts.JSON.readValue(value.sourceDeps(),ResultSourceDeps.class),value.expiresAt()));
    }
    @Override public boolean put(VerifiedSystemScope scope,String conversationId,String keyDigest,String currentStagePdg,CachedValue value) {
        return cache.put(scope,conversationId,keyDigest,currentStagePdg,new CacheRepository.Entry(value.capabilityId(),SchemaContracts.JSON.writeValueAsString(value.value()),SchemaContracts.JSON.writeValueAsString(value.sourceDeps()),value.expiresAt()));
    }
}
