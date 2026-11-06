package com.nori.ai.gateway.core.evidence;

import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.registry.*;
import java.util.*;

public final class EvidenceAssembler {
    public record KnowledgeHit(EvidenceSourceRef source,ResultSourceDeps.DocKey document) {
        public KnowledgeHit {Objects.requireNonNull(source);Objects.requireNonNull(document);}
        @Override public String toString(){return "KnowledgeHit[보호됨]";}
    }
    public record KnowledgeEvidence(Evidence value,EvidenceSourceRef source) {
        public KnowledgeEvidence {Objects.requireNonNull(value);Objects.requireNonNull(source);}
        @Override public String toString(){return "KnowledgeEvidence[보호됨]";}
    }
    public static List<KnowledgeHit> projectKnowledge(CapabilityManifest manifest,ToolResultEnvelope result,VerifiedSystemScope scope) {
        if(manifest.toolKind()!=ToolKind.KNOWLEDGE||!Set.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.PARTIAL,ResultStatus.STALE,ResultStatus.CONFLICT).contains(result.status()))return List.of();
        var hits=new LinkedHashMap<String,KnowledgeHit>();
        for(var block:result.blocks()) {
            var content=block.content();if(content==null||!content.isObject()||!content.path("items").isArray())throw invalidKnowledge();
            for(var item:content.get("items")) {
            if(!item.isObject())throw invalidKnowledge();
            String owner=metadata(item,"ownerSystem"),ref=metadata(item,"sourceRef"),chunk=metadata(item,"chunkId"),revision=metadata(item,"revision");
            OwnerSystem system;try{system=OwnerSystem.valueOf(owner);}catch(IllegalArgumentException invalid){throw invalidKnowledge();}
            if(system!=manifest.ownerSystem()||system!=scope.boundSystem()||!new OwnedReference(system,ref).belongsTo(system))throw invalidKnowledge();
            var hit=new KnowledgeHit(new EvidenceSourceRef(system,ref,chunk,revision),new ResultSourceDeps.DocKey(system.name(),ref,revision));
            String key=system.name()+"\u0000"+chunk;var previous=hits.putIfAbsent(key,hit);
            if(previous!=null&&!previous.equals(hit))throw invalidKnowledge();
            }
        }
        return List.copyOf(hits.values());
    }
    public static List<KnowledgeEvidence> knowledgeEvidence(CapabilityManifest manifest,ToolResultEnvelope result,VerifiedSystemScope scope) {
        return projectKnowledge(manifest,result,scope).stream().map(hit->new KnowledgeEvidence(
            new Evidence(UUID.randomUUID().toString(),manifest.ownerSystem(),manifest.toolKind().name(),new OwnedReference(manifest.ownerSystem(),manifest.sourceAllowlistRef()),null,result.observedAt(),result.traceId(),null,"OBSERVED",scope.actorRef()),hit.source())).toList();
    }
    private static String metadata(tools.jackson.databind.JsonNode item,String field) {
        var value=item.get(field);if(value==null||!value.isString()||value.asString().isBlank())throw invalidKnowledge();return value.asString();
    }
    private static IllegalArgumentException invalidKnowledge(){return new IllegalArgumentException("문서 근거 계약을 확인할 수 없습니다");}
    public List<Evidence> validate(List<Evidence> values,VerifiedSystemScope scope) {
        Set<String> ids=new HashSet<>();
        for(var value:values)if(value.evidenceId()==null||value.evidenceId().isBlank()||!ids.add(value.evidenceId())||value.ownerSystem()!=scope.boundSystem()
                ||value.sourceRef()==null||!value.sourceRef().belongsTo(scope.boundSystem())||value.observedAt()==null||!scope.actorRef().equals(value.actorAccessScope())
                ||value.deploymentRef()!=null&&!value.deploymentRef().belongsTo(scope.boundSystem()))throw new IllegalArgumentException("근거 연결을 확인할 수 없습니다");
        return List.copyOf(values);
    }
    public Verdict verdict(boolean directRuleEvidence,boolean complete,boolean conflicting,boolean verifiedDeployment) {
        if(conflicting)return Verdict.CONFLICTING_EVIDENCE;
        if(!complete)return Verdict.INSUFFICIENT_EVIDENCE;
        return directRuleEvidence&&verifiedDeployment?Verdict.CONFIRMED:Verdict.PROBABLE;
    }
}
