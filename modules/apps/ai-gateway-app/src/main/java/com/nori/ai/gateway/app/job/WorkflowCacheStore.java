package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.cache.ToolResultCache.*;
import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.result.ToolResultEnvelope;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.result.ResultSourceDeps;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.repository.CacheRepository;
import java.util.*;

public final class WorkflowCacheStore implements Store {
    private final CacheRepository cache;private final ContentSanitizer sanitizer;private final CapabilityRegistry registry;
    public WorkflowCacheStore(CacheRepository cache,ContentSanitizer sanitizer,CapabilityRegistry registry){this.cache=cache;this.sanitizer=sanitizer;this.registry=registry;}
    @Override public Optional<CachedValue> find(VerifiedSystemScope scope,String conversationId,String keyDigest,String currentStagePdg) {
        return cache.find(scope,conversationId,keyDigest,currentStagePdg).map(value->new CachedValue(value.capabilityId(),SchemaContracts.parse(value.value()),SchemaContracts.JSON.readValue(value.sourceDeps(),ResultSourceDeps.class),value.expiresAt()));
    }
    @Override public boolean put(VerifiedSystemScope scope,String conversationId,String keyDigest,String currentStagePdg,CachedValue value) {
        // 저장 전에 실행 기록과 같은 typed 마스킹을 적용합니다. find는 저장된 마스킹 값을 그대로 돌려줍니다.
        var manifest=registry.find(value.capabilityId()).orElseThrow(()->new IllegalStateException("캐시 계약을 확인할 수 없습니다"));
        var masked=WorkflowContent.tool(SchemaContracts.JSON.treeToValue(value.value(),ToolResultEnvelope.class),manifest,scope,sanitizer);
        return cache.put(scope,conversationId,keyDigest,currentStagePdg,new CacheRepository.Entry(value.capabilityId(),SchemaContracts.JSON.writeValueAsString(masked),SchemaContracts.JSON.writeValueAsString(value.sourceDeps()),value.expiresAt()));
    }
}
