package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.port.JobRequestInputReader;
import com.nori.ai.gateway.persistence.repository.JobEventRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.*;

class ExpiryRaceIT extends ApiWebSupport {
    @Test @Tag("AC-24-RETAIN-EXPIRY") void pausedAsynchronousClosersCannotPermitResponseWriteOrNewCallAtExpiry() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD,"*");
        var accepted=accept("actor","expiry","질문",null,dlg());var expires=NOW.plusSeconds(1);
        jdbc().sql("UPDATE gw.conversation SET expires_at=:expiry WHERE id=:id").param("expiry",java.sql.Timestamp.from(expires)).param("id",accepted.conversationId()).update();
        clock().freezeAt(expires.minusMillis(1),false);
        var recording=new RecordingEmitter();var guard=context.getBean(ExpiryCheckpoint.class);var writer=new GuardedSseWriter(recording,expires,guard,0);
        assertThat(writer.heartbeat()).isTrue();assertThat(request("GET","/v1/analyses/"+accepted.jobId(),"actor",null,null,false).statusCode()).isEqualTo(200);
        var release=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.CountDownLatch(1);
        var scheduler=(java.util.concurrent.ScheduledExecutorService)org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(ExpiryEmitterCloser.class),"scheduler");
        scheduler.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var connect=java.net.http.HttpRequest.newBuilder(uri("/v1/analyses/"+accepted.jobId()+"/events")).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",signer.sign(claims("actor",null),clock().instant())).GET().build();
        var stream=java.net.http.HttpClient.newHttpClient().send(connect,java.net.http.HttpResponse.BodyHandlers.ofInputStream());assertThat(stream.statusCode()).isEqualTo(200);
        clock().freezeAt(expires,false);int before=recording.writes;
        assertThat(writer.send(accepted.jobId(),new JobEventRepository.Event(1,"tool.started","{}",false))).isFalse();assertThat(recording.writes).isEqualTo(before);assertThat(recording.closed).isTrue();
        for(String path:List.of("/v1/analyses/"+accepted.jobId(),"/v1/conversations/"+accepted.conversationId(),"/v1/analyses/"+accepted.jobId()+"/events","/v1/audit/jobs/"+accepted.jobId())) {
            var response=request("GET",path,"actor",null,null,false);assertThat(response.statusCode()).isEqualTo(404);assertThat(response.body()).isEmpty();
        }
        assertThat(context.getBean(JobRequestInputReader.class).findRequestInput(scope("actor"),accepted.jobId())).isEmpty();
        var token=new com.nori.ai.gateway.core.job.CancellationToken();var starts=new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(()->{guard.assertLive(expires,token);starts.incrementAndGet();}).isInstanceOf(com.nori.ai.gateway.core.expiry.ConversationExpiryGuard.ConversationExpired.class);
        assertThat(starts).hasValue(0);assertThat(token.reason()).contains(com.nori.ai.gateway.core.job.CancellationToken.Reason.EXPIRED);
        assertThat(observations().workflows).isEmpty();assertThat(calls().count("authorize")).isEqualTo(0);
        context.getBean(SseHub.class).heartbeat();
        try(var body=stream.body()){assertThat(new String(body.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).contains("job.queued").doesNotContain("heartbeat");}
        finally{release.countDown();}
    }
    static class RecordingEmitter extends SseEmitter {
        int writes;boolean closed;
        @Override public void send(SseEventBuilder builder) {writes++;}
        @Override public void complete() {closed=true;}
    }
}
