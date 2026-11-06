package com.nori.ai.gateway.app.config;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.PlanValidator;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.seam.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.Clock;
import java.util.*;

/** Spring에 의존하지 않는 Core의 빈 등록 책임을 이 설정에 모읍니다. */
@Configuration(proxyBeanMethods = false)
public class CoreBeansConfiguration {
    @Bean com.nori.ai.gateway.core.sanitize.ContentSanitizer contentSanitizer() { return new com.nori.ai.gateway.core.sanitize.ContentSanitizer(); }
    @Bean com.nori.ai.gateway.core.expiry.ConversationExpiryGuard conversationExpiryGuard() { return new com.nori.ai.gateway.core.expiry.ConversationExpiryGuard(); }
    @Bean com.nori.ai.gateway.core.job.GenerationSemaphore generationSemaphore(CapacityProfile capacity) { return new com.nori.ai.gateway.core.job.GenerationSemaphore(capacity.generationConcurrency()); }
    @Bean ManifestLoader manifestLoader() { return new ManifestLoader(); }
    @Bean ContractVersionPolicy contractVersionPolicy() { return new ContractVersionPolicy(); }
    @Bean CapabilityRegistry capabilityRegistry(ManifestLoader loader, ContractVersionPolicy versions) {
        return new CapabilityRegistry(loader.loadClasspath("manifests/tc/index.json"),
            loader.loadWorkflowsClasspath("workflows/tc/index.json"), Set.of(ImplementationPhase.PHASE_1), versions);
    }
    @Bean PlanValidator planValidator(CapabilityRegistry registry, ManifestLoader loader, PlanInjectionSeam seam) {
        var manifests = loader.loadClasspath("manifests/tc/index.json");
        // 중복 Capability를 허용하지 않는 현재 Registry 전체 크기와 비용 합계를 상한으로 사용합니다.
        return new PlanValidator(registry, seam, Clock.systemUTC(), manifests.size(), manifests.stream().mapToLong(CapabilityManifest::cost).sum());
    }
    @Bean ToolResultContractValidator toolResultContractValidator(ContractVersionPolicy versions) { return new ToolResultContractValidator(versions); }
    @Bean ToolDispatcher toolDispatcher(CapabilityRegistry registry, PlanValidator validator, ToolResultContractValidator results,
            DispatchInjectionSeam seam, List<ToolExecutor> executors) {
        return new ToolDispatcher(registry, validator, results, seam, executors, Clock.systemUTC());
    }
    @Bean
    @ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "false", matchIfMissing = true)
    PlanInjectionSeam planInjectionSeam() { return new PlanInjectionSeam.NoOp(); }
    @Bean
    @ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "false", matchIfMissing = true)
    DispatchInjectionSeam dispatchInjectionSeam() { return new DispatchInjectionSeam.NoOp(); }
}
