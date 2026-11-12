package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.plan.PlanStep;
import com.nori.ai.gateway.core.plan.ResourceRef;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;
import static com.nori.ai.gateway.core.EvidenceFixtures.*;
import static com.nori.ai.gateway.core.workflow.EvidenceNeed.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 승인 프로필·Registry view·Manifest 기본 경로로 근거 계획을 조합하는 결정론 규칙(Plan §16.3 R1·R3, SPEC §28.2)과 프로필 구조 불변식.
 * 각 사례는 정상 입력에 결함 하나만 둔다.
 */
class EvidencePlanBuilderTest {
    private final CapabilityRegistry registry=new CapabilityRegistry(new ManifestLoader().loadClasspath("manifests/tc/index.json"),workflows(),Set.of(ImplementationPhase.PHASE_1),new ContractVersionPolicy());
    private final EvidencePlanBuilder builder=new EvidencePlanBuilder(tcProfile());
    private static final Map<String,String> SERVER_VALUES=Map.of("eqp","EQP_001","from","2026-11-04T00:00Z","to","2026-11-04T01:00Z");
    private WorkflowDefinition workflow(TaskType task){return workflows().stream().filter(w->w.taskType()==task).findFirst().orElseThrow();}
    private RegistryView view(){return registry.view(scope());}
    private static List<String> providers(EvidencePlan plan,EvidenceNeed need){return requirement(plan,need).providers().stream().map(EvidenceRequirement.Provider::stepId).toList();}
    private static EvidenceRequirement requirement(EvidencePlan plan,EvidenceNeed need){return plan.requirements().stream().filter(r->r.need()==need).findFirst().orElseThrow();}
    private static List<String> stepIds(List<EvidencePlan.ProfileStep> steps){return steps.stream().map(step->step.step().stepId()).toList();}

    /** 시험용 StepFactory: 프로필 argument 이름을 고정 서버 값으로만 채우고, 값이 없는 이름이 하나라도 있으면 입력 부족이다. */
    private static EvidencePlanBuilder.StepFactory factory(Map<String,String> values,boolean blocked){
        return new EvidencePlanBuilder.StepFactory(){
            @Override public boolean blocked(CapabilityManifest manifest){return blocked;}
            @Override public Optional<PlanStep> create(String stepId,EvidencePlanProfile.Entry entry,CapabilityManifest manifest,List<String> dependsOn){
                var args=SchemaContracts.JSON.createObjectNode();
                for(var input:entry.inputs()){if(!values.containsKey(input.name()))return Optional.empty();args.put(input.name(),values.get(input.name()));}
                return Optional.of(new PlanStep(stepId,manifest.capabilityId(),dependsOn,List.of(),args));
            }
        };
    }
    private EvidencePlan plan(TaskType task,RegistryView view,List<EvidenceNeed> needs,Set<EvidenceNeed> added,EvidencePlanBuilder.StepFactory steps){
        return builder.plan(workflow(task),view,needs,added,AnswerMode.B,steps);
    }

    @Test void profileMappedManifestStepsRunOnlyWhenARequiredEntryOfABoundNeedSelectsThem() {
        var plan=builder.plan(workflow(TaskType.INCIDENT_ANALYSIS),view(),List.of(S,H),Set.of(),AnswerMode.D,factory(SERVER_VALUES,false));
        assertThat(plan.profileMappedSteps()).containsExactlyInAnyOrder("snapshot","commLogs","logs","trace","dlq","git","knowledge");
        assertThat(plan.selectedWorkflowSteps()).as("H 필수 = 통신 로그·로그, Trace·DLQ 는 Manifest 필수여도 고르지 않는다").containsExactlyInAnyOrder("snapshot","commLogs","logs");
        assertThat(plan.deselected("trace")).isTrue();assertThat(plan.deselected("dlq")).isTrue();
        assertThat(providers(plan,S)).containsExactly("snapshot");
        assertThat(providers(plan,H)).containsExactly("commLogs","logs");
        assertThat(stepIds(plan.supplementSteps())).as("선택 항목 Trace·DLQ 는 H 가 비었을 때만").containsExactly("tc.history.trace","tc.history.dlq");
        assertThat(requirement(plan,H).unavailable()).extracting(EvidenceRequirement.Unavailable::capabilityId,EvidenceRequirement.Unavailable::reason)
            .as("권한 선언이 없는 최근 이벤트는 Registry view 에 없다").containsExactly(tuple("tc.event.getRecent",EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE));
        assertThat(plan.addedSteps()).isEmpty();
        assertThat(requirement(plan,S).unavailableItems()).containsExactly("Port 목록","진행 LOT");
        assertThat(plan.answerMode()).isEqualTo(AnswerMode.D);
        assertThat(plan.profileVersion()).isEqualTo("tc-evidence-profile-v1");
    }

