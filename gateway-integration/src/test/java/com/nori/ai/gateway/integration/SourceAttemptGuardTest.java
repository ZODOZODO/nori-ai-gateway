package com.nori.ai.gateway.integration;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.loki.*;
import com.nori.ai.gateway.integration.tempo.TempoTraceConnector;
import com.nori.ai.gateway.integration.gitlab.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.integration.ConnectorTestSupport.*;

class SourceAttemptGuardTest {
    @TempDir Path directory;
    private static final GitLabSourcePolicy.Repository REPO=new GitLabSourcePolicy.Repository(22,"nori-tc-gateway",Set.of("main"),Set.of("src/main"),"test-approval");
    private static final GitLabSourcePolicy.LineageEntry ENTRY=new GitLabSourcePolicy.LineageEntry("tc-gateway",11,"package","nori-tc-gateway",22,"SIBLING_CLONE_LOG","test-approval");
    private String source(String kind){return kind.equals("logs")?LokiSourcePolicy.SOURCE_REF:kind.equals("trace")?TempoTraceConnector.SOURCE_REF:GitLabSourcePolicy.SOURCE_REF;}
    private String empty(String kind){return kind.equals("logs")?emptyLoki():kind.equals("trace")?"{\"traces\":[]}":"[]";}
    private ResultStatus invoke(String kind,ConnectorTestSupport stub,SourceReadAccess access,SourceReadAccess.Request request) throws Exception {
        var window=new TimeWindow(NOW.minusSeconds(1),NOW);
        if(kind.equals("logs"))return new LokiLogConnector(stub.http("loki"),access,ignored->Optional.empty(),new LokiProperties(),CLOCK).search(request,new LokiLogConnector.LogQuery(window,List.of(),1000)).status();
        if(kind.equals("trace"))return new TempoTraceConnector(stub.http("tempo"),access).search(request,"tc-gateway",window).status();
        Path file=directory.resolve("test-read-token");Files.writeString(file,"local-test-read-token");var token=new GitLabReadTokenFile(file);var policy=new GitLabSourcePolicy(List.of(REPO),List.of(ENTRY));
        if(kind.equals("code"))return new GitLabCodeConnector(stub.http("gitlab"),access,policy,token).searchCode(request,"ERROR",new GitLabCodeConnector.CodeScope(REPO.sourceRepo(),"src/main"),"main").status();
        var value=new GitLabPipelineLineageReader(stub.http("gitlab"),access,policy,token).siblingCommits(request,ENTRY,7);
        return value.reason()==GitLabPipelineLineageReader.Reason.DENIED?ResultStatus.DENIED:value.reason()==GitLabPipelineLineageReader.Reason.SOURCE_UNAVAILABLE?ResultStatus.SOURCE_UNAVAILABLE:ResultStatus.EMPTY;
    }
    @Test void firstCheckAndRetryPreserveTheExactLifecycleExceptionEvenWhenScopeExpired() throws Exception {
        for(String kind:List.of("logs","trace","code","lineage"))for(boolean retry:List.of(false,true))try(var stub=new ConnectorTestSupport()) {
            var original=new IllegalStateException("중단 시험 원객체");var stopped=new AtomicBoolean(!retry);
            stub.responder=request->{stopped.set(true);return new ConnectorTestSupport.Response(500,"{}");};
            var base=request(source(kind),com.nori.ai.gateway.core.scope.OwnerSystem.TC,retry?NOW.plusSeconds(60):NOW);
            var request=new SourceReadAccess.Request(base.scope(),base.delegatedCall(),base.sourceAllowlistRef(),()->{if(stopped.get())throw original;});
            assertThatThrownBy(()->invoke(kind,stub,access(),request)).isSameAs(original);
            assertThat(stub.requests).hasSize(retry?1:0);
        }
    }
    @Test void denialEmptyPermissionsAndUnavailableStopThePhysicalRetryWithoutChangingFailureClass() throws Exception {
        for(String kind:List.of("logs","trace","code","lineage"))for(Outcome rejected:List.of(new DelegationInvalid(),new Allowed(Set.of(),"b".repeat(64)),new Unavailable()))try(var stub=new ConnectorTestSupport()) {
            var current=new AtomicReference<Outcome>();
            var access=access((call,required)->current.get()==null?new Allowed(required,"b".repeat(64)):current.get());
            stub.responder=request->{current.set(rejected);return new ConnectorTestSupport.Response(500,"{}");};
            assertThat(invoke(kind,stub,access,request(source(kind)))).isEqualTo(rejected instanceof Unavailable?ResultStatus.SOURCE_UNAVAILABLE:ResultStatus.DENIED);
            assertThat(stub.requests).hasSize(1);
        }
    }
    @Test void anAllowedRetryReallyPerformsTwoHttpRequestsAndErrorsEscapeUnchanged() throws Exception {
        for(String kind:List.of("logs","trace","code","lineage"))try(var stub=new ConnectorTestSupport()) {
            stub.responder=request->new ConnectorTestSupport.Response(stub.requests.size()==1?500:200,empty(kind));
            assertThat(invoke(kind,stub,access(),request(source(kind)))).isEqualTo(ResultStatus.EMPTY);
            assertThat(stub.requests).hasSize(kind.equals("logs")?5:2);
        }
        try(var stub=new ConnectorTestSupport()) {
            var original=new AssertionError("중단 시험 Error");var base=request(LokiSourcePolicy.SOURCE_REF);
            var request=new SourceReadAccess.Request(base.scope(),base.delegatedCall(),base.sourceAllowlistRef(),()->{throw original;});
            assertThatThrownBy(()->invoke("logs",stub,access(),request)).isSameAs(original);assertThat(stub.requests).isEmpty();
        }
    }
    @Test void timeSpentInTheGuardConsumesTheSameDeadlineBeforeTransportStarts() throws Exception {
        try(var stub=new ConnectorTestSupport()) {
            var nanos=new AtomicLong();var http=new SourceHttpClient(stub.base(),"loki",SourceObservability.noop(),nanos::get);var calls=new AtomicInteger();
            assertThatThrownBy(()->http.json(http.uri(List.of("test"),Map.of()),null,http.deadline(Duration.ofSeconds(1)),()->{calls.incrementAndGet();nanos.addAndGet(Duration.ofSeconds(1).toNanos());})).isInstanceOf(SourceHttpClient.Failure.class);
            assertThat(calls).hasValue(1);assertThat(stub.requests).isEmpty();
        }
    }
    @Test void lifecycleChangeDuringAuthorizationStopsTheVeryFirstHttpBeforeTransport() throws Exception {
        for(String kind:List.of("logs","trace","code","lineage"))try(var stub=new ConnectorTestSupport()) {
            var original=new IllegalStateException("인가 중 작업 종료");var ended=new AtomicBoolean();var authorizations=new AtomicInteger();var callbacks=new AtomicInteger();
            var access=access((call,required)->{authorizations.incrementAndGet();ended.set(true);return new Allowed(required,"b".repeat(64));});var base=request(source(kind));
            var request=new SourceReadAccess.Request(base.scope(),base.delegatedCall(),base.sourceAllowlistRef(),()->{callbacks.incrementAndGet();if(ended.get())throw original;});
            assertThatThrownBy(()->invoke(kind,stub,access,request)).isSameAs(original);assertThat(authorizations).hasValue(1);assertThat(callbacks).hasValue(2);assertThat(stub.requests).isEmpty();
        }
    }
}
