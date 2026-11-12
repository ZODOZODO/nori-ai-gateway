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
    /** 승인 근거 프로필 planner(WorkflowExecutionConfiguration)가 있으면 분류 wire v3 근거 계획을 쓰고, 없는 구성은 결속 근거 요구가 있는 Job 을 거부한다(Plan §16.4). */
    @Bean com.nori.ai.gateway.core.workflow.WorkflowPlanBuilder workflowPlanBuilder(CapabilityRegistry registry,Optional<com.nori.ai.gateway.core.workflow.EvidencePlanBuilder> evidence){return evidence.map(value->new com.nori.ai.gateway.core.workflow.WorkflowPlanBuilder(registry,value)).orElseGet(()->new com.nori.ai.gateway.core.workflow.WorkflowPlanBuilder(registry));}
    @Bean com.nori.ai.gateway.core.workflow.EvidenceCoverageEvaluator evidenceCoverageEvaluator(){return new com.nori.ai.gateway.core.workflow.EvidenceCoverageEvaluator();}
    @Bean com.nori.ai.gateway.core.time.TimeWindowResolver timeWindowResolver(){return new com.nori.ai.gateway.core.time.TimeWindowResolver();}
    @Bean com.nori.ai.gateway.core.blocks.UiSchemaValidator uiSchemaValidator(){return new com.nori.ai.gateway.core.blocks.UiSchemaValidator();}
    @Bean com.nori.ai.gateway.core.blocks.BlockBuilder blockBuilder(com.nori.ai.gateway.core.blocks.UiSchemaValidator validator){return new com.nori.ai.gateway.core.blocks.BlockBuilder(validator);}
    @Bean com.nori.ai.gateway.core.workflow.TerminalOutcomeAssembler terminalOutcomeAssembler(){return new com.nori.ai.gateway.core.workflow.TerminalOutcomeAssembler();}
    @Bean com.nori.ai.gateway.core.workflow.ConversationSummaryBuilder conversationSummaryBuilder(){return new com.nori.ai.gateway.core.workflow.ConversationSummaryBuilder();}
    @Bean com.nori.ai.gateway.core.workflow.LogSummarizer logSummarizer(){return new com.nori.ai.gateway.core.workflow.LogSummarizer();}
    @Bean com.nori.ai.gateway.core.blocks.GeneralQueryHelpBuilder generalQueryHelpBuilder(){return new com.nori.ai.gateway.core.blocks.GeneralQueryHelpBuilder();}
    @Bean com.nori.ai.gateway.core.evidence.EvidenceAssembler evidenceAssembler(){return new com.nori.ai.gateway.core.evidence.EvidenceAssembler();}
    @Bean com.nori.ai.gateway.core.cache.ToolResultCache toolResultCache(com.nori.ai.gateway.core.cache.ToolResultCache.Store store,PlanValidator validator,com.nori.ai.gateway.core.result.ResultAccessEvaluator access,com.nori.ai.gateway.core.result.KnowledgeDocumentStateReader documents,com.nori.ai.gateway.core.expiry.ExpiryCheckpoint expiry,Clock clock){return new com.nori.ai.gateway.core.cache.ToolResultCache(store,validator,access,documents,expiry,clock);}
    @Bean com.nori.ai.gateway.core.workflow.WorkflowExecutor coreWorkflowExecutor(CapabilityRegistry registry,PlanValidator validator,ToolDispatcher dispatcher,com.nori.ai.gateway.core.authz.SourceAuthorizationStep authorization,com.nori.ai.gateway.core.expiry.ExpiryCheckpoint expiry,Clock clock,com.nori.ai.gateway.core.cache.ToolResultCache cache,com.nori.ai.gateway.core.blocks.BlockBuilder blocks,com.nori.ai.gateway.core.workflow.WorkflowExecutor.ExecutionRecorder recorder,com.nori.ai.gateway.core.workflow.LogSummarizer summarizer,com.nori.ai.gateway.core.blocks.GeneralQueryHelpBuilder help,com.nori.ai.gateway.core.workflow.EvidenceCoverageEvaluator coverage){return new com.nori.ai.gateway.core.workflow.WorkflowExecutor(registry,validator,dispatcher,authorization,expiry,clock,cache,blocks,recorder,summarizer,help,coverage);}
    @Bean com.nori.ai.gateway.core.authz.SourcePermissionMap sourcePermissionMap() {
        try (var input = CoreBeansConfiguration.class.getClassLoader().getResourceAsStream("config/source-permissions/tc-v1.json")) {
            if (input == null) throw new IllegalStateException("Source 권한 매핑이 없습니다");
            return com.nori.ai.gateway.core.authz.SourcePermissionMap.fromJson(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) { throw new IllegalStateException("Source 권한 매핑을 읽을 수 없습니다"); }
    }
    @Bean com.nori.ai.gateway.core.authz.SourceAuthorizationStep sourceAuthorizationStep(com.nori.ai.gateway.core.authz.SourcePermissionMap permissions,
            com.nori.ai.gateway.core.port.TcSourceAuthorizationPort authorization, Optional<com.nori.ai.gateway.app.hooks.SeamRegistry> seams) {
        return seams.map(seam -> new com.nori.ai.gateway.core.authz.SourceAuthorizationStep(permissions, authorization, seam::permissionMappingExcluded))
            .orElseGet(() -> new com.nori.ai.gateway.core.authz.SourceAuthorizationStep(permissions, authorization));
    }
    @Bean com.nori.ai.gateway.core.retention.RetentionClassifier retentionClassifier() { return new com.nori.ai.gateway.core.retention.RetentionClassifier(); }
    @Bean com.nori.ai.gateway.core.retention.TrimCompletenessEvaluator trimCompletenessEvaluator() { return new com.nori.ai.gateway.core.retention.TrimCompletenessEvaluator(); }
    @Bean com.nori.ai.gateway.core.retention.DlqCompletenessEvaluator dlqCompletenessEvaluator() { return new com.nori.ai.gateway.core.retention.DlqCompletenessEvaluator(); }
    @Bean com.nori.ai.gateway.core.result.ResultAccessEvaluator resultAccessEvaluator() { return new com.nori.ai.gateway.core.result.ResultAccessEvaluator(); }
    @Bean com.nori.ai.gateway.core.result.KnowledgeDocumentStateReader knowledgeDocumentStateReader() { return new com.nori.ai.gateway.core.result.FailClosedKnowledgeDocumentStateReader(); }
    @Bean com.nori.ai.gateway.core.result.EvidenceSourceReader evidenceSourceReader() { return new com.nori.ai.gateway.core.result.FailClosedEvidenceSourceReader(); }
    @Bean com.nori.ai.gateway.core.expiry.ExpiryCheckpoint expiryCheckpoint(com.nori.ai.gateway.core.expiry.ConversationExpiryGuard guard,Clock clock) { return new com.nori.ai.gateway.core.expiry.ExpiryCheckpoint(guard,clock); }
    @Bean com.nori.ai.gateway.core.sanitize.ContentSanitizer contentSanitizer() { return new com.nori.ai.gateway.core.sanitize.ContentSanitizer(); }
    @Bean com.nori.ai.gateway.core.audit.AuditFieldAllowlist auditFieldAllowlist(com.nori.ai.gateway.core.sanitize.ContentSanitizer sanitizer, CapabilityRegistry registry) { return new com.nori.ai.gateway.core.audit.AuditFieldAllowlist(sanitizer, registry); }
    @Bean com.nori.ai.gateway.core.expiry.ConversationExpiryGuard conversationExpiryGuard() { return new com.nori.ai.gateway.core.expiry.ConversationExpiryGuard(); }
    @Bean com.nori.ai.gateway.core.job.GenerationSemaphore generationSemaphore(CapacityProfile capacity) { return new com.nori.ai.gateway.core.job.GenerationSemaphore(capacity.generationConcurrency()); }
    @Bean com.nori.ai.gateway.core.budget.TokenBudgetPolicy tokenBudgetPolicy() { return com.nori.ai.gateway.core.budget.TokenBudgetPolicy.current(); }
    @Bean com.nori.ai.gateway.core.budget.PromptReducer promptReducer(com.nori.ai.gateway.core.budget.PromptTokenCounting counter, com.nori.ai.gateway.core.budget.TokenBudgetPolicy policy) { return new com.nori.ai.gateway.core.budget.PromptReducer(counter, policy); }
    @Bean com.nori.ai.gateway.core.budget.PreCallBudgetAssert preCallBudgetAssert(com.nori.ai.gateway.core.budget.PromptTokenCounting counter, com.nori.ai.gateway.core.budget.TokenBudgetPolicy policy) { return new com.nori.ai.gateway.core.budget.PreCallBudgetAssert(counter, policy); }
    @Bean com.nori.ai.gateway.core.budget.TruncationDetector truncationDetector() { return new com.nori.ai.gateway.core.budget.TruncationDetector(); }
    @Bean ManifestLoader manifestLoader() { return new ManifestLoader(); }
    @Bean ContractVersionPolicy contractVersionPolicy() { return new ContractVersionPolicy(); }
    @Bean CapabilityRegistry capabilityRegistry(ManifestLoader loader, ContractVersionPolicy versions) {
        return new CapabilityRegistry(loader.loadClasspath("manifests/tc/index.json"),
            loader.loadWorkflowsClasspath("workflows/tc/index.json"), Set.of(ImplementationPhase.PHASE_1), versions);
    }
    @Bean PlanValidator planValidator(CapabilityRegistry registry, ManifestLoader loader, PlanInjectionSeam seam, Clock clock) {
        var manifests = loader.loadClasspath("manifests/tc/index.json");
        // 중복 Capability를 허용하지 않는 현재 Registry 전체 크기와 비용 합계를 상한으로 사용합니다.
        return new PlanValidator(registry, seam, clock, manifests.size(), manifests.stream().mapToLong(CapabilityManifest::cost).sum());
    }
    @Bean ToolResultContractValidator toolResultContractValidator(ContractVersionPolicy versions) { return new ToolResultContractValidator(versions); }
    @Bean ToolDispatcher toolDispatcher(CapabilityRegistry registry, PlanValidator validator, ToolResultContractValidator results,
            DispatchInjectionSeam seam, List<ToolExecutor> executors, Clock clock) {
        return new ToolDispatcher(registry, validator, results, seam, executors, clock);
    }
    @Bean
    @ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "false", matchIfMissing = true)
    PlanInjectionSeam planInjectionSeam() { return new PlanInjectionSeam.NoOp(); }
    @Bean
    @ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "false", matchIfMissing = true)
    DispatchInjectionSeam dispatchInjectionSeam() { return new DispatchInjectionSeam.NoOp(); }
}
