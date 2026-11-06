package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import java.time.Instant;
import java.util.*;

/** 실제 어댑터 관측을 기존 raw Tool 계약에만 투영합니다. */
final class ToolResults {
    private ToolResults() {}
    static ToolResultEnvelope result(CapabilityManifest manifest,VerifiedSystemScope scope,ResultStatus status,List<?> items,Instant observedAt,
            List<String> missing,Collection<Warning> warnings,List<ToolResultMetadata.MissingRange> missingRanges,String revision) {
        String id=UUID.randomUUID().toString();var source=new OwnedReference(scope.boundSystem(),manifest.sourceAllowlistRef());
        boolean data=Set.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.PARTIAL,ResultStatus.STALE,ResultStatus.CONFLICT).contains(status);
        var ref=data?new OwnedReference(scope.boundSystem(),"data://"+scope.boundSystem().name()+"/tool/"+id):null;
        var content=SchemaContracts.JSON.createObjectNode().set("items",SchemaContracts.JSON.valueToTree(items));
        List<Evidence> evidence=data?List.of(new Evidence(UUID.randomUUID().toString(),scope.boundSystem(),manifest.toolKind().name(),source,null,observedAt,null,null,"OBSERVED",scope.actorRef())):List.of();
        return new ToolResultEnvelope(id,manifest.capabilityId(),manifest.contractVersion(),manifest.schemaDigest(),scope.boundSystem(),status,ref,List.of(),observedAt,revision,
            missing,List.copyOf(warnings),status==ResultStatus.FAILED?ErrorCode.CONTRACT_INVALID:null,UUID.randomUUID().toString(),new ToolResultMetadata(source,manifest.sourceFreshnessPolicy(),false,missingRanges),null,
            data?List.of(new ResultBlock("table",ref,content)):List.of(),List.of(),evidence,List.of(),List.of());
    }
}
