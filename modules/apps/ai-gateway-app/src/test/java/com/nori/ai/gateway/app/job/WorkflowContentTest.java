package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.blocks.*;
import com.nori.ai.gateway.core.evidence.EvidenceAssembler;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.scope.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkflowContentTest {
    private static final String PHONE="01012345678";
    private static final String PRIVATE="연락 01012345678 token=local-secret user@example.com SENTINEL:private-content";
    private final ContentSanitizer sanitizer=new ContentSanitizer();
    private final VerifiedSystemScope scope=new VerifiedSystemScope(OwnerSystem.TC,"fixture",new ActorRef(PHONE),PHONE,Set.of("EQP_READ","AI_DOC_READ"),"a".repeat(64),Instant.parse("2026-11-06T00:00:00Z"),Instant.parse("2026-11-06T00:01:00Z"),PHONE);
    private CapabilityManifest manifest(ToolKind kind){return new ManifestLoader().loadClasspath("manifests/tc/index.json").stream().filter(value->value.toolKind()==kind).findFirst().orElseThrow();}
    @Test void validatedKnowledgeIdentifiersAndActorRemainExactWhileFreeContentIsMasked() {
        var manifest=manifest(ToolKind.KNOWLEDGE);var source="knowledge://TC/docs/"+PHONE;
        var row=Map.of("ownerSystem","TC","sourceRef",source,"chunkId",PHONE,"revision",PHONE,"text",PRIVATE);
        var raw=ToolResults.result(manifest,scope,ResultStatus.SUCCESS,List.of(row),scope.issuedAt(),List.of(),Set.of(),List.of(),PHONE);var masked=WorkflowContent.tool(raw,manifest,scope,sanitizer);
        assertThat(masked.toolExecutionId()).isEqualTo(raw.toolExecutionId());assertThat(masked.dataRef()).isEqualTo(raw.dataRef());assertThat(masked.sourceRevision()).isEqualTo(PHONE);assertThat(masked.evidence()).isEqualTo(raw.evidence());assertThat(masked.evidence().getFirst().actorAccessScope().value()).isEqualTo(PHONE);
        var item=masked.blocks().getFirst().content().path("items").get(0);assertThat(item.path("sourceRef").asString()).isEqualTo(source);assertThat(item.path("chunkId").asString()).isEqualTo(PHONE);assertThat(item.path("revision").asString()).isEqualTo(PHONE);assertMasked(item.path("text").asString());
        var ui=new BlockBuilder(new UiSchemaValidator()).build(manifest,raw).getFirst();var display=WorkflowContent.block(ui,EvidenceAssembler.projectKnowledge(manifest,raw,scope),"rows",sanitizer);new UiSchemaValidator().validate(display,OwnerSystem.TC);
        assertThat(display.content().path("rows").get(0).path("revision").asString()).isEqualTo(PHONE);assertMasked(display.content().path("rows").get(0).path("text").asString());assertThat(raw.blocks().getFirst().content().toString()).contains(PRIVATE);
    }
    @Test void anArbitraryRawItemCannotBypassMaskingByChoosingTrustedMetadataFieldNames() {
        var manifest=manifest(ToolKind.BUSINESS);var raw=ToolResults.result(manifest,scope,ResultStatus.SUCCESS,List.of(Map.of("sourceRef",PHONE,"revision",PHONE,"text",PRIVATE)),scope.issuedAt(),List.of(),Set.of(),List.of(),null);
        var item=WorkflowContent.tool(raw,manifest,scope,sanitizer).blocks().getFirst().content().path("items").get(0);assertThat(item.path("sourceRef").asString()).isEqualTo("[마스킹]");assertThat(item.path("revision").asString()).isEqualTo("[마스킹]");assertMasked(item.path("text").asString());
    }
    @Test void aContractValidNullClaimTextRemainsNullAndItsOwnedReferencesRemainExact() {
        var manifest=manifest(ToolKind.BUSINESS);var raw=ToolResults.result(manifest,scope,ResultStatus.SUCCESS,List.of(Map.of("text",PRIVATE)),scope.issuedAt(),List.of(),Set.of(),List.of(),null);
        var tree=(tools.jackson.databind.node.ObjectNode)SchemaContracts.JSON.valueToTree(raw);tree.set("claims",SchemaContracts.JSON.valueToTree(List.of(new Claim(PHONE,Verdict.PROBABLE,null,List.of(new OwnedReference(OwnerSystem.TC,"evidence://TC/"+PHONE))))));raw=SchemaContracts.JSON.treeToValue(tree,ToolResultEnvelope.class);
        var versions=new ContractVersionPolicy();new CapabilityRegistry(List.of(manifest),List.of(),Set.of(ImplementationPhase.PHASE_1),versions);assertThat(new com.nori.ai.gateway.core.dispatch.ToolResultContractValidator(versions).validate(raw,manifest)).isSameAs(raw);
        var masked=WorkflowContent.tool(raw,manifest,scope,sanitizer);assertThat(masked.claims().getFirst().text()).isNull();assertThat(masked.claims().getFirst().claimId()).isEqualTo(PHONE);assertThat(masked.claims().getFirst().evidenceRefs()).isEqualTo(raw.claims().getFirst().evidenceRefs());
    }
    // mask-v1 전화 규칙과 겹치는 규격 값입니다: hex32 traceId, hex16 spanId(소/대문자), 9/10자리 start/end, numeric pipeline version.
    private static final String TRACE_ID="aaaa01012345678"+"b".repeat(13)+"0001",LOWER_SPAN="ab01012345678cde",UPPER_SPAN="AB01012345678CDE",START="101234567",END="1012345678",VERSION="1012345678";
    private static Map<String,Object> span(String service,String spanId,String start,String end,String version){
        var span=new LinkedHashMap<String,Object>();span.put("service",service);span.put("spanId",spanId);span.put("name","equipment SENTINEL:span-name");
        span.put("startTimeUnixNano",start);span.put("endTimeUnixNano",end);span.put("serviceVersion",version);return span;
    }
    private tools.jackson.databind.JsonNode traceItem(ToolKind kind,String traceId,List<Map<String,Object>> spans){
        var manifest=manifest(kind);var raw=ToolResults.result(manifest,scope,ResultStatus.SUCCESS,List.of(Map.of("traceId",traceId,"spans",spans)),scope.issuedAt(),List.of(),Set.of(),List.of(),null);
        return WorkflowContent.tool(raw,manifest,scope,sanitizer).blocks().getFirst().content().path("items").get(0);
    }
    @Test void specValidTraceMetadataStaysExactAlthoughTheSanitizerWouldChangeIt() {
        for(String value:List.of(TRACE_ID,LOWER_SPAN,UPPER_SPAN,START,END,VERSION))assertThat(sanitizer.sanitize(value).text()).as("전제: 실제 sanitizer는 원값을 바꿉니다").isNotEqualTo(value);
        var item=traceItem(ToolKind.TRACE,TRACE_ID,List.of(span("tc-gateway",LOWER_SPAN,START,END,VERSION),span("tc-business",UPPER_SPAN,START,START,VERSION)));
        assertThat(item.path("traceId").asString()).isEqualTo(TRACE_ID);
        var first=item.path("spans").get(0);var second=item.path("spans").get(1);
        assertThat(first.path("spanId").asString()).isEqualTo(LOWER_SPAN);assertThat(second.path("spanId").asString()).isEqualTo(UPPER_SPAN);
        assertThat(first.path("startTimeUnixNano").asString()).isEqualTo(START);assertThat(first.path("endTimeUnixNano").asString()).isEqualTo(END);
        assertThat(second.path("endTimeUnixNano").asString()).isEqualTo(START);
        assertThat(first.path("serviceVersion").asString()).isEqualTo(VERSION);assertThat(second.path("serviceVersion").asString()).isEqualTo(VERSION);
        assertThat(first.path("name").asString()).contains("[마스킹]").doesNotContain("SENTINEL:span-name");
    }
    @Test void otherKindsInvalidValuesAndFreeTraceTextKeepTheExistingMasking() {
        var log=traceItem(ToolKind.LOG,TRACE_ID,List.of(span("tc-gateway",LOWER_SPAN,START,END,VERSION)));
        assertThat(log.path("traceId").asString()).isNotEqualTo(TRACE_ID);var logSpan=log.path("spans").get(0);
        for(String field:List.of("spanId","startTimeUnixNano","endTimeUnixNano","serviceVersion"))assertThat(logSpan.path(field).asString()).as(field).contains("[마스킹]");
        var invalid=traceItem(ToolKind.TRACE,TRACE_ID.toUpperCase(Locale.ROOT),List.of(
            span("tc-gateway","a01012345678cde",END,START,"01012345678"),
            span("tc-gateway","gb01012345678cde",START,END+"x","1012345678a"),
            span("tc-other",LOWER_SPAN,START,END,VERSION)));
        assertThat(invalid.path("traceId").asString()).contains("[마스킹]");
        var reversed=invalid.path("spans").get(0);var nonNumeric=invalid.path("spans").get(1);var foreign=invalid.path("spans").get(2);
        assertThat(reversed.path("spanId").asString()).contains("[마스킹]");assertThat(reversed.path("startTimeUnixNano").asString()).contains("[마스킹]");
        assertThat(reversed.path("endTimeUnixNano").asString()).contains("[마스킹]");assertThat(reversed.path("serviceVersion").asString()).contains("[마스킹]");
        assertThat(nonNumeric.path("spanId").asString()).contains("[마스킹]");assertThat(nonNumeric.path("startTimeUnixNano").asString()).contains("[마스킹]");
        assertThat(nonNumeric.path("endTimeUnixNano").asString()).contains("[마스킹]");assertThat(nonNumeric.path("serviceVersion").asString()).contains("[마스킹]");
        assertThat(foreign.path("spanId").asString()).isEqualTo(LOWER_SPAN);assertThat(foreign.path("serviceVersion").asString()).contains("[마스킹]");
        for(var span:invalid.path("spans"))assertThat(span.path("name").asString()).contains("[마스킹]").doesNotContain("SENTINEL:span-name");
    }
    private void assertMasked(String value){assertThat(value).contains("[마스킹]").doesNotContain(PHONE,"local-secret","user@example.com","SENTINEL:private-content");}
}
