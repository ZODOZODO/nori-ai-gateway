package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.app.security.ScopeAssembler;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.port.JobRequestInputReader;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.persistence.queue.*;
import com.nori.ai.gateway.persistence.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 실제 Tool 생산 자료의 마지막 쓰기만 독립적으로 경합·실패시킵니다. */
class WorkflowTerminalCommitIT extends P09QueueSupport {
    static final class CallFault { final java.util.concurrent.atomic.AtomicReference<Throwable> next=new java.util.concurrent.atomic.AtomicReference<>(); }
    static final class ClockTrip { volatile Thread thread; int calls; void arm(){calls=0;thread=Thread.currentThread();} }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) static class ClockConfiguration {
        @org.springframework.context.annotation.Bean ClockTrip terminalClockTrip(){return new ClockTrip();}
        @org.springframework.context.annotation.Bean CallFault actualBusinessCallFault(){return new CallFault();}
        @org.springframework.context.annotation.Bean static org.springframework.beans.factory.config.BeanPostProcessor failAfterActualBusinessCall(org.springframework.beans.factory.ObjectProvider<CallFault> faults){return new org.springframework.beans.factory.config.BeanPostProcessor(){
            public Object postProcessAfterInitialization(Object bean,String name){if(!(bean instanceof com.nori.ai.gateway.tcmock.MockTcBusinessReadAdapter original))return bean;var actual=spy(original);doAnswer(invocation->{var value=invocation.callRealMethod();var failure=faults.getObject().next.getAndSet(null);if(failure!=null)throw failure;return value;}).when(actual).getEqpSnapshot(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());return actual;}
        };}
        @org.springframework.context.annotation.Bean static org.springframework.beans.factory.config.BeanPostProcessor observeSharedClock(org.springframework.beans.factory.ObjectProvider<ClockTrip> trips){return new org.springframework.beans.factory.config.BeanPostProcessor(){
            public Object postProcessAfterInitialization(Object bean,String name){if(!(bean instanceof ControllableClock original))return bean;var actual=spy(original);doAnswer(invocation->{var trip=trips.getObject();if(trip.thread==Thread.currentThread()&&++trip.calls==2){trip.thread=null;actual.advance(1);}return invocation.callRealMethod();}).when(actual).instant();return actual;}
        };}
    }
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,ClockConfiguration.class};}
    private record Produced(JobAdmissionService.Accepted accepted,TerminalSpec spec) {}
    private record Prepared(JobAdmissionService.Accepted accepted,JobContext job) {}
    private Prepared prepare(String key) {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");
        hooks().arm("actor",key,new TestHookRegistry.Scenario(TaskType.LIVE_QUERY,Set.of(),null,false));
        String delegation=registeredDlg("actor");
        var accepted=context.getBean(JobAdmissionService.class).accept(scope("actor"),new JobAdmissionService.AdmissionRequest("/v1/query",null,"실제 저장 자료",workflowFields(),key,delegation));
        var queue=context.getBean(QueueJobRepository.class);context.getBean(TransactionTemplate.class).executeWithoutResult(tx->assertThat(queue.start(accepted.jobId(),clock().instant())).isTrue());
        var row=queue.nonterminal().stream().filter(value->value.jobId().equals(accepted.jobId())).findFirst().orElseThrow();var workerScope=context.getBean(ScopeAssembler.class).forWorker(row);
        var request=context.getBean(JobRequestInputReader.class).findRequestInput(workerScope,accepted.jobId()).orElseThrow();
        var job=new JobContext(accepted.jobId(),accepted.conversationId(),workerScope,delegation,row.conversationExpiresAt(),new CancellationToken(),InferenceMode.DISABLED,Map.of("taskType",TaskType.LIVE_QUERY.name()),request);
        return new Prepared(accepted,job);
    }
    private Produced produce(String key) throws Exception {
        var prepared=prepare(key);var spec=context.getBean(com.nori.ai.gateway.core.job.WorkflowExecutor.class).execute(prepared.job());
        assertThat(spec.commitData()).isPresent();assertThat(spec.commitData().orElseThrow().evidence()).isNotEmpty();return new Produced(prepared.accepted(),spec);
    }
    private JobTerminalCommitter committer(List<TerminalCommitObserver> observers) {
        return new JobTerminalCommitter(jdbc(),context.getBean(PlatformTransactionManager.class),clock(),context.getBean(ConversationExpiryGuard.class),context.getBean(JobEventPublisher.class),observers,Duration.ofSeconds(180),context.getBean(SlotLedger.class)::terminated,
            context.getBean(ConversationRepository.class),context.getBean(MessageRepository.class),context.getBean(EvidenceRepository.class),context.getBean(ToolExecutionRepository.class));
    }
    private void noFinalData(Produced value) {
        String job=value.accepted().jobId(),conversation=value.accepted().conversationId();
        for(String table:List.of("result","evidence","audit_event"))assertThat(jdbc().sql("SELECT count(*) FROM gw."+table+" WHERE job_id=:job").param("job",job).query(Integer.class).single()).as(table).isZero();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.message WHERE conversation_id=:conversation AND role='assistant'").param("conversation",conversation).query(Integer.class).single()).isZero();
        var scope=value.spec().commitData().orElseThrow().scope();var conversations=context.getBean(ConversationRepository.class);assertThat(conversations.findContext(scope,conversation)).isEmpty();assertThat(conversations.findSummary(scope,conversation)).isEmpty();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.job_event WHERE job_id=:job AND type IN ('job.completed','job.partial','job.failed','job.cancelled')").param("job",job).query(Integer.class).single()).isZero();
    }
    private TerminalSpec changed(Produced value,TerminalSpec.CommitData data){return value.spec().withCommitData(data);}
    private TerminalSpec.CommitData copy(TerminalSpec.CommitData data,String message,long seq,List<TerminalSpec.StoredEvidence> evidence,Optional<TerminalSpec.AssistantMessage> assistant){return new TerminalSpec.CommitData(data.scope(),data.jobId(),data.conversationId(),message,seq,evidence,assistant,data.context(),data.summary(),data.sourceDeps());}
    @Test void actualFinalBatchRollsBackOnTheExistingFaultInjectorAndAWinningRetryStoresItOnce() throws Exception {
        var value=produce("final-rollback");var fault=spy(context.getBean(FaultInjector.class));var original=new IllegalStateException("시험 종결 실패");
        doAnswer(invocation->{invocation.callRealMethod();throw original;}).when(fault).beforeCommit(value.accepted().jobId());
        var events=new ArrayList<JobEventPublisher.Published>();context.addApplicationListener(event->{if(event instanceof org.springframework.context.PayloadApplicationEvent<?> payload&&payload.getPayload() instanceof JobEventPublisher.Published published)events.add(published);});
        assertThatThrownBy(()->committer(List.of(fault)).commit(value.accepted().jobId(),value.spec())).isSameAs(original);noFinalData(value);assertThat(events).isEmpty();assertThat(state(value.accepted().jobId())).isEqualTo("RUNNING");
        assertThat(jdbc().sql("SELECT count(*) FROM gw.tool_execution WHERE job_id=:job").param("job",value.accepted().jobId()).query(Integer.class).single()).isOne();
        var actual=context.getBean(JobTerminalCommitter.class);assertThat(actual.commit(value.accepted().jobId(),value.spec())).isTrue();assertThat(actual.commit(value.accepted().jobId(),value.spec())).isFalse();
        assertThat(events).singleElement().satisfies(event->assertThat(event.type()).isEqualTo("job.completed"));assertThat(count("result")).isOne();assertThat(count("audit_event")).isOne();assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence WHERE job_id=:job").param("job",value.accepted().jobId()).query(Integer.class).single()).isEqualTo(value.spec().commitData().orElseThrow().evidence().size());
    }
    @Test void wrongMessageSequenceDuplicateEvidenceAndAnotherJobsActualToolReferenceAllRollBack() throws Exception {
        var value=produce("bindings");var other=produce("another-job");var data=value.spec().commitData().orElseThrow();var otherData=other.spec().commitData().orElseThrow();var assistant=data.assistantMessage().orElseThrow();
        var invalid=List.of(copy(data,otherData.requestMessageId(),data.jobSeq(),data.evidence(),data.assistantMessage()),copy(data,data.requestMessageId(),data.jobSeq()+1,data.evidence(),data.assistantMessage()),copy(data,data.requestMessageId(),data.jobSeq(),List.of(data.evidence().getFirst(),data.evidence().getFirst()),data.assistantMessage()),
            copy(data,data.requestMessageId(),data.jobSeq(),data.evidence(),Optional.of(new TerminalSpec.AssistantMessage(assistant.messageId(),assistant.contentMasked(),assistant.resourceRefs(),otherData.assistantMessage().orElseThrow().toolExecutionRefs(),assistant.responseRef()))));
        for(var forged:invalid){assertThatThrownBy(()->context.getBean(JobTerminalCommitter.class).commit(value.accepted().jobId(),changed(value,forged))).isInstanceOf(IllegalArgumentException.class);noFinalData(value);}
        var scope=data.scope();for(var wrongScope:List.of(new com.nori.ai.gateway.core.scope.VerifiedSystemScope(com.nori.ai.gateway.core.scope.OwnerSystem.WM,scope.authenticatedServiceRef(),scope.actorRef(),scope.sub(),scope.perm(),scope.pdg(),scope.issuedAt(),scope.expiresAt(),scope.requestId()),new com.nori.ai.gateway.core.scope.VerifiedSystemScope(scope.boundSystem(),scope.authenticatedServiceRef(),new com.nori.ai.gateway.core.scope.ActorRef("other"),"other",scope.perm(),scope.pdg(),scope.issuedAt(),scope.expiresAt(),scope.requestId()),otherData.scope())) {
            var forged=new TerminalSpec.CommitData(wrongScope,data.jobId(),data.conversationId(),data.requestMessageId(),data.jobSeq(),data.evidence(),data.assistantMessage(),data.context(),data.summary(),data.sourceDeps());assertThatThrownBy(()->context.getBean(JobTerminalCommitter.class).commit(value.accepted().jobId(),changed(value,forged))).isInstanceOf(IllegalArgumentException.class);noFinalData(value);
        }
        assertThatThrownBy(()->new JobTerminalCommitter(jdbc(),context.getBean(PlatformTransactionManager.class),clock(),context.getBean(ConversationExpiryGuard.class),context.getBean(JobEventPublisher.class),List.of(),Duration.ofSeconds(180),context.getBean(SlotLedger.class)::terminated).commit(value.accepted().jobId(),value.spec())).isInstanceOf(IllegalStateException.class);noFinalData(value);
        assertThat(context.getBean(JobTerminalCommitter.class).commit(other.accepted().jobId(),other.spec())).isTrue();var collision=copy(data,data.requestMessageId(),data.jobSeq(),List.of(otherData.evidence().getFirst()),data.assistantMessage());
        assertThatThrownBy(()->context.getBean(JobTerminalCommitter.class).commit(value.accepted().jobId(),changed(value,collision))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);noFinalData(value);assertThat(state(value.accepted().jobId())).isEqualTo("RUNNING");assertThat(state(other.accepted().jobId())).isEqualTo("SUCCEEDED");
    }
    @Test void expiryInsideTheFinalBatchRollsBackEveryNewRowAndCommitsOnlyOneSuppressedExpiryEvent() throws Exception {
        var value=produce("batch-expiry");jdbc().sql("UPDATE gw.conversation SET expires_at=:at WHERE id=:conversation").param("at",java.sql.Timestamp.from(NOW.plusSeconds(1))).param("conversation",value.accepted().conversationId()).update();
        var advance=new TerminalCommitObserver(){public void beforeCommit(String job){if(job.equals(value.accepted().jobId())&&state(job).equals("SUCCEEDED"))clock().advance(1);}public void afterCommit(String job){}};
        assertThat(committer(List.of(advance)).commit(value.accepted().jobId(),value.spec())).isTrue();assertThat(state(value.accepted().jobId())).isEqualTo("CANCELLED");
        assertThat(count("result")).isZero();assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence").query(Integer.class).single()).isZero();assertThat(jdbc().sql("SELECT count(*) FROM gw.message WHERE role='assistant'").query(Integer.class).single()).isZero();
        for(String table:List.of("context","conversation_summary"))assertThat(jdbc().sql("SELECT count(*) FROM gw."+table).query(Integer.class).single()).isZero();
        assertThat(jdbc().sql("SELECT decision FROM gw.audit_event WHERE job_id=:job").param("job",value.accepted().jobId()).query(String.class).single()).isEqualTo("EXPIRY_EDGE");assertThat(jdbc().sql("SELECT external_suppressed FROM gw.job_event WHERE job_id=:job AND type='job.cancelled'").param("job",value.accepted().jobId()).query(Boolean.class).single()).isTrue();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.tool_execution WHERE job_id=:job").param("job",value.accepted().jobId()).query(Integer.class).single()).isOne();
    }
    @Test void expiryBetweenTheLockedPolicyCheckAndValidateDataUsesExpiryEdgeInsteadOfABindingFailure() throws Exception {
        var value=produce("validation-expiry");jdbc().sql("UPDATE gw.conversation SET expires_at=:at WHERE id=:conversation").param("at",java.sql.Timestamp.from(clock().instant().plusSeconds(1))).param("conversation",value.accepted().conversationId()).update();
        var trip=context.getBean(ClockTrip.class);trip.arm();assertThat(context.getBean(JobTerminalCommitter.class).commit(value.accepted().jobId(),value.spec())).isTrue();assertThat(trip.thread).isNull();assertThat(state(value.accepted().jobId())).isEqualTo("CANCELLED");
        assertThat(count("result")).isZero();assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence").query(Integer.class).single()).isZero();assertThat(jdbc().sql("SELECT decision FROM gw.audit_event WHERE job_id=:job").param("job",value.accepted().jobId()).query(String.class).single()).isEqualTo("EXPIRY_EDGE");
    }
    @Test void actualBusinessReadRuntimeAndErrorAreRecordedMinimallyAndTheSameOriginalObjectIsRethrown() throws Exception {
        int count=0;for(Throwable original:List.of(new IllegalStateException("SENTINEL:runtime-call"),new AssertionError("SENTINEL:error-call"))) {
            var prepared=prepare("actual-call-failure-"+count++);context.getBean(CallFault.class).next.set(original);
            assertThatThrownBy(()->context.getBean(com.nori.ai.gateway.core.job.WorkflowExecutor.class).execute(prepared.job())).isSameAs(original);
            String raw=jdbc().sql("SELECT payload::text FROM gw.tool_execution WHERE job_id=:job").param("job",prepared.accepted().jobId()).query(String.class).single();var record=json.readTree(raw);assertThat(record.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("id","capabilityId","status");assertThat(record.path("status").asString()).isEqualTo("FAILED");assertThat(record.path("id").asString()).isNotBlank();assertThat(record.path("capabilityId").asString()).isEqualTo("tc.eqp.getOperationalSnapshot");assertThat(raw).doesNotContain("SENTINEL","IllegalStateException","AssertionError");assertThat(state(prepared.accepted().jobId())).isEqualTo("RUNNING");
        }
        assertThat(calls().count("getEqpSnapshot")).isEqualTo(2);assertThat(count("result")).isZero();assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence").query(Integer.class).single()).isZero();
    }
}
