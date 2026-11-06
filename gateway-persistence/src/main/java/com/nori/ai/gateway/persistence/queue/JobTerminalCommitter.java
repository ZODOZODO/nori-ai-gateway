package com.nori.ai.gateway.persistence.queue;

import com.nori.ai.gateway.core.audit.AuditEventType;
import com.nori.ai.gateway.core.job.TerminalSpec;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.result.JobStatus;
import com.nori.ai.gateway.persistence.repository.*;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

public final class JobTerminalCommitter {
    private final JdbcClient jdbc;
    private final PlatformTransactionManager manager;
    private final Clock clock;
    private final ConversationExpiryGuard expiry;
    private final JobEventPublisher publisher;
    private final List<TerminalCommitObserver> observers;
    private final Duration lifetime;
    private final java.util.function.Predicate<String> runningCallTerminated;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final EvidenceRepository evidence;
    private final ToolExecutionRepository executions;
    public JobTerminalCommitter(JdbcClient jdbc, PlatformTransactionManager manager, Clock clock,
            ConversationExpiryGuard expiry, JobEventPublisher publisher, List<TerminalCommitObserver> observers, Duration lifetime,
            java.util.function.Predicate<String> runningCallTerminated) {
        this(jdbc,manager,clock,expiry,publisher,observers,lifetime,runningCallTerminated,null,null,null,null);
    }
    public JobTerminalCommitter(JdbcClient jdbc,PlatformTransactionManager manager,Clock clock,
            ConversationExpiryGuard expiry,JobEventPublisher publisher,List<TerminalCommitObserver> observers,Duration lifetime,
            java.util.function.Predicate<String> runningCallTerminated,ConversationRepository conversations,MessageRepository messages,
            EvidenceRepository evidence,ToolExecutionRepository executions) {
        this.jdbc=jdbc;this.manager=manager;this.clock=clock;this.expiry=expiry;this.publisher=publisher;this.observers=List.copyOf(observers);this.lifetime=lifetime;this.runningCallTerminated=Objects.requireNonNull(runningCallTerminated);
        this.conversations=conversations;this.messages=messages;this.evidence=evidence;this.executions=executions;
    }
    public boolean commit(String jobId, TerminalSpec requested) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("외부 트랜잭션 안에서는 종결할 수 없습니다");
        try { return commitAttempt(jobId,requested); }
        catch (FinalWriteExpired expired) { return commitAttempt(jobId,TerminalSpec.forCause(TerminalSpec.Cause.EXPIRY_EDGE)); }
    }
    private boolean commitAttempt(String jobId,TerminalSpec requested) {
        long[] event = {0};
        boolean won = Boolean.TRUE.equals(new TransactionTemplate(manager).execute(tx -> {
            var rows = jdbc.sql("SELECT j.owner_system,j.actor_ref,j.conversation_id,c.expires_at,j.created_at,j.status,j.cancel_requested_at,j.request_message_id,j.seq FROM gw.job j JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system WHERE j.id=:id AND j.status IN ('QUEUED','RUNNING') AND (:queuedOnly=false OR j.status='QUEUED') FOR UPDATE OF j,c")
                .param("queuedOnly",requested.terminalCause()==TerminalSpec.Cause.QUEUE_TIMEOUT)
                .param("id",jobId).query((rs,n) -> new Object[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5).toInstant(),rs.getString(6),rs.getTimestamp(7),rs.getString(8),rs.getLong(9)}).list();
            if (rows.isEmpty()) return false;
            var row=rows.getFirst(); Instant policyNow=clock.instant();
            // 저장 정밀도로 내리기 전에 정책을 판정하여 180초 직후의 나노초 경계를 보존합니다.
            Instant now=policyNow.truncatedTo(ChronoUnit.MICROS);
            // QUEUED 취소 판단 뒤 디스패치가 먼저 이겼으면 진행 호출을 먼저 종료시킵니다.
            if(requested.terminalCause()==TerminalSpec.Cause.CANCELLED&&"RUNNING".equals(row[5])&&!runningCallTerminated.test(jobId))return false;
            TerminalSpec spec=requested;
            if(Set.of(TerminalSpec.Cause.NORMAL,TerminalSpec.Cause.START_GATE_DENIED,TerminalSpec.Cause.START_GATE_UNAVAILABLE).contains(spec.terminalCause())){
                if(row[6]!=null)spec=TerminalSpec.forCause(TerminalSpec.Cause.CANCELLED);
                else if("RUNNING".equals(row[5])&&policyNow.isAfter(((Instant)row[4]).plus(lifetime)))spec=TerminalSpec.forCause(TerminalSpec.Cause.JOB_TIMEOUT);
            }
            try { expiry.assertLive((Instant)row[3],policyNow); }
            catch (ConversationExpiryGuard.ConversationExpired expired) { spec=TerminalSpec.forCause(TerminalSpec.Cause.EXPIRY_EDGE); }
            var terminal=spec;
            var data=spec.terminalCause()==TerminalSpec.Cause.NORMAL?spec.commitData():Optional.<TerminalSpec.CommitData>empty();
            data.ifPresent(value->validateData(jobId,row,value));
            int updated=jdbc.sql("""
                UPDATE gw.job SET status=:status,terminal_at=:now,error_code=:error,result_ref=:result,delegation_id=NULL
                WHERE id=:id AND status IN ('QUEUED','RUNNING')
                """).param("status",spec.jobStatus().name()).param("now",Timestamp.from(now)).param("error",spec.errorCode()==null?null:spec.errorCode().name())
                .param("result",spec.resultStatus()==null?null:jobId).param("id",jobId).update();
            if (updated==0) return false;
            if (spec.resultStatus()!=null) jdbc.sql("INSERT INTO gw.result(job_id,owner_system,status,error_code,payload,result_source_deps,created_at) VALUES (:id,:owner,:status,:error,CAST(:payload AS jsonb),CAST(:deps AS jsonb),:now)")
                .param("id",jobId).param("owner",row[0]).param("status",spec.resultStatus().name()).param("error",spec.errorCode()==null?null:spec.errorCode().name())
                .param("payload",encode(spec.resultPayload())).param("deps",encode(spec.resultSourceDeps())).param("now",Timestamp.from(now)).update();
            data.ifPresent(value->storeData(jobId,row,value));
            event[0]=jdbc.sql("INSERT INTO gw.job_event(job_id,owner_system,type,payload,created_at,external_suppressed) VALUES (:id,:owner,:type,CAST(:payload AS jsonb),:now,:suppressed) RETURNING id")
                .param("id",jobId).param("owner",row[0]).param("type",spec.eventType())
                .param("payload",encode(eventPayload(jobId,spec))).param("now",Timestamp.from(now)).param("suppressed",spec.externalSuppressed()).query(Long.class).single();
            jdbc.sql("INSERT INTO gw.audit_event(recorded_at,owner_system,actor_ref,request_id,conversation_id,job_id,event_type,status,error_code,decision) VALUES (:now,:owner,:actor,:id,:conversation,:id,:event,:status,:error,:decision)")
                .param("now",Timestamp.from(now)).param("owner",row[0]).param("actor",row[1]).param("id",jobId).param("conversation",row[2])
                .param("event",AuditEventType.JOB_TERMINAL.name()).param("status",spec.jobStatus().name()).param("error",spec.errorCode()==null?null:spec.errorCode().name()).param("decision",spec.terminalCause().name()).update();
            for (var observer:observers) observer.beforeCommit(jobId);
            if(data.isPresent())assertFinalLive((Instant)row[3]);
            return true;
        }));
        // 직접 소유한 tx가 완전히 끝난 다음에만 발행과 슬롯 신호를 보냅니다.
        if (won) {
            try { for (var observer:observers) observer.afterCommit(jobId); publisher.publish(event[0]); }
            finally { publisher.terminalCommitted(jobId); }
        }
        return won;
    }
    private static final class FinalWriteExpired extends RuntimeException {}
    private void assertFinalLive(Instant expiresAt) {
        try { expiry.assertLive(expiresAt,clock.instant()); }
        catch (ConversationExpiryGuard.ConversationExpired expired) { throw new FinalWriteExpired(); }
    }
    private void validateData(String jobId,Object[] row,TerminalSpec.CommitData data) {
        assertFinalLive((Instant)row[3]);
        if(conversations==null||messages==null||evidence==null||executions==null)throw new IllegalStateException("최종 자료 저장 의존성이 없습니다");
        var scope=data.scope();
        if(!scope.validAt(clock.instant())) {assertFinalLive((Instant)row[3]);throw new IllegalArgumentException("최종 자료 인증 범위가 만료되었습니다");}
        if(!scope.boundSystem().name().equals(row[0])||!scope.actorRef().value().equals(row[1])
                ||!scope.requestId().equals(jobId)||!jobId.equals(data.jobId())||!row[2].equals(data.conversationId())
                ||!row[7].equals(data.requestMessageId())||((Long)row[8]).longValue()!=data.jobSeq())throw new IllegalArgumentException("최종 자료 결속이 다릅니다");
        boolean matched=jdbc.sql("SELECT EXISTS(SELECT 1 FROM gw.message WHERE id=:message AND conversation_id=:conversation AND owner_system=:owner AND role='user')")
            .param("message",data.requestMessageId()).param("conversation",data.conversationId()).param("owner",row[0]).query(Boolean.class).single();
        if(!matched)throw new IllegalArgumentException("요청 메시지 결속이 다릅니다");
        Set<String> ids=new HashSet<>();
        for(var stored:data.evidence()) {
            var value=stored.value();
            if(!ids.add(value.evidenceId())||value.ownerSystem()!=scope.boundSystem()||!scope.actorRef().equals(value.actorAccessScope())
                    ||value.sourceRef()==null||!value.sourceRef().belongsTo(scope.boundSystem())
                    ||stored.source().filter(source->source.ownerSystem()!=scope.boundSystem()).isPresent())throw new IllegalArgumentException("근거 결속이 다릅니다");
        }
        data.assistantMessage().ifPresent(message->{
            if(!jobId.equals(message.responseRef())||message.resourceRefs().stream().anyMatch(ref->ref.ref().startsWith("system://")&&!new com.nori.ai.gateway.core.scope.OwnedReference(scope.boundSystem(),ref.ref()).belongsTo(scope.boundSystem()))
                    ||new HashSet<>(message.toolExecutionRefs()).size()!=message.toolExecutionRefs().size())throw new IllegalArgumentException("응답 메시지 결속이 다릅니다");
            for(String id:message.toolExecutionRefs())if(executions.find(scope,jobId,id).isEmpty()) {assertFinalLive((Instant)row[3]);throw new IllegalArgumentException("실행 참조 결속이 다릅니다");}
        });
        assertFinalLive((Instant)row[3]);
    }
    private void stored(boolean stored,Instant expiresAt) {
        if(!stored){assertFinalLive(expiresAt);throw new IllegalStateException("최종 자료를 저장할 수 없습니다");}
    }
    private void storeData(String jobId,Object[] row,TerminalSpec.CommitData data) {
        var scope=data.scope();Instant expiresAt=(Instant)row[3];assertFinalLive(expiresAt);
        for(var item:data.evidence()) {
            var value=item.value();var source=item.source();
            stored(evidence.insert(scope,jobId,new EvidenceRepository.Entry(value.evidenceId(),source.map(com.nori.ai.gateway.core.result.EvidenceSourceRef::sourceRef).orElse(value.sourceRef().ref()),
                source.map(com.nori.ai.gateway.core.result.EvidenceSourceRef::chunkId).orElse(null),source.map(com.nori.ai.gateway.core.result.EvidenceSourceRef::expectedRevision).orElse(null),encode(value))),expiresAt);
        }
        data.assistantMessage().ifPresent(message->stored(messages.insert(scope,data.conversationId(),new MessageRepository.Message(message.messageId(),"assistant",message.contentMasked(),encode(message.resourceRefs()),encode(message.toolExecutionRefs()),message.responseRef(),null)),expiresAt));
        data.context().ifPresent(context->stored(conversations.saveContext(scope,data.conversationId(),encode(context)),expiresAt));
        data.summary().ifPresent(summary->stored(conversations.saveSummary(scope,data.conversationId(),encode(summary)),expiresAt));
        assertFinalLive(expiresAt);
    }
    private Map<String,Object> eventPayload(String jobId,TerminalSpec spec) {
        var payload=new LinkedHashMap<String,Object>();payload.put("jobId",jobId);payload.put("jobStatus",spec.jobStatus().name());
        if(spec.errorCode()!=null)payload.put("errorCode",spec.errorCode().name());return payload;
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("종결 자료를 직렬화할 수 없습니다"); }
    }
}
