package com.nori.ai.gateway.app;

import com.nori.ai.gateway.integration.IntegrationModuleConfiguration;
import com.nori.ai.gateway.persistence.PersistenceModuleConfiguration;
import com.nori.ai.gateway.tcmock.TcMockModuleConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ArchitectureRulesTest {
    @Test void workflowAttemptReceiptCacheAndExactMessageCallsHaveClosedProductionOwners() throws Exception {
        Map<String,Set<String>> rules=new LinkedHashMap<>();
        rules.put("core.authz.StepAttempt.create",Set.of("core.plan.PlanValidator$Prepared"));
        rules.put("core.authz.StepAttempt.belongsTo",Set.of("core.plan.PlanValidator$Prepared"));
        rules.put("core.authz.StepAttempt.claimEvaluation",Set.of("core.authz.SourceAuthorizationStep"));
        rules.put("core.authz.StepAttempt.consume",Set.of("core.authz.SourceAuthorizationStep$StepGrant","core.dispatch.ToolDispatcher"));
        rules.put("core.authz.StepAttempt.close",Set.of("core.authz.SourceAuthorizationStep","core.dispatch.ToolDispatcher"));
        rules.put("core.plan.PlanValidator$Prepared.boundStep",Set.of("core.dispatch.ToolDispatcher"));
        rules.put("core.plan.PlanValidator$Prepared.newAttempt",Set.of("core.workflow.WorkflowExecutor"));
        rules.put("core.authz.SourceAuthorizationStep$StepGrant.consume",Set.of("core.dispatch.ToolDispatcher","core.cache.ToolResultCache"));
        rules.put("core.authz.SourceAuthorizationStep.authorizeStep",Set.of("core.workflow.WorkflowExecutor"));
        rules.put("core.dispatch.ExecutionContext.boundInputs",Set.of("core.dispatch.ToolDispatcher","app.job.DirectSourceToolExecutor"));
        rules.put("core.cache.ToolResultCache$Store.find",Set.of("core.cache.ToolResultCache"));
        rules.put("core.cache.ToolResultCache$Store.put",Set.of("core.cache.ToolResultCache"));
        rules.put("persistence.repository.MessageRepository.findPreviousResolvedTimeWindow",Set.of("app.job.WorkflowExecutionAdapter"));
        rules.put("persistence.repository.MessageRepository.updateResolvedTimeWindow",Set.of("app.job.WorkflowExecutionAdapter"));
        rules.put("persistence.repository.ToolExecutionRepository.insert",Set.of("app.job.WorkflowExecutionRecords"));
        var actual=new HashMap<String,Set<String>>();
        for(var type:mainClasses()) {
            for(var dependency:type.getDirectDependenciesFromSelf())if(type.getPackageName().startsWith(PREFIX+"core.plan"))assertThat(dependency.getTargetClass().getName()).doesNotStartWith(PREFIX+"core.workflow.");
            for(var call:type.getMethodCallsFromSelf()) {
                String owner=call.getTargetOwner().getName();if(!owner.startsWith(PREFIX))continue;
                String target=owner.substring(PREFIX.length())+"."+call.getName(),caller=type.getName().substring(PREFIX.length());
                if(rules.containsKey(target)) {assertThat(rules.get(target)).as("실제 호출자 %s -> %s",caller,target).contains(caller);actual.computeIfAbsent(target,ignored->new HashSet<>()).add(caller);}
                if(owner.equals(PREFIX+"persistence.repository.CacheRepository")&&((call.getName().equals("find")&&call.getTarget().getRawParameterTypes().size()==4)||(call.getName().equals("put")&&call.getTarget().getRawParameterTypes().size()==5)))assertThat(caller).isEqualTo("app.job.WorkflowCacheStore");
                if(owner.equals(PREFIX+"core.port.TcBusinessReadPort")&&call.getName().equals("listDlq")&&caller.equals("app.job.BusinessToolExecutor"))assertThat(call.getTarget().getRawParameterTypes()).hasSize(4);
            }
            for(var call:type.getConstructorCallsFromSelf())if(call.getTargetOwner().getName().equals(PREFIX+"core.dispatch.ExecutionContext$BoundInputs"))assertThat(type.getName()).isEqualTo(PREFIX+"core.workflow.WorkflowExecutor");
        }
        for(var rule:rules.entrySet())assertThat(actual.getOrDefault(rule.getKey(),Set.of())).as(rule.getKey()).containsExactlyInAnyOrderElementsOf(rule.getValue());
    }
    private static final String PREFIX="com.nori.ai.gateway.";
    private static Map<String,Set<String>> queueBoundaries(){
        var rules=new LinkedHashMap<String,Set<String>>();
        rules.put("queue.AdmissionLookupRepository.find(com.nori.ai.gateway.core.scope.VerifiedSystemScope,java.lang.String)",Set.of("app.job.IdempotentAdmissionLookup"));
        rules.put("queue.QueueJobRepository.count(java.lang.String)",Set.of("app.job.JobAdmissionService","app.job.JobDispatcher","app.hooks.TestHookController"));
        rules.put("queue.QueueJobRepository.nonterminal()",Set.of("app.job.JobTimeoutSweeper","app.job.recovery.StartupRecoveryRunner","app.ops.ExpiryEdgeJobCanceller"));
        rules.put("queue.QueueJobRepository.nextQueued()",Set.of("app.job.JobDispatcher"));
        rules.put("queue.QueueJobRepository.start(java.lang.String,java.time.Instant)",Set.of("app.job.JobDispatcher"));
        rules.put("queue.JobDelegationReader.forWorker(java.lang.String)",Set.of("app.job.JobWorker"));
        rules.put("queue.JobTerminalCommitter.commit(java.lang.String,com.nori.ai.gateway.core.job.TerminalSpec)",Set.of("app.job.JobWorker","app.job.JobTimeoutSweeper","app.job.JobCancellationService","app.job.recovery.StartupRecoveryRunner"));
        rules.put("queue.JobTerminalCommitter.commitExpiredQueued(java.lang.String)",Set.of("app.ops.ExpiryEdgeJobCanceller"));
        rules.put("queue.JobEventPublisher.publish(long)",Set.of("persistence.queue.JobTerminalCommitter"));
        rules.put("queue.JobEventPublisher.publishQueued(java.lang.String)",Set.of("app.job.JobDispatcher"));
        rules.put("queue.JobEventPublisher.publishStarted(java.lang.String)",Set.of("app.job.JobDispatcher"));
        rules.put("queue.JobEventPublisher.terminalCommitted(java.lang.String)",Set.of("persistence.queue.JobTerminalCommitter"));
        rules.put("queue.JobEventPublisher.republishTerminal()",Set.of("app.job.recovery.StartupRecoveryRunner"));
        for(String method:List.of("beforeCommit","afterCommit"))rules.put("queue.TerminalCommitObserver."+method+"(java.lang.String)",Set.of("persistence.queue.JobTerminalCommitter"));
        rules.put("queue.recovery.InstanceEpochRepository.find()",Set.of("app.job.recovery.RecoveryModeRouter"));
        rules.put("queue.recovery.InstanceEpochRepository.insertIfAbsent(java.lang.String,java.time.Instant)",Set.of("app.job.recovery.RecoveryModeRouter"));
        rules.put("queue.recovery.InstanceEpochRepository.markRestoreCompleted(java.lang.String,java.time.Instant,java.time.Instant)",Set.of());
        rules.put("queue.recovery.CacheInvalidationRepository.deleteAllCacheEntries()",Set.of("app.job.recovery.StartupRecoveryRunner"));
        rules.put("queue.recovery.DbSystemIdentifierReader.read()",Set.of("app.job.recovery.RecoveryModeRouter"));
        rules.put("queue.recovery.DatabaseEmptinessProbe.isEmpty()",Set.of("app.job.recovery.RecoveryModeRouter"));
        return rules;
    }
    @Test void everyQueueSqlMethodHasClosedSignatureAndActualProductionCaller()throws Exception{
        var rules=queueBoundaries();var observed=new HashMap<String,Set<String>>();var declared=new HashSet<String>();
        for(var type:mainClasses()){
            if(type.getPackageName().startsWith(PREFIX+"persistence.queue")&&!type.reflect().isRecord()&&!type.getName().contains("$")){
                for(var method:type.getMethods())if(method.getOwner().equals(type)&&method.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))declared.add(method.getFullName().substring((PREFIX+"persistence.").length()).replace(", ",","));
            }
            for(var call:type.getMethodCallsFromSelf()){
                String owner=call.getTargetOwner().getName();if(!owner.startsWith(PREFIX+"persistence.queue.")||owner.equals(type.getName())||call.getTargetOwner().reflect().isRecord())continue;
                String signature=owner.substring((PREFIX+"persistence.").length())+"."+call.getName()+"("+String.join(",",call.getTarget().getRawParameterTypes().stream().map(c->c.getName()).toList())+")";
                assertThat(rules).as(signature).containsKey(signature);String caller=type.getName().substring(PREFIX.length());
                assertThat(rules.get(signature)).as("저장 경계 %s -> %s",caller,signature).contains(caller);observed.computeIfAbsent(signature,k->new HashSet<>()).add(caller);
            }
        }
        assertThat(declared).containsExactlyInAnyOrderElementsOf(rules.keySet());
        for(var rule:rules.entrySet())assertThat(observed.getOrDefault(rule.getKey(),Set.of())).as(rule.getKey()).containsExactlyInAnyOrderElementsOf(rule.getValue());
        // 동명 타입·알려지지 않은 메서드·목록 밖 호출자를 허용하는 포괄 예외가 없습니다.
        assertThat(rules).doesNotContainKey("queue.UnrelatedReader.forWorker(java.lang.String)");
        assertThat(rules.get("queue.JobDelegationReader.forWorker(java.lang.String)")).doesNotContain("app.job.JobAdmissionService");
        Path sources=Path.of(System.getProperty("aigw.rootDir"),"modules/adapter/ai-gateway-db-adapter/src/main/java/com/nori/ai/gateway/persistence");
        var permitted=Set.of("repository/JobRepository.java","queue/JobDelegationReader.java","queue/JobTerminalCommitter.java");
        try(var paths=Files.walk(sources)){for(var file:paths.filter(p->p.toString().endsWith(".java")).toList())if(Files.readString(file).contains("delegation_id"))assertThat(permitted).contains(sources.relativize(file).toString().replace('\\','/'));}
    }
    @Test void generalAuditWritesHaveExactlyThePlannedProductionCallers()throws Exception{
        var rules=new LinkedHashMap<String,Set<String>>();
        rules.put("persistence.audit.AuditWriter.write",Set.of("app.observability.AuditRecorder"));
        rules.put("persistence.audit.AuditWriter.writeForJob",Set.of("app.observability.AuditRecorder"));
        rules.put("app.observability.AuditRecorder.write",Set.of("app.security.ServiceTokenAuthFilter","app.job.JobAdmissionService"));
        rules.put("app.observability.AuditRecorder.writeForJob",Set.of("app.job.WorkflowExecutionRecords","app.job.WorkflowExecutionAdapter"));
        rules.put("persistence.repository.AuditRepository.insert",Set.of("persistence.audit.AuditWriter"));
        rules.put("persistence.repository.AuditRepository.insertForJob",Set.of("persistence.audit.AuditWriter"));
        rules.put("core.workflow.WorkflowExecutor$ExecutionRecorder.blocked",Set.of("core.workflow.WorkflowExecutor"));
        var observed=new HashMap<String,Set<String>>();
        for(var type:mainClasses())for(var call:type.getMethodCallsFromSelf()){
            String target=call.getTargetOwner().getName().substring(Math.min(PREFIX.length(),call.getTargetOwner().getName().length()))+"."+call.getName();
            if(!call.getTargetOwner().getName().startsWith(PREFIX)||!rules.containsKey(target))continue;
            String caller=type.getName().substring(PREFIX.length());caller=caller.contains("$")?caller.substring(0,caller.indexOf('$')):caller;
            observed.computeIfAbsent(target,k->new HashSet<>()).add(caller);
        }
        for(var rule:rules.entrySet())assertThat(observed.getOrDefault(rule.getKey(),Set.of())).as(rule.getKey()).containsExactlyInAnyOrderElementsOf(rule.getValue());
    }
    static JavaClasses mainClasses() throws Exception {
        Set<Path> locations = new HashSet<>();
        // 참조된 타입의 자동 해석에 기대지 않고 모든 제품 main 산출물을 직접 수집합니다.
        for (String module : List.of("modules/apps/ai-gateway-app","modules/adapter/ai-gateway-db-adapter","modules/ai-gateway-core","modules/adapter/ai-gateway-inference-adapter","modules/adapter/ai-gateway-tc-mock-adapter","gateway-eval")) {
            Path output = Path.of(System.getProperty("aigw.rootDir"),module,"build/classes/java/main");
            assertThat(output).isDirectory(); locations.add(output);
        }
        return new ClassFileImporter().importPaths(locations);
    }

    @Test void enforcesModuleDirectionsAndScopeConstructorBoundary() throws Exception {
        JavaClasses classes = mainClasses();
        assertThat(classes).isNotEmpty();
        for (var type : classes) {
            String name = type.getName();
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getName();
                boolean invalid = name.startsWith("com.nori.ai.gateway.core.") &&
                    (target.startsWith("org.springframework.") || target.startsWith("java.sql.") || target.startsWith("javax.sql.") ||
                     target.startsWith("java.net.http.") || target.startsWith("com.nori.ai.gateway.") && !target.startsWith("com.nori.ai.gateway.core."));
                invalid |= name.startsWith("com.nori.ai.gateway.persistence.") && target.startsWith("com.nori.ai.gateway.integration.");
                invalid |= name.startsWith("com.nori.ai.gateway.integration.") && target.startsWith("com.nori.ai.gateway.persistence.");
                invalid |= name.startsWith("com.nori.ai.gateway.tcmock.") &&
                    (target.startsWith("com.nori.ai.gateway.persistence.") || target.startsWith("com.nori.ai.gateway.integration.") || target.startsWith("com.nori.ai.gateway.app."));
                invalid |= name.startsWith("com.nori.ai.gateway.eval.") && target.startsWith("com.nori.ai.gateway.") && !target.startsWith("com.nori.ai.gateway.eval.");
                assertThat(invalid).as("모듈 의존 방향: %s -> %s", name, target).isFalse();
            }
            for (var call : type.getConstructorCallsFromSelf()) {
                if (call.getTargetOwner().getName().equals("com.nori.ai.gateway.core.scope.VerifiedSystemScope")) {
                    assertThat(name).isEqualTo("com.nori.ai.gateway.app.security.ScopeAssembler");
                }
            }
            assertThat(type.getAnnotations().stream().map(a -> a.getRawType().getName()))
                .doesNotContain("org.springframework.context.annotation.ComponentScan", "org.springframework.boot.autoconfigure.SpringBootApplication");
        }
    }

    @Test void restrictsRejectionAppendAndEveryVerifiedIdentityConstructorToTheirOwners() throws Exception {
        var expectedConstructors = Map.of(
            "com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository", "com.nori.ai.gateway.persistence.audit.auth.AuthRejectionPersistenceConfiguration",
            "com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppend", "com.nori.ai.gateway.app.security.AuthRejectionAuditor",
            "com.nori.ai.gateway.app.security.VerifiedServiceIdentity", "com.nori.ai.gateway.app.security.SecurityCredentialFiles",
            "com.nori.ai.gateway.app.security.ActorAssertionVerifier$VerifiedAssertion", "com.nori.ai.gateway.app.security.ActorAssertionVerifier");
        var constructorCallers = new HashMap<String,Set<String>>();
        var appendCallers = new HashSet<String>();
        var auditorCallers = new HashSet<String>();
        for (var type : mainClasses()) {
            assertThat(type.getName()).doesNotContain("StubAssertionSigner", "tcmock.testing", "tcmock.verify");
            for (var call : type.getConstructorCallsFromSelf()) {
                String target = call.getTargetOwner().getName();
                if (expectedConstructors.containsKey(target)) {
                    assertThat(type.getName()).as("신원 생성자 호출 경계").isEqualTo(expectedConstructors.get(target));
                    constructorCallers.computeIfAbsent(target,key -> new HashSet<>()).add(type.getName());
                }
            }
            for (var call : type.getMethodCallsFromSelf()) {
                if (call.getTargetOwner().getName().equals("com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository") && call.getName().equals("append"))
                    appendCallers.add(type.getName());
                if (call.getTargetOwner().getName().equals("com.nori.ai.gateway.app.security.AuthRejectionAuditor") && Set.of("unauthenticated","serviceVerified","actorVerified").contains(call.getName()))
                    auditorCallers.add(type.getName());
            }
        }
        assertThat(constructorCallers.keySet()).containsExactlyInAnyOrderElementsOf(expectedConstructors.keySet());
        assertThat(appendCallers).containsExactly("com.nori.ai.gateway.app.security.AuthRejectionAuditor");
        assertThat(auditorCallers).containsExactly("com.nori.ai.gateway.app.security.ServiceTokenAuthFilter");
    }
}
