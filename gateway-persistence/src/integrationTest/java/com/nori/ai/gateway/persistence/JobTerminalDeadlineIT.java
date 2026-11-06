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
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class JobTerminalDeadlineIT extends PgIntegrationSupport {
    private static final Instant CREATED=Instant.parse("2026-11-06T00:00:00Z");
    static class AdmissionClock { @Bean Clock admissionClock(){return Clock.fixed(CREATED,ZoneOffset.UTC);} }

    @Test void rawClockRetainsExactAndSuccessorJobDeadlineBeforeTimestampStorage(){
        try(var database=rawDatabase()){
            database.bootstrap(false);database.migrate();
            try(var context=application(database,AdmissionClock.class)){
                var jdbc=context.getBean(JdbcClient.class);
                var manager=context.getBean(PlatformTransactionManager.class);
                var scope=new VerifiedSystemScope(OwnerSystem.TC,"fixture",new ActorRef("actor"),"actor",Set.of(),
                    "a".repeat(64),CREATED,CREATED.plusSeconds(60),"request");
                assertThat(context.getBean(ConversationRepository.class).insert(scope,"deadline-conversation")).isTrue();
                for(long delta:new long[]{0,1}){
                    String id="deadline-"+delta;
                    assertThat(context.getBean(com.nori.ai.gateway.persistence.repository.MessageRepository.class).insert(scope,"deadline-conversation",new com.nori.ai.gateway.persistence.repository.MessageRepository.Message("message-"+id,"user","기한 시험","[]","[]",null,null))).isTrue();
                    assertThat(context.getBean(JobRepository.class).insert(scope,
                        new JobRepository.NewJob(id,"deadline-conversation",null,"digest","/v1/query","message-"+id,Map.of()),null)).isTrue();
                    Boolean started=new TransactionTemplate(manager).execute(tx->new QueueJobRepository(jdbc).start(id,CREATED));
                    assertThat(started).isTrue();
                    // 제어 시계의 microsecond 내림을 거치지 않는 실제 Clock 입력으로 정책 경계를 검사합니다.
                    Clock rawClock=Clock.fixed(CREATED.plusSeconds(180).plusNanos(delta),ZoneOffset.UTC);
                    var published=new ArrayList<Object>();
                    var publisher=new JobEventPublisher(jdbc,rawClock,published::add);
                    var committer=new JobTerminalCommitter(jdbc,manager,rawClock,new ConversationExpiryGuard(),publisher,
                        List.of(),Duration.ofSeconds(180),jobId->true);
                    assertThat(committer.commit(id,TerminalSpec.outcome(JobStatus.SUCCEEDED,ResultStatus.SUCCESS,null,Map.of("fixture",true)))).isTrue();
                    assertThat(jdbc.sql("SELECT status FROM gw.job WHERE id=:id").param("id",id).query(String.class).single())
                        .isEqualTo(delta==0?"SUCCEEDED":"TIMED_OUT");
                    assertThat(jdbc.sql("SELECT status FROM gw.result WHERE job_id=:id").param("id",id).query(String.class).single())
                        .isEqualTo(delta==0?"SUCCESS":"TIMEOUT");
                    assertThat(jdbc.sql("SELECT decision FROM gw.audit_event WHERE job_id=:id").param("id",id).query(String.class).single())
                        .isEqualTo(delta==0?"NORMAL":"JOB_TIMEOUT");
                    assertThat(jdbc.sql("SELECT terminal_at FROM gw.job WHERE id=:id").param("id",id)
                        .query((rs,row)->rs.getTimestamp(1).toInstant()).single()).isEqualTo(CREATED.plusSeconds(180));
                    assertThat(jdbc.sql("SELECT count(*) FROM gw.job_event WHERE job_id=:id").param("id",id).query(Integer.class).single()).isOne();
                    assertThat(published).hasSize(2);
                }
            }
        }
    }
}
