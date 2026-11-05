package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

@Tag("stage-F1")
@Tag("label-MOCK_API")
class ContractVersionPolicyTest {
    @Test @Tag("AC-17-VERSION") void unsupportedManifestIsValidButNeverRegisteredOrExposed() {
        for (String version:List.of("2.0","1.1","1","1.0.0")) {
            var m=modify(manifest(),"contractVersion",version);var policy=new ContractVersionPolicy();var registry=registry(m,policy);
            assertThat(registry.find(m.capabilityId())).isEmpty();assertThat(registry.view(scope()).capabilities()).isEmpty();
            assertThat(policy.accepts("1.0",m.schemaDigest())).isFalse();
            assertThat(validator(registry,new PlanInjectionSeam.NoOp()).validate(plan(step()),scope(),new Resolver()).status()).isEqualTo(ResultStatus.UNSUPPORTED);
        }
    }
    @Test @Tag("AC-17-VERSION") void resultVersionOrDigestMismatchClearsEveryDownstreamPayload() {
        var m=manifest();var policy=new ContractVersionPolicy();registry(m,policy);var validator=new ToolResultContractValidator(policy);
        var valid=result(m);assertThat(validator.validate(valid,m)).isSameAs(valid);
        for (ToolResultEnvelope bad:List.of(changeResult(valid,"contractVersion","2.0"),changeResult(valid,"schemaDigest","f".repeat(64)))) assertCleared(validator.validate(bad,m));
        assertCleared(new ToolResultContractValidator(new ContractVersionPolicy()).validate(valid,m));
    }
    @Test void rejectsWrongOwnerDataEvidenceAndSourceBindingsWithoutLeakingThem() {
        var m=manifest();var policy=new ContractVersionPolicy();registry(m,policy);var validator=new ToolResultContractValidator(policy);var valid=result(m);
        var otherRef=new OwnedReference(OwnerSystem.WM,"evidence://WM/private/item");
        for (ToolResultEnvelope bad:List.of(changeResult(valid,"ownerSystem",OwnerSystem.WM),changeResult(valid,"dataRef",otherRef),
            changeResult(valid,"evidenceRefs",List.of(otherRef)),changeResult(valid,"citations",List.of(otherRef)),changeResult(valid,"composerInputs",List.of(otherRef)),
            changeResult(valid,"metadata",new ToolResultMetadata(new OwnedReference(OwnerSystem.TC,"sources://TC/other"),"LIVE",false,List.of())))) {
            var rejected=validator.validate(bad,m);assertCleared(rejected);assertThat(rejected.sourceRevision()).isNull();assertThat(rejected.metadata()).isNull();
        }
    }
    @Test void executedAdapterMismatchIsValidatedInDispatcher() {
        var m=manifest();var policy=new ContractVersionPolicy();var registry=registry(m,policy);
        ToolExecutor bad=new ToolExecutor() {
            public ToolKind toolKind(){return ToolKind.BUSINESS;}
            public ToolResultEnvelope execute(PlanStep s,CapabilityManifest manifest,VerifiedSystemScope scope,ExecutionContext context){return changeResult(result(manifest),"contractVersion","2.0");}
        };
        var dispatcher=new ToolDispatcher(registry,validator(registry,new PlanInjectionSeam.NoOp()),new ToolResultContractValidator(policy),new DispatchInjectionSeam.NoOp(),List.of(bad),CLOCK);
        assertCleared(dispatcher.dispatch(step(),scope(),new ExecutionContext(new Resolver(),t -> {})));
    }
    @Test void distinguishesEmptyObservationsPartialMissingDataAndContractFailure() {
        var m=manifest();var policy=new ContractVersionPolicy();registry(m,policy);var validator=new ToolResultContractValidator(policy);
        var tree=SchemaContracts.JSON.<tools.jackson.databind.node.ObjectNode>valueToTree(result(m));
        tree.put("status","EMPTY");tree.putNull("dataRef");
        for(String key:List.of("blocks","claims","evidence","evidenceRefs","citations","composerInputs"))tree.putArray(key);
        var empty=SchemaContracts.JSON.treeToValue(tree,ToolResultEnvelope.class);
        assertThat(validator.validate(empty,m).status()).isEqualTo(ResultStatus.EMPTY);
        tree.put("status","PARTIAL");assertCleared(validator.validate(SchemaContracts.JSON.treeToValue(tree,ToolResultEnvelope.class),m));
        tree.putArray("missingFields").add("items");
        assertThat(validator.validate(SchemaContracts.JSON.treeToValue(tree,ToolResultEnvelope.class),m).status()).isEqualTo(ResultStatus.PARTIAL);
        assertCleared(validator.validate(null,m));
    }

    private void assertCleared(ToolResultEnvelope result) {
        assertThat(result.status()).isEqualTo(ResultStatus.FAILED);assertThat(result.errorCode()).isEqualTo(ErrorCode.CONTRACT_INVALID);
        assertThat(result.blocks()).isEmpty();assertThat(result.claims()).isEmpty();assertThat(result.evidence()).isEmpty();
        assertThat(result.evidenceRefs()).isEmpty();assertThat(result.citations()).isEmpty();assertThat(result.composerInputs()).isEmpty();assertThat(result.dataRef()).isNull();
    }
}
