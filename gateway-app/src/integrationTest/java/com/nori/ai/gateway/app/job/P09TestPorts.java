package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.seam.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@TestConfiguration(proxyBeanMethods=false)
public class P09TestPorts {
    public static final String PDG="a".repeat(64);
    public static final class Observations {
        public final AtomicInteger authorization=new AtomicInteger();
        public final List<JobContext> workflows=new CopyOnWriteArrayList<>();
        public volatile String gate="ALLOWED";
        public volatile BlockingCall blockingCall;
        public final AtomicInteger published=new AtomicInteger();
    }
    public static final class BlockingCall implements CancellationToken.InFlightCall {
        public final CountDownLatch entered=new CountDownLatch(1),allowExit=new CountDownLatch(1),ended=new CountDownLatch(1);
        public final AtomicBoolean aborted=new AtomicBoolean();
        public void start(){Thread.ofVirtual().start(()->{entered.countDown();try{allowExit.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{ended.countDown();}});}
        @Override public void abort(){aborted.set(true);}
        @Override public boolean awaitTermination(java.time.Duration timeout)throws InterruptedException{return ended.await(timeout.toMillis(),TimeUnit.MILLISECONDS);}
    }
    @Bean Observations p09Observations(){return new Observations();}
    @Bean TcSourceAuthorizationPort p09Authorization(Observations observations){return (call,required)->{
        if(!required.isEmpty())throw new AssertionError("실행 개시 인가는 빈 권한 집합이어야 합니다");
        observations.authorization.incrementAndGet();
        return switch(observations.gate){case "INVALID"->new TcSourceAuthorizationPort.DelegationInvalid();case "DOWN"->new TcSourceAuthorizationPort.Unavailable();case "BAD_PDG"->new TcSourceAuthorizationPort.Allowed(Set.of(),"bad");default->new TcSourceAuthorizationPort.Allowed(Set.of(),PDG);};
    };}
    @Bean WorkflowExecutor p09Workflow(Observations observations,Optional<PausePointRegistry> pauses){return job->{
        observations.workflows.add(job);
        if(observations.blockingCall!=null){var call=observations.blockingCall;job.cancellation().register(call);call.start();call.ended.await();job.cancellation().completed(call);}
        if(pauses.isPresent())pauses.get().hit(PausePointRegistry.Point.TOOL_IN_FLIGHT,job.jobId());
        var payload=new LinkedHashMap<String,Object>();payload.put("fixture",true);payload.put("requestPerm",job.scope().perm().stream().sorted().toList());payload.put("requestPdg",job.scope().pdg());payload.put("requestId",job.scope().requestId());payload.put("issuedAt",job.scope().issuedAt().toString());payload.put("expiresAt",job.scope().expiresAt().toString());
        payload.put("inputMessageId",job.requestInput().messageId());payload.put("inputJobSeq",job.requestInput().jobSeq());payload.put("inputQuestion",job.requestInput().maskedQuestion());payload.put("requestFields",job.requestInput().requestFields());
        return TerminalSpec.outcome(JobStatus.SUCCEEDED,ResultStatus.SUCCESS,null,payload);
    };}
    @TestConfiguration(proxyBeanMethods=false) @Profile("verify")
    @ConditionalOnProperty(name="ai.gateway.test-hooks.enabled",havingValue="true")
    public static class VerifySeams {
        @Bean PlanInjectionSeam p09PlanSeam(){return new PlanInjectionSeam.NoOp();}
        @Bean DispatchInjectionSeam p09DispatchSeam(){return new DispatchInjectionSeam.NoOp();}
    }
}
