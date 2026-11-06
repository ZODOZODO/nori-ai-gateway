package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.persistence.queue.JobTerminalCommitter;
import com.nori.ai.gateway.persistence.repository.JobRepository;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobQueueIT extends P09QueueSupport {
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,WorkflowSourceFixture.class};}
    @Test void startedTransitionEventAndPublicationAreAtomicAndLosingOrRolledBackStartsStaySilent() throws Exception {
        var published=new CopyOnWriteArrayList<com.nori.ai.gateway.persistence.queue.JobEventPublisher.Published>();
        context.addApplicationListener(event->{if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload&&payload.getPayload() instanceof com.nori.ai.gateway.persistence.queue.JobEventPublisher.Published value)published.add(value);});
        var running=admit("actor","started",Set.of(PausePointRegistry.Point.BEFORE_CLASSIFICATION));await(()->pauses().reachedCount(PausePointRegistry.Point.BEFORE_CLASSIFICATION,running.jobId())==1);
        var times=jdbc().sql("SELECT j.started_at,e.created_at FROM gw.job j JOIN gw.job_event e ON e.job_id=j.id WHERE j.id=:job AND e.type='job.started'").param("job",running.jobId()).query().singleRow();
        assertThat(times.get("started_at")).isEqualTo(times.get("created_at"));assertThat(startedCount(running.jobId())).isOne();
        assertThat(published.stream().filter(e->e.jobId().equals(running.jobId())).map(com.nori.ai.gateway.persistence.queue.JobEventPublisher.Published::type)).containsExactly("job.queued","job.started");
        var queue=context.getBean(com.nori.ai.gateway.persistence.queue.QueueJobRepository.class);var tx=context.getBean(TransactionTemplate.class);
        assertThatThrownBy(()->queue.start(running.jobId(),NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(tx.<Boolean>execute(status->queue.start(running.jobId(),NOW))).isFalse();assertThat(startedCount(running.jobId())).isOne();
        var cancelled=admit("actor","cancel-before-start",Set.of());assertThat(context.getBean(JobCancellationService.class).cancel(scope("actor"),cancelled.jobId())).isTrue();
        assertThat(tx.<Boolean>execute(status->queue.start(cancelled.jobId(),NOW))).isFalse();assertThat(startedCount(cancelled.jobId())).isZero();
        var rollback=admit("actor","rollback-start",Set.of());
        tx.executeWithoutResult(status->{assertThat(queue.start(rollback.jobId(),NOW)).isTrue();status.setRollbackOnly();});
        assertThat(state(rollback.jobId())).isEqualTo("QUEUED");assertThat(startedCount(rollback.jobId())).isZero();
        assertThat(published.stream().filter(e->e.jobId().equals(rollback.jobId())&&e.type().equals("job.started"))).isEmpty();
        pauses().release(PausePointRegistry.Point.BEFORE_CLASSIFICATION,running.jobId());
    }
    private int startedCount(String job) {return jdbc().sql("SELECT count(*) FROM gw.job_event WHERE job_id=:job AND type='job.started'").param("job",job).query(Integer.class).single();}
    @Test @Tag("AC-28-FIFO") @Tag("AC-28-DEPTH") @Tag("AC-28-FULL") void fifoDepthAndFullQueue()throws Exception{
        var first=admit("actor","first",Set.of(PausePointRegistry.Point.BEFORE_CLASSIFICATION));await(()->state(first.jobId()).equals("RUNNING"));
        var ids=new ArrayList<String>();for(int i=0;i<4;i++)ids.add(admit("actor","q"+i,Set.of()).jobId());
        assertThat(context.getBean(com.nori.ai.gateway.persistence.queue.QueueJobRepository.class).count("QUEUED")).isEqualTo(4);
        assertThatThrownBy(()->accept("actor","overflow","질문",null,dlg())).isInstanceOfSatisfying(JobAdmissionService.Rejected.class,e->{assertThat(e.status()).isEqualTo(503);assertThat(e.error()).isEqualTo("QUEUE_FULL");assertThat(e.retryAfter()).isEqualTo(60);});
        assertThat(count("conversation")).isEqualTo(5);assertThat(count("job")).isEqualTo(5);
        pauses().release(PausePointRegistry.Point.BEFORE_CLASSIFICATION,first.jobId());await(()->ids.stream().allMatch(id->state(id).equals("SUCCEEDED")));
        assertThat(observations().workflows.stream().map(JobContext::jobId)).containsExactlyElementsOf(java.util.stream.Stream.concat(java.util.stream.Stream.of(first.jobId()),ids.stream()).toList());
    }
    @Test @Tag("AC-28-QTIMEOUT") void queuedSixtyIsAllowedSixtyOneFailsAndOneEightyOneStillQueueTimeout()throws Exception{
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");
        var first=admit("actor","sixty",Set.of());clock().advance(60);pauses().release(PausePointRegistry.Point.SWEEPER_HOLD,"*");
        context.getBean(JobTimeoutSweeper.class).sweepNow();assertThat(state(first.jobId())).isEqualTo("QUEUED");
        clock().advance(1);context.getBean(JobTimeoutSweeper.class).sweepNow();assertThat(state(first.jobId())).isEqualTo("FAILED");assertResult(first.jobId(),"FAILED","QUEUE_TIMEOUT","QUEUE_TIMEOUT");
        var second=admit("actor","late",Set.of());clock().advance(181);context.getBean(JobTimeoutSweeper.class).sweepNow();assertResult(second.jobId(),"FAILED","QUEUE_TIMEOUT","QUEUE_TIMEOUT");assertThat(calls().count("authorize")).isEqualTo(0);
    }
    @Test @Tag("AC-28-JOBLIMIT") @Tag("AC-28-SLOT") void exactLifetimeBoundaryAndOneSlotRelease()throws Exception{
        pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");var job=admit("actor","lifetime",Set.of(PausePointRegistry.Point.BEFORE_CLASSIFICATION));await(()->pauses().reachedCount(PausePointRegistry.Point.BEFORE_CLASSIFICATION,job.jobId())==1);
        clock().advance(180);pauses().release(PausePointRegistry.Point.SWEEPER_HOLD,"*");context.getBean(JobTimeoutSweeper.class).sweepNow();assertThat(state(job.jobId())).isEqualTo("RUNNING");
        clock().advance(1);context.getBean(JobTimeoutSweeper.class).sweepNow();await(()->state(job.jobId()).equals("TIMED_OUT"));assertResult(job.jobId(),"TIMEOUT",null,"JOB_TIMEOUT");
        var ledger=context.getBean(SlotLedger.class);await(()->ledger.activeCount()==0);assertThat(ledger.releaseCount()).isOne();assertThatThrownBy(()->ledger.release(job.jobId())).isInstanceOf(IllegalStateException.class);
        assertThat(context.getBean(JobTerminalCommitter.class).commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.JOB_TIMEOUT))).isFalse();assertThat(count("result")).isOne();
    }
    @Test @Tag("AC-28-JOBLIMIT-Q") void queueTimeIsPartOfWholeLifetime()throws Exception{
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");var job=admit("actor","combined",Set.of(PausePointRegistry.Point.BEFORE_CLASSIFICATION));
        clock().advance(50);pauses().release(PausePointRegistry.Point.DISPATCH_HOLD,"*");await(()->pauses().reachedCount(PausePointRegistry.Point.BEFORE_CLASSIFICATION,job.jobId())==1);
        clock().advance(140);pauses().release(PausePointRegistry.Point.SWEEPER_HOLD,"*");context.getBean(JobTimeoutSweeper.class).sweepNow();await(()->state(job.jobId()).equals("TIMED_OUT"));assertResult(job.jobId(),"TIMEOUT",null,"JOB_TIMEOUT");
    }
    @Test @Tag("AC-28-CANCEL") void queuedCancelAndRunningAbortWaitForActualCallTermination()throws Exception{
        var call=context.getBean(WorkflowSourceFixture.Server.class);call.blockNext();
        var running=incident("cancel-running",Set.of());assertThat(call.entered.await(10,TimeUnit.SECONDS)).isTrue();
        var queued=admit("actor","cancel-queued",Set.of());var cancel=context.getBean(JobCancellationService.class);
        assertThat(context.getBean(JobTerminalCommitter.class).commit(running.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.CANCELLED))).isFalse();
        assertThat(cancel.cancel(scope("other"),running.jobId())).isFalse();assertThat(cancel.cancel(scope("actor"),queued.jobId())).isTrue();assertThat(state(queued.jobId())).isEqualTo("CANCELLED");
        assertThat(jdbc().sql("SELECT cancel_requested_at IS NULL FROM gw.job WHERE id=:id").param("id",queued.jobId()).query(Boolean.class).single()).isTrue();
        assertThat(cancel.cancel(scope("actor"),running.jobId())).isTrue();await(()->context.getBean(SlotLedger.class).token(running.jobId()).orElseThrow().reason().isPresent());
        assertThat(state(running.jobId())).isEqualTo("RUNNING");assertThat(context.getBean(SlotLedger.class).activeCount()).isOne();assertThat(count("result")).isZero();
        call.release.countDown();await(()->state(running.jobId()).equals("CANCELLED"));assertThat(call.ended.getCount()).isZero();assertThat(call.logs).hasValue(1);await(()->context.getBean(SlotLedger.class).activeCount()==0);
        assertThat(jdbc().sql("SELECT count(*) FROM gw.job_event WHERE type='job.cancelled'").query(Integer.class).single()).isEqualTo(2);
        call.blockNext();var late=incident("late-cancel-signal",Set.of(PausePointRegistry.Point.TERMINAL_PRECOMMIT));
        assertThat(call.entered.await(10,TimeUnit.SECONDS)).isTrue();
        var requested=new CountDownLatch(1);var allowSignal=new CountDownLatch(1);
        var repository=spy(context.getBean(JobRepository.class));var ledger=context.getBean(SlotLedger.class);
        doAnswer(invocation->{
            var accepted=invocation.callRealMethod();requested.countDown();
            if(!allowSignal.await(15,TimeUnit.SECONDS))throw new IllegalStateException("시험 취소 신호 대기 한도를 넘었습니다");
            return accepted;
        }).when(repository).requestCancel(any(),eq(late.jobId()),eq("CANCELLED"));
        var delayed=new JobCancellationService(repository,context.getBean(JobTerminalCommitter.class),ledger);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var cancellation=executor.submit(()->delayed.cancel(scope("actor"),late.jobId()));
            try{
                assertThat(requested.await(10,TimeUnit.SECONDS)).isTrue();
                call.release.countDown();
                assertThat(pauses().awaitReached(PausePointRegistry.Point.TERMINAL_PRECOMMIT,late.jobId(),Duration.ofSeconds(10))).isTrue();
                assertThat(call.ended.getCount()).isZero();
                assertThat(ledger.terminated(late.jobId())).isTrue();
                allowSignal.countDown();assertThat(cancellation.get(10,TimeUnit.SECONDS)).isTrue();
                // 호출이 끝난 뒤 지연된 신호가 terminal tx를 중단시키지 않아야 합니다.
                assertThat(ledger.token(late.jobId()).orElseThrow().reason()).isEmpty();
            }finally{
                allowSignal.countDown();call.release.countDown();
                pauses().release(PausePointRegistry.Point.TERMINAL_PRECOMMIT,late.jobId());
            }
        }
        await(()->state(late.jobId()).equals("CANCELLED"));await(()->ledger.activeCount()==0);
        assertThat(ledger.releaseCount()).isEqualTo(2);assertThat(count("result")).isZero();assertThat(count("audit_event")).isEqualTo(3);
        assertThat(jdbc().sql("SELECT count(*) FROM gw.job_event WHERE job_id=:id AND type='job.cancelled'").param("id",late.jobId()).query(Integer.class).single()).isOne();
    }
    @Test @Tag("AC-28-ATOMIC") void twoAdmissionsAtLastQueuePlaceProduceExactlyOneAcceptance()throws Exception{
        var running=admit("actor","running",Set.of(PausePointRegistry.Point.BEFORE_CLASSIFICATION));await(()->state(running.jobId()).equals("RUNNING"));for(int i=0;i<3;i++)admit("actor","fill"+i,Set.of());
        var barrier=new CyclicBarrier(2);try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var tasks=new ArrayList<Future<Integer>>();for(String key:List.of("a","b")){hooks().arm("actor",key,new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false));tasks.add(executor.submit(()->{barrier.await();try{accept("actor",key,"질문",null,dlg());return 202;}catch(JobAdmissionService.Rejected e){return e.status();}}));}
            assertThat(List.of(tasks.get(0).get(),tasks.get(1).get())).containsExactlyInAnyOrder(202,503);
        }assertThat(count("job")).isEqualTo(5);assertThat(count("message")).isEqualTo(5);pauses().release(PausePointRegistry.Point.BEFORE_CLASSIFICATION,running.jobId());
    }
    @Test void startGateDenialUnavailabilityCatalogAndExpiryPreventAllClassification()throws Exception{
        for(String gate:List.of("INVALID","DOWN","CATALOG_DOWN")){
            context.getBean(com.nori.ai.gateway.tcmock.MockScenarioStore.class).select(gate.equals("INVALID")?"logout":gate.equals("DOWN")?"authz-down":"catalog-down");var job=admit("actor","gate"+gate,Set.of());await(()->state(job.jobId()).equals("FAILED"));assertResult(job.jobId(),gate.equals("INVALID")?"DENIED":"SOURCE_UNAVAILABLE",null,gate.equals("INVALID")?"START_GATE_DENIED":"START_GATE_UNAVAILABLE");
        }assertThat(observations().workflows).isEmpty();assertThat(calls().count("authorize")).isEqualTo(3);
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");var expired=admit("actor","expired",Set.of());jdbc().sql("UPDATE gw.conversation SET expires_at=:expiry WHERE id=:id").param("expiry",java.sql.Timestamp.from(NOW.plusSeconds(1))).param("id",expired.conversationId()).update();clock().advance(1);
        pauses().release(PausePointRegistry.Point.DISPATCH_HOLD,"*");await(()->state(expired.jobId()).equals("CANCELLED"));assertThat(calls().count("authorize")).isEqualTo(3);assertThat(jdbc().sql("SELECT count(*) FROM gw.result WHERE job_id=:id").param("id",expired.jobId()).query(Integer.class).single()).isZero();
        assertThat(jdbc().sql("SELECT external_suppressed FROM gw.job_event WHERE job_id=:id AND type='job.cancelled'").param("id",expired.jobId()).query(Boolean.class).single()).isTrue();
    }
    @Test void terminalRejectsOuterTransactionAndLosingRaceCreatesNoDuplicate()throws Exception{
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");var job=admit("actor","terminal-race",Set.of());var committer=context.getBean(JobTerminalCommitter.class);
        assertThatThrownBy(()->context.getBean(TransactionTemplate.class).execute(s->committer.commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.CANCELLED)))).isInstanceOf(IllegalStateException.class);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var one=pool.submit(()->committer.commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.CANCELLED)));var two=pool.submit(()->committer.commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.CANCELLED)));assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder(true,false);}
        assertThat(count("audit_event")).isOne();assertThat(count("job_event")).isEqualTo(2);
    }
    private void assertResult(String id,String status,String error,String decision){
        var row=jdbc().sql("SELECT r.status,r.error_code,a.decision FROM gw.result r JOIN gw.audit_event a ON a.job_id=r.job_id WHERE r.job_id=:id AND a.event_type='JOB_TERMINAL'").param("id",id).query().singleRow();
        assertThat(row.get("status")).isEqualTo(status);assertThat(row.get("error_code")).isEqualTo(error);assertThat(row.get("decision")).isEqualTo(decision);
        assertThat(jdbc().sql("SELECT delegation_id IS NULL FROM gw.job WHERE id=:id").param("id",id).query(Boolean.class).single()).isTrue();
    }
}