    @Test void outsideWorkflowRequiredEntriesBecomeFirstRoundStepsAndOptionalOnesWaitForTheSupplementRound() {
        var plan=plan(TaskType.LIVE_QUERY,view(),List.of(S,H),Set.of(),factory(SERVER_VALUES,false));
        assertThat(stepIds(plan.addedSteps())).as("LQ Manifest 밖 승인 READ 를 1차 계획에 더한다").containsExactly("tc.history.commLogs","tc.history.logs");
        assertThat(providers(plan,H)).containsExactly("tc.history.commLogs","tc.history.logs");
        assertThat(plan.addedSteps().getFirst().step().arguments().toString()).isEqualTo("{\"eqp\":\"EQP_001\",\"from\":\"2026-11-04T00:00Z\",\"to\":\"2026-11-04T01:00Z\"}");
        assertThat(stepIds(plan.supplementSteps())).as("선택 항목은 근거가 비었을 때만").containsExactly("tc.history.trace","tc.history.dlq");
        assertThat(providers(plan,S)).containsExactly("snapshot");
        var normal=plan(TaskType.LIVE_QUERY,view(),List.of(S,N),Set.of(),factory(SERVER_VALUES,false));
        assertThat(stepIds(normal.addedSteps())).containsExactly("tc.normal.model");
        assertThat(normal.addedSteps().getFirst().step().arguments().toString()).as("모델 READ 는 승인 argument 가 없다").isEqualTo("{}");
        assertThat(requirement(normal,N).unavailable()).extracting(EvidenceRequirement.Unavailable::capabilityId,EvidenceRequirement.Unavailable::reason)
            .as("F3 PLANNED Knowledge 는 실행·성공으로 꾸미지 않는다").containsExactly(tuple("tc.knowledge.search",EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE));
    }

    @Test void implementationNeedSelectsTheWorkflowGitStepAndComparisonOnlyDependsOnBothSides() {
        var plan=builder.plan(workflow(TaskType.INCIDENT_ANALYSIS),view(),List.of(H,N,I,C),Set.of(H,N,I),AnswerMode.B,factory(SERVER_VALUES,false));
        assertThat(plan.selectedWorkflowSteps()).containsExactlyInAnyOrder("commLogs","logs","git","trace");
        assertThat(providers(plan,I)).as("I = Git + 당시 배포 Trace").containsExactly("git","trace");
        assertThat(providers(plan,H)).as("I 로 이미 계획된 Trace 는 H 에도 제공자로 센다").containsExactly("commLogs","logs","trace");
        assertThat(stepIds(plan.supplementSteps())).as("같은 조회 반복 0: Trace 는 보완 후보에서 빠진다").containsExactly("tc.history.dlq");
        assertThat(providers(plan,N)).containsExactly("tc.normal.model");
        assertThat(requirement(plan,C).providers()).isEmpty();
        assertThat(requirement(plan,C).dependsOn()).containsExactly(N,I,S,H);
        assertThat(requirement(plan,H).serverAdded()).isTrue();
        assertThat(requirement(plan,C).serverAdded()).isFalse();
    }

