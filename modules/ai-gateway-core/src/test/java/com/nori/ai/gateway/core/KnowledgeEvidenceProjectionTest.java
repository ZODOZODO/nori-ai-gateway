package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.evidence.EvidenceAssembler;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class KnowledgeEvidenceProjectionTest {
    private final CapabilityManifest manifest=modify(manifest(),"toolKind","KNOWLEDGE");
    private final VerifiedSystemScope scope=new WorkflowAuthorizationPreparationTest.Fixture().scope;
    private Map<String,Object> hit(String source,String chunk,String revision){return Map.of("ownerSystem","TC","sourceRef",source,"chunkId",chunk,"revision",revision,"text","검증된 문서");}
    private ToolResultEnvelope envelope(Object items) {
        var content=SchemaContracts.JSON.createObjectNode().set("items",SchemaContracts.JSON.valueToTree(items));
        return changeResult(result(manifest),"blocks",List.of(new ResultBlock("table",result(manifest).dataRef(),content)));
    }
    @Test void sameMetadataCreatesPairedSourceAndDocKeysWithoutCopyingEnvelopeRevision() {
        var a=hit("knowledge://TC/docs/a","a:0","a-v1");var b=hit("knowledge://TC/docs/b","b:0","b-v9");
        var raw=envelope(List.of(a,a,hit("knowledge://TC/docs/a","a:1","a-v1"),b));
        var hits=EvidenceAssembler.projectKnowledge(manifest,raw,scope);assertThat(hits).hasSize(3);
        assertThat(hits.stream().map(EvidenceAssembler.KnowledgeHit::document).distinct()).containsExactly(new ResultSourceDeps.DocKey("TC","knowledge://TC/docs/a","a-v1"),new ResultSourceDeps.DocKey("TC","knowledge://TC/docs/b","b-v9"));
        var evidence=EvidenceAssembler.knowledgeEvidence(manifest,raw,scope);assertThat(evidence).hasSize(3);
        for(int i=0;i<hits.size();i++){assertThat(evidence.get(i).source()).isEqualTo(hits.get(i).source());assertThat(evidence.get(i).value().sourceRef().ref()).isEqualTo(manifest.sourceAllowlistRef());assertThat(evidence.get(i).value().actorAccessScope()).isEqualTo(scope.actorRef());}
        assertThat(evidence.stream().map(pair->pair.source().expectedRevision())).containsExactly("a-v1","a-v1","b-v9");assertThat(raw.sourceRevision()).isEqualTo("rev");
        assertThat(EvidenceAssembler.projectKnowledge(manifest,envelope(List.of()),scope)).isEmpty();
    }
    @Test void malformedMetadataIsContractInvalidBeforeAnInvocationCanExposeRawData() {
        var good=hit("knowledge://TC/docs/a","a:0","v1");List<Object> malformed=new ArrayList<>();
        for(String field:List.of("ownerSystem","sourceRef","chunkId","revision"))for(Object value:Arrays.asList(null,"",17)) {var map=new LinkedHashMap<>(good);map.put(field,value);malformed.add(List.of(map));}
        for(String field:List.of("ownerSystem","sourceRef","chunkId","revision")){var map=new LinkedHashMap<>(good);map.remove(field);malformed.add(List.of(map));}
        malformed.add(List.of(hit("knowledge://WM/docs/a","a:0","v1")));var foreign=new LinkedHashMap<>(good);foreign.put("ownerSystem","WM");malformed.add(List.of(foreign));
        malformed.add(List.of(good,hit("knowledge://TC/docs/b","a:0","v2")));malformed.add(List.of("문자열 행"));
        for(Object items:malformed)assertRejected(envelope(items));
        assertRejected(changeResult(result(manifest),"blocks",List.of(new ResultBlock("table",result(manifest).dataRef(),null))));
    }
    private void assertRejected(ToolResultEnvelope raw) {
        var fixture=new WorkflowAuthorizationPreparationTest.Fixture();var versions=new ContractVersionPolicy();var registry=registry(manifest,versions);var validator=validator(registry,new PlanInjectionSeam.NoOp());
        var prepared=(PlanValidator.Prepared)validator.prepare(plan(step()),scope,fixture.resolver);var attempt=prepared.newAttempt(step(),scope,StepAttempt.Purpose.DISPATCH);
        var authorization=new SourceAuthorizationStep(new SourcePermissionMap(Map.of(manifest.sourceAllowlistRef(),new SourcePermissionMap.Entry(Set.of("EQP_READ"),false))),(call,required)->new TcSourceAuthorizationPort.Allowed(required,"b".repeat(64)));
        ToolExecutor executor=new ToolExecutor(){public ToolKind toolKind(){return ToolKind.KNOWLEDGE;}public ToolResultEnvelope execute(PlanStep step,CapabilityManifest actual,VerifiedSystemScope scope,ExecutionContext context){return raw;}};
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),List.of(executor),CLOCK);
        var outcome=(ToolDispatcher.Returned)dispatcher.dispatchCurrent(prepared,attempt,scope,(SourceAuthorizationStep.StepGrant)authorization.authorizeStep(attempt,scope,fixture.resolver.delegatedCall()),new ExecutionContext(fixture.resolver,ignored->{}));
        assertThat(outcome.result().errorCode()).isEqualTo(ErrorCode.CONTRACT_INVALID);assertThat(outcome.result().blocks()).isEmpty();assertThat(outcome.invocation().orElseThrow().validatedResult()).isEmpty();assertThat(outcome.invocation().orElseThrow().id()).isNotEqualTo(raw.toolExecutionId());
    }
}
