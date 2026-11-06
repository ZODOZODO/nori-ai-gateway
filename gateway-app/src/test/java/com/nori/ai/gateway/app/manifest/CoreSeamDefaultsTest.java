package com.nori.ai.gateway.app.manifest;

import com.nori.ai.gateway.app.config.CoreBeansConfiguration;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class CoreSeamDefaultsTest {
    private ApplicationContextRunner runner(java.time.Clock clock) {return new ApplicationContextRunner().withUserConfiguration(CoreBeansConfiguration.class,com.nori.ai.gateway.tcmock.TcMockModuleConfiguration.class)
        .withBean(java.time.Clock.class,()->clock)
        .withBean(com.nori.ai.gateway.core.cache.ToolResultCache.Store.class,()->org.mockito.Mockito.mock(com.nori.ai.gateway.core.cache.ToolResultCache.Store.class))
        .withBean(com.nori.ai.gateway.core.workflow.WorkflowExecutor.ExecutionRecorder.class,()->(job,invocation)->{throw new AssertionError("빈 등록 시험에서 실제 호출이 발생했습니다");})
        .withBean(com.nori.ai.gateway.app.config.CapacityProfile.class,()->new com.nori.ai.gateway.app.config.CapacityProfile(1,1,4,java.time.Duration.ofSeconds(60),java.time.Duration.ofSeconds(180)));}
    private final ApplicationContextRunner runner=runner(java.time.Clock.systemUTC());
    @Test void defaultAndExplicitFalseHaveExactlyOneNoOpOfEachType() {
        runner.run(this::assertDefaults);
        runner.withPropertyValues("ai.gateway.test-hooks.enabled=false").run(this::assertDefaults);
    }
    @Test void enabledHooksWithoutOwningPhaseFailClosed() {
        runner.withPropertyValues("ai.gateway.test-hooks.enabled=true").run(context -> assertThat(context).hasFailed());
    }
    @Test void verifyRegistersOneConcreteRegistryForBothInterfaces() {
        var clock=java.time.Clock.systemUTC();
        runner.withUserConfiguration(com.nori.ai.gateway.app.hooks.MockHooksConfiguration.class)
            .withBean(com.nori.ai.gateway.app.hooks.TestHookRegistry.class,()->new com.nori.ai.gateway.app.hooks.TestHookRegistry(clock,new com.nori.ai.gateway.app.hooks.PausePointRegistry()))
            .withInitializer(context->context.getEnvironment().setActiveProfiles("verify")).withPropertyValues("ai.gateway.test-hooks.enabled=true")
            .run(context->{assertThat(context).hasNotFailed().hasSingleBean(PlanInjectionSeam.class).hasSingleBean(DispatchInjectionSeam.class);
                assertThat(context.getBean(PlanInjectionSeam.class)).isSameAs(context.getBean(DispatchInjectionSeam.class)).isExactlyInstanceOf(com.nori.ai.gateway.app.hooks.SeamRegistry.class);});
    }
    @Test void validatorAndDispatcherUseTheActualGatewayClockForFutureValidScope() {
        var at=java.time.Instant.parse("2040-01-01T00:00:00Z");
        class MutableClock extends java.time.Clock {
            java.time.Instant value=at;
            public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
            public java.time.Clock withZone(java.time.ZoneId zone){return this;}
            public java.time.Instant instant(){return value;}
        }
        var clock=new MutableClock();
        runner(clock).run(context->{
            var validator=context.getBean(com.nori.ai.gateway.core.plan.PlanValidator.class);var dispatcher=context.getBean(com.nori.ai.gateway.core.dispatch.ToolDispatcher.class);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(validator,"clock")).isSameAs(clock);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(dispatcher,"clock")).isSameAs(clock);
            var scope=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(com.nori.ai.gateway.core.scope.OwnerSystem.TC,"fixture",new com.nori.ai.gateway.core.scope.ActorRef("actor"),"actor",java.util.Set.of("EQP_READ"),"a".repeat(64),at.minusSeconds(1),at.plusSeconds(60),"future");
            var plan=new com.nori.ai.gateway.core.plan.PlanEnvelope("future","conversation","질문",java.util.List.of(),com.nori.ai.gateway.core.registry.TaskType.GENERAL_QUERY,java.util.List.of());
            assertThat(validator.validate(plan,scope,null).accepted()).isTrue();
            for(long offset:new long[]{-1,0,1}){
                clock.value=scope.expiresAt().plusNanos(offset);var reads=new java.util.concurrent.atomic.AtomicInteger();
                var resolver=new com.nori.ai.gateway.core.plan.ResourceResolver(){
                    public ResourceState resolve(com.nori.ai.gateway.core.plan.ResourceRef resource,com.nori.ai.gateway.core.scope.VerifiedSystemScope actual){reads.incrementAndGet();return new ResourceState(actual.boundSystem(),"EQP",true);}
                    public SourceBinding source(com.nori.ai.gateway.core.registry.CapabilityManifest manifest,com.nori.ai.gateway.core.scope.VerifiedSystemScope actual){return new SourceBinding(actual.boundSystem(),manifest.sourceAllowlistRef(),true);}
                    public CurrentAuthorization authorize(com.nori.ai.gateway.core.registry.CapabilityManifest manifest,com.nori.ai.gateway.core.plan.PlanStep step,com.nori.ai.gateway.core.scope.VerifiedSystemScope actual){return new CurrentAuthorization(actual.boundSystem(),manifest.sourceAllowlistRef(),actual.actorRef(),actual.pdg(),true,actual.perm(),java.util.Set.of("READ"));}
                    public java.time.ZoneId timezone(com.nori.ai.gateway.core.scope.VerifiedSystemScope actual){return java.time.ZoneOffset.UTC;}
                };
                assertThat(validator.validate(plan,scope,null).accepted()).isEqualTo(offset<0);
                var step=new com.nori.ai.gateway.core.plan.PlanStep("one","tc.eqp.getOperationalSnapshot",java.util.List.of(),java.util.List.of(new com.nori.ai.gateway.core.plan.ResourceRef("EQP","EQP_001")),com.nori.ai.gateway.core.registry.SchemaContracts.parse("{\"eqp\":\"EQP_001\"}"));
                var result=dispatcher.dispatch(step,scope,new com.nori.ai.gateway.core.dispatch.ExecutionContext(resolver,trace->{}));
                assertThat(reads.get()).isEqualTo(offset<0?1:0);
                assertThat(result.errorCode()).isEqualTo(offset<0?null:com.nori.ai.gateway.core.result.ErrorCode.SYSTEM_SCOPE_MISMATCH);
            }
        });
    }
    private void assertDefaults(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasNotFailed().hasSingleBean(PlanInjectionSeam.class).hasSingleBean(DispatchInjectionSeam.class);
        assertThat(context.getBean(PlanInjectionSeam.class)).isExactlyInstanceOf(PlanInjectionSeam.NoOp.class);
        assertThat(context.getBean(DispatchInjectionSeam.class)).isExactlyInstanceOf(DispatchInjectionSeam.NoOp.class);
    }
}
