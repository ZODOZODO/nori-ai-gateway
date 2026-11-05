package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class PlanValidatorOrderTest {
    private final PlanValidator validator=validator(registry(manifest(),new ContractVersionPolicy()),new PlanInjectionSeam.NoOp());
    @Test void unknownCapabilityAndNullArgumentsReachUnsupportedWithoutEarlyOwnerRejection() {
        var unknown=new PlanStep("unknown","wm.unknown.read",List.of(),List.of(),null);
        var outcome=validator.validate(plan(unknown),scope(),new Resolver());
        assertThat(outcome.rejectReason()).isEqualTo(RejectReason.UNSUPPORTED);
        assertThat(outcome.trace()).extracting(ValidationOutcome.Trace::step).containsExactly(1,2,3,4,5,6,7,8,9);
        assertThat(validator.validate(plan(new PlanStep("unknown",null,List.of(),List.of(),null)),scope(),new Resolver()).rejectReason()).isEqualTo(RejectReason.PLAN_INVALID);
        assertThat(validator.validate(null,scope(),new Resolver()).rejectReason()).isEqualTo(RejectReason.PLAN_INVALID);
        assertThat(validator.validate(plan(step()),null,new Resolver()).trace()).singleElement().extracting(ValidationOutcome.Trace::step).isEqualTo(1);
    }
    @Test void resourceAbsenceIsStepEightEvenWhenQueryMentionsOtherSystem() {
        assertThat(validator.validate(plan(step()),scope(),new Resolver()).accepted()).isTrue();
        var resolver=new Resolver();resolver.exists=false;resolver.resourceOwner=null;
        var out=validator.validate(plan(step()),scope(),resolver);
        assertThat(out.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);assertThat(out.status()).isEqualTo(ResultStatus.FAILED);
        assertThat(out.trace().getLast().step()).isEqualTo(8);
    }
    @Test void resourceArgumentsAreResolvedEvenWhenPlanResourcesOmitted() {
        var resolver=new Resolver();resolver.exists=false;resolver.resourceOwner=null;
        var step=new PlanStep("s1",ID,List.of(),List.of(),SchemaContracts.parse("{\"eqp\":\"WM\"}"));
        assertThat(validator.validate(plan(step),scope(),resolver).rejectReason()).isEqualTo(RejectReason.RESOURCE_NOT_FOUND);
    }
    @Test void rejectsOwnersBeforeSourceAndPermissionsBeforeAvailability() {
        var resolver=new Resolver();resolver.resourceOwner=OwnerSystem.WM;resolver.permissions=Set.of();
        var out=validator.validate(plan(step()),scope(),resolver);
        assertThat(out.trace().getLast().step()).isEqualTo(3);assertThat(out.rejectReason()).isEqualTo(RejectReason.OWNER_MISMATCH);
        resolver.resourceOwner=OwnerSystem.TC;
        out=validator.validate(plan(step()),scope(),resolver);assertThat(out.trace().getLast().step()).isEqualTo(5);
        resolver.permissions=scope().perm();resolver.pdg="stale";
        assertThat(validator.validate(plan(step()),scope(),resolver).rejectReason()).isEqualTo(RejectReason.DENIED);
        resolver.pdg="digest";resolver.sourceAllowed=false;
        assertThat(validator.validate(plan(step()),scope(),resolver).trace().getLast().step()).isEqualTo(4);
    }
    @Test void deniesEmptyPermissionMappingsAndUsesBackendAnyOf() {
        var m=modify(manifest(),"requiredPermission",List.of());
        assertThat(validator(registry(m,new ContractVersionPolicy()),new PlanInjectionSeam.NoOp()).validate(plan(step()),scope(),new Resolver()).rejectReason()).isEqualTo(RejectReason.DENIED);
        m=modify(manifest(),"requiredPermission",List.of("UNKNOWN","EQP_READ"));
        assertThat(validator(registry(m,new ContractVersionPolicy()),new PlanInjectionSeam.NoOp()).validate(plan(step()),scope(),new Resolver()).accepted()).isTrue();
    }
    @Test void rejectsCyclesDuplicatesCostsAndQRefAtStepSeven() {
        var a=new PlanStep("s1",ID,List.of("s1"),List.of(),step().arguments());
        for (PlanEnvelope bad : List.of(plan(a),plan(step(),step()),plan(step(SchemaContracts.parse("{\"eqp\":\"EQP_001\",\"qRef\":\"Q1\"}")))))
            assertThat(validator.validate(bad,scope(),new Resolver()).trace().getLast().step()).isEqualTo(7);
        var low=new PlanValidator(registry(manifest(),new ContractVersionPolicy()),new PlanInjectionSeam.NoOp(),CLOCK,1,0);
        assertThat(low.validate(plan(step()),scope(),new Resolver()).rejectReason()).isEqualTo(RejectReason.PLAN_INVALID);
    }
    @Test void resourceTypeAndMissingWorkflowAreDistinctAndUnsupportedIsLast() {
        var resolver=new Resolver();resolver.actualType="MODEL";
        assertThat(validator.validate(plan(step()),scope(),resolver).rejectReason()).isEqualTo(RejectReason.RESOURCE_NOT_FOUND);
        var emptyRegistry=new CapabilityRegistry(List.of(manifest()),List.of(),Set.of(ImplementationPhase.PHASE_1),new ContractVersionPolicy());
        assertThat(validator(emptyRegistry,new PlanInjectionSeam.NoOp()).validate(plan(step()),scope(),new Resolver()).trace().getLast().step()).isEqualTo(9);
        var uns=new PlanEnvelope("request","conversation","질문",List.of(),TaskType.UNSUPPORTED_REQUEST,List.of());
        assertThat(validator.validate(uns,scope(),new Resolver()).status()).isEqualTo(ResultStatus.UNSUPPORTED);
    }
    @Test void nullInjectedDataAndInvalidResourceFieldsArePlanErrors() {
        var invalid=validator(registry(manifest(),new ContractVersionPolicy()),m -> null);
        assertThat(invalid.validate(plan(step()),scope(),new Resolver()).rejectReason()).isEqualTo(RejectReason.PLAN_INVALID);
        var bad=new PlanEnvelope("request","conversation","질문",List.of(new ResourceRef(null,"value")),TaskType.LIVE_QUERY,List.of(step()));
        assertThat(validator.validate(bad,scope(),new Resolver()).rejectReason()).isEqualTo(RejectReason.PLAN_INVALID);
    }

}
