package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.blocks.*;
import com.nori.ai.gateway.core.cache.ToolResultCache;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.expiry.*;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class WorkflowExecutorTest {
    /** 실제 호출 기록만 모으고 사전 차단 기록은 단계 결과 상태로 따로 둔다. */
    record Recording(List<ToolDispatcher.InvocationRecord> invocations,List<ResultStatus> blocked) implements com.nori.ai.gateway.core.workflow.WorkflowExecutor.ExecutionRecorder {
        Recording(List<ToolDispatcher.InvocationRecord> invocations){this(invocations,new ArrayList<>());}
        @Override public void record(JobContext job,ToolDispatcher.InvocationRecord invocation){invocations.add(invocation);}
        @Override public void blocked(JobContext job,String capabilityId,ResultStatus status,Optional<ExecutionContext.DispatchTrace> trace){blocked.add(status);}
    }
    @Test void processorUsesOnlyLogAncestorsInDeclarationOrderAndCacheUnknownGitMakesNoCacheAuthorization() {
        var loader=new ManifestLoader();var manifests=loader.loadClasspath("manifests/tc/index.json");var versions=new ContractVersionPolicy();var registry=new CapabilityRegistry(manifests,workflows(),Set.of(ImplementationPhase.PHASE_1),versions);
        var validator=new PlanValidator(registry,new PlanInjectionSeam.NoOp(),CLOCK,9,100);var scope=new WorkflowAuthorizationPreparationTest.Fixture().scope;
        scope=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(scope.boundSystem(),scope.authenticatedServiceRef(),scope.actorRef(),scope.sub(),DomainFixtures.scope().perm(),scope.pdg(),scope.issuedAt(),scope.expiresAt(),scope.requestId());
        List<String> queries=new ArrayList<>();List<Set<String>> authorizations=new ArrayList<>();Map<String,SourcePermissionMap.Entry> mappings=new LinkedHashMap<>();
        manifests.stream().filter(manifest->manifest.toolKind()!=ToolKind.BUSINESS&&!manifest.requiredPermission().isEmpty()).forEach(manifest->mappings.put(manifest.sourceAllowlistRef(),new SourcePermissionMap.Entry(Set.copyOf(manifest.requiredPermission()),true)));
        var authorization=new SourceAuthorizationStep(new SourcePermissionMap(mappings),(call,required)->{authorizations.add(required);return new TcSourceAuthorizationPort.Allowed(required,"b".repeat(64));});
        List<ToolExecutor> tools=new ArrayList<>();for(var kind:List.of(ToolKind.BUSINESS,ToolKind.LOG,ToolKind.TRACE,ToolKind.GIT))tools.add(new ToolExecutor(){
            public ToolKind toolKind(){return kind;}
            public ToolResultEnvelope execute(PlanStep step,CapabilityManifest manifest,com.nori.ai.gateway.core.scope.VerifiedSystemScope scope,ExecutionContext context) {
                if(kind==ToolKind.GIT){queries.add(step.arguments().path("q").asString());assertThat(context.boundInputs().orElseThrow().bindings().keySet()).containsExactly("trace");}
                String text=kind==ToolKind.LOG?"LOG_CODE":kind==ToolKind.TRACE?"TRACE_LEAK":"BUSINESS_LEAK";
                var raw=result(manifest);raw=changeResult(raw,"toolExecutionId",UUID.randomUUID().toString());raw=changeResult(raw,"metadata",new ToolResultMetadata(raw.metadata().sourceRef(),manifest.sourceFreshnessPolicy(),false,List.of()));
                return changeResult(raw,"blocks",List.of(new ResultBlock("table",raw.dataRef(),SchemaContracts.JSON.createObjectNode().set("items",SchemaContracts.JSON.valueToTree(List.of(Map.of("text",text)))))));
            }
        });
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),tools,CLOCK);var expiry=new ExpiryCheckpoint(new ConversationExpiryGuard(),CLOCK);var store=new ToolResultCacheTest.Store();var cache=new ToolResultCache(store,validator,new ResultAccessEvaluator(),(permissions,documents)->Map.of(),expiry,CLOCK);var records=new ArrayList<ToolDispatcher.InvocationRecord>();
        var executor=new com.nori.ai.gateway.core.workflow.WorkflowExecutor(registry,validator,dispatcher,authorization,expiry,CLOCK,cache,new BlockBuilder(new UiSchemaValidator()),new Recording(records),new com.nori.ai.gateway.core.workflow.LogSummarizer(),new GeneralQueryHelpBuilder());
        var fields=Map.<String,Object>of("context",Map.of("timezone","UTC","referenceTime",NOW.toString(),"timeWindow",Map.of("from",NOW.minusSeconds(60).toString(),"to",NOW.toString())),"resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001")));
        var job=new JobContext("request","conversation",scope,"d".repeat(22),NOW.plusSeconds(600),new CancellationToken(),InferenceMode.DISABLED,Map.of("taskType","INCIDENT_ANALYSIS"),new JobRequestInput("message",1,"질문",fields));
        var definition=registry.workflow(scope,TaskType.INCIDENT_ANALYSIS).orElseThrow();var plan=new com.nori.ai.gateway.core.workflow.WorkflowPlanBuilder(registry).build(definition,registry.view(scope),job);
        var resolver=new WorkflowAuthorizationPreparationTest.Fixture().resolver;
        for(int i=0;i<2;i++)assertThat(executor.run(job,plan,resolver).rejection()).isEmpty();
        assertThat(queries).containsExactly("LOG_CODE","LOG_CODE");assertThat(authorizations.stream().filter(required->required.contains("AI_CODE_READ"))).hasSize(2);
        assertThat(store.calls).hasSize(6);assertThat(records).hasSize(8);
    }
    @Test void aGeneralQueryWithFalseFlagsAndANonexistentSelectedReferenceNeverReadsOrDispatchesAnything() {
        var fixture=new WorkflowAuthorizationPreparationTest.Fixture();var loader=new ManifestLoader();var versions=new ContractVersionPolicy();var registry=new CapabilityRegistry(loader.loadClasspath("manifests/tc/index.json"),workflows(),Set.of(ImplementationPhase.PHASE_1),versions);
        var validator=new PlanValidator(registry,new PlanInjectionSeam.NoOp(),CLOCK,9,100);var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),List.of(),CLOCK);var expiry=new ExpiryCheckpoint(new ConversationExpiryGuard(),CLOCK);var store=new ToolResultCacheTest.Store();var cache=new ToolResultCache(store,validator,new ResultAccessEvaluator(),(permissions,documents)->Map.of(),expiry,CLOCK);var recorded=new ArrayList<ToolDispatcher.InvocationRecord>();
        var executor=new com.nori.ai.gateway.core.workflow.WorkflowExecutor(registry,validator,dispatcher,fixture.source,expiry,CLOCK,cache,new BlockBuilder(new UiSchemaValidator()),new Recording(recorded),new com.nori.ai.gateway.core.workflow.LogSummarizer(),new GeneralQueryHelpBuilder());
        var fields=Map.<String,Object>of("resourceRefs",List.of(Map.of("type","EQP","ref","EQP-not-existing")),"options",Map.of("allowTools",false,"allowKnowledge",false));
        var job=new JobContext("request","conversation",fixture.scope,"d".repeat(22),NOW.plusSeconds(600),new CancellationToken(),InferenceMode.DISABLED,Map.of("taskType","GENERAL_QUERY"),new JobRequestInput("message",1,"도움말",fields));
        var definition=registry.workflow(job.scope(),TaskType.GENERAL_QUERY).orElseThrow();var plan=new com.nori.ai.gateway.core.workflow.WorkflowPlanBuilder(registry).build(definition,registry.view(job.scope()),job);assertThat(plan.toolPlan().targetResources()).isEmpty();assertThat(job.requestInput().requestFields()).isEqualTo(fields);assertThat(job.requestInput().selectedResourceRefs()).containsExactly(new ResourceRef("EQP","EQP-not-existing"));
        var outcome=executor.run(job,plan,fixture.resolver);assertThat(outcome.rejection()).isEmpty();assertThat(outcome.blocks()).singleElement().satisfies(block->assertThat(block.type()).isEqualTo("table"));assertThat(fixture.reads).hasValue(0);assertThat(fixture.legacy).hasValue(0);assertThat(fixture.authCalls).hasValue(0);assertThat(fixture.toolCalls).hasValue(0);assertThat(store.calls).isEmpty();assertThat(recorded).isEmpty();assertThat(outcome.toolExecutionRefs()).isEmpty();
    }

}