    @Test void inactiveCapabilityMissingInputOptionBlockAndUnboundStepFieldNeverBecomeSteps() {
        var withoutModelPermission=registry.view(scope(OwnerSystem.TC,Set.of("EQP_READ")));
        var inactive=plan(TaskType.LIVE_QUERY,withoutModelPermission,List.of(S,N),Set.of(),factory(SERVER_VALUES,false));
        assertThat(inactive.addedSteps()).as("Registry 비활성 → 단계 0").isEmpty();
        assertThat(requirement(inactive,N).unavailable()).extracting(EvidenceRequirement.Unavailable::reason).containsOnly(EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE);
        var noWindow=plan(TaskType.LIVE_QUERY,view(),List.of(S,H),Set.of(),factory(Map.of("eqp","EQP_001"),false));
        assertThat(noWindow.addedSteps()).as("결속할 시간창이 없으면 단계 0").isEmpty();
        assertThat(noWindow.supplementSteps()).isEmpty();
        assertThat(requirement(noWindow,H).unavailable()).extracting(EvidenceRequirement.Unavailable::capabilityId,EvidenceRequirement.Unavailable::reason).contains(
            tuple("tc.commLogs.search",EvidenceRequirement.UnavailableReason.INPUT_UNAVAILABLE),tuple("tc.logs.search",EvidenceRequirement.UnavailableReason.INPUT_UNAVAILABLE));
        var blocked=plan(TaskType.LIVE_QUERY,view(),List.of(S,H),Set.of(),factory(SERVER_VALUES,true));
        assertThat(blocked.addedSteps()).isEmpty();
        assertThat(requirement(blocked,H).unavailable()).extracting(EvidenceRequirement.Unavailable::reason).contains(EvidenceRequirement.UnavailableReason.OPTION_BLOCKED);
        var git=plan(TaskType.LIVE_QUERY,view(),List.of(S,I),Set.of(),factory(SERVER_VALUES,false));
        assertThat(stepIds(git.addedSteps())).as("선행 근거 결과 필드를 받는 workflow 밖 Git 은 바인딩 단계가 없어 입력 부족, 당시 배포 Trace 만 1차").containsExactly("tc.impl.deployTrace");
        assertThat(requirement(git,I).unavailable()).extracting(EvidenceRequirement.Unavailable::capabilityId,EvidenceRequirement.Unavailable::reason)
            .containsExactly(tuple("tc.git.searchCode",EvidenceRequirement.UnavailableReason.INPUT_UNAVAILABLE));
        var evaluator=new EvidenceCoverageEvaluator();var empty=new WorkflowOutcome(List.of(),List.of(),List.of(),new com.nori.ai.gateway.core.result.ResultSourceDeps(List.of(),List.of()),List.of(),List.of(),Optional.empty());
        assertThat(evaluator.evaluate(inactive,empty)).filteredOn(c->c.need()==N).extracting(EvidenceCoverage::status).containsExactly(EvidenceCoverage.Status.UNSUPPORTED);
        assertThat(evaluator.evaluate(noWindow,empty)).filteredOn(c->c.need()==H).extracting(EvidenceCoverage::status).as("입력 부족은 미실행").containsExactly(EvidenceCoverage.Status.NOT_EXECUTED);
    }

    @Test void mutationCapabilitiesAreRejectedAndOtherOwnerEntriesAreNeverPlanned() {
        var model=registry.find("tc.model.read").orElseThrow();
        var writable=new RegistryView(OwnerSystem.TC,List.of(modify(model,"sideEffects","SYSTEM_WRITE")));
        assertThatThrownBy(()->plan(TaskType.LIVE_QUERY,writable,List.of(N),Set.of(),factory(SERVER_VALUES,false))).isInstanceOf(IllegalStateException.class);
        var foreign=new EvidencePlanBuilder(new EvidencePlanProfile(OwnerSystem.TC,"v",List.of(entry("x",N,"wm.inventory.get",true,List.of(),List.of(),List.of(),List.of()))));
        var plan=foreign.plan(workflow(TaskType.LIVE_QUERY),view(),List.of(N),Set.of(),AnswerMode.B,factory(SERVER_VALUES,false));
        assertThat(plan.addedSteps()).isEmpty();
        assertThat(requirement(plan,N).unavailable()).extracting(EvidenceRequirement.Unavailable::reason).containsExactly(EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE);
    }

