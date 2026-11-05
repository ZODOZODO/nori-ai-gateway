package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.core.dispatch.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

@Tag("stage-F1")
@Tag("label-MOCK_API")
class PlanValidatorAvailabilityTest {
    @Test @Tag("AC-17-P") void plannedIsRejectedAtStepSix() { unavailable("availability",Availability.PLANNED,RejectReason.PLANNED); }
    @Test @Tag("AC-17-U") void unsupportedIsRejectedAtStepSix() { unavailable("availability",Availability.UNSUPPORTED,RejectReason.UNSUPPORTED); }
    @Test @Tag("AC-17-D") void disabledIsRejectedAtStepSix() { unavailable("availability",Availability.DISABLED,RejectReason.DISABLED); }
    @Test @Tag("AC-17-PHASE") void inactivePhaseIsRejectedAtStepSix() { unavailable("implementationPhase",ImplementationPhase.PHASE_2,RejectReason.PHASE_INACTIVE); }
    private void unavailable(String field, Object value, RejectReason expected) {
        var versions = new ContractVersionPolicy(); var registry = registry(manifest(),versions); var resolver = new Resolver();
        assertThat(validator(registry,new PlanInjectionSeam.NoOp()).validate(plan(step()),scope(),resolver).accepted()).isTrue();
        var injected = validator(registry,m -> modify(m,field,value));
        AtomicInteger calls = new AtomicInteger();
        var dispatcher = new ToolDispatcher(registry,injected,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),List.of(executor(calls)),CLOCK);
        ValidationOutcome outcome = injected.validate(plan(step()),scope(),resolver);
        if (outcome.accepted()) dispatcher.dispatch(step(),scope(),new ExecutionContext(resolver,t -> {}));
        assertThat(outcome.accepted()).isFalse();assertThat(outcome.rejectReason()).isEqualTo(expected);
        assertThat(outcome.trace()).extracting(ValidationOutcome.Trace::step).containsExactly(1,2,3,4,5,6);
        assertThat(outcome.trace().stream().filter(t -> t.step()==6)).singleElement().satisfies(t -> {
            assertThat(t.passed()).isFalse();assertThat(t.reason()).isEqualTo(expected);
        });
        assertThat(calls).hasValue(0);
    }
}
