package com.nori.ai.gateway.app;

import com.nori.ai.gateway.app.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import static org.assertj.core.api.Assertions.*;

class CapacityProfileValidationTest {
    @EnableConfigurationProperties({CapacityProfile.class,InferenceModeProperties.class}) static class Properties{}
    private ApplicationContextRunner runner(){return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class)).withUserConfiguration(Properties.class);}
    private String[] valid(){return new String[]{"ai.gateway.capacity.max-running-jobs=1","ai.gateway.capacity.generation-concurrency=1","ai.gateway.capacity.queued-depth=4","ai.gateway.capacity.queue-wait-limit=60s","ai.gateway.capacity.job-lifetime-limit=180s","ai.gateway.inference.mode=disabled"};}
    @Test void exactInitialProfileBindsAndEveryMissingFieldFails(){
        runner().withPropertyValues(valid()).run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(CapacityProfile.class).queuedDepth()).isEqualTo(4);});
        for(int omitted=0;omitted<valid().length;omitted++){
            var input=new java.util.ArrayList<>(java.util.List.of(valid()));input.remove(omitted);runner().withPropertyValues(input.toArray(String[]::new)).run(c->assertThat(c).hasFailed());
        }
    }
    @Test void zeroNegativeFractionalAndOpenEnumValuesFail(){
        for(String value:new String[]{"0","-1","1.5"})for(String key:new String[]{"max-running-jobs","generation-concurrency","queued-depth"})runner().withPropertyValues(valid()).withPropertyValues("ai.gateway.capacity."+key+"="+value).run(c->assertThat(c).hasFailed());
        for(String value:new String[]{"0s","-1s","0.5s","1.5s"})runner().withPropertyValues(valid()).withPropertyValues("ai.gateway.capacity.queue-wait-limit="+value).run(c->assertThat(c).hasFailed());
        runner().withPropertyValues(valid()).withPropertyValues("ai.gateway.inference.mode=optional").run(c->assertThat(c).hasFailed());
    }
}
