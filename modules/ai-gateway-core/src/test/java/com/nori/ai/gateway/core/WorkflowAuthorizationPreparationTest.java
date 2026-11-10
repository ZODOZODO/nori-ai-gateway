package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class WorkflowAuthorizationPreparationTest {
    static final class Fixture {
        final VerifiedSystemScope scope=new VerifiedSystemScope(OwnerSystem.TC,"service",new ActorRef("actor"),"actor",Set.of("EQP_READ"),"a".repeat(64),NOW.minusSeconds(60),NOW.plusSeconds(60),"request");
        final CapabilityManifest manifest=modify(manifest(),"toolKind","LOG");
        final ContractVersionPolicy versions=new ContractVersionPolicy();
        final CapabilityRegistry registry=registry(manifest,versions);
        final PlanValidator validator=validator(registry,new PlanInjectionSeam.NoOp());
        final AtomicInteger reads=new AtomicInteger(),legacy=new AtomicInteger(),authCalls=new AtomicInteger(),toolCalls=new AtomicInteger();
        final AtomicReference<WorkflowResourceResolver.Lookup> resource=new AtomicReference<>(new WorkflowResourceResolver.Found(new ResourceResolver.ResourceState(OwnerSystem.TC,"EQP",true)));
        final AtomicReference<TcSourceAuthorizationPort.Outcome> outcome=new AtomicReference<>(new TcSourceAuthorizationPort.Allowed(Set.of("EQP_READ"),"b".repeat(64)));
        final AtomicReference<PlanStep> executed=new AtomicReference<>();
        final WorkflowResourceResolver resolver=new WorkflowResourceResolver(){
            public Lookup lookup(ResourceRef ref,VerifiedSystemScope scope){reads.incrementAndGet();return resource.get();}
            public SourceBinding source(CapabilityManifest m,VerifiedSystemScope scope){return new SourceBinding(m.ownerSystem(),m.sourceAllowlistRef(),true);}
            public CurrentAuthorization authorize(CapabilityManifest m,PlanStep step,VerifiedSystemScope scope){legacy.incrementAndGet();return WorkflowResourceResolver.super.authorize(m,step,scope);}
            public ZoneId timezone(VerifiedSystemScope scope){return ZoneOffset.UTC;}
            public TcSourceAuthorizationPort.DelegatedCall delegatedCall(){return new TcSourceAuthorizationPort.DelegatedCall("actor","d".repeat(22));}
            public void beforeRead(){}
        };
        final SourceAuthorizationStep source=new SourceAuthorizationStep(new SourcePermissionMap(Map.of(manifest.sourceAllowlistRef(),new SourcePermissionMap.Entry(Set.of("EQP_READ"),false))), (call,required)->{authCalls.incrementAndGet();return outcome.get();});
        ToolDispatcher dispatcher(java.util.function.Supplier<ToolResultEnvelope> response) {
            ToolExecutor tool=new ToolExecutor(){public ToolKind toolKind(){return ToolKind.LOG;}public ToolResultEnvelope execute(PlanStep step,CapabilityManifest m,VerifiedSystemScope s,ExecutionContext c){toolCalls.incrementAndGet();executed.set(step);return response.get();}};
            return new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),List.of(tool),CLOCK);
        }
        PlanValidator.Prepared prepared(){return (PlanValidator.Prepared)validator.prepare(plan(step()),scope,resolver);}
        SourceAuthorizationStep.StepGrant grant(StepAttempt attempt){return (SourceAuthorizationStep.StepGrant)source.authorizeStep(attempt,scope,resolver.delegatedCall());}
    }
    @Test void pendingNeverUsesLegacyAuthorizationAndCurrentDifferentDigestReachesRealExecutor() {
        var f=new Fixture();var prepared=f.prepared();assertThat(prepared.authorizationState()).isEqualTo(PlanValidator.AuthorizationState.PENDING);assertThat(f.legacy).hasValue(0);assertThat(f.authCalls).hasValue(0);
        var attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);var grant=f.grant(attempt);
        assertThat(grant.currentPdg()).isNotEqualTo(f.scope.pdg());assertThat(f.validator.authorizedStep(prepared,attempt,grant,f.scope)).isTrue();
        var outcome=f.dispatcher(()->result(f.manifest)).dispatchCurrent(prepared,attempt,f.scope,grant,new ExecutionContext(f.resolver,ignored->{}));
        assertThat(outcome).isInstanceOf(ToolDispatcher.Returned.class);assertThat(f.toolCalls).hasValue(1);assertThat(f.authCalls).hasValue(1);assertThat(f.legacy).hasValue(0);
    }
    @Test void typedReadDeniedUnavailableAndAbsenceRemainDistinctAndStaticRejectionHasNoStepAuthorization() {
        var f=new Fixture();f.resource.set(new WorkflowResourceResolver.Denied());assertThat(f.validator.prepare(plan(step()),f.scope,f.resolver)).isEqualTo(new PlanValidator.ReadRejected(ResultStatus.DENIED));
        f.resource.set(new WorkflowResourceResolver.Unavailable());assertThat(f.validator.prepare(plan(step()),f.scope,f.resolver)).isEqualTo(new PlanValidator.ReadRejected(ResultStatus.SOURCE_UNAVAILABLE));
        f.resource.set(new WorkflowResourceResolver.Absent());var absent=(PlanValidator.Rejected)f.validator.prepare(plan(step()),f.scope,f.resolver);assertThat(absent.outcome().errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(f.authCalls).hasValue(0);assertThat(f.toolCalls).hasValue(0);assertThat(absent.outcome().trace()).extracting(ValidationOutcome.Trace::step).doesNotContain(5);
    }
    @Test void runtimeQSnapshotReachesExecutorAndOriginalJsonMutationsCannotReplaceIt() {
        var f=new Fixture();var prepared=f.prepared();var args=(tools.jackson.databind.node.ObjectNode)step().arguments();args.put("q","SOCKET_ERROR");
        var bound=new PlanStep("s1",ID,List.of(),step().resources(),args);var attempt=prepared.newAttempt(bound,f.scope,StepAttempt.Purpose.DISPATCH);args.put("q","변조");((tools.jackson.databind.node.ObjectNode)bound.arguments()).put("q","다른 변조");
        f.dispatcher(()->result(f.manifest)).dispatchCurrent(prepared,attempt,f.scope,f.grant(attempt),new ExecutionContext(f.resolver,ignored->{}));
        assertThat(f.executed.get().arguments().get("q").asString()).isEqualTo("SOCKET_ERROR");
        var invalid=new PlanStep("s1",ID,List.of(),step().resources(),SchemaContracts.parse("{\"eqp\":\"OTHER\",\"q\":\"x\"}"));assertThatThrownBy(()->prepared.newAttempt(invalid,f.scope,StepAttempt.Purpose.DISPATCH)).isInstanceOf(IllegalArgumentException.class);
        var over=new PlanStep("s1",ID,List.of(),step().resources(),SchemaContracts.JSON.createObjectNode().put("eqp","EQP_001").put("q","x".repeat(257)));assertThatThrownBy(()->prepared.newAttempt(over,f.scope,StepAttempt.Purpose.DISPATCH)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void canonicalArgumentOrderHasSameDigestWhileChangedQHasDifferentDigest() {
        var f=new Fixture();var prepared=f.prepared();
        var a=prepared.newAttempt(step(SchemaContracts.parse("{\"q\":\"WM 으로 보낸 메시지\",\"eqp\":\"EQP_001\"}")),f.scope,StepAttempt.Purpose.DISPATCH);
        var b=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);
        var c=prepared.newAttempt(step(SchemaContracts.parse("{\"eqp\":\"EQP_001\",\"q\":\"다른 검색어\"}")),f.scope,StepAttempt.Purpose.DISPATCH);
        assertThat(a.binding().argumentsDigest()).isEqualTo(b.binding().argumentsDigest()).isNotEqualTo(c.binding().argumentsDigest());
    }
}
