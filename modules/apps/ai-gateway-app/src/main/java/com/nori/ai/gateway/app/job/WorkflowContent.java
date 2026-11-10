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
        var blocks=original.blocks().stream().map(block->manifest.toolKind()==com.nori.ai.gateway.core.registry.ToolKind.TRACE?traceMetadata(block,block(block,hits,"items",sanitizer)):block(block,hits,"items",sanitizer)).toList();
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
    /**
     * TRACE 결과의 규격 검증 식별자만 원값으로 둔다. hit binding이 miss와 같은 provenance·GIT 정렬 키를 쓰게 하기 위함이며,
     * 자유 문자열(span name 등)·조건 밖 값·다른 kind의 같은 키는 기존 마스킹을 유지한다.
     */
    private static ResultBlock traceMetadata(ResultBlock original,ResultBlock masked) {
        if(original.content()==null||!original.content().path("items").isArray()||!masked.content().path("items").isArray())return masked;
        var content=masked.content().deepCopy();var sourceItems=original.content().get("items");var items=content.get("items");
        if(sourceItems.size()!=items.size())throw new IllegalArgumentException("추적 표시 자료 결속이 다릅니다");
        for(int i=0;i<sourceItems.size();i++) {
            var source=sourceItems.get(i);if(!source.isObject()||!items.get(i).isObject())continue;var item=(tools.jackson.databind.node.ObjectNode)items.get(i);
            // 기존 TempoTraceConnector 검증 형식입니다.
            String traceId=string(source,"traceId");if(traceId!=null&&traceId.matches("[0-9a-f]{32}"))item.put("traceId",traceId);
            var sourceSpans=source.path("spans");var spans=item.path("spans");
            if(!sourceSpans.isArray()||!spans.isArray()||sourceSpans.size()!=spans.size())continue;
            for(int j=0;j<sourceSpans.size();j++) {
                var span=sourceSpans.get(j);if(!span.isObject()||!spans.get(j).isObject())continue;var target=(tools.jackson.databind.node.ObjectNode)spans.get(j);
                // OTel Trace API·OTLP 규격 SpanId(16자리 hex·대소문자 무관·전부 0 아님)입니다.
                String spanId=string(span,"spanId");if(spanId!=null&&io.opentelemetry.api.trace.SpanId.isValid(spanId.toLowerCase(Locale.ROOT)))target.put("spanId",spanId);
                String start=string(span,"startTimeUnixNano"),end=string(span,"endTimeUnixNano");
                if(start!=null&&end!=null&&start.matches("[0-9]{1,30}")&&end.matches("[0-9]{1,30}")&&new java.math.BigInteger(end).compareTo(new java.math.BigInteger(start))>=0)
                    {target.put("startTimeUnixNano",start);target.put("endTimeUnixNano",end);}
                // 기존 DeploymentEvidenceResolver 조회 조건(승인 앱·양의 long pipeline 번호)입니다.
                String service=string(span,"service"),version=string(span,"serviceVersion");
                if(service!=null&&com.nori.ai.gateway.integration.loki.LokiSourcePolicy.APPS.contains(service)&&version!=null&&version.matches("[1-9][0-9]*")&&fitsLong(version))target.put("serviceVersion",version);
            }
        }
        return new ResultBlock(masked.type(),masked.dataRef(),content);
    }
    private static String string(JsonNode node,String field){return node.path(field).isString()?node.get(field).asString():null;}
    private static boolean fitsLong(String digits){try{Long.parseLong(digits);return true;}catch(NumberFormatException overflow){return false;}}
}
