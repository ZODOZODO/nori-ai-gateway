package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.core.workflow.StepBinding;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class ExecutionContextBindingTest {
    private CapabilityManifest tool(String id,ToolKind kind) {
        var value=(tools.jackson.databind.node.ObjectNode)SchemaContracts.JSON.valueToTree(manifest());value.put("toolId",id).put("capabilityId",id).put("toolKind",kind.name());return new ManifestLoader().decode(value);
    }
    @Test void exactAttemptTraceAncestorReachesGitAndDefensiveCopiesKeepInputsImmutable() {
        var fixture=new WorkflowAuthorizationPreparationTest.Fixture();var trace=tool("tc.test.trace",ToolKind.TRACE);var git=tool("tc.test.git",ToolKind.GIT);var versions=new ContractVersionPolicy();
        var registry=new CapabilityRegistry(List.of(trace,git),workflows(),Set.of(ImplementationPhase.PHASE_1),versions);var validator=validator(registry,new PlanInjectionSeam.NoOp());
        var traceStep=new PlanStep("trace",trace.capabilityId(),List.of(),step().resources(),step().arguments());var gitStep=new PlanStep("git",git.capabilityId(),List.of("trace"),step().resources(),step().arguments());
        var prepared=(PlanValidator.Prepared)validator.prepare(plan(traceStep,gitStep),fixture.scope,fixture.resolver);var attempt=prepared.newAttempt(gitStep,fixture.scope,StepAttempt.Purpose.DISPATCH);
        var json=SchemaContracts.JSON.createObjectNode().putArray("items");json.addObject().put("traceId","actual-trace");
        var value=SchemaContracts.JSON.createObjectNode().set("items",json);var binding=new StepBinding(OwnerSystem.TC,new OwnedReference(OwnerSystem.TC,"data://TC/tool/trace"),value);
        var mutable=new LinkedHashMap<String,StepBinding>();mutable.put("trace",binding);var inputs=new ExecutionContext.BoundInputs(attempt,mutable);mutable.clear();json.addObject().put("traceId","changed");
        ((tools.jackson.databind.node.ObjectNode)inputs.bindings().get("trace").value()).put("changed",true);
        assertThat(inputs.bindings().get("trace").value().path("items")).hasSize(1);assertThat(inputs.bindings().get("trace").value().has("changed")).isFalse();assertThatThrownBy(()->inputs.bindings().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(inputs.toString()).doesNotContain("actual-trace");assertThat(binding.toString()).doesNotContain("actual-trace");
        var calls=new AtomicInteger();ToolExecutor executor=new ToolExecutor(){public ToolKind toolKind(){return ToolKind.GIT;}public com.nori.ai.gateway.core.result.ToolResultEnvelope execute(PlanStep step,CapabilityManifest manifest,VerifiedSystemScope scope,ExecutionContext context){assertThat(context.boundInputs()).contains(inputs);calls.incrementAndGet();return result(manifest);}};
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),List.of(executor),CLOCK);
        assertThat(new ExecutionContext(fixture.resolver,ignored->{}).boundInputs()).isEmpty();
        int reads=fixture.reads.get();assertThatThrownBy(()->dispatcher.dispatch(gitStep,fixture.scope,new ExecutionContext(fixture.resolver,ignored->{},Optional.of(inputs)))).isInstanceOf(IllegalArgumentException.class);assertThat(fixture.reads).hasValue(reads);
        assertThat(dispatcher.dispatchCurrent(prepared,attempt,fixture.scope,fixture.grant(attempt),new ExecutionContext(fixture.resolver,ignored->{},Optional.of(inputs)))).isInstanceOf(ToolDispatcher.Returned.class);assertThat(calls).hasValue(1);
    }
    @Test void missingCarrierWrongAttemptNonTraceAndForeignOwnerFailBeforeAnyResourceRead() {
        var fixture=new WorkflowAuthorizationPreparationTest.Fixture();var git=tool("tc.test.git",ToolKind.GIT);var versions=new ContractVersionPolicy();var registry=registry(git,versions);var validator=validator(registry,new PlanInjectionSeam.NoOp());var gitStep=new PlanStep("git",git.capabilityId(),List.of(),step().resources(),step().arguments());
        var prepared=(PlanValidator.Prepared)validator.prepare(plan(gitStep),fixture.scope,fixture.resolver);var count=new AtomicInteger();ToolExecutor tool=new ToolExecutor(){public ToolKind toolKind(){return ToolKind.GIT;}public com.nori.ai.gateway.core.result.ToolResultEnvelope execute(PlanStep step,CapabilityManifest manifest,VerifiedSystemScope scope,ExecutionContext context){count.incrementAndGet();return result(manifest);}};
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),List.of(tool),CLOCK);
        for(String invalid:List.of("missing","other-attempt","current-step","foreign-owner","nonancestor")) {
            var attempt=prepared.newAttempt(gitStep,fixture.scope,StepAttempt.Purpose.DISPATCH);var other=prepared.newAttempt(gitStep,fixture.scope,StepAttempt.Purpose.DISPATCH);
            var binding=new StepBinding(invalid.equals("foreign-owner")?OwnerSystem.WM:OwnerSystem.TC,new OwnedReference(invalid.equals("foreign-owner")?OwnerSystem.WM:OwnerSystem.TC,invalid.equals("foreign-owner")?"data://WM/trace":"data://TC/trace"),SchemaContracts.parse("{\"items\":[]}"));
            Optional<ExecutionContext.BoundInputs> inputs=invalid.equals("missing")?Optional.empty():Optional.of(new ExecutionContext.BoundInputs(invalid.equals("other-attempt")?other:attempt,invalid.equals("other-attempt")?Map.of():Map.of(invalid.equals("nonancestor")?"absent":"git",binding)));
            var grant=fixture.grant(attempt);int reads=fixture.reads.get();assertThatThrownBy(()->dispatcher.dispatchCurrent(prepared,attempt,fixture.scope,grant,new ExecutionContext(fixture.resolver,ignored->{},inputs))).isInstanceOf(IllegalArgumentException.class);assertThat(fixture.reads).hasValue(reads);
        }
        assertThat(count).hasValue(0);
    }
}
