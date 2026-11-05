package com.nori.ai.gateway.app.manifest;

import com.nori.ai.gateway.app.config.CoreBeansConfiguration;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class CoreSeamDefaultsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(CoreBeansConfiguration.class);
    @Test void defaultAndExplicitFalseHaveExactlyOneNoOpOfEachType() {
        runner.run(this::assertDefaults);
        runner.withPropertyValues("ai.gateway.test-hooks.enabled=false").run(this::assertDefaults);
    }
    @Test void enabledHooksWithoutOwningPhaseFailClosed() {
        runner.withPropertyValues("ai.gateway.test-hooks.enabled=true").run(context -> assertThat(context).hasFailed());
    }
    private void assertDefaults(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().hasSingleBean(PlanInjectionSeam.class).hasSingleBean(DispatchInjectionSeam.class);
        assertThat(context.getBean(PlanInjectionSeam.class)).isExactlyInstanceOf(PlanInjectionSeam.NoOp.class);
        assertThat(context.getBean(DispatchInjectionSeam.class)).isExactlyInstanceOf(DispatchInjectionSeam.NoOp.class);
    }
}
