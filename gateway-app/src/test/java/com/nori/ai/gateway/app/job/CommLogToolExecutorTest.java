package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.port.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.retention.*;
import com.nori.ai.gateway.core.scope.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommLogToolExecutorTest {
    private final Instant now=Instant.parse("2026-11-06T00:00:00Z");
    private final CapabilityManifest manifest=new ManifestLoader().loadClasspath("manifests/tc/index.json").stream().filter(m->m.capabilityId().equals("tc.commLogs.search")).findFirst().orElseThrow();
    private final TcBusinessReadPort business=mock(TcBusinessReadPort.class);
    private final WorkflowResourceResolver resolver=mock(WorkflowResourceResolver.class);
    private final VerifiedSystemScope scope=new VerifiedSystemScope(OwnerSystem.TC,"test",new ActorRef("actor"),"actor",Set.of("COMM_LOG_READ"),"a".repeat(64),now.minusSeconds(1),now.plusSeconds(60),"job");
    private final TcSourceAuthorizationPort.DelegatedCall call=new TcSourceAuthorizationPort.DelegatedCall("actor","delegation-test-value-valid");
    private final TimeWindow window=new TimeWindow(now.minusSeconds(1800).plusNanos(1),now.minusSeconds(1).plusNanos(9));
    private TcBusinessReadPort.TcReadResult<TcBusinessReadPort.CommLogData> value(ResultStatus status,boolean unverified) {
        var completeness=new TrimCompletenessEvaluator.KeyObservation(true,5000,5000,now.minusSeconds(600),1,true,true,List.of());
        var data=Set.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.PARTIAL).contains(status)?new TcBusinessReadPort.CommLogData(status==ResultStatus.EMPTY?List.of():List.of(new TcBusinessReadPort.CommLogEntry(window.startAt(),"IN","EVENT")),completeness):null;
        return new TcBusinessReadPort.TcReadResult<>(status,now,data,List.of(),unverified?Set.of(Warning.RETENTION_UNVERIFIED):Set.of(),new TcBusinessReadPort.RetentionMeta(List.of(),unverified?List.of(window):List.of()));
    }
    private ToolResultEnvelope run(ResultStatus first,ResultStatus second) {
        when(resolver.delegatedCall()).thenReturn(call);when(resolver.timezone(scope)).thenReturn(ZoneOffset.UTC);
        when(business.getCommLogs(call,"EQP-001","gateway",window)).thenReturn(value(first,true));
        when(business.getCommLogs(call,"EQP-001","business",window)).thenReturn(value(second,false));
        var args=SchemaContracts.JSON.createObjectNode().put("eqp","EQP-001").put("from",window.startAt().toString()).put("to",window.endAt().toString());
        return new BusinessToolExecutor(business,Clock.fixed(now,ZoneOffset.UTC)).execute(new PlanStep("commLogs",manifest.capabilityId(),List.of(),List.of(new ResourceRef("EQP","EQP-001")),args),manifest,scope,new ExecutionContext(resolver,t->{}));
    }
    @Test void exactTwoSourceCallsAndStrictSchemaKeepUnverifiedNanosecondsWithoutConfirmedMissing() {
        var result=run(ResultStatus.SUCCESS,ResultStatus.EMPTY);assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS);
        verify(business).getCommLogs(call,"EQP-001","gateway",window);verify(business).getCommLogs(call,"EQP-001","business",window);verifyNoMoreInteractions(business);
        var content=result.blocks().getFirst().content();assertThat(SchemaContracts.matches(manifest.outputSchema(),content)).isTrue();
        assertThat(content.path("items").get(0).path("retentionMeta").path("unverifiedRanges").get(0).path("startAt").asString()).isEqualTo(window.startAt().toString());
        assertThat(result.metadata().missingRanges()).isEmpty();assertThat(result.warnings()).contains(Warning.RETENTION_UNVERIFIED);
        var invalid=content.deepCopy();((tools.jackson.databind.node.ObjectNode)invalid.path("items").get(0)).put("unexpected",true);assertThat(SchemaContracts.matches(manifest.outputSchema(),invalid)).isFalse();
    }
    @Test void oneUnavailableSourceIsPartialAndNamesOnlyMissingSource() {
        var result=run(ResultStatus.SUCCESS,ResultStatus.SOURCE_UNAVAILABLE);assertThat(result.status()).isEqualTo(ResultStatus.PARTIAL);assertThat(result.missingFields()).containsExactly("business");assertThat(result.blocks().getFirst().content().path("items").get(1).path("completeness").isNull()).isTrue();
    }
    @Test void denialAndContractFailureNeverExposeDataOrBecomeEmpty() {
        var versions=new ContractVersionPolicy();new CapabilityRegistry(List.of(manifest),List.of(),Set.of(ImplementationPhase.PHASE_1),versions);
        for(var status:List.of(ResultStatus.DENIED,ResultStatus.FAILED)) {var result=run(status,ResultStatus.SUCCESS);assertThat(result.status()).isEqualTo(status);assertThat(result.blocks()).isEmpty();if(status==ResultStatus.FAILED)assertThat(result.errorCode()).isEqualTo(ErrorCode.CONTRACT_INVALID);assertThat(new ToolResultContractValidator(versions).validate(result,manifest).status()).isEqualTo(status);}
    }
}
