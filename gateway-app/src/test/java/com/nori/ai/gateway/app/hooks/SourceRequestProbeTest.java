package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.mock.http.client.*;
import org.springframework.transaction.support.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class SourceRequestProbeTest {
    private final TestHookRegistry hooks=new TestHookRegistry(Clock.systemUTC(),new PausePointRegistry());
    private final SourceRequestProbe probe=new SourceRequestProbe(hooks);
    private void bind(String job){hooks.arm("actor",job,new TestHookRegistry.Scenario(TaskType.INCIDENT_ANALYSIS,Set.of(),null,false));TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);try{hooks.bindOnAdmission("actor",job,job);for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);}finally{TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}}
    private void request(String query,int status){String uri="http://127.0.0.1/loki/api/v1/query_range?query="+URLEncoder.encode(query,java.nio.charset.StandardCharsets.UTF_8)+"&start=1000000001&end=2000000002";var context=new ClientRequestObservationContext(new MockClientHttpRequest(HttpMethod.GET,URI.create(uri)));probe.onStart(context);context.setResponse(new MockClientHttpResponse(new byte[0],org.springframework.http.HttpStatus.valueOf(status)));probe.onStop(context);}
    private SourceRequestProbe.Observation observation(String job){return probe.snapshot(job).observations().getFirst();}
    private void terminalCommit(String job,int status){TransactionSynchronizationManager.initSynchronization();try{probe.beforeCommit(job);for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(status);}finally{TransactionSynchronizationManager.clearSynchronization();}}
    @Test void observationStaysRunningUntilTerminalCommitAndRolledBackTerminalDoesNotOpenIt(){var controller=new SourceObservationController(probe);bind("job");try(var scope=probe.open("job","tc.logs.search")){}
        assertThat(controller.read("job").getStatusCode().value()).isEqualTo(409);
        terminalCommit("job",TransactionSynchronization.STATUS_ROLLED_BACK);assertThat(probe.terminal("job")).isFalse();assertThat(controller.read("job").getStatusCode().value()).isEqualTo(409);
        terminalCommit("job",TransactionSynchronization.STATUS_COMMITTED);assertThat(controller.read("job").getStatusCode().value()).isEqualTo(200);assertThat(controller.read("job").getBody()).isEqualTo(probe.snapshot("job"));
        terminalCommit("unbound",TransactionSynchronization.STATUS_COMMITTED);assertThat(probe.terminal("unbound")).isFalse();assertThat(controller.read("unbound").getStatusCode().value()).isEqualTo(404);
        assertThat(hooks.resetIfIdle(()->true)).isTrue();assertThat(probe.terminal("job")).isFalse();assertThat(controller.read("job").getStatusCode().value()).isEqualTo(404);}
    @Test void completedZeroIsDifferentFromMissingAndIdleResetPreservesActiveProof(){bind("zero");try(var scope=probe.open("zero","tc.logs.search")){}assertThat(observation("zero").status()).isEqualTo("COMPLETE");assertThat(observation("zero").count()).isZero();assertThat(probe.snapshot("missing").status()).isEqualTo("MISSING");assertThat(hooks.resetIfIdle(()->false)).isFalse();assertThat(observation("zero").status()).isEqualTo("COMPLETE");assertThat(hooks.resetIfIdle(()->true)).isTrue();assertThat(probe.snapshot("zero").status()).isEqualTo("MISSING");}
    @Test void actualAttemptsIncludingRetryAndFailureAreCountedWithoutRawValues(){bind("attempts");try(var scope=probe.open("attempts","tc.logs.search")){request("{app=\"tc-gateway\", namespace=\"tc\"} |= \"EQP-SENTINEL\"",500);request("{app=\"tc-gateway\", namespace=\"tc\"} |= \"EQP-SENTINEL\"",200);}var value=observation("attempts");assertThat(value.status()).isEqualTo("COMPLETE");assertThat(value.count()).isEqualTo(2);assertThat(value.attempts()).extracting(SourceRequestProbe.Attempt::outcome).containsExactly("FAILED","SUCCESS");assertThat(value.attempts()).extracting(SourceRequestProbe.Attempt::digest).containsOnly(SourceRequestProbe.digest("tc-gateway","tc","1000000001","2000000002"));assertThat(SchemaContracts.JSON.writeValueAsString(probe.snapshot("attempts"))).doesNotContain("EQP-SENTINEL","127.0.0.1","1000000001","query_range");}
    @Test void unionNamespaceOnlyBadEscapeAndOutsideAppCannotBecomeAllowlist(){int index=0;for(String query:List.of("{app=\"tc-gateway\", namespace=\"tc\"} or {namespace=\"wm\"}","{namespace=\"tc\"}","{app=\"tc-gateway\", namespace=\"tc\"} |= \"\\x\"","{app=\"wm-ui-front\", namespace=\"tc\"}")){String job="bad-"+index++;bind(job);try(var scope=probe.open(job,"tc.logs.search")){request(query,200);}assertThat(observation(job).status()).isEqualTo("INVALID");}}
    @Test void threadScopeAndExceptionDoNotLeakToUnboundRequestOrNextJob()throws Exception {request("{app=\"tc-gateway\", namespace=\"tc\"}",200);bind("first");bind("next");try{try(var scope=probe.open("first","tc.logs.search")){try(var executor=Executors.newSingleThreadExecutor()){executor.submit(()->request("{app=\"tc-gateway\", namespace=\"tc\"}",200)).get();}throw new IllegalStateException("fixed");}}catch(IllegalStateException expected){}assertThat(observation("first").count()).isZero();try(var scope=probe.open("next","tc.logs.search")){request("{app=\"tc-gateway\", namespace=\"tc\"}",200);}assertThat(observation("next").count()).isOne();}
    @Test void boundedAttemptsAndJobsFailClosed(){bind("full");try(var scope=probe.open("full","tc.logs.search")){for(int i=0;i<=SourceRequestProbe.MAX_ATTEMPTS;i++)request("{app=\"tc-gateway\", namespace=\"tc\"}",200);}assertThat(observation("full").status()).isEqualTo("OVERFLOW");for(int i=1;i<SourceRequestProbe.MAX_JOBS;i++){String job="job-"+i;bind(job);try(var scope=probe.open(job,"tc.logs.search")){}}bind("overflow");try(var scope=probe.open("overflow","tc.logs.search")){}assertThat(probe.snapshot("overflow").status()).isEqualTo("OVERFLOW");}
}
