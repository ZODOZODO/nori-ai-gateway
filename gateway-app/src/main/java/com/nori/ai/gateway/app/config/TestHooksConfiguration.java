package com.nori.ai.gateway.app.config;

import com.nori.ai.gateway.app.hooks.*;
import jakarta.servlet.DispatcherType;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
@Profile("verify")
@ConditionalOnProperty(name="ai.gateway.test-hooks.enabled",havingValue="true")
@Import(TestHookController.class)
public class TestHooksConfiguration {
    @Bean ControllableClock controllableClock(Environment environment){
        try{
            String initial=environment.getProperty("ai.gateway.test-hooks.clock.initial");
            var mode=ControllableClock.Mode.valueOf(environment.getProperty("ai.gateway.test-hooks.clock.mode","SYSTEM"));
            return new ControllableClock(mode,initial==null?null:Instant.parse(initial));
        }catch(RuntimeException failure){throw new IllegalStateException("시험 시계 설정이 잘못되었습니다");}
    }
    @Bean PausePointRegistry pausePointRegistry(){return new PausePointRegistry();}
    @Bean TestHookRegistry testHookRegistry(ControllableClock clock,PausePointRegistry pauses){return new TestHookRegistry(clock,pauses);}
    @Bean StructuredClassificationHook structuredClassificationHook(TestHookRegistry hooks){return new StructuredClassificationHook(hooks);}
    @Bean FaultInjector faultInjector(TestHookRegistry hooks,PausePointRegistry pauses){return new FaultInjector(hooks,pauses);}
    @Bean FilterRegistrationBean<TestHookAuthFilter> testHookAuthFilterRegistration(Environment environment){
        final TestHookAuthFilter filter;
        try{filter=new TestHookAuthFilter(Path.of(environment.getRequiredProperty("ai.gateway.test-hooks.token-sha256-file")));}
        catch(RuntimeException failure){throw new IllegalStateException("시험 훅 인증 파일이 잘못되었습니다");}
        var registration=new FilterRegistrationBean<>(filter);registration.setName("testHookAuthFilter");registration.addUrlPatterns("/__test/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);return registration;
    }
}
