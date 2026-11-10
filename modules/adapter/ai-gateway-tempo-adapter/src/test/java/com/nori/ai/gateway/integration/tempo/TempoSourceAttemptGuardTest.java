package com.nori.ai.gateway.integration.tempo;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.ConnectorTestSupport;
import com.nori.ai.gateway.integration.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

// 종전 SourceAttemptGuardTest 의 kind "trace" 부분(단언 그대로).
class TempoSourceAttemptGuardTest {
    private ResultStatus invoke(ConnectorTestSupport stub,SourceReadAccess access,SourceReadAccess.Request request) throws Exception {
        var window=new TimeWindow(NOW.minusSeconds(1),NOW);
        return new TempoTraceConnector(stub.http("tempo"),access).search(request,"tc-gateway",window).status();
    }
    @Test void firstCheckAndRetryPreserveTheExactLifecycleExceptionEvenWhenScopeExpired() throws Exception {
        for(boolean retry:List.of(false,true))try(var stub=new ConnectorTestSupport()) {
            var original=new IllegalStateException("중단 시험 원객체");var stopped=new AtomicBoolean(!retry);
            stub.responder=request->{stopped.set(true);return new ConnectorTestSupport.Response(500,"{}");};
            var base=request(TempoTraceConnector.SOURCE_REF,com.nori.ai.gateway.core.scope.OwnerSystem.TC,retry?NOW.plusSeconds(60):NOW);
            var request=new SourceReadAccess.Request(base.scope(),base.delegatedCall(),base.sourceAllowlistRef(),()->{if(stopped.get())throw original;});
            assertThatThrownBy(()->invoke(stub,access(),request)).isSameAs(original);
            assertThat(stub.requests).hasSize(retry?1:0);
        }
    }
    @Test void denialEmptyPermissionsAndUnavailableStopThePhysicalRetryWithoutChangingFailureClass() throws Exception {
        for(Outcome rejected:List.of(new DelegationInvalid(),new Allowed(Set.of(),"b".repeat(64)),new Unavailable()))try(var stub=new ConnectorTestSupport()) {
            var current=new AtomicReference<Outcome>();
            var access=access((call,required)->current.get()==null?new Allowed(required,"b".repeat(64)):current.get());
            stub.responder=request->{current.set(rejected);return new ConnectorTestSupport.Response(500,"{}");};
            assertThat(invoke(stub,access,request(TempoTraceConnector.SOURCE_REF))).isEqualTo(rejected instanceof Unavailable?ResultStatus.SOURCE_UNAVAILABLE:ResultStatus.DENIED);
            assertThat(stub.requests).hasSize(1);
        }
    }
    @Test void anAllowedRetryReallyPerformsTwoHttpRequestsAndErrorsEscapeUnchanged() throws Exception {
        try(var stub=new ConnectorTestSupport()) {
            stub.responder=request->new ConnectorTestSupport.Response(stub.requests.size()==1?500:200,"{\"traces\":[]}");
            assertThat(invoke(stub,access(),request(TempoTraceConnector.SOURCE_REF))).isEqualTo(ResultStatus.EMPTY);
            assertThat(stub.requests).hasSize(2);
        }
    }
    @Test void lifecycleChangeDuringAuthorizationStopsTheVeryFirstHttpBeforeTransport() throws Exception {
        try(var stub=new ConnectorTestSupport()) {
            var original=new IllegalStateException("인가 중 작업 종료");var ended=new AtomicBoolean();var authorizations=new AtomicInteger();var callbacks=new AtomicInteger();
            var access=access((call,required)->{authorizations.incrementAndGet();ended.set(true);return new Allowed(required,"b".repeat(64));});var base=request(TempoTraceConnector.SOURCE_REF);
            var request=new SourceReadAccess.Request(base.scope(),base.delegatedCall(),base.sourceAllowlistRef(),()->{callbacks.incrementAndGet();if(ended.get())throw original;});
            assertThatThrownBy(()->invoke(stub,access,request)).isSameAs(original);assertThat(authorizations).hasValue(1);assertThat(callbacks).hasValue(2);assertThat(stub.requests).isEmpty();
        }
    }
}
