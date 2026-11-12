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
import com.nori.ai.gateway.core.workflow.*;
import com.nori.ai.gateway.core.workflow.WorkflowExecutor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static com.nori.ai.gateway.core.DomainFixtures.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 승인 프로필 단계와 근거 보완 round 가 같은 WorkflowExecutor·PlanValidator·Dispatcher·recorder 경로로 실행되는지(SPEC §28.2·§28.8, Plan §16.3 R3).
 * LQ 에 S·H 를 결속하면 Manifest 밖 통신 로그·로그 READ 가 1차에 실행되고, H 가 비면 선택 항목 Trace·DLQ 가 보완 round 1회로 실행된다. 각 사례는 결함 하나만 둔다.
 */
class EvidenceSupplementExecutorTest {
    private final ContractVersionPolicy versions=new ContractVersionPolicy();
    private final List<CapabilityManifest> manifests=new ManifestLoader().loadClasspath("manifests/tc/index.json");
    private final CapabilityRegistry registry=new CapabilityRegistry(manifests,workflows(),Set.of(ImplementationPhase.PHASE_1),versions);
    private final PlanValidator validator=new PlanValidator(registry,new PlanInjectionSeam.NoOp(),CLOCK,manifests.size(),100);
    private final com.nori.ai.gateway.core.scope.VerifiedSystemScope scope;
    private final List<String> dispatched=new ArrayList<>();
    /** 로그 단계가 돌려줄 행 수(0 이면 H 가 빈다). */
    private int logRows;
    /** 마지막으로 계획한 Job(계획과 실행이 같은 Job 결속을 쓴다). */
    private JobContext job;

    EvidenceSupplementExecutorTest() {
        var base=new WorkflowAuthorizationPreparationTest.Fixture().scope;
        scope=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(base.boundSystem(),base.authenticatedServiceRef(),base.actorRef(),base.sub(),DomainFixtures.scope().perm(),base.pdg(),base.issuedAt(),base.expiresAt(),base.requestId());
    }

