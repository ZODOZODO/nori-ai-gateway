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
    @Bean JobTerminalCommitter jobTerminalCommitter(JdbcClient jdbc,PlatformTransactionManager manager,Clock clock,ConversationExpiryGuard expiry,JobEventPublisher publisher,List<TerminalCommitObserver> observers,CapacityProfile capacity,SlotLedger slots){return new JobTerminalCommitter(jdbc,manager,clock,expiry,publisher,observers,capacity.jobLifetimeLimit(),slots::terminated);}
    @Bean SlotLedger slotLedger(){return new SlotLedger();}
    @Bean IdempotentAdmissionLookup idempotentAdmissionLookup(AdmissionLookupRepository repository,Clock clock){return new IdempotentAdmissionLookup(repository,clock);}
    @Bean @ConditionalOnProperty(name="ai.gateway.inference.mode",havingValue="disabled")
    DisabledInferenceReadiness disabledInferenceReadiness(Environment env,Optional<StructuredClassificationHook> hook){return new DisabledInferenceReadiness(Arrays.asList(env.getActiveProfiles()).contains("verify"),env.getProperty("ai.gateway.test-hooks.enabled",Boolean.class,false),hook.isPresent());}
    @Bean JobAdmissionService jobAdmissionService(PlatformTransactionManager manager,QueueMaintenanceRepository control,IdempotentAdmissionLookup lookup,InferenceReadiness readiness,CapacityProfile capacity,QueueJobRepository queue,
            ConversationRepository conversations,MessageRepository messages,JobRepository jobs,JobEventRepository events,ContentSanitizer sanitizer,Optional<TestHookRegistry> hooks,Optional<PausePointRegistry> pauses,ApplicationEventPublisher publisher,ReadinessGate recovery){return new JobAdmissionService(manager,control,lookup,readiness,capacity,queue,conversations,messages,jobs,events,sanitizer,hooks,pauses,publisher,recovery);}
    @Bean JobWorker jobWorker(JobDelegationReader delegations,ScopeAssembler scopes,ConversationExpiryGuard expiry,Clock clock,TcSourceAuthorizationPort authorization,WorkflowExecutor workflow,JobTerminalCommitter terminal,JobRepository jobs,InferenceModeProperties inference,Optional<StructuredClassificationHook> hook,Optional<PausePointRegistry> pauses,Optional<FaultInjector> faults,SlotLedger slots,com.nori.ai.gateway.core.port.JobRequestInputReader inputs){return new JobWorker(delegations,scopes,expiry,clock,authorization,workflow,terminal,jobs,inference,hook,pauses,faults,slots,inputs);}
    @Bean StartupRecoveryRunner startupRecoveryRunner(QueueJobRepository jobs,JobTerminalCommitter terminal,JobEventPublisher events,CacheInvalidationRepository caches,Clock clock,ConversationExpiryGuard expiry,CapacityProfile capacity){return new StartupRecoveryRunner(jobs,terminal,events,caches,clock,expiry,capacity);}
    @Bean RecoveryModeRouter recoveryModeRouter(InstanceEpochRepository epochs,DbSystemIdentifierReader identifiers,DatabaseEmptinessProbe empty,RecoveryProperties properties,Clock clock,StartupRecoveryRunner normal,Optional<RestoreRecoveryHandler> restore){return new RecoveryModeRouter(epochs,identifiers,empty,properties,clock,normal,restore);}
    @Bean ReadinessGate readinessGate(List<ReadinessPrecondition> preconditions,ApplicationEventPublisher publisher){return new ReadinessGate(preconditions,publisher);}
    @Bean JobDispatcher jobDispatcher(TransactionTemplate tx,QueueMaintenanceRepository control,QueueJobRepository queue,JobWorker worker,InferenceReadiness inference,ReadinessGate gate,CapacityProfile capacity,Clock clock,SlotLedger slots,Optional<PausePointRegistry> pauses,JobEventPublisher publisher){return new JobDispatcher(tx,control,queue,worker,inference,gate,capacity,clock,slots,pauses,publisher);}
    @Bean JobTimeoutSweeper jobTimeoutSweeper(QueueJobRepository queue,JobTerminalCommitter terminal,SlotLedger slots,CapacityProfile capacity,Clock clock,Optional<PausePointRegistry> pauses,ReadinessGate gate){return new JobTimeoutSweeper(queue,terminal,slots,capacity,clock,pauses,gate);}
    @Bean JobCancellationService jobCancellationService(JobRepository jobs,JobTerminalCommitter terminal,SlotLedger slots){return new JobCancellationService(jobs,terminal,slots);}
    @Bean HealthIndicator recoveryHealthIndicator(ReadinessGate gate){return ()->gate.ready()?Health.up().build():Health.down().build();}
}
