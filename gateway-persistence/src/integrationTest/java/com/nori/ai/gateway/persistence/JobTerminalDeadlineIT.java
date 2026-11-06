package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.job.TerminalSpec;
import com.nori.ai.gateway.core.result.JobStatus;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.ActorRef;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.queue.JobEventPublisher;
import com.nori.ai.gateway.persistence.queue.JobTerminalCommitter;
import com.nori.ai.gateway.persistence.queue.QueueJobRepository;
import com.nori.ai.gateway.persistence.repository.AuditRepository;
import com.nori.ai.gateway.persistence.repository.ConversationRepository;
import com.nori.ai.gateway.persistence.repository.JobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class JobTerminalDeadlineIT extends PgIntegrationSupport {
    private static final Instant CREATED=Instant.parse("2026-11-06T00:00:00Z");
    // 순수 DB fixture의 명시 합성 접수 원점이다. 실제 계측 trace로 가장하지 않는다.
    private static final String ORIGIN_TRACE="0af7651916cd43dd8448eb211c80319c";
    static class AdmissionClock { @Bean Clock admissionClock(){return Clock.fixed(CREATED,ZoneOffset.UTC);} }

    private static VerifiedSystemScope scope(){
        return new VerifiedSystemScope(OwnerSystem.TC,"fixture",new ActorRef("actor"),"actor",Set.of(),"a".repeat(64),CREATED,CREATED.plusSeconds(60),UUID.randomUUID().toString());
    }
    /** 같은 저장소 API로 Job과 그 접수 감사 원점을 만든다. withOrigin=false는 원점 없는 종결 rollback 시험에서만 쓴다. */
    private static void job(ConfigurableApplicationContext context,VerifiedSystemScope scope,String conversation,String id,boolean withOrigin){
        assertThat(context.getBean(com.nori.ai.gateway.persistence.repository.MessageRepository.class).insert(scope,conversation,new com.nori.ai.gateway.persistence.repository.MessageRepository.Message("message-"+id,"user","기한 시험","[]","[]",null,null))).isTrue();
        assertThat(context.getBean(JobRepository.class).insert(scope,
            new JobRepository.NewJob(id,conversation,null,"digest","/v1/query","message-"+id,Map.of()),null)).isTrue();
        if(withOrigin)assertThat(context.getBean(AuditRepository.class).insert(scope,new AuditRepository.Event(scope.requestId(),conversation,id,ORIGIN_TRACE,"JOB_ACCEPTED",
            null,null,null,null,null,null,null,null,null))).isTrue();
    }
    private static JobTerminalCommitter committer(ConfigurableApplicationContext context,Clock clock,List<Object> published){
        var jdbc=context.getBean(JdbcClient.class);
        return new JobTerminalCommitter(jdbc,context.getBean(PlatformTransactionManager.class),clock,new ConversationExpiryGuard(),new JobEventPublisher(jdbc,clock,published::add),
            List.of(),Duration.ofSeconds(180),jobId->true);
    }
    private static List<Object> writes(JdbcClient jdbc,String id){
        return List.of(jdbc.sql("SELECT status FROM gw.job WHERE id=:id").param("id",id).query(String.class).single(),
            jdbc.sql("SELECT count(*) FROM gw.job_event WHERE job_id=:id").param("id",id).query(Integer.class).single(),
            jdbc.sql("SELECT count(*) FROM gw.result WHERE job_id=:id").param("id",id).query(Integer.class).single(),
            jdbc.sql("SELECT count(*) FROM gw.audit_event WHERE job_id=:id AND event_type='JOB_TERMINAL'").param("id",id).query(Integer.class).single());
    }

    @Test void rawClockRetainsExactAndSuccessorJobDeadlineBeforeTimestampStorage(){
        try(var database=rawDatabase()){
            database.bootstrap(false);database.migrate();
            try(var context=application(database,AdmissionClock.class)){
                var jdbc=context.getBean(JdbcClient.class);
                var manager=context.getBean(PlatformTransactionManager.class);
                var scope=scope();
                assertThat(context.getBean(ConversationRepository.class).insert(scope,"deadline-conversation")).isTrue();
                for(long delta:new long[]{0,1}){
                    String id="deadline-"+delta;
                    job(context,scope,"deadline-conversation",id,true);
                    Boolean started=new TransactionTemplate(manager).execute(tx->new QueueJobRepository(jdbc).start(id,CREATED));
                    assertThat(started).isTrue();
                    // 제어 시계의 microsecond 내림을 거치지 않는 실제 Clock 입력으로 정책 경계를 검사합니다.
                    Clock rawClock=Clock.fixed(CREATED.plusSeconds(180).plusNanos(delta),ZoneOffset.UTC);
                    var published=new ArrayList<Object>();
                    var committer=committer(context,rawClock,published);
                    assertThat(committer.commit(id,TerminalSpec.outcome(JobStatus.SUCCEEDED,ResultStatus.SUCCESS,null,Map.of("fixture",true)))).isTrue();
                    assertThat(jdbc.sql("SELECT status FROM gw.job WHERE id=:id").param("id",id).query(String.class).single())
                        .isEqualTo(delta==0?"SUCCEEDED":"TIMED_OUT");
                    assertThat(jdbc.sql("SELECT status FROM gw.result WHERE job_id=:id").param("id",id).query(String.class).single())
                        .isEqualTo(delta==0?"SUCCESS":"TIMEOUT");
                    // 종결 감사는 같은 Job 접수 원점의 request·trace를 쓰고 커밋된 Result 상태를 남깁니다.
                    assertThat(jdbc.sql("SELECT decision, request_id, trace_id, result_status, service_ref FROM gw.audit_event WHERE job_id=:id AND event_type='JOB_TERMINAL'").param("id",id).query().singleRow())
                        .containsEntry("decision",delta==0?"NORMAL":"JOB_TIMEOUT").containsEntry("request_id",scope.requestId()).containsEntry("trace_id",ORIGIN_TRACE)
                        .containsEntry("result_status",delta==0?"SUCCESS":"TIMEOUT").containsEntry("service_ref",null);
                    assertThat(jdbc.sql("SELECT terminal_at FROM gw.job WHERE id=:id").param("id",id)
                        .query((rs,row)->rs.getTimestamp(1).toInstant()).single()).isEqualTo(CREATED.plusSeconds(180));
                    // QueueJobRepository.start가 job.started 1행을 먼저 쓰므로 started+종결 2행입니다.
                    assertThat(jdbc.sql("SELECT count(*) FROM gw.job_event WHERE job_id=:id").param("id",id).query(Integer.class).single()).isEqualTo(2);
                    assertThat(published).hasSize(2);
                }
            }
        }
    }

    @Test void expiredQueuedCommitWritesOnlyForAStillQueuedExpiredJobAndMissingOriginRollsBack(){
        try(var database=rawDatabase()){
            database.bootstrap(false);database.migrate();
            try(var context=application(database,AdmissionClock.class)){
                var jdbc=context.getBean(JdbcClient.class);
                var manager=context.getBean(PlatformTransactionManager.class);
                var scope=scope();
                assertThat(context.getBean(ConversationRepository.class).insert(scope,"expiry-conversation")).isTrue();
                Instant expiresAt=jdbc.sql("SELECT expires_at FROM gw.conversation WHERE id='expiry-conversation'").query((rs,row)->rs.getTimestamp(1).toInstant()).single();
                job(context,scope,"expiry-conversation","queued",true);
                job(context,scope,"expiry-conversation","running",true);
                job(context,scope,"expiry-conversation","no-origin",false);
                Boolean started=new TransactionTemplate(manager).execute(tx->new QueueJobRepository(jdbc).start("running",CREATED));assertThat(started).isTrue();
                var published=new ArrayList<Object>();

                // 실제 만료 전 QUEUED와 Dispatcher가 먼저 RUNNING으로 바꾼 행은 쓰기 없이 false입니다.
                var live=committer(context,Clock.fixed(expiresAt.minusNanos(1),ZoneOffset.UTC),published);
                assertThat(live.commitExpiredQueued("queued")).isFalse();
                assertThat(writes(jdbc,"queued")).containsExactly("QUEUED",0,0,0);
                var expired=committer(context,Clock.fixed(expiresAt,ZoneOffset.UTC),published);
                assertThat(expired.commitExpiredQueued("running")).isFalse();
                // start의 job.started 1행 외 종결 쓰기는 없습니다.
                assertThat(writes(jdbc,"running")).containsExactly("RUNNING",1,0,0);
                assertThat(published).isEmpty();

                assertThat(expired.commitExpiredQueued("queued")).isTrue();
                assertThat(jdbc.sql("SELECT status, error_code, result_ref, delegation_id FROM gw.job WHERE id='queued'").query().singleRow())
                    .containsEntry("status","CANCELLED").containsEntry("error_code",null).containsEntry("result_ref",null).containsEntry("delegation_id",null);
                assertThat(jdbc.sql("SELECT type, external_suppressed FROM gw.job_event WHERE job_id='queued' ORDER BY id DESC LIMIT 1").query().singleRow())
                    .containsEntry("type","job.cancelled").containsEntry("external_suppressed",true);
                assertThat(writes(jdbc,"queued")).containsExactly("CANCELLED",1,0,1);
                assertThat(jdbc.sql("SELECT decision FROM gw.audit_event WHERE job_id='queued' AND event_type='JOB_TERMINAL'").query(String.class).single()).isEqualTo("EXPIRY_EDGE");
                assertThat(expired.commitExpiredQueued("queued")).isFalse();
                assertThat(writes(jdbc,"queued")).containsExactly("CANCELLED",1,0,1);

                // 접수 감사 원점이 없으면 종결 전체가 되돌아갑니다.
                assertThatThrownBy(()->expired.commitExpiredQueued("no-origin")).isInstanceOf(IllegalStateException.class);
                assertThat(writes(jdbc,"no-origin")).containsExactly("QUEUED",0,0,0);
            }
        }
    }

    @Test void duplicateAcceptedOriginsStoreNoJobAuditAndRollBackTheTerminalWhileExactlyOneOriginStores(){
        try(var database=rawDatabase()){
            database.bootstrap(false);database.migrate();
            try(var context=application(database,AdmissionClock.class)){
                var jdbc=context.getBean(JdbcClient.class);
                var scope=scope();
                assertThat(context.getBean(ConversationRepository.class).insert(scope,"origin-conversation")).isTrue();
                job(context,scope,"origin-conversation","one",true);
                job(context,scope,"origin-conversation","two",true);
                // 정상 접수는 원점 1행이지만 DB 제약은 없으므로 같은 Job에 두 번째 원점을 직접 둡니다.
                assertThat(context.getBean(AuditRepository.class).insert(scope,new AuditRepository.Event(scope.requestId(),"origin-conversation","two",ORIGIN_TRACE,"JOB_ACCEPTED",
                    null,null,null,null,null,null,null,null,null))).isTrue();
                var audit=context.getBean(AuditRepository.class);var writer=context.getBean(com.nori.ai.gateway.persistence.audit.AuditWriter.class);
                var classified=new AuditRepository.Event(null,"origin-conversation","two",null,"JOB_CLASSIFIED",null,null,null,null,null,null,null,null,null,"LIVE_QUERY");
                int before=jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single();
                assertThat(audit.insertForJob(scope,classified)).isFalse();
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isEqualTo(before);
                // 이 단독 컨텍스트의 허용 목록은 conversationId·jobId가 UUID여야 하므로 같은 원점 판정은 저장소 결과로 확인하고, writer는 UUID Job으로 확인합니다.
                String conversation=UUID.randomUUID().toString(),jobId=UUID.randomUUID().toString();
                assertThat(context.getBean(ConversationRepository.class).insert(scope,conversation)).isTrue();
                job(context,scope,conversation,jobId,true);
                assertThat(audit.insert(scope,new AuditRepository.Event(scope.requestId(),conversation,jobId,ORIGIN_TRACE,"JOB_ACCEPTED",null,null,null,null,null,null,null,null,null))).isTrue();
                int beforeWriter=jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single();
                assertThatThrownBy(()->writer.writeForJob(com.nori.ai.gateway.core.audit.AuditEvent.jobClassified(scope,conversation,jobId,com.nori.ai.gateway.core.registry.TaskType.LIVE_QUERY)))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isEqualTo(beforeWriter);
                // 원점이 정확히 1행이면 그 원점의 request·trace로 저장합니다.
                assertThat(audit.insertForJob(scope,new AuditRepository.Event(null,"origin-conversation","one",null,"JOB_CLASSIFIED",null,null,null,null,null,null,null,null,null,"LIVE_QUERY"))).isTrue();
                assertThat(jdbc.sql("SELECT request_id, trace_id, service_ref, task_type FROM gw.audit_event WHERE job_id='one' AND event_type='JOB_CLASSIFIED'").query().singleRow())
                    .containsEntry("request_id",scope.requestId()).containsEntry("trace_id",ORIGIN_TRACE).containsEntry("service_ref",null).containsEntry("task_type","LIVE_QUERY");
                // 종결도 원점이 중복이면 같은 tx에서 되돌아갑니다.
                var committer=committer(context,Clock.fixed(CREATED.plusSeconds(1),ZoneOffset.UTC),new ArrayList<>());
                assertThatThrownBy(()->committer.commit("two",TerminalSpec.forCause(TerminalSpec.Cause.CANCELLED))).isInstanceOf(IllegalStateException.class);
                assertThat(writes(jdbc,"two")).containsExactly("QUEUED",0,0,0);
            }
        }
    }
}
