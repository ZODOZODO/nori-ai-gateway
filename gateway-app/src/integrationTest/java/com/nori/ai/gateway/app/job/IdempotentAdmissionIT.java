package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdempotentAdmissionIT extends P09QueueSupport {
    @Test void duplicateConflictExpiredAndCrossIdentityAreDistinct()throws Exception{
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");String delegation=dlg();var first=accept("actor","same","질문",null,delegation);
        var before=counts();var again=accept("actor","same","질문",null,dlg());assertThat(again.jobId()).isEqualTo(first.jobId());assertThat(counts()).isEqualTo(before);
        assertThat(jdbc().sql("SELECT delegation_id FROM gw.job WHERE id=:id").param("id",first.jobId()).query(String.class).single().equals(delegation)).isTrue();
        assertThatThrownBy(()->accept("actor","same","다른 질문",null,registeredDlg("actor"))).isInstanceOfSatisfying(JobAdmissionService.Rejected.class,e->{assertThat(e.status()).isEqualTo(409);assertThat(e.error()).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");});assertThat(counts()).isEqualTo(before);
        assertThat(accept("other","same","질문",null,dlg()).jobId()).isNotEqualTo(first.jobId());
        var wm=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(com.nori.ai.gateway.core.scope.OwnerSystem.WM,"wm",new com.nori.ai.gateway.core.scope.ActorRef("actor"),"actor",Set.of(),P09TestPorts.PDG,NOW,NOW.plusSeconds(60),"wm-request");
        assertThat(context.getBean(JobAdmissionService.class).accept(wm,new JobAdmissionService.AdmissionRequest("/v1/query",null,"질문",Map.of(),"same",registeredDlg("actor"))).jobId()).isNotEqualTo(first.jobId());
        jdbc().sql("UPDATE gw.conversation SET expires_at=:expiry WHERE id=:id").param("expiry",java.sql.Timestamp.from(NOW.plusSeconds(1))).param("id",first.conversationId()).update();clock().advance(1);var expiredBefore=counts();
        assertThatThrownBy(()->accept("actor","same","질문",null,registeredDlg("actor"))).isInstanceOfSatisfying(JobAdmissionService.Rejected.class,e->assertThat(e.status()).isEqualTo(404));assertThat(counts()).isEqualTo(expiredBefore);
    }
    @Test void admitRaceAtQueueBoundary()throws Exception{
        var running=admit("actor","running",Set.of(PausePointRegistry.Point.TOOL_IN_FLIGHT));await(()->state(running.jobId()).equals("RUNNING"));for(int i=0;i<3;i++)admit("actor","fill"+i,Set.of());
        String point="actor:race";pauses().hold(PausePointRegistry.Point.ADMISSION_FAST_MISS,point);var before=counts();String firstDlg=dlg(),secondDlg=dlg();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var a=executor.submit(()->accept("actor","race","질문",null,firstDlg));var b=executor.submit(()->accept("actor","race","질문",null,secondDlg));
            await(()->pauses().reachedCount(PausePointRegistry.Point.ADMISSION_FAST_MISS,point)==2);pauses().release(PausePointRegistry.Point.ADMISSION_FAST_MISS,point);
            var accepted=a.get();assertThat(b.get().jobId()).isEqualTo(accepted.jobId());
            assertThat(count("job")-before.get(0)).isOne();assertThat(count("message")-before.get(1)).isOne();assertThat(count("job_event")-before.get(2)).isOne();
            assertThat(context.getBean(com.nori.ai.gateway.persistence.queue.QueueJobRepository.class).count("QUEUED")).isEqualTo(4);
            assertThat(context.getBean(JobAdmissionService.class).uniqueFallbackCount()).isZero();
            String committed=jdbc().sql("SELECT delegation_id FROM gw.job WHERE id=:id").param("id",accepted.jobId()).query(String.class).single();assertThat(Set.of(firstDlg,secondDlg).contains(committed)).isTrue();
            assertThat(accept("actor","race","질문",null,dlg()).jobId()).isEqualTo(accepted.jobId());
        }pauses().release(PausePointRegistry.Point.TOOL_IN_FLIGHT,running.jobId());
    }
    @Test void nullableContextRemainsImmutableAcrossIdempotentAdmission(){
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        var callerContext=new LinkedHashMap<String,Object>();
        callerContext.put("timezone","+09:00");
        callerContext.put("referenceTime","2026-10-04T10:00:00+09:00");
        callerContext.put("timeWindow",null);
        var request=new JobAdmissionService.AdmissionRequest("/v1/query",null,"질문",Map.of("context",callerContext),"nullable-context",registeredDlg("actor"));
        callerContext.put("timezone","UTC");callerContext.remove("timeWindow");
        assertThat((Map<String,Object>)request.requestFields().get("context")).containsEntry("timezone","+09:00").containsEntry("timeWindow",null);
        assertThatThrownBy(()->request.requestFields().put("timezone","UTC")).isInstanceOf(UnsupportedOperationException.class);
        var service=context.getBean(JobAdmissionService.class);
        var first=service.accept(scope("actor"),request);
        assertThat(state(first.jobId())).isEqualTo("QUEUED");
        var before=counts();
        var reorderedContext=new LinkedHashMap<String,Object>();
        reorderedContext.put("timeWindow",null);
        reorderedContext.put("referenceTime","2026-10-04T10:00:00+09:00");
        reorderedContext.put("timezone","+09:00");
        var replay=new JobAdmissionService.AdmissionRequest("/v1/query",null,"질문",Map.of("context",reorderedContext),"nullable-context",registeredDlg("actor"));
        assertThat(service.accept(scope("actor"),replay).jobId()).isEqualTo(first.jobId());
        assertThat(counts()).isEqualTo(before);
    }
    private List<Integer> counts(){return List.of(count("job"),count("message"),count("job_event"),count("conversation"),count("audit_event"));}
}
