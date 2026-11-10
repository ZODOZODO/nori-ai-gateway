package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ScopeAwareInjectionSeamTest {
    @Test void existingSamAndNoOpDelegateFromScopedOverload() {
        var manifest=manifest();var scope=scope();
        PlanInjectionSeam plan=value->value;DispatchInjectionSeam dispatch=value->value;
        var target=new DispatchTarget(manifest,List.of(),null);
        assertThat(plan.inspect(manifest,scope)).isSameAs(manifest);
        assertThat(new PlanInjectionSeam.NoOp().inspect(manifest,scope)).isSameAs(manifest);
        assertThat(dispatch.inspect(target,scope)).isSameAs(target);
        assertThat(new DispatchInjectionSeam.NoOp().inspect(target,scope)).isSameAs(target);
    }
    @Test void realValidatorAndDispatcherForwardTheExactVerifiedScope() {
        var scope=scope();var manifest=manifest();var versions=new ContractVersionPolicy();var registry=registry(manifest,versions);
        var inspected=new java.util.concurrent.atomic.AtomicInteger();
        PlanInjectionSeam plan=new PlanInjectionSeam(){
            public CapabilityManifest inspect(CapabilityManifest value){throw new AssertionError("scope 전달이 필요합니다");}
            public CapabilityManifest inspect(CapabilityManifest value,VerifiedSystemScope actual){assertThat(actual).isSameAs(scope);inspected.incrementAndGet();return value;}
        };
        var validator=validator(registry,plan);
        assertThat(validator.validate(plan(step()),scope,new Resolver()).accepted()).isTrue();
        DispatchInjectionSeam dispatch=new DispatchInjectionSeam(){
            public DispatchTarget inspect(DispatchTarget value){throw new AssertionError("scope 전달이 필요합니다");}
            public DispatchTarget inspect(DispatchTarget value,VerifiedSystemScope actual){assertThat(actual).isSameAs(scope);inspected.incrementAndGet();return value;}
        };
        var executor=new ToolExecutor(){
            public ToolKind toolKind(){return ToolKind.BUSINESS;}
            public com.nori.ai.gateway.core.result.ToolResultEnvelope execute(com.nori.ai.gateway.core.plan.PlanStep step,CapabilityManifest tool,VerifiedSystemScope actual,ExecutionContext context){return result(tool);}
        };
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),dispatch,List.of(executor),CLOCK);
        assertThat(dispatcher.dispatch(step(),scope,new ExecutionContext(new Resolver(),trace->{})).status()).isEqualTo(com.nori.ai.gateway.core.result.ResultStatus.SUCCESS);
        assertThat(inspected).hasValue(2);
    }
    @Test void expiredScopeAndInitialDispatcherOwnerMismatchStillBlockBeforeSeamOrBackend() {
        var versions=new ContractVersionPolicy();var registry=registry(manifest(),versions);
        var plans=new java.util.concurrent.atomic.AtomicInteger();var dispatches=new java.util.concurrent.atomic.AtomicInteger();
        PlanInjectionSeam plan=value->{plans.incrementAndGet();return value;};
        DispatchInjectionSeam dispatch=value->{dispatches.incrementAndGet();return value;};
        var validator=validator(registry,plan);
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),dispatch,List.of(),CLOCK);
        var expired=new VerifiedSystemScope(OwnerSystem.TC,"service",new ActorRef("actor"),"actor",Set.of("EQP_READ"),"digest",NOW.minusSeconds(60),NOW,"expired");
        var backend=new java.util.concurrent.atomic.AtomicInteger();
        var resolver=new Resolver(){
            @Override public ResourceState resolve(com.nori.ai.gateway.core.plan.ResourceRef ref,VerifiedSystemScope actual){backend.incrementAndGet();return super.resolve(ref,actual);}
            @Override public SourceBinding source(CapabilityManifest tool,VerifiedSystemScope actual){backend.incrementAndGet();return super.source(tool,actual);}
        };
        assertThat(validator.validate(plan(step()),expired,resolver).rejectReason()).isEqualTo(com.nori.ai.gateway.core.plan.RejectReason.SYSTEM_SCOPE_MISMATCH);
        var traces=new ArrayList<ExecutionContext.DispatchTrace>();
        assertThat(dispatcher.dispatch(step(),expired,new ExecutionContext(resolver,traces::add)).errorCode()).isEqualTo(com.nori.ai.gateway.core.result.ErrorCode.SYSTEM_SCOPE_MISMATCH);
        var mismatched=dispatcher.dispatch(step(),scope(OwnerSystem.WM,Set.of("EQP_READ")),new ExecutionContext(resolver,traces::add));
        assertThat(mismatched.status()).isEqualTo(com.nori.ai.gateway.core.result.ResultStatus.UNSUPPORTED);assertThat(mismatched.errorCode()).isNull();
        assertThat(traces).extracting(ExecutionContext.DispatchTrace::reason).containsExactly(com.nori.ai.gateway.core.plan.RejectReason.SYSTEM_SCOPE_MISMATCH,com.nori.ai.gateway.core.plan.RejectReason.OWNER_MISMATCH);
        assertThat(plans).hasValue(0);assertThat(dispatches).hasValue(0);assertThat(backend).hasValue(0);
    }
}
