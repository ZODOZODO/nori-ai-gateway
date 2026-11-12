package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;
import static com.nori.ai.gateway.core.EvidenceFixtures.*;
import static com.nori.ai.gateway.core.workflow.EvidenceNeed.*;
import static org.assertj.core.api.Assertions.*;

/** 승인 프로필·Registry view·기존 workflow 로 근거 계획을 조합하는 결정론 규칙(Plan §16.3 R1·R3)과 프로필 구조 불변식. */
class EvidencePlanBuilderTest {
    private final CapabilityRegistry registry=new CapabilityRegistry(new ManifestLoader().loadClasspath("manifests/tc/index.json"),workflows(),Set.of(ImplementationPhase.PHASE_1),new ContractVersionPolicy());
    private final EvidencePlanBuilder builder=new EvidencePlanBuilder(tcProfile());
    private WorkflowDefinition workflow(TaskType task){return workflows().stream().filter(w->w.taskType()==task).findFirst().orElseThrow();}
    private RegistryView view(){return registry.view(scope());}
    private static List<String> providers(EvidencePlan plan,EvidenceNeed need){return plan.requirements().stream().filter(r->r.need()==need).findFirst().orElseThrow().providers().stream().map(EvidenceRequirement.Provider::stepId).toList();}
    private static EvidenceRequirement requirement(EvidencePlan plan,EvidenceNeed need){return plan.requirements().stream().filter(r->r.need()==need).findFirst().orElseThrow();}

    @Test void incidentStateAndHistoryKeepTheMandatoryDefaultPathAndSelectNoOptionalStep() {
        var plan=builder.plan(workflow(TaskType.INCIDENT_ANALYSIS),view(),List.of(S,H),Set.of(),AnswerMode.D);
        assertThat(plan.selectedOptionalSteps()).isEmpty();
        assertThat(providers(plan,S)).containsExactly("snapshot");
        assertThat(providers(plan,H)).containsExactly("commLogs","logs","trace","dlq");
        assertThat(requirement(plan,H).unavailable()).extracting(EvidenceRequirement.Unavailable::capabilityId,EvidenceRequirement.Unavailable::reason)
            .containsExactly(tuple("tc.event.getRecent",EvidenceRequirement.UnavailableReason.NOT_IN_WORKFLOW));
        assertThat(requirement(plan,S).unavailableItems()).containsExactly("Port 목록","진행 LOT");
        assertThat(plan.answerMode()).isEqualTo(AnswerMode.D);
        assertThat(plan.profileVersion()).isEqualTo("tc-evidence-profile-v1");
    }

    @Test void implementationNeedSelectsGitAndPlannedKnowledgeIsReportedInactiveNotFaked() {
        var plan=builder.plan(workflow(TaskType.INCIDENT_ANALYSIS),view(),List.of(H,N,I,C),Set.of(H,N,I),AnswerMode.B);
        assertThat(plan.selectedOptionalSteps()).containsExactly("git");
        assertThat(providers(plan,N)).as("F3 PLANNED Knowledge 는 제공자가 아니다").isEmpty();
        assertThat(requirement(plan,N).unavailable()).extracting(EvidenceRequirement.Unavailable::capabilityId,EvidenceRequirement.Unavailable::reason)
            .containsExactly(tuple("tc.knowledge.search",EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE),tuple("tc.model.read",EvidenceRequirement.UnavailableReason.NOT_IN_WORKFLOW));
        assertThat(requirement(plan,C).providers()).isEmpty();
        assertThat(requirement(plan,C).dependsOn()).containsExactly(N,I,S,H);
        assertThat(requirement(plan,H).serverAdded()).isTrue();
        assertThat(requirement(plan,C).serverAdded()).isFalse();
    }

    @Test void theSameInputAlwaysYieldsTheSamePlanAndGeneralQueryHasNoRequirement() {
        var first=builder.plan(workflow(TaskType.INCIDENT_ANALYSIS),view(),List.of(S,H,I),Set.of(),AnswerMode.B);
        for(int i=0;i<3;i++)assertThat(builder.plan(workflow(TaskType.INCIDENT_ANALYSIS),view(),List.of(S,H,I),Set.of(),AnswerMode.B)).isEqualTo(first);
        assertThat(builder.plan(workflow(TaskType.GENERAL_QUERY),view(),List.of(),Set.of(),AnswerMode.B).requirements()).isEmpty();
    }

    @Test void otherOwnerViewsAndUnboundServerCodesAreRejected() {
        var wm=new RegistryView(OwnerSystem.WM,List.of());
        assertThatThrownBy(()->builder.plan(workflow(TaskType.LIVE_QUERY),wm,List.of(S),Set.of(),AnswerMode.B)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->builder.plan(workflow(TaskType.LIVE_QUERY),view(),List.of(S),Set.of(H),AnswerMode.B)).isInstanceOf(IllegalArgumentException.class);
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

    private JobContext job(TaskType task,Map<String,Object> classification){
        return new JobContext("request","conversation",scope(),"delegation",NOW.plusSeconds(1000),new CancellationToken(),InferenceMode.ENABLED,classification,
            new JobRequestInput("message",1,"질문",Map.of("resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001")))));
    }
    private static Map<String,Object> v3(TaskType task,List<String> needs,List<String> added,String mode){
        return Map.of("taskType",task.name(),"evidenceNeeds",needs,"serverAddedNeeds",added,"answerMode",mode,"wireVersion","classify-wire-v3");
    }

    @Test void workflowPlanBuilderUsesOnlyTheServerBoundNeedsAndKeepsTheLegacyPathWithoutThem() {
        var planner=new WorkflowPlanBuilder(registry,builder);
        var window=Map.<String,Object>of("from","2026-11-04T00:00Z","to","2026-11-04T01:00Z");
        var withoutI=planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(TaskType.INCIDENT_ANALYSIS,v3(TaskType.INCIDENT_ANALYSIS,List.of("S","H"),List.of(),"B")),window);
        assertThat(withoutI.steps()).extracting(step->step.definition().stepId()).containsExactly("snapshot","commLogs","logs","trace","dlq","summary");
        assertThat(withoutI.evidence()).isPresent();
        var withI=planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(TaskType.INCIDENT_ANALYSIS,v3(TaskType.INCIDENT_ANALYSIS,List.of("S","H","I"),List.of(),"B")),window);
        assertThat(withI.steps()).extracting(step->step.definition().stepId()).containsExactly("snapshot","commLogs","logs","trace","dlq","summary","git");
        var legacy=planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(TaskType.INCIDENT_ANALYSIS,Map.of("taskType","INCIDENT_ANALYSIS")),window);
        assertThat(legacy.evidence()).isEmpty();
        assertThat(legacy.steps()).extracting(step->step.definition().stepId()).contains("git");
        var withoutProfile=new WorkflowPlanBuilder(registry);
        assertThatThrownBy(()->withoutProfile.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(TaskType.INCIDENT_ANALYSIS,v3(TaskType.INCIDENT_ANALYSIS,List.of("H"),List.of(),"B")),window))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->planner.build(workflow(TaskType.INCIDENT_ANALYSIS),view(),job(TaskType.INCIDENT_ANALYSIS,Map.of("taskType","INCIDENT_ANALYSIS","evidenceNeeds",List.of("H","H"),"serverAddedNeeds",List.of(),"answerMode","B")),window))
            .as("결속 형식 위반").isInstanceOf(IllegalArgumentException.class);
    }
}
