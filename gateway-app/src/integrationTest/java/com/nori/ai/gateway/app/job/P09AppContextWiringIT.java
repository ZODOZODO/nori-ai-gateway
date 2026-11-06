package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.AiGatewayApplication;
import com.nori.ai.gateway.app.config.*;
import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.app.job.recovery.*;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.persistence.queue.JobTerminalCommitter;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import static org.assertj.core.api.Assertions.*;

class P09AppContextWiringIT extends P09QueueSupport {
    private String hookFileFailure;
    private final String hashSentinel="hash-sentinel-"+UUID.randomUUID();
    @Override protected void prepareSecurityCredentials(){super.prepareSecurityCredentials();try{if("missing".equals(hookFileFailure))java.nio.file.Files.delete(securityDirectory.resolve("hook.sha256"));if("invalid".equals(hookFileFailure))java.nio.file.Files.writeString(securityDirectory.resolve("hook.sha256"),hashSentinel);}catch(java.io.IOException failure){throw new IllegalStateException("시험 훅 파일 변형 실패");}}
    static boolean failLate;static int signals;static JobDispatcher createdDispatcher;static JobTimeoutSweeper createdSweeper;
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{P09AdmissionEndpoint.class,Observe.class};}
    @TestConfiguration(proxyBeanMethods=false) static class Observe{
        @Bean ReadinessPrecondition later(){return new ReadinessPrecondition(){public int order(){return 20;}public void run(){if(failLate)throw new IllegalStateException("후순위 확인 실패");}};}
        @Bean static BeanPostProcessor startupObservation(){return new BeanPostProcessor(){@Override public Object postProcessAfterInitialization(Object bean,String name){
            if(bean instanceof JobDispatcher dispatcher){assertThat(dispatcher.started()).isFalse();createdDispatcher=dispatcher;}
            if(bean instanceof JobTimeoutSweeper sweeper){assertThat(sweeper.started()).isFalse();createdSweeper=sweeper;}return bean;
        }};}
    }
    @Test void realCoreBeansSingleClockAndActualAdmissionWorkerPath()throws Exception{
        for(Class<?> type:List.of(ContentSanitizer.class,ConversationExpiryGuard.class,GenerationSemaphore.class,WorkflowExecutor.class,TcSourceAuthorizationPort.class,JobWorker.class,JobTerminalCommitter.class,Clock.class))assertThat(context.getBeansOfType(type)).hasSize(1);
        Clock clock=context.getBean(Clock.class);assertThat(clock.instant()).isEqualTo(NOW);
        for(Class<?> type:List.of(JobWorker.class,JobTerminalCommitter.class,RecoveryModeRouter.class,StartupRecoveryRunner.class,com.nori.ai.gateway.app.security.ActorAssertionVerifier.class))assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(type),"clock")).isSameAs(clock);
        assertThat(context.getBean(InferenceReadiness.class).ready()).isTrue();var accepted=admit("actor","wiring",Set.of());await(()->state(accepted.jobId()).equals("SUCCEEDED"));
        assertThat(observations().workflows).singleElement().satisfies(j->{assertThat(j.scope().perm()).containsExactlyInAnyOrder("EQP_READ","MODEL_READ");assertThat(j.inferenceMode()).isEqualTo(InferenceMode.DISABLED);});
        assertThat(context.getBeansOfType(com.nori.ai.gateway.core.dispatch.ToolExecutor.class)).isEmpty();
        context.getBean(ReadinessGate.class).down();int before=count("job");
        assertThat(postQuery("actor","before-ready","질문").statusCode()).isEqualTo(503);assertThat(count("job")).isEqualTo(before);
    }
    @Test @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void mandatoryHookHashRejectsMissingAndInvalidWithoutRawCause(org.springframework.boot.test.system.CapturedOutput output)throws Exception{
        context.close();context=null;
        for(String failure:List.of("missing","invalid")){hookFileFailure=failure;assertThatThrownBy(()->startApplication(extraSources())).hasStackTraceContaining("시험 훅 인증 파일이 잘못되었습니다");}
        assertThat(output.getAll().contains(hashSentinel)).isFalse();hookFileFailure=null;
    }
    @Test void missingProductPortsFailAndLaterPreconditionFailurePublishesNoStart()throws Exception{
        var props=new HashMap<String,Object>();securityProperties().forEach(props::put);
        props.putAll(Map.of("spring.datasource.url",database.jdbcUrl(),"spring.datasource.username","aigw_app","spring.datasource.password",database.passwordFor("aigw_app"),"spring.flyway.enabled",false));
        props.putAll(Map.of("ai.gateway.capacity.max-running-jobs",1,"ai.gateway.capacity.generation-concurrency",1,"ai.gateway.capacity.queued-depth",4,"ai.gateway.capacity.queue-wait-limit","60s","ai.gateway.capacity.job-lifetime-limit","180s","ai.gateway.inference.mode","disabled"));
        props.putAll(Map.of("spring.kafka.bootstrap-servers","127.0.0.1:1","nori.storage.s3.enabled",true,"nori.storage.s3.endpoint","http://127.0.0.1:1","nori.storage.s3.bucket","fixture","nori.storage.s3.access-key","dummy","nori.storage.s3.secret-key","dummy","nori.storage.s3.region","us-east-1"));
        assertThatThrownBy(()->new SpringApplicationBuilder(AiGatewayApplication.class).web(WebApplicationType.NONE).profiles("verify").properties(props).run()).hasStackTraceContaining(TcSourceAuthorizationPort.class.getName());
        context.close();context=null;failLate=true;
        try{assertThatThrownBy(()->startApplication(extraSources())).hasMessage("후순위 확인 실패");assertThat(createdDispatcher.started()).isFalse();assertThat(createdSweeper.started()).isFalse();}
        finally{failLate=false;}
    }
}
