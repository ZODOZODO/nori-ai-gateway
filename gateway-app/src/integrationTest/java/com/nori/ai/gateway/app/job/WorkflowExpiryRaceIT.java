package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.tcmock.MockTcBusinessReadAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class WorkflowExpiryRaceIT extends P09QueueSupport {
    static final class ReadObservation {
        final AtomicInteger gateway=new AtomicInteger(),business=new AtomicInteger();
        volatile Runnable afterGateway=()->{};
    }
    @TestConfiguration(proxyBeanMethods=false) static class ReadConfiguration {
        @Bean ReadObservation workflowReadObservation(){return new ReadObservation();}
        @Bean static BeanPostProcessor observeActualDlq(ObjectProvider<ReadObservation> observations){return new BeanPostProcessor(){
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(!(bean instanceof MockTcBusinessReadAdapter business))return bean;var actual=org.mockito.Mockito.spy(business);
                org.mockito.Mockito.doAnswer(invocation->{var result=invocation.callRealMethod();var observer=observations.getObject();if("gateway".equals(invocation.getArgument(2))){observer.gateway.incrementAndGet();observer.afterGateway.run();}else observer.business.incrementAndGet();return result;})
                    .when(actual).listDlq(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());return actual;
            }
        };}
    }
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,WorkflowSourceFixture.class,ReadConfiguration.class};}
    private JobAdmissionService.Accepted held(String key) throws Exception {
        pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");var accepted=incident(key,Set.of(PausePointRegistry.Point.BEFORE_CLASSIFICATION));
        assertThat(pauses().awaitReached(PausePointRegistry.Point.BEFORE_CLASSIFICATION,accepted.jobId(),Duration.ofSeconds(10))).isTrue();return accepted;
    }
    private void shorten(JobAdmissionService.Accepted job) {
        jdbc().sql("UPDATE gw.conversation SET expires_at=:at WHERE id=:conversation").param("at",java.sql.Timestamp.from(NOW.plusSeconds(1))).param("conversation",job.conversationId()).update();
        // Worker의 이미 읽은 대화 만료를 바꾸는 시험 대신 새 Worker가 정확 expiry를 읽도록 접수 전 hold를 사용합니다.
    }
    @Test void theNextPhysicalLokiReadStopsAtConversationExpiryWithoutASweeperOrFinalWrites() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");
        var accepted=incident("loki-expiry",Set.of());shorten(accepted);var server=context.getBean(WorkflowSourceFixture.Server.class);var first=new AtomicBoolean(true);
        server.afterStop=()->{if(first.compareAndSet(true,false))clock().advance(1);};pauses().release(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        await(()->state(accepted.jobId()).equals("CANCELLED"));assertThat(server.logs).hasValue(1);assertThat(server.traces).hasValue(0);
        assertThat(jdbc().sql("SELECT count(*) FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query(Integer.class).single()).isZero();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence WHERE job_id=:job").param("job",accepted.jobId()).query(Integer.class).single()).isZero();assertThat(count("cache_entry")).isZero();await(()->context.getBean(SlotLedger.class).activeCount()==0);
        assertThat(jdbc().sql("SELECT external_suppressed FROM gw.job_event WHERE job_id=:job AND type='job.cancelled'").param("job",accepted.jobId()).query(Boolean.class).single()).isTrue();
    }
    @Test void aGatewayDlqReadCannotStartTheBusinessReadAfterExpiry() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");var accepted=incident("dlq-expiry",Set.of());shorten(accepted);
        var observation=context.getBean(ReadObservation.class);observation.afterGateway=()->clock().advance(1);pauses().release(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        await(()->state(accepted.jobId()).equals("CANCELLED"));assertThat(observation.gateway).hasValue(1);assertThat(observation.business).hasValue(0);assertThat(calls().count("listDlq")).isOne();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query(Integer.class).single()).isZero();assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence WHERE job_id=:job").param("job",accepted.jobId()).query(Integer.class).single()).isZero();
    }
    @Test void cancellationAndTimeoutWaitForTheActualSourceCallAndPreventEveryLaterRead() throws Exception {
        for(var reason:List.of(CancellationToken.Reason.CANCELLED,CancellationToken.Reason.TIMEOUT)) {
            var server=context.getBean(WorkflowSourceFixture.Server.class);server.blockNext();int before=server.logs.get();var accepted=incident("source-stop-"+reason,Set.of());assertThat(server.entered.await(10,TimeUnit.SECONDS)).isTrue();
            var ledger=context.getBean(SlotLedger.class);var token=ledger.token(accepted.jobId()).orElseThrow();token.signal(reason);
            assertThat(state(accepted.jobId())).isEqualTo("RUNNING");assertThat(ledger.activeCount()).isOne();assertThat(server.ended.getCount()).isOne();
            server.release.countDown();await(()->state(accepted.jobId()).equals(reason==CancellationToken.Reason.CANCELLED?"CANCELLED":"TIMED_OUT"));await(()->ledger.activeCount()==0);
            assertThat(server.logs).hasValue(before+1);assertThat(server.traces).hasValue(0);assertThat(server.ended.getCount()).isZero();assertThat(count("cache_entry")).isZero();
        }
    }
}
