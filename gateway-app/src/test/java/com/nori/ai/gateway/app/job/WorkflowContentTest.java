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
    private void assertMasked(String value){assertThat(value).contains("[마스킹]").doesNotContain(PHONE,"local-secret","user@example.com","SENTINEL:private-content");}
}
