package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.registry.TaskType;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HookArmingIT extends P09QueueSupport {
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,Observation.class};}
    @TestConfiguration(proxyBeanMethods=false) static class Observation{
        @Bean static BeanPostProcessor spyRegistry(){return new BeanPostProcessor(){@Override public Object postProcessAfterInitialization(Object bean,String name){return bean instanceof TestHookRegistry?spy(bean):bean;}};}
    }
    @Test void sameActorSeparateKeysBindOnceAndDoNotCrossJobs()throws Exception{
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        hooks().arm("actor","one",new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false));hooks().arm("actor","two",new TestHookRegistry.Scenario(TaskType.LIVE_QUERY,Set.of(),null,false));
        assertThatThrownBy(()->hooks().arm("actor","one",new TestHookRegistry.Scenario(TaskType.LIVE_QUERY,Set.of(),null,false))).isInstanceOf(TestHookRegistry.Conflict.class);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var a=pool.submit(()->accept("actor","one","질문",null,dlg()));var b=pool.submit(()->accept("actor","two","질문",null,dlg()));var one=a.get();var two=b.get();
            assertThat(hooks().binding(one.jobId()).orElseThrow().taskType()).isEqualTo(TaskType.GENERAL_QUERY);assertThat(hooks().binding(two.jobId()).orElseThrow().taskType()).isEqualTo(TaskType.LIVE_QUERY);
            int count=hooks().reservationCount();assertThat(accept("actor","one","질문",null,dlg()).jobId()).isEqualTo(one.jobId());assertThat(hooks().reservationCount()).isEqualTo(count);
            assertThatThrownBy(()->hooks().arm("actor","one",new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false))).isInstanceOf(TestHookRegistry.Conflict.class);
            assertThatThrownBy(()->accept("actor","one","변경",null,dlg())).isInstanceOf(JobAdmissionService.Rejected.class);assertThat(hooks().reservationCount()).isEqualTo(count);
            assertThat(hook("/__test/reset","POST","{}",hookToken).statusCode()).isEqualTo(409);
            pauses().release(PausePointRegistry.Point.DISPATCH_HOLD,"*");await(()->state(one.jobId()).equals("SUCCEEDED")&&state(two.jobId()).equals("SUCCEEDED"));
            assertThat(observations().workflows.stream().filter(j->j.jobId().equals(two.jobId())).findFirst().orElseThrow().classification()).containsEntry("taskType","LIVE_QUERY");
            assertThat(hook("/__test/reset","POST","{}",hookToken).statusCode()).isEqualTo(204);assertThat(hooks().binding(one.jobId())).isEmpty();assertThat(clock().mode()).isEqualTo(ControllableClock.Mode.SYSTEM);
        }
    }
    @Test void bindingExistsWhenPollerWinsBeforeAfterCommitWake()throws Exception{
        hooks().arm("actor","aftercommit",new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(PausePointRegistry.Point.ADMISSION_AFTER_COMMIT),null,false));
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var response=pool.submit(()->accept("actor","aftercommit","질문",null,dlg()));await(()->count("job")==1);
            String id=jdbc().sql("SELECT id FROM gw.job").query(String.class).single();await(()->pauses().reachedCount(PausePointRegistry.Point.ADMISSION_AFTER_COMMIT,id)==1);
            await(()->state(id).equals("SUCCEEDED"));assertThat(hooks().binding(id)).isPresent();assertThat(observations().workflows).singleElement().satisfies(j->assertThat(j.classification()).containsEntry("taskType","GENERAL_QUERY"));
            assertThat(response.isDone()).isFalse();pauses().release(PausePointRegistry.Point.ADMISSION_AFTER_COMMIT,id);assertThat(response.get().jobId()).isEqualTo(id);
        }
    }
    @Test void reservationRollsBackAndArmingTtlAndClockRulesAreEnforced()throws Exception{
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");hooks().arm("actor","rollback",new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false));
        var reserved=new CountDownLatch(1);var rollback=new CountDownLatch(1);
        doAnswer(invocation->{
            invocation.callRealMethod();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                @Override public void beforeCommit(boolean readOnly){
                    reserved.countDown();
                    try{if(!rollback.await(15,TimeUnit.SECONDS))throw new IllegalStateException("시험 rollback 정지점 한도를 넘었습니다");}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("시험 rollback 대기가 중단되었습니다");}
                    throw new DuplicateKeyException("시험 접수 실패");
                }
            });
            return null;
        }).when(hooks()).bindOnAdmission(eq("actor"),eq("rollback"),anyString());
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){
            var admission=pool.submit(()->accept("actor","rollback","질문",null,dlg()));
            try{assertThat(reserved.await(10,TimeUnit.SECONDS)).isTrue();assertThat(count("job")).isZero();assertThat(hook("/__test/reset","POST","{}",hookToken).statusCode()).isEqualTo(409);}
            finally{rollback.countDown();}
            assertThatThrownBy(admission::get).hasCauseInstanceOf(DuplicateKeyException.class);
        }
        assertThat(count("job")).isZero();assertThat(count("conversation")).isZero();assertThat(hooks().armed("actor","rollback")).isPresent();
        doCallRealMethod().when(hooks()).bindOnAdmission(eq("actor"),eq("rollback"),anyString());var accepted=accept("actor","rollback","질문",null,dlg());assertThat(hooks().binding(accepted.jobId())).isPresent();
        hooks().arm("other","expire",new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false));clock().advance(60);assertThat(hooks().armed("other","expire")).isEmpty();
        assertThatThrownBy(()->clock().freezeAt(NOW.minusSeconds(1),false)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->clock().advance(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