    private WorkflowExecutor executor(Supplier<ToolCallBudget> budgets) {
        Map<String,SourcePermissionMap.Entry> mappings=new LinkedHashMap<>();
        manifests.stream().filter(m->m.toolKind()!=ToolKind.BUSINESS&&!m.requiredPermission().isEmpty()).forEach(m->mappings.put(m.sourceAllowlistRef(),new SourcePermissionMap.Entry(Set.copyOf(m.requiredPermission()),true)));
        var authorization=new SourceAuthorizationStep(new SourcePermissionMap(mappings),(call,required)->new TcSourceAuthorizationPort.Allowed(required,"b".repeat(64)));
        List<ToolExecutor> tools=new ArrayList<>();
        for(var kind:List.of(ToolKind.BUSINESS,ToolKind.LOG,ToolKind.TRACE))tools.add(new ToolExecutor(){
            public ToolKind toolKind(){return kind;}
            public ToolResultEnvelope execute(PlanStep step,CapabilityManifest manifest,com.nori.ai.gateway.core.scope.VerifiedSystemScope scope,ExecutionContext context) {
                dispatched.add(manifest.capabilityId());
                List<Map<String,String>> items=new ArrayList<>();
                if(manifest.capabilityId().equals("tc.eqp.getOperationalSnapshot"))items.add(Map.of("text","EQP_001 RUN"));
                if(manifest.capabilityId().equals("tc.logs.search"))for(int i=0;i<logRows;i++)items.add(Map.of("text","LOG_"+i));
                var raw=result(manifest);raw=changeResult(raw,"toolExecutionId",UUID.randomUUID().toString());raw=changeResult(raw,"metadata",new ToolResultMetadata(raw.metadata().sourceRef(),manifest.sourceFreshnessPolicy(),false,List.of()));
                return changeResult(raw,"blocks",List.of(new ResultBlock("table",raw.dataRef(),SchemaContracts.JSON.createObjectNode().set("items",SchemaContracts.JSON.valueToTree(items)))));
            }
        });
        var dispatcher=new ToolDispatcher(registry,validator,new ToolResultContractValidator(versions),new DispatchInjectionSeam.NoOp(),tools,CLOCK);
        var expiry=new ExpiryCheckpoint(new ConversationExpiryGuard(),CLOCK);
        var cache=new ToolResultCache(new ToolResultCacheTest.Store(),validator,new ResultAccessEvaluator(),(permissions,documents)->Map.of(),expiry,CLOCK);
        return new WorkflowExecutor(registry,validator,dispatcher,authorization,expiry,CLOCK,cache,new BlockBuilder(new UiSchemaValidator()),new WorkflowExecutorTest.Recording(new ArrayList<>()),
            new LogSummarizer(),new GeneralQueryHelpBuilder(),new EvidenceCoverageEvaluator(),budgets);
    }
    private WorkflowExecutionPlan plan(List<String> needs) { return plan(TaskType.LIVE_QUERY,needs); }
    private WorkflowExecutionPlan plan(TaskType task,List<String> needs) {
        var fields=Map.<String,Object>of("context",Map.of("timezone","UTC","referenceTime",NOW.toString(),"timeWindow",Map.of("from",NOW.minusSeconds(60).toString(),"to",NOW.toString())),"resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001")));
        var classification=Map.<String,Object>of("taskType",task.name(),"evidenceNeeds",needs,"serverAddedNeeds",List.of(),"answerMode","B","wireVersion","classify-wire-v3");
        job=job(classification,fields);
        return new WorkflowPlanBuilder(registry,new EvidencePlanBuilder(EvidenceFixtures.tcProfile())).build(registry.workflow(scope,task).orElseThrow(),registry.view(scope),job);
    }
    private JobContext job(Map<String,Object> classification,Map<String,Object> fields){
        return new JobContext("request","conversation",scope,"d".repeat(22),NOW.plusSeconds(600),new CancellationToken(),InferenceMode.ENABLED,classification,new JobRequestInput("message",1,"질문",fields));
    }
    private WorkflowOutcome run(WorkflowExecutor executor,WorkflowExecutionPlan plan){return executor.run(job,plan,new WorkflowAuthorizationPreparationTest.Fixture().resolver);}
    private static EvidenceCoverage history(WorkflowOutcome outcome){return outcome.coverage().stream().filter(c->c.need()==EvidenceNeed.H).findFirst().orElseThrow();}

    @Test void outsideManifestProfileStepsRunInTheFirstRoundAndCollectedHistoryNeedsNoSupplement() {
        logRows=2;
        var outcome=run(executor(ToolCallBudget::new),plan(List.of("S","H")));
        assertThat(outcome.rejection()).isEmpty();
        assertThat(dispatched).as("LQ Manifest 밖 승인 READ 가 같은 Dispatcher 로 1차 실행, H 수집됨 → 보완 0").containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search","tc.logs.search");
        assertThat(history(outcome).status()).isEqualTo(EvidenceCoverage.Status.COLLECTED);
        assertThat(outcome.toolExecutionRefs()).hasSize(3);
    }

    @Test void emptyHistoryRunsTheOptionalEntriesInOneSupplementRoundWithoutRepeatingAnyQuery() {
        logRows=0;
        var executor=executor(ToolCallBudget::new);var plan=plan(List.of("S","H"));
        var outcome=run(executor,plan);
        assertThat(outcome.rejection()).isEmpty();
        assertThat(dispatched).as("1차 3건 + 보완 round 1회 2건, 같은 조회 반복 0").containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search","tc.logs.search","tc.trace.search","tc.dlq.list");
        assertThat(outcome.stages()).extracting(WorkflowOutcome.Stage::stepId).containsExactly("snapshot","tc.history.commLogs","tc.history.logs","tc.history.trace","tc.history.dlq");
        assertThat(history(outcome).status()).as("보완 뒤에도 비어 있지만 2회째 round 는 없다").isEqualTo(EvidenceCoverage.Status.NO_DATA);
        assertThat(outcome.toolExecutionRefs()).as("보완 호출도 같은 recorder").hasSize(5);
        dispatched.clear();
        run(executor,plan);
        assertThat(dispatched).as("한도와 round 는 Job 실행마다 새로 센다").hasSize(5);
    }

    @Test void supplementCallsCountAgainstTheSameJobBudgetAndAZeroRoundLimitRunsNone() {
        logRows=0;
        var limited=run(executor(()->new ToolCallBudget(4,3,2,1)),plan(List.of("S","H")));
        assertThat(dispatched).as("전체 한도 4 = 1차 3 + 보완 1").containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search","tc.logs.search","tc.trace.search");
        assertThat(limited.trace()).contains(new WorkflowOutcome.Trace("tc.history.dlq",WorkflowOutcome.TraceOutcome.TOOL_LIMIT_REACHED));
        dispatched.clear();
        run(executor(()->new ToolCallBudget(12,3,2,0)),plan(List.of("S","H")));
        assertThat(dispatched).as("보완 round 한도 0").containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search","tc.logs.search");
    }

    @Test void incidentTraceAndDlqAreProfileChoicesNotManifestMandatesInTheBoundNeedsPath() {
        logRows=2;
        run(executor(ToolCallBudget::new),plan(TaskType.INCIDENT_ANALYSIS,List.of("S","H")));
        assertThat(dispatched).as("v3 INC, H 수집됨 → Trace·DLQ 0").containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search","tc.logs.search");
        dispatched.clear();logRows=0;
        var empty=run(executor(ToolCallBudget::new),plan(TaskType.INCIDENT_ANALYSIS,List.of("S","H")));
        assertThat(dispatched).as("v3 INC, 1차 H 가 비면 보완 round 로 Trace·DLQ 각 1회").containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search","tc.logs.search","tc.trace.search","tc.dlq.list");
        assertThat(empty.stages()).extracting(WorkflowOutcome.Stage::stepId).containsExactly("snapshot","commLogs","logs","tc.history.trace","tc.history.dlq");
    }
}
