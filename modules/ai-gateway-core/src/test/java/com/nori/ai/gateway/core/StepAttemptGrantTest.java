package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.result.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class StepAttemptGrantTest {
    @Test void receiptIsSingleUseAndCannotCrossAttemptPurposeOrPreparation() {
        var f=new WorkflowAuthorizationPreparationTest.Fixture();var prepared=f.prepared();var dispatcher=f.dispatcher(()->result(f.manifest));var attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);var grant=f.grant(attempt);
        var other=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);int reads=f.reads.get();
        assertThatThrownBy(()->dispatcher.dispatchCurrent(prepared,other,f.scope,grant,new ExecutionContext(f.resolver,ignored->{}))).isInstanceOf(IllegalArgumentException.class);assertThat(f.reads).hasValue(reads);assertThat(f.toolCalls).hasValue(0);
        assertThatThrownBy(()->f.source.authorizeStep(attempt,f.scope,f.resolver.delegatedCall())).isInstanceOf(IllegalStateException.class);assertThat(f.authCalls).hasValue(1);
        var cacheAttempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);var cacheGrant=f.grant(cacheAttempt);
        assertThatThrownBy(()->dispatcher.dispatchCurrent(prepared,cacheAttempt,f.scope,cacheGrant,new ExecutionContext(f.resolver,ignored->{}))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->f.prepared().boundStep(other)).isInstanceOf(IllegalArgumentException.class);
        dispatcher.dispatchCurrent(prepared,attempt,f.scope,grant,new ExecutionContext(f.resolver,ignored->{}));reads=f.reads.get();
        assertThatThrownBy(()->dispatcher.dispatchCurrent(prepared,attempt,f.scope,grant,new ExecutionContext(f.resolver,ignored->{}))).isInstanceOf(IllegalArgumentException.class);assertThat(f.reads).hasValue(reads);assertThat(f.toolCalls).hasValue(1);
    }
    @Test void denialUnavailableMalformedCurrentDigestAndEmptyAllowedNeverReachExecutor() {
        for(var outcome:List.of(new TcSourceAuthorizationPort.Allowed(Set.of(),"b".repeat(64)),new TcSourceAuthorizationPort.Allowed(Set.of("EQP_READ"),"stale"),new TcSourceAuthorizationPort.Allowed(Set.of("OTHER"),"b".repeat(64)),new TcSourceAuthorizationPort.Unavailable())) {
            var f=new WorkflowAuthorizationPreparationTest.Fixture();f.outcome.set(outcome);var prepared=f.prepared();var attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);
            assertThat(f.source.authorizeStep(attempt,f.scope,f.resolver.delegatedCall())).isNotInstanceOf(SourceAuthorizationStep.StepGrant.class);assertThat(f.toolCalls).hasValue(0);assertThat(f.authCalls).hasValue(1);
        }
    }
    @Test void validFailedContractInvalidKeepsIdButRejectedRawUsesFallbackAndNoRawPayload() {
        var f=new WorkflowAuthorizationPreparationTest.Fixture();var valid=changeResult(result(f.manifest),"status",ResultStatus.FAILED);
        valid=changeResult(valid,"errorCode",ErrorCode.CONTRACT_INVALID);
        for(String field:List.of("dataRef"))valid=changeResult(valid,field,null);
        for(String field:List.of("evidenceRefs","blocks","claims","evidence","citations","composerInputs"))valid=changeResult(valid,field,List.of());
        var response=valid;var prepared=f.prepared();var attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);
        var returned=(ToolDispatcher.Returned)f.dispatcher(()->response).dispatchCurrent(prepared,attempt,f.scope,f.grant(attempt),new ExecutionContext(f.resolver,ignored->{}));
        assertThat(returned.invocation().orElseThrow().id()).isEqualTo("execution");assertThat(returned.invocation().orElseThrow().validatedResult()).isPresent();
        prepared=f.prepared();attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);
        var invalid=changeResult(result(f.manifest),"schemaDigest","0".repeat(64));returned=(ToolDispatcher.Returned)f.dispatcher(()->invalid).dispatchCurrent(prepared,attempt,f.scope,f.grant(attempt),new ExecutionContext(f.resolver,ignored->{}));
        assertThat(returned.invocation().orElseThrow().id()).isNotEqualTo("execution");assertThat(returned.invocation().orElseThrow().validatedResult()).isEmpty();
    }
    @Test void realRuntimeAndErrorArePreservedWithMinimalInvocationAndSuppression() {
        for(Throwable failure:List.of(new IllegalStateException("원 예외"),new AssertionError("원 오류"))) {
            var f=new WorkflowAuthorizationPreparationTest.Fixture();var prepared=f.prepared();var attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.DISPATCH);
            var threw=(ToolDispatcher.Threw)f.dispatcher(()->{if(failure instanceof RuntimeException runtime)throw runtime;throw (Error)failure;}).dispatchCurrent(prepared,attempt,f.scope,f.grant(attempt),new ExecutionContext(f.resolver,ignored->{}));
            assertThat(threw.invocation().validatedResult()).isEmpty();var secondary=new IllegalStateException("저장 실패");threw.suppress(secondary);
            assertThatThrownBy(threw::rethrow).isSameAs(failure);assertThat(failure.getSuppressed()).containsExactly(secondary);
        }
    }
}