    @Test void theSameInputAlwaysYieldsTheSamePlanAndGeneralQueryHasNoRequirement() {
        var first=plan(TaskType.LIVE_QUERY,view(),List.of(S,H,N),Set.of(),factory(SERVER_VALUES,false));
        for(int i=0;i<3;i++)assertThat(plan(TaskType.LIVE_QUERY,view(),List.of(S,H,N),Set.of(),factory(SERVER_VALUES,false))).isEqualTo(first);
        assertThat(plan(TaskType.GENERAL_QUERY,view(),List.of(),Set.of(),factory(SERVER_VALUES,false)).requirements()).isEmpty();
    }

    @Test void otherOwnerViewsAndUnboundServerCodesAreRejected() {
        var wm=new RegistryView(OwnerSystem.WM,List.of());
        assertThatThrownBy(()->plan(TaskType.LIVE_QUERY,wm,List.of(S),Set.of(),factory(SERVER_VALUES,false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->plan(TaskType.LIVE_QUERY,view(),List.of(S),Set.of(H),factory(SERVER_VALUES,false))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void profileStructureRejectsDuplicatesCyclesDirectComparisonAndUndeclaredBindingSources() {
        var snapshot=entry("a",S,"tc.eqp.getOperationalSnapshot",List.of(),List.of(),List.of());
        assertThatThrownBy(()->new EvidencePlanProfile(OwnerSystem.TC,"v",List.of(snapshot,snapshot))).as("중복 ID").isInstanceOf(IllegalArgumentException.class);
        var forward=entry("b",I,"tc.git.searchCode",List.of("c"),List.of(),List.of());
        var later=entry("c",H,"tc.logs.search",List.of(),List.of(),List.of());
        assertThatThrownBy(()->new EvidencePlanProfile(OwnerSystem.TC,"v",List.of(forward,later))).as("뒤 항목 참조(순환 가능)").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->entry("d",C,"tc.logs.search",List.of(),List.of(),List.of())).as("C 직접 제공").isInstanceOf(IllegalArgumentException.class);
        var unbound=new EvidencePlanProfile.Entry("e",I,"tc.git.searchCode",false,List.of(new EvidencePlanProfile.Input("q",EvidencePlanProfile.InputSource.STEP_FIELD,"a","items")),List.of(),List.of(),List.of());
        assertThatThrownBy(()->new EvidencePlanProfile(OwnerSystem.TC,"v",List.of(snapshot,unbound))).as("선행 근거가 아닌 바인딩").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EvidencePlanProfile.Input("q",EvidencePlanProfile.InputSource.ARGUMENT,"a","items")).as("argument 에 경로").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new EvidencePlanProfile(OwnerSystem.TC,"v",List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private JobContext job(Map<String,Object> classification){
        return new JobContext("request","conversation",scope(),"delegation",NOW.plusSeconds(1000),new CancellationToken(),InferenceMode.ENABLED,classification,
            new JobRequestInput("message",1,"질문",Map.of("resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001")))));
    }
    private static Map<String,Object> v3(TaskType task,List<String> needs,List<String> added,String mode){
        return Map.of("taskType",task.name(),"evidenceNeeds",needs,"serverAddedNeeds",added,"answerMode",mode,"wireVersion","classify-wire-v3");
    }

    @Test void workflowPlanBuilderUsesOnlyTheServerBoundNeedsAndKeepsTheLegacyPathWithoutThem() {
        var planner=new WorkflowPlanBuilder(registry,builder);
        var window=Map.<String,Object>of("from","2026-11-04T00:00Z","to","2026-11-04T01:00Z");
        var withoutI=planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(v3(TaskType.INCIDENT_ANALYSIS,List.of("S","H"),List.of(),"B")),window);
        assertThat(withoutI.steps()).extracting(step->step.definition().stepId()).as("v3 INC: 프로필이 고르지 않은 Manifest 필수 Trace·DLQ 제외").containsExactly("snapshot","commLogs","logs","summary");
        assertThat(withoutI.definition().steps()).filteredOn(step->step.stepId().equals("summary")).singleElement().satisfies(step->assertThat(step.dependsOn()).as("빠진 단계의 선행으로 잇는다").containsExactly("logs"));
        assertThat(withoutI.evidence()).isPresent();
        var historyOnly=planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(v3(TaskType.INCIDENT_ANALYSIS,List.of("H"),List.of(),"B")),window);
        assertThat(historyOnly.steps()).extracting(step->step.definition().stepId()).as("S 미결속 → snapshot 도 프로필 매핑 단계라 제외").containsExactly("commLogs","logs","summary");
        var withI=planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(v3(TaskType.INCIDENT_ANALYSIS,List.of("S","H","I"),List.of(),"B")),window);
        assertThat(withI.steps()).extracting(step->step.definition().stepId()).containsExactly("snapshot","commLogs","logs","trace","summary","git");
        assertThat(withI.toolPlan().plan()).filteredOn(step->step.stepId().equals("git")).singleElement().satisfies(step->assertThat(step.dependsOn()).as("Git 은 당시 배포 Trace 결과를 선행으로 받는다").containsExactly("trace"));
        var legacy=planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(Map.of("taskType","INCIDENT_ANALYSIS")),window);
        assertThat(legacy.evidence()).isEmpty();
        assertThat(legacy.steps()).extracting(step->step.definition().stepId()).as("F1 고정 workflow 그대로").containsExactly("snapshot","commLogs","logs","trace","dlq","summary","git");
        assertThat(legacy.definition()).isEqualTo(workflow(TaskType.INCIDENT_ANALYSIS));
        var withoutProfile=new WorkflowPlanBuilder(registry);
        assertThatThrownBy(()->withoutProfile.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(v3(TaskType.INCIDENT_ANALYSIS,List.of("H"),List.of(),"B")),window))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(Map.of("taskType","INCIDENT_ANALYSIS","evidenceNeeds",List.of("H","H"),"serverAddedNeeds",List.of(),"answerMode","B")),window))
            .as("결속 형식 위반").isInstanceOf(IllegalArgumentException.class);
    }

    @Test void workflowPlanBuilderAppendsProfileStepsToTheSameValidatedPlanWithServerBoundArguments() {
        var planner=new WorkflowPlanBuilder(registry,builder);
        var window=Map.<String,Object>of("from","2026-11-04T00:00Z","to","2026-11-04T01:00Z");
        var live=planner.build(workflow(TaskType.LIVE_QUERY),view(),job(v3(TaskType.LIVE_QUERY,List.of("S","H","N"),List.of(),"B")),window);
        assertThat(live.steps()).extracting(step->step.definition().stepId()).as("Manifest 기본 경로 뒤에 1차 프로필 단계").containsExactly("snapshot","tc.history.commLogs","tc.history.logs","tc.normal.model");
        assertThat(live.toolPlan().plan()).extracting(PlanStep::capabilityId).containsExactly("tc.eqp.getOperationalSnapshot","tc.commLogs.search","tc.logs.search","tc.model.read");
        var logs=live.toolPlan().plan().get(2);
        assertThat(logs.arguments().toString()).isEqualTo("{\"eqp\":\"EQP_001\",\"from\":\"2026-11-04T00:00Z\",\"to\":\"2026-11-04T01:00Z\"}");
        assertThat(logs.resources()).containsExactly(new ResourceRef("EQP","EQP_001"));
        assertThat(live.toolPlan().plan().get(3).resources()).as("모델 READ 는 EQP 리소스를 받지 않는다").isEmpty();
        assertThat(live.evidence().orElseThrow().supplementSteps()).extracting(step->step.step().capabilityId()).containsExactly("tc.trace.search","tc.dlq.list");
        var knowledge=planner.build(workflow(TaskType.KNOWLEDGE_QUERY),view(),job(v3(TaskType.KNOWLEDGE_QUERY,List.of("N"),List.of(),"B")),window);
        assertThat(knowledge.steps()).extracting(step->step.definition().stepId()).as("PLANNED Knowledge 는 프로필이 고르지 않아 빠지고 승인 모델 READ 만").containsExactly("tc.normal.model");
        assertThat(knowledge.toolPlan().targetResources()).as("남은 단계가 받지 않는 EQP 는 계획 대상에서 뺀다").isEmpty();
        var noWindow=planner.build(workflow(TaskType.LIVE_QUERY),view(),job(v3(TaskType.LIVE_QUERY,List.of("S","H"),List.of(),"B")),Map.of());
        assertThat(noWindow.steps()).extracting(step->step.definition().stepId()).as("시간창 없음 → 입력 부족, 단계 0").containsExactly("snapshot");
    }
}
