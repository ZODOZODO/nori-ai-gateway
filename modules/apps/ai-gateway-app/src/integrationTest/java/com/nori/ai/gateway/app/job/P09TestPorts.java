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
        public final List<JobContext> workflows=new CopyOnWriteArrayList<>();
        public final AtomicInteger published=new AtomicInteger();
    }
    @Bean Observations p09Observations(){return new Observations();}
    @Bean static org.springframework.beans.factory.config.BeanPostProcessor p13WorkflowObservation(org.springframework.beans.factory.ObjectProvider<Observations> observations) {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(!(bean instanceof WorkflowExecutionAdapter adapter))return bean;
                var actual=org.mockito.Mockito.spy(adapter);
                // 실제 실행은 BEFORE_COMPOSER 정지점의 InterruptedException 을 선언하지만 stub 등록은 호출하지 않는다.
                try{org.mockito.Mockito.doAnswer(invocation->{var job=invocation.getArgument(0,JobContext.class);observations.getObject().workflows.add(job);return invocation.callRealMethod();})
                    .when(actual).execute(org.mockito.ArgumentMatchers.any(JobContext.class));}catch(InterruptedException impossible){throw new IllegalStateException("관찰 stub 등록이 중단되었습니다",impossible);}
                return actual;
            }
        };
    }
}
