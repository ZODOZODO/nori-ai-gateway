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
    @Bean SourceRetentionAttributesProvider sourceRetentionAttributesProvider(ObjectProvider<SourceRetentionReader> reader){return new SourceRetentionAttributesProvider(reader);}
    @Bean WorkflowExecutionAdapter workflowExecutionAdapter(CapabilityRegistry registry,WorkflowPlanBuilder builder,WorkflowExecutor executor,WorkflowResourceResolverFactory resolvers,TimeWindowResolver windows,MessageRepository messages,ExpiryCheckpoint expiry,ContentSanitizer sanitizer,TerminalOutcomeAssembler assembler,ConversationSummaryBuilder summaries,com.nori.ai.gateway.core.evidence.EvidenceAssembler evidence,com.nori.ai.gateway.app.observability.AuditRecorder audit){return new WorkflowExecutionAdapter(registry,builder,executor,resolvers,windows,messages,expiry,sanitizer,assembler,summaries,evidence,audit);}
}
