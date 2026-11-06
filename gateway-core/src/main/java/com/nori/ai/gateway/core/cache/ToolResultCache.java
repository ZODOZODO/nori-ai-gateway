package com.nori.ai.gateway.core.cache;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.authz.SourceAuthorizationStep.StepGrant;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.job.JobContext;
import com.nori.ai.gateway.core.plan.PlanValidator;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ToolResultCache {
    public interface Store {
        Optional<CachedValue> find(VerifiedSystemScope scope,String conversationId,String keyDigest,String currentStagePdg);
        boolean put(VerifiedSystemScope scope,String conversationId,String keyDigest,String currentStagePdg,CachedValue value);
    }
    public record CachedValue(String capabilityId,JsonNode value,ResultSourceDeps sourceDeps,Instant expiresAt) {
        public CachedValue {Objects.requireNonNull(capabilityId);Objects.requireNonNull(value);Objects.requireNonNull(sourceDeps);Objects.requireNonNull(expiresAt);value=value.deepCopy();}
        @Override public JsonNode value(){return value.deepCopy();}
        @Override public String toString(){return "CachedValue[보호됨]";}
    }
    public record CacheInputs(Optional<String> modelRevision,Optional<String> sourceRevision,String sourceFreshnessPolicy) {
        public CacheInputs {Objects.requireNonNull(modelRevision);Objects.requireNonNull(sourceRevision);Objects.requireNonNull(sourceFreshnessPolicy);}
    }
    private final Store store;private final PlanValidator validator;private final ResultAccessEvaluator access;
    private final KnowledgeDocumentStateReader documents;private final ExpiryCheckpoint expiry;private final Clock clock;
    public ToolResultCache(Store store,PlanValidator validator,ResultAccessEvaluator access,KnowledgeDocumentStateReader documents,ExpiryCheckpoint expiry,Clock clock) {
        this.store=store;this.validator=validator;this.access=access;this.documents=documents;this.expiry=expiry;this.clock=clock;
    }
    public Optional<CachedValue> find(PlanValidator.Prepared prepared,StepAttempt attempt,StepGrant grant,JobContext job,CacheInputs inputs) {
        check(prepared,attempt,grant,job,StepAttempt.Purpose.CACHE_LOOKUP);String key=key(prepared,attempt,grant,inputs);
        grant.consume(attempt,StepAttempt.Purpose.CACHE_LOOKUP);
        if(key==null)return Optional.empty();
        expiry.assertLive(job.conversationExpiresAt(),job.cancellation());
        var value=store.find(job.scope(),prepared.plan().conversationId(),key,grant.currentPdg());
        return value.filter(item->item.capabilityId().equals(attempt.binding().capabilityId())&&clock.instant().isBefore(item.expiresAt())
            &&access.evaluate(job.scope().perm(),item.sourceDeps(),documents.states(job.scope().perm(),Set.copyOf(item.sourceDeps().knowledgeDocuments())))==ResultAccessEvaluator.Decision.ALLOW);
    }
    public boolean put(PlanValidator.Prepared prepared,StepAttempt attempt,StepGrant grant,JobContext job,CacheInputs inputs,CachedValue value) {
        check(prepared,attempt,grant,job,StepAttempt.Purpose.CACHE_PUT);String key=key(prepared,attempt,grant,inputs);
        if(!value.capabilityId().equals(attempt.binding().capabilityId()))throw new IllegalArgumentException("캐시 자료 연결이 다릅니다");
        grant.consume(attempt,StepAttempt.Purpose.CACHE_PUT);
        if(key==null)return false;
        expiry.assertLive(job.conversationExpiresAt(),job.cancellation());
        Instant expires=clock.instant().plusSeconds(60);if(job.conversationExpiresAt().isBefore(expires))expires=job.conversationExpiresAt();if(value.expiresAt().isBefore(expires))expires=value.expiresAt();
        if(!clock.instant().isBefore(expires))return false;
        return store.put(job.scope(),prepared.plan().conversationId(),key,grant.currentPdg(),new CachedValue(value.capabilityId(),value.value(),value.sourceDeps(),expires));
    }
    private void check(PlanValidator.Prepared prepared,StepAttempt attempt,StepGrant grant,JobContext job,StepAttempt.Purpose purpose) {
        expiry.assertLive(job.conversationExpiresAt(),job.cancellation());
        if(attempt.purpose()!=purpose||!prepared.plan().conversationId().equals(job.conversationId())||!job.jobId().equals(prepared.plan().requestId())
                ||!validator.authorizedStep(prepared,attempt,grant,job.scope())||attempt.mode()!=StepAttempt.Mode.DIRECT_SOURCE)throw new IllegalArgumentException("캐시 인가 연결이 다릅니다");
    }
    private String key(PlanValidator.Prepared prepared,StepAttempt attempt,StepGrant grant,CacheInputs inputs) {
        var manifest=prepared.manifest(attempt.binding().stepId());
        if(manifest.toolKind()==ToolKind.BUSINESS||manifest.toolKind()==ToolKind.KNOWLEDGE||!manifest.sourceFreshnessPolicy().equals(inputs.sourceFreshnessPolicy()))throw new IllegalArgumentException("캐시 정책이 다릅니다");
        if(inputs.modelRevision().isPresent()||manifest.toolKind()==ToolKind.GIT&&inputs.sourceRevision().filter(value->value.matches("[0-9a-f]{40}")).isEmpty())return null;
        var binding=attempt.binding();
        var values=List.of(binding.ownerSystem().name(),binding.actorRef().value(),binding.sub(),grant.currentPdg(),prepared.plan().conversationId(),binding.capabilityId(),binding.contractVersion(),binding.schemaDigest(),binding.argumentsDigest(),binding.resourcesDigest(),binding.sourceRef(),inputs.sourceFreshnessPolicy(),inputs.modelRevision().orElse(""),inputs.sourceRevision().orElse(""));
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(SchemaContracts.canonicalize(SchemaContracts.JSON.writeValueAsString(values)).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException failure){throw new IllegalStateException("캐시 키를 만들 수 없습니다",failure);}
    }
}
