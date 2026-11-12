package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.authz.SourcePermissionMap;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.port.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.time.TimeWindowResolver;
import com.nori.ai.gateway.core.workflow.*;
import com.nori.ai.gateway.integration.loki.*;
import com.nori.ai.gateway.integration.tempo.TempoTraceConnector;
import com.nori.ai.gateway.integration.gitlab.*;
import com.nori.ai.gateway.persistence.repository.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(LokiProperties.class)
public class WorkflowExecutionConfiguration {
    @Bean WorkflowResourceResolverFactory workflowResourceResolverFactory(TcBusinessReadPort business,SourcePermissionMap permissions,CapabilityRegistry registry,ExpiryCheckpoint expiry){return new WorkflowResourceResolverFactory(business,permissions,registry,expiry);}
    @Bean WorkflowCacheStore workflowCacheStore(CacheRepository cache,ContentSanitizer sanitizer,CapabilityRegistry registry){return new WorkflowCacheStore(cache,sanitizer,registry);}
    @Bean WorkflowExecutionRecords workflowExecutionRecords(ToolExecutionRepository executions,ContentSanitizer sanitizer,ExpiryCheckpoint expiry,CapabilityRegistry registry,com.nori.ai.gateway.app.observability.AuditRecorder audit){return new WorkflowExecutionRecords(executions,sanitizer,expiry,registry,audit);}
    @Bean BusinessToolExecutor businessToolExecutor(TcBusinessReadPort business,Clock clock){return new BusinessToolExecutor(business,clock);}
    @Bean DirectSourceToolExecutor logToolExecutor(ObjectProvider<LokiLogConnector> logs,ObjectProvider<TempoTraceConnector> traces,ObjectProvider<GitLabCodeConnector> code,ObjectProvider<DirectSourceToolExecutor.GitExecutionScopes> scopes,Clock clock,LokiProperties properties,DeploymentEvidenceResolver deployments,ObjectProvider<com.nori.ai.gateway.app.hooks.SourceRequestProbe> probe){return new DirectSourceToolExecutor(ToolKind.LOG,logs,traces,code,scopes,clock,properties,deployments,java.util.Optional.ofNullable(probe.getIfAvailable()));}
    @Bean DirectSourceToolExecutor traceToolExecutor(ObjectProvider<LokiLogConnector> logs,ObjectProvider<TempoTraceConnector> traces,ObjectProvider<GitLabCodeConnector> code,ObjectProvider<DirectSourceToolExecutor.GitExecutionScopes> scopes,Clock clock,LokiProperties properties,DeploymentEvidenceResolver deployments,ObjectProvider<com.nori.ai.gateway.app.hooks.SourceRequestProbe> probe){return new DirectSourceToolExecutor(ToolKind.TRACE,logs,traces,code,scopes,clock,properties,deployments,java.util.Optional.ofNullable(probe.getIfAvailable()));}
    @Bean DirectSourceToolExecutor gitToolExecutor(ObjectProvider<LokiLogConnector> logs,ObjectProvider<TempoTraceConnector> traces,ObjectProvider<GitLabCodeConnector> code,ObjectProvider<DirectSourceToolExecutor.GitExecutionScopes> scopes,Clock clock,LokiProperties properties,DeploymentEvidenceResolver deployments,ObjectProvider<com.nori.ai.gateway.app.hooks.SourceRequestProbe> probe){return new DirectSourceToolExecutor(ToolKind.GIT,logs,traces,code,scopes,clock,properties,deployments,java.util.Optional.ofNullable(probe.getIfAvailable()));}
    @Bean DeploymentEvidenceResolver deploymentEvidenceResolver(ObjectProvider<GitLabPipelineLineageReader> reader){return new DeploymentEvidenceResolver(reader);}
    /** TC 승인 근거 프로필(Plan §16.3 R1). workflows/tc/index.json 과 다른 구조라 별도 명시 loader 로 읽고 형식 위반은 기동 실패다. */
    @Bean EvidenceProfileLoader evidenceProfileLoader(CapabilityRegistry registry){return new EvidenceProfileLoader(registry);}
    @Bean EvidencePlanBuilder evidencePlanBuilder(EvidenceProfileLoader loader){return new EvidencePlanBuilder(loader.loadClasspath(EvidenceProfileLoader.TC_PROFILE,com.nori.ai.gateway.core.scope.OwnerSystem.TC));}
    @Bean SourceRetentionAttributesProvider sourceRetentionAttributesProvider(ObjectProvider<SourceRetentionReader> reader){return new SourceRetentionAttributesProvider(reader);}
    @Bean WorkflowExecutionAdapter workflowExecutionAdapter(CapabilityRegistry registry,WorkflowPlanBuilder builder,WorkflowExecutor executor,WorkflowResourceResolverFactory resolvers,TimeWindowResolver windows,MessageRepository messages,ExpiryCheckpoint expiry,ContentSanitizer sanitizer,TerminalOutcomeAssembler assembler,ConversationSummaryBuilder summaries,com.nori.ai.gateway.core.evidence.EvidenceAssembler evidence,com.nori.ai.gateway.app.observability.AuditRecorder audit,
            java.util.Optional<com.nori.ai.gateway.core.llm.ComposerService> composer,LogSummarizer logSummarizer,java.util.Optional<com.nori.ai.gateway.app.hooks.PausePointRegistry> pauses){return new WorkflowExecutionAdapter(registry,builder,executor,resolvers,windows,messages,expiry,sanitizer,assembler,summaries,evidence,audit,composer,logSummarizer,pauses);}
    /**
     * Response Composer(Plan P20). 추론 비활성에서는 빈 자체가 없다(N7). 프롬프트는 compose-v1 이고 답변 토큰은 pin tokenizer 로 센다.
     * 추론 활성 조건과 프롬프트 자원을 이 Job 실행 설정이 함께 가진다(Plan §16.4 WorkflowExecutionConfiguration 의 Composer 배선).
     */
    @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="ai.gateway.inference.mode",havingValue="enabled")
    com.nori.ai.gateway.core.llm.ComposerService composerService(InferenceClient inference,com.nori.ai.gateway.core.budget.PromptReducer reducer,com.nori.ai.gateway.core.budget.TokenBudgetPolicy policy,
            com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter tokenizer,ExpiryCheckpoint expiry,java.util.Optional<com.nori.ai.gateway.core.llm.LlmCallObserver> observer){
        return new com.nori.ai.gateway.core.llm.ComposerService(inference,reducer,policy,tokenizer::countText,expiry,observer.orElse(com.nori.ai.gateway.core.llm.LlmCallObserver.NONE),prompt("prompts/compose-v1.txt"));
    }
    private static String prompt(String path){
        try(var input=WorkflowExecutionConfiguration.class.getClassLoader().getResourceAsStream(path)){
            if(input==null)throw new IllegalStateException("프롬프트 자원이 없습니다: "+path);
            return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }catch(java.io.IOException failure){throw new IllegalStateException("프롬프트 자원을 읽을 수 없습니다: "+path,failure);}
    }
}
