package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import tools.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.evidence.EvidenceAssembler;
import com.nori.ai.gateway.core.registry.CapabilityManifest;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.*;

/** JSON 구문을 손상시키지 않고 자유 문자열 값에 기존 마스킹을 적용합니다. */
final class WorkflowContent {
    private WorkflowContent() {}
    static JsonNode mask(JsonNode value,ContentSanitizer sanitizer) {
        if(value.isString())return SchemaContracts.JSON.getNodeFactory().stringNode(sanitizer.sanitize(value.asString()).text());
        if(value.isObject()){var copy=SchemaContracts.JSON.createObjectNode();for(var entry:value.properties())copy.set(entry.getKey(),mask(entry.getValue(),sanitizer));return copy;}
        if(value.isArray()){var copy=SchemaContracts.JSON.createArrayNode();for(var item:value)copy.add(mask(item,sanitizer));return copy;}
        return value.deepCopy();
    }
    static ToolResultEnvelope tool(ToolResultEnvelope original,CapabilityManifest manifest,VerifiedSystemScope scope,ContentSanitizer sanitizer) {
        var hits=EvidenceAssembler.projectKnowledge(manifest,original,scope);
        var blocks=original.blocks().stream().map(block->block(block,hits,"items",sanitizer)).toList();
        var claims=original.claims().stream().map(claim->new Claim(claim.claimId(),claim.verdict(),text(claim.text(),sanitizer),claim.evidenceRefs())).toList();
        return new ToolResultEnvelope(original.toolExecutionId(),original.capabilityId(),original.contractVersion(),original.schemaDigest(),original.ownerSystem(),original.status(),original.dataRef(),original.evidenceRefs(),original.observedAt(),original.sourceRevision(),original.missingFields().stream().map(value->sanitizer.sanitize(value).text()).toList(),original.warnings(),original.errorCode(),original.traceId(),original.metadata(),original.clarification(),blocks,claims,original.evidence(),original.citations(),original.composerInputs());
    }
    static ResultBlock block(ResultBlock original,List<EvidenceAssembler.KnowledgeHit> hits,String rowsField,ContentSanitizer sanitizer) {
        if(original.content()==null)return original;
        var content=mask(original.content(),sanitizer);
        if("rows".equals(rowsField)&&"table".equals(original.type()))((tools.jackson.databind.node.ObjectNode)content).set("columns",original.content().get("columns").deepCopy());
        // 기존 순수 metadata 검증으로 확인한 문서 식별자만 복구하며 임의 row 키는 보호하지 않습니다.
        if(!hits.isEmpty()) {
            var byChunk=new HashMap<String,EvidenceSourceRef>();hits.forEach(hit->byChunk.put(hit.source().chunkId(),hit.source()));
            var sourceRows=original.content().get(rowsField);var rows=content.get(rowsField);
            if(sourceRows==null||rows==null||!sourceRows.isArray()||!rows.isArray()||sourceRows.size()!=rows.size())throw new IllegalArgumentException("문서 표시 자료 결속이 다릅니다");
            for(int i=0;i<sourceRows.size();i++) {
                var source=sourceRows.get(i);var reference=source.path("chunkId").isString()?byChunk.get(source.get("chunkId").asString()):null;
                if(reference==null||!source.path("ownerSystem").asString().equals(reference.ownerSystem().name())||!source.path("sourceRef").asString().equals(reference.sourceRef())||!source.path("revision").asString().equals(reference.expectedRevision()))throw new IllegalArgumentException("문서 표시 자료 결속이 다릅니다");
                var row=(tools.jackson.databind.node.ObjectNode)rows.get(i);row.put("ownerSystem",reference.ownerSystem().name()).put("sourceRef",reference.sourceRef()).put("chunkId",reference.chunkId()).put("revision",reference.expectedRevision());
            }
        }
        return new ResultBlock(original.type(),original.dataRef(),content);
    }
    static String text(String value,ContentSanitizer sanitizer){return value==null?null:sanitizer.sanitize(value).text();}
}
