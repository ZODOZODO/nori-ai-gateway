package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.core.dispatch.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

@Tag("stage-F1")
@Tag("label-MOCK_API")
class DispatcherOwnerCheckTest {
    @Test @Tag("AC-21-TOOL") void checksActualToolOwnerAfterValidRegistryAndPlanPath() { boundary(true); }
    @Test @Tag("AC-21-TOOL") void checksActualResourceOwnerAfterValidRegistryAndPlanPath() { boundary(false); }
    private void boundary(boolean injectTool) {
        var versions=new ContractVersionPolicy();var registry=registry(manifest(),versions);var resolver=new Resolver();
        assertThat(registry.view(scope()).find(ID)).isPresent();
        var validator=validator(registry,new PlanInjectionSeam.NoOp());
        assertThat(validator.validate(plan(step()),scope(),resolver).accepted()).as("Dispatcher 경계 시험의 선행 Fixture가 유효해야 합니다").isTrue();
        AtomicInteger calls=new AtomicInteger();List<ExecutionContext.DispatchTrace> trace=new ArrayList<>();
        DispatchInjectionSeam seam=target -> injectTool
            ? new DispatchTarget(otherOwner(target.tool()),target.resources(),target.source())
            : new DispatchTarget(target.tool(),List.of(new ResourceResolver.ResourceState(OwnerSystem.WM,"EQP",true)),target.source());
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),seam,List.of(executor(calls)),CLOCK);
        var result=dispatcher.dispatch(step(),scope(),new ExecutionContext(resolver,trace::add));
        assertThat(calls).hasValue(0);assertThat(trace).singleElement().extracting(ExecutionContext.DispatchTrace::reason).isEqualTo(RejectReason.OWNER_MISMATCH);
        assertThat(result.ownerSystem()).isEqualTo(scope().boundSystem());assertThat(result.dataRef()).isNull();assertThat(result.evidence()).isEmpty();
    }
    @Test void executesValidSameSourceButRechecksCurrentAuthorizationAndFailsClosedWithoutExecutor() {
        var versions=new ContractVersionPolicy();var registry=registry(manifest(),versions);var resolver=new Resolver();var validator=validator(registry,new PlanInjectionSeam.NoOp());
        AtomicInteger calls=new AtomicInteger();var results=new ToolResultContractValidator(versions);
        var dispatcher=new ToolDispatcher(registry,validator,results,new DispatchInjectionSeam.NoOp(),List.of(executor(calls)),CLOCK);
        assertThat(dispatcher.dispatch(step(),scope(),new ExecutionContext(resolver,t -> {})).status()).isEqualTo(ResultStatus.SUCCESS);assertThat(calls).hasValue(1);
        resolver.permissions=Set.of();
        assertThat(dispatcher.dispatch(step(),scope(),new ExecutionContext(resolver,t -> {})).status()).isEqualTo(ResultStatus.DENIED);assertThat(calls).hasValue(1);
        resolver.permissions=scope().perm();
        var closed=new ToolDispatcher(registry,validator,results,new DispatchInjectionSeam.NoOp(),List.of(),CLOCK);
        assertThat(closed.dispatch(step(),scope(),new ExecutionContext(resolver,t -> {})).status()).isEqualTo(ResultStatus.UNSUPPORTED);assertThat(calls).hasValue(1);
    }
    @Test void overlongQAndQRefNeverReachExecutor() {
        var versions=new ContractVersionPolicy();var registry=registry(manifest(),versions);var resolver=new Resolver();var validator=validator(registry,new PlanInjectionSeam.NoOp());AtomicInteger calls=new AtomicInteger();
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),List.of(executor(calls)),CLOCK);
        var args=SchemaContracts.JSON.createObjectNode().put("eqp","EQP_001").put("q","😀".repeat(257));
        var out=dispatcher.dispatch(step(args),scope(),new ExecutionContext(resolver,t -> {}));
        assertThat(out.errorCode()).isEqualTo(ErrorCode.INPUT_BUDGET_EXCEEDED);assertThat(out.clarification().fields()).containsExactly("q");
        args.remove("q");args.put("qRef","Q1");
        assertThat(dispatcher.dispatch(step(args),scope(),new ExecutionContext(resolver,t -> {})).errorCode()).isEqualTo(ErrorCode.PLAN_INVALID);
        assertThat(calls).hasValue(0);
    }
}
