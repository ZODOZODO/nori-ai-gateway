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
    @Test void expiredRunningSourceCallKeepsTerminalDelegationAndSlotUntilTheCallActuallyEndsThenCommitsOnce() throws Exception {
        pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");
        var canceller=context.getBean(com.nori.ai.gateway.app.ops.ExpiryEdgeJobCanceller.class);await(canceller::started);
        var server=context.getBean(WorkflowSourceFixture.Server.class);server.blockNext();var ledger=context.getBean(SlotLedger.class);int released=ledger.releaseCount();
        var accepted=incident("source-expiry",Set.of());assertThat(server.entered.await(10,TimeUnit.SECONDS)).isTrue();
        shorten(accepted);clock().advance(1);
        // 만료 경계 Job은 실행 중 호출에 신호만 보내며 실제 호출이 끝나기 전에는 종결·위임 삭제·슬롯 반납을 하지 않습니다.
        await(()->ledger.token(accepted.jobId()).orElseThrow().reason().equals(Optional.of(CancellationToken.Reason.EXPIRED)));
        Thread.sleep(1500);
        assertThat(state(accepted.jobId())).isEqualTo("RUNNING");assertThat(server.ended.getCount()).isOne();
        assertThat(jdbc().sql("SELECT delegation_id IS NOT NULL FROM gw.job WHERE id=:id").param("id",accepted.jobId()).query(Boolean.class).single()).isTrue();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.audit_event WHERE job_id=:id AND event_type='JOB_TERMINAL'").param("id",accepted.jobId()).query(Integer.class).single()).isZero();
        assertThat(ledger.activeCount()).isOne();assertThat(ledger.releaseCount()).isEqualTo(released);
        server.release.countDown();
        await(()->state(accepted.jobId()).equals("CANCELLED"));await(()->ledger.activeCount()==0);Thread.sleep(1500);
        assertThat(server.ended.getCount()).isZero();assertThat(ledger.releaseCount()).isEqualTo(released+1);
        assertThat(jdbc().sql("SELECT delegation_id IS NULL FROM gw.job WHERE id=:id").param("id",accepted.jobId()).query(Boolean.class).single()).isTrue();
        assertThat(jdbc().sql("SELECT decision FROM gw.audit_event WHERE job_id=:id AND event_type='JOB_TERMINAL'").param("id",accepted.jobId()).query(String.class).list()).containsExactly("EXPIRY_EDGE");
        assertThat(jdbc().sql("SELECT type FROM gw.job_event WHERE job_id=:id AND type IN ('job.completed','job.partial','job.failed','job.cancelled')").param("id",accepted.jobId()).query(String.class).list()).containsExactly("job.cancelled");
        // 만료로 끝난 실제 호출도 Tool 감사로 남습니다.
        assertThat(assertJobAuditLineage(accepted.jobId(),true)).contains("JOB_CLASSIFIED","TOOL_CALL");
    }
    @Test void toolCallAuditFailureOnTheThrewPathIsCountedAndLoggedWhileThePrimaryCancellationWins() throws Exception {
        try(var connection=database.connection("aigw_migrator");var statement=connection.createStatement()) {
            statement.execute("CREATE FUNCTION gw.reject_tool_call_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '시험 Tool 감사 거부'; END $$");
            // incident의 앞선 snapshot·commLogs Tool 감사는 정상 저장되고, 차단 대상인 logs Tool 감사만 거부합니다.
            statement.execute("CREATE TRIGGER reject_tool_call_audit BEFORE INSERT ON gw.audit_event FOR EACH ROW WHEN (NEW.event_type = 'TOOL_CALL' AND NEW.tool_id = 'tc.logs.search') EXECUTE FUNCTION gw.reject_tool_call_audit()");
        }
        var failures=context.getBean(io.micrometer.core.instrument.MeterRegistry.class).get(com.nori.ai.gateway.app.observability.MetricsCatalog.AUDIT_WRITE_FAILURES).counter();
        double before=failures.count();
        var captured=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();captured.start();
        var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);root.addAppender(captured);
        try {
            var server=context.getBean(WorkflowSourceFixture.Server.class);server.blockNext();int reads=server.logs.get();
            var accepted=incident("tool-audit-failure",Set.of());assertThat(server.entered.await(10,TimeUnit.SECONDS)).isTrue();
            context.getBean(SlotLedger.class).token(accepted.jobId()).orElseThrow().signal(CancellationToken.Reason.CANCELLED);
            server.release.countDown();
            // 실제 호출 종료 뒤 Threw 경로의 원 예외(취소)가 종결을 정하고, 거부된 Tool 감사는 suppressed로 붙어도 counter·고정 코드로 관측됩니다.
            await(()->state(accepted.jobId()).equals("CANCELLED"));
            // 다음 앱 읽기 직전 검사가 executor 안에서 취소 예외를 내므로 물리 읽기는 1회뿐입니다(Threw 경로).
            assertThat(server.logs).hasValue(reads+1);
            assertThat(failures.count()).isEqualTo(before+1);
            assertThat(captured.list.stream().filter(event->event.getLoggerName().equals("com.nori.ai.gateway.app.observability.AuditRecorder")).map(ch.qos.logback.classic.spi.ILoggingEvent::getMessage).toList())
                .containsExactly("AUDIT_WRITE_FAILED");
            assertThat(captured.list.stream().filter(event->"AUDIT_WRITE_FAILED".equals(event.getMessage()))).allSatisfy(event->{assertThat(event.getArgumentArray()).isNullOrEmpty();assertThat(event.getThrowableProxy()).isNull();});
            assertThat(assertJobAuditLineage(accepted.jobId(),true)).contains("TOOL_CALL");
            assertThat(jdbc().sql("SELECT tool_id FROM gw.audit_event WHERE job_id=:id AND event_type='TOOL_CALL' ORDER BY id").param("id",accepted.jobId()).query(String.class).list())
                .containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search");
            assertThat(jdbc().sql("SELECT decision FROM gw.audit_event WHERE job_id=:id AND event_type='JOB_TERMINAL'").param("id",accepted.jobId()).query(String.class).single()).isEqualTo("CANCELLED");
        } finally { root.detachAppender(captured); captured.stop(); }
    }
}
