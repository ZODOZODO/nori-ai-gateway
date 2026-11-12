package com.nori.ai.gateway.app.config;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.app.job.*;
import com.nori.ai.gateway.app.job.recovery.*;
import com.nori.ai.gateway.app.security.ScopeAssembler;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.persistence.maintenance.QueueMaintenanceRepository;
import com.nori.ai.gateway.persistence.queue.*;
import com.nori.ai.gateway.persistence.queue.recovery.*;
import com.nori.ai.gateway.persistence.repository.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties({CapacityProfile.class,InferenceModeProperties.class,RecoveryProperties.class})
public class JobConfiguration {
    @Bean AdmissionLookupRepository admissionLookupRepository(JdbcClient jdbc){return new AdmissionLookupRepository(jdbc);}
    @Bean QueueJobRepository queueJobRepository(JdbcClient jdbc){return new QueueJobRepository(jdbc);}
    @Bean JobDelegationReader jobDelegationReader(JdbcClient jdbc){return new JobDelegationReader(jdbc);}
    @Bean InstanceEpochRepository instanceEpochRepository(JdbcClient jdbc){return new InstanceEpochRepository(jdbc);}
    @Bean CacheInvalidationRepository cacheInvalidationRepository(JdbcClient jdbc){return new CacheInvalidationRepository(jdbc);}
    @Bean DbSystemIdentifierReader dbSystemIdentifierReader(JdbcClient jdbc){return new DbSystemIdentifierReader(jdbc);}
    @Bean DatabaseEmptinessProbe databaseEmptinessProbe(JdbcClient jdbc){return new DatabaseEmptinessProbe(jdbc);}
    @Bean JobEventPublisher jobEventPublisher(JdbcClient jdbc,Clock clock,ApplicationEventPublisher publisher){return new JobEventPublisher(jdbc,clock,publisher);}
    @Bean JobTerminalCommitter jobTerminalCommitter(JdbcClient jdbc,PlatformTransactionManager manager,Clock clock,ConversationExpiryGuard expiry,JobEventPublisher publisher,List<TerminalCommitObserver> observers,CapacityProfile capacity,SlotLedger slots,ConversationRepository conversations,MessageRepository messages,EvidenceRepository evidence,ToolExecutionRepository executions){return new JobTerminalCommitter(jdbc,manager,clock,expiry,publisher,observers,capacity.jobLifetimeLimit(),slots::terminated,conversations,messages,evidence,executions);}
    @Bean SlotLedger slotLedger(){return new SlotLedger();}
    @Bean IdempotentAdmissionLookup idempotentAdmissionLookup(AdmissionLookupRepository repository,Clock clock){return new IdempotentAdmissionLookup(repository,clock);}
    @Bean @ConditionalOnProperty(name="ai.gateway.inference.mode",havingValue="disabled")
    DisabledInferenceReadiness disabledInferenceReadiness(Environment env,Optional<StructuredClassificationHook> hook){return new DisabledInferenceReadiness(Arrays.asList(env.getActiveProfiles()).contains("verify"),env.getProperty("ai.gateway.test-hooks.enabled",Boolean.class,false),hook.isPresent());}
    @Bean JobAdmissionService jobAdmissionService(PlatformTransactionManager manager,QueueMaintenanceRepository control,IdempotentAdmissionLookup lookup,InferenceReadiness readiness,Optional<com.nori.ai.gateway.integration.inference.InferenceReadinessService> inferenceStates,CapacityProfile capacity,QueueJobRepository queue,
            ConversationRepository conversations,MessageRepository messages,JobRepository jobs,JobEventRepository events,ContentSanitizer sanitizer,Optional<TestHookRegistry> hooks,Optional<PausePointRegistry> pauses,ApplicationEventPublisher publisher,ReadinessGate recovery,com.nori.ai.gateway.app.observability.AuditRecorder audit){return new JobAdmissionService(manager,control,lookup,readiness,inferenceStates,capacity,queue,conversations,messages,jobs,events,sanitizer,hooks,pauses,publisher,recovery,audit);}
    @Bean JobWorker jobWorker(JobDelegationReader delegations,ScopeAssembler scopes,ConversationExpiryGuard expiry,Clock clock,TcSourceAuthorizationPort authorization,WorkflowExecutor workflow,JobTerminalCommitter terminal,JobRepository jobs,InferenceModeProperties inference,Optional<StructuredClassificationHook> hook,Optional<PausePointRegistry> pauses,Optional<FaultInjector> faults,SlotLedger slots,com.nori.ai.gateway.core.port.JobRequestInputReader inputs,Optional<com.nori.ai.gateway.core.llm.ClassificationService> classifier){return new JobWorker(delegations,scopes,expiry,clock,authorization,workflow,terminal,jobs,inference,hook,pauses,faults,slots,inputs,classifier);}
    /**
     * 분류 서비스(Plan P20). 프롬프트는 F0 v2 등록 classify-v2 원문이고 {{REGISTRY}} 는 boundSystem Registry 와 시스템 소유 영역으로 기동 때 한 번 만든다.
     * QRefContext 는 요청별 값이라 빈이 아니다. 답변 토큰은 pin tokenizer 로 센다(SPEC §14.5 방법 (b)).
     */
    @Bean @ConditionalOnProperty(name="ai.gateway.inference.mode",havingValue="enabled")
    com.nori.ai.gateway.core.llm.ClassificationService classificationService(com.nori.ai.gateway.core.port.InferenceClient inference,com.nori.ai.gateway.core.budget.TokenBudgetPolicy policy,
            com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter tokenizer,com.nori.ai.gateway.core.expiry.ExpiryCheckpoint expiry,Optional<com.nori.ai.gateway.core.llm.LlmCallObserver> observer,
            com.nori.ai.gateway.core.registry.ManifestLoader loader,com.nori.ai.gateway.core.registry.CapabilityRegistry registry){
        var systems=new ArrayList<com.nori.ai.gateway.core.llm.ClassificationRegistryText.SystemArea>();
        for(var system:com.nori.ai.gateway.core.registry.SchemaContracts.JSON.readTree(resource("prompts/classify-registry-systems-v2.json")).required("systems"))
            systems.add(new com.nori.ai.gateway.core.llm.ClassificationRegistryText.SystemArea(system.required("system").asString(),texts(system.required("aliases")),texts(system.required("ownedAreas"))));
        var lines=com.nori.ai.gateway.core.llm.ClassificationRegistryText.lines(com.nori.ai.gateway.core.scope.OwnerSystem.TC,loader.loadClasspath("manifests/tc/index.json"),registry::phaseActive);
        String reference=com.nori.ai.gateway.core.llm.ClassificationRegistryText.render(com.nori.ai.gateway.core.scope.OwnerSystem.TC,systems,lines);
        return new com.nori.ai.gateway.core.llm.ClassificationService(inference,policy,tokenizer::countText,expiry,observer.orElse(com.nori.ai.gateway.core.llm.LlmCallObserver.NONE),resource("prompts/classify-v2.txt"),reference);
    }
    static String resource(String path){
        try(var input=JobConfiguration.class.getClassLoader().getResourceAsStream(path)){
            if(input==null)throw new IllegalStateException("프롬프트 자원이 없습니다: "+path);
            return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }catch(java.io.IOException failure){throw new IllegalStateException("프롬프트 자원을 읽을 수 없습니다: "+path,failure);}
    }
    private static List<String> texts(tools.jackson.databind.JsonNode array){
        if(!array.isArray())throw new IllegalStateException("시스템 참고 정보 형식이 잘못되었습니다");
        var values=new ArrayList<String>();for(var value:array){if(!value.isString())throw new IllegalStateException("시스템 참고 정보 형식이 잘못되었습니다");values.add(value.asString());}return List.copyOf(values);
    }
    @Bean StartupRecoveryRunner startupRecoveryRunner(QueueJobRepository jobs,JobTerminalCommitter terminal,JobEventPublisher events,CacheInvalidationRepository caches,Clock clock,ConversationExpiryGuard expiry,CapacityProfile capacity){return new StartupRecoveryRunner(jobs,terminal,events,caches,clock,expiry,capacity);}
    @Bean RecoveryModeRouter recoveryModeRouter(InstanceEpochRepository epochs,DbSystemIdentifierReader identifiers,DatabaseEmptinessProbe empty,RecoveryProperties properties,Clock clock,StartupRecoveryRunner normal,Optional<RestoreRecoveryHandler> restore){return new RecoveryModeRouter(epochs,identifiers,empty,properties,clock,normal,restore);}
    @Bean ReadinessGate readinessGate(List<ReadinessPrecondition> preconditions,ApplicationEventPublisher publisher){return new ReadinessGate(preconditions,publisher);}
    @Bean JobDispatcher jobDispatcher(TransactionTemplate tx,QueueMaintenanceRepository control,QueueJobRepository queue,JobWorker worker,InferenceReadiness inference,ReadinessGate gate,CapacityProfile capacity,Clock clock,SlotLedger slots,Optional<PausePointRegistry> pauses,JobEventPublisher publisher){return new JobDispatcher(tx,control,queue,worker,inference,gate,capacity,clock,slots,pauses,publisher);}
    @Bean JobTimeoutSweeper jobTimeoutSweeper(QueueJobRepository queue,JobTerminalCommitter terminal,SlotLedger slots,CapacityProfile capacity,Clock clock,Optional<PausePointRegistry> pauses,ReadinessGate gate){return new JobTimeoutSweeper(queue,terminal,slots,capacity,clock,pauses,gate);}
    @Bean JobCancellationService jobCancellationService(JobRepository jobs,JobTerminalCommitter terminal,SlotLedger slots){return new JobCancellationService(jobs,terminal,slots);}
    @Bean HealthIndicator recoveryHealthIndicator(ReadinessGate gate){return ()->gate.ready()?Health.up().build():Health.down().build();}
}
