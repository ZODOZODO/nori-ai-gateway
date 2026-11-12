package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.blocks.UiSchemaValidator;
import com.nori.ai.gateway.core.plan.PlanStep;
import com.nori.ai.gateway.core.registry.ToolKind;
import com.nori.ai.gateway.core.result.ResultSourceDeps;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.nori.ai.gateway.core.EvidenceFixtures.*;
import static com.nori.ai.gateway.core.workflow.EvidenceNeed.*;
import static org.assertj.core.api.Assertions.*;

/** Coverage 판정(SPEC §28.8)·보완 round 후보와 한도(Plan §16.3 R3)·상세 보고 투영(R4)의 단일 결함 fixture. */
class EvidenceCoverageEvaluatorTest {
    private final EvidenceCoverageEvaluator evaluator=new EvidenceCoverageEvaluator();
    private static EvidenceRequirement requirement(EvidenceNeed need,boolean added,String... steps){
        List<EvidenceRequirement.Provider> providers=new ArrayList<>();for(String step:steps)providers.add(new EvidenceRequirement.Provider("entry-"+step,step,"cap."+step));
        return new EvidenceRequirement(need,added,providers,List.of(),List.of(),List.of("보고 "+need),need==S?List.of("Port 목록"):List.of());
    }
    private static EvidenceRequirement comparison(){return new EvidenceRequirement(C,false,List.of(),List.of(),List.of(N,I,S,H),List.of(),List.of());}
    private static WorkflowOutcome outcome(WorkflowOutcome.Stage... stages){return new WorkflowOutcome(List.of(stages),List.of(),List.of(),new ResultSourceDeps(List.of(),List.of()),List.of(),List.of(),Optional.empty());}
    private static EvidencePlan plan(EvidenceRequirement... requirements){return new EvidencePlan("v",AnswerMode.D,List.of(requirements),Set.of(),Set.of(),List.of(),List.of());}
    private static EvidencePlan.ProfileStep candidate(EvidenceNeed need,String entry,String capability){return new EvidencePlan.ProfileStep(need,entry,new PlanStep(entry,capability,List.of(),List.of(),null));}
    private static List<EvidenceCoverage> coverage(EvidenceNeed need,EvidenceCoverage.Status status){return List.of(new EvidenceCoverage(need,status,false,List.of(),List.of(),List.of()));}
    private static Map<EvidenceNeed,EvidenceCoverage.Status> statuses(List<EvidenceCoverage> coverage){Map<EvidenceNeed,EvidenceCoverage.Status> map=new EnumMap<>(EvidenceNeed.class);coverage.forEach(c->map.put(c.need(),c.status()));return map;}

    @Test void eachStageOutcomeMapsToExactlyOneDistinguishableStatus() {
        var plan=plan(requirement(S,false,"snapshot"),requirement(H,true,"logs"),requirement(N,false),requirement(I,false,"git"),requirement(R,false,"knowledge"));
        var coverage=evaluator.evaluate(plan,outcome(stage("snapshot","cap.snapshot",ResultStatus.SUCCESS,true,2),stage("logs","cap.logs",ResultStatus.EMPTY,true,0),
            stage("knowledge","cap.knowledge",ResultStatus.DENIED,false,0)));
        assertThat(statuses(coverage)).containsExactly(entry(S,EvidenceCoverage.Status.COLLECTED),entry(H,EvidenceCoverage.Status.NO_DATA),entry(N,EvidenceCoverage.Status.UNSUPPORTED),
            entry(I,EvidenceCoverage.Status.NOT_EXECUTED),entry(R,EvidenceCoverage.Status.DENIED));
        var state=coverage.getFirst();
        assertThat(state.sources()).extracting(EvidenceCoverage.Source::stepId,EvidenceCoverage.Source::count).containsExactly(tuple("snapshot",2));
        assertThat(state.sources().getFirst().observedAt()).isEqualTo(DomainFixtures.NOW);
        assertThat(state.unavailableItems()).containsExactly("Port 목록");
        assertThat(coverage.get(1).serverAdded()).isTrue();
        assertThat(statuses(evaluator.evaluate(plan(requirement(S,false,"snapshot")),outcome(stage("snapshot","cap.snapshot",ResultStatus.SOURCE_UNAVAILABLE,false,0)))))
            .containsEntry(S,EvidenceCoverage.Status.SOURCE_UNAVAILABLE);
        assertThat(statuses(evaluator.evaluate(plan(requirement(H,false,"logs")),outcome(stage("logs","cap.logs",ResultStatus.OUT_OF_RETENTION,false,0)))))
            .containsEntry(H,EvidenceCoverage.Status.OUT_OF_RETENTION);
        assertThat(statuses(evaluator.evaluate(plan(requirement(H,false,"commLogs")),outcome(stage("commLogs","cap.commLogs",ResultStatus.EMPTY,true,2)))))
            .as("출처별 빈 행을 돌려준 EMPTY 도 자료 없음").containsEntry(H,EvidenceCoverage.Status.NO_DATA);
    }

    @Test void comparisonNeverClaimsAResultWithoutBothSidesAndAnApprovedCondition() {
        var oneSide=evaluator.evaluate(plan(requirement(S,false,"snapshot"),requirement(N,false),comparison()),outcome(stage("snapshot","cap.snapshot",ResultStatus.SUCCESS,true,1)));
        assertThat(statuses(oneSide)).containsEntry(C,EvidenceCoverage.Status.COMPARISON_SIDE_MISSING);
        var bothSides=evaluator.evaluate(plan(requirement(S,false,"snapshot"),requirement(I,false,"git"),comparison()),
            outcome(stage("snapshot","cap.snapshot",ResultStatus.SUCCESS,true,1),stage("git","cap.git",ResultStatus.SUCCESS,true,3)));
        assertThat(statuses(bothSides)).containsEntry(C,EvidenceCoverage.Status.NO_APPROVED_CONDITION);
        assertThat(bothSides.getLast().sources()).isEmpty();
        var emptyActual=evaluator.evaluate(plan(requirement(S,false,"snapshot"),requirement(I,false,"git"),comparison()),
            outcome(stage("snapshot","cap.snapshot",ResultStatus.EMPTY,true,0),stage("git","cap.git",ResultStatus.SUCCESS,true,3)));
        assertThat(statuses(emptyActual)).as("기록 없음을 관측으로 비교하지 않는다").containsEntry(C,EvidenceCoverage.Status.COMPARISON_SIDE_MISSING);
    }

    @Test void supplementRoundOnlyForEmptyNeedsWithPreparedCandidatesNotYetPlannedAndAtMostOnce() {
        var trace=candidate(H,"tc.history.trace","tc.trace.search");var dlq=candidate(H,"tc.history.dlq","tc.dlq.list");var model=candidate(N,"tc.normal.model","tc.model.read");
        var plan=new EvidencePlan("v",AnswerMode.B,List.of(requirement(H,false,"logs"),requirement(N,false)),Set.of(),Set.of(),List.of(),List.of(trace,dlq,model));
        var budget=new ToolCallBudget();
        assertThat(evaluator.supplement(plan,coverage(H,EvidenceCoverage.Status.NO_DATA),Set.of("tc.logs.search"),budget)).as("비어 있는 근거의 후보만").containsExactly(trace,dlq);
        assertThat(evaluator.supplement(plan,coverage(H,EvidenceCoverage.Status.NO_DATA),Set.of("tc.logs.search"),budget)).as("round 1회 한도 — 2회째 없음").isEmpty();
        assertThat(evaluator.supplement(plan,coverage(H,EvidenceCoverage.Status.NO_DATA),Set.of("tc.trace.search"),new ToolCallBudget())).as("1차에 계획된 동일 조회 반복 0").containsExactly(dlq);
        assertThat(evaluator.supplement(plan,coverage(H,EvidenceCoverage.Status.NOT_EXECUTED),Set.of(),new ToolCallBudget())).containsExactly(trace,dlq);
        assertThat(evaluator.supplement(plan,coverage(N,EvidenceCoverage.Status.UNSUPPORTED),Set.of(),new ToolCallBudget())).containsExactly(model);
        for(var status:List.of(EvidenceCoverage.Status.DENIED,EvidenceCoverage.Status.SOURCE_UNAVAILABLE,EvidenceCoverage.Status.OUT_OF_RETENTION,EvidenceCoverage.Status.COLLECTED)) {
            var unused=new ToolCallBudget();
            assertThat(evaluator.supplement(plan,coverage(H,status),Set.of(),unused)).as("실패·인가 거부·보존 부족을 재검색으로 숨기지 않음: "+status).isEmpty();
            assertThat(unused.tryStartSupplementRound()).as("후보가 없으면 round 를 쓰지 않는다").isTrue();
        }
        var merged=plan.withSupplement(List.of(trace));
        assertThat(merged.requirements().getFirst().providers()).extracting(EvidenceRequirement.Provider::stepId).containsExactly("logs","tc.history.trace");
        assertThat(merged.requirements().get(1).providers()).isEmpty();
    }

    @Test void explicitLimitsBoundTheSameCountersAndRejectNegativeValues() {
        var budget=new ToolCallBudget(1,1,1,0);
        assertThat(budget.tryAcquire("tc.logs.search",ToolKind.LOG)).isTrue();
        assertThat(budget.tryAcquire("tc.trace.search",ToolKind.TRACE)).as("전체 한도").isFalse();
        assertThat(budget.tryStartSupplementRound()).as("round 한도 0").isFalse();
        assertThatThrownBy(()->new ToolCallBudget(-1,3,2,1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void toolCallBudgetEnforcesTotalSameToolAndKnowledgeSearchLimits() {
        var budget=new ToolCallBudget();
        for(int i=0;i<3;i++)assertThat(budget.tryAcquire("tc.logs.search",ToolKind.LOG)).isTrue();
        assertThat(budget.tryAcquire("tc.logs.search",ToolKind.LOG)).as("같은 Tool 3").isFalse();
        assertThat(budget.tryAcquire("tc.knowledge.search",ToolKind.KNOWLEDGE)).isTrue();
        assertThat(budget.tryAcquire("tc.knowledge.search",ToolKind.KNOWLEDGE)).isTrue();
        assertThat(budget.tryAcquire("tc.knowledge.search",ToolKind.KNOWLEDGE)).as("Knowledge search 2").isFalse();
        assertThat(budget.tryAcquire("tc.knowledge.getSource",ToolKind.KNOWLEDGE)).as("문맥 재조회는 Tool 예산에만 포함").isTrue();
        for(String id:List.of("a","b","c","d","e","f"))assertThat(budget.tryAcquire("tc."+id,ToolKind.BUSINESS)).isTrue();
        assertThat(budget.total()).isEqualTo(12);
        assertThat(budget.tryAcquire("tc.g",ToolKind.BUSINESS)).as("전체 12").isFalse();
        assertThat(budget.total()).isEqualTo(12);
    }

    @Test void detailedReportIsASingleValidatedTableWithMissingItemsAndNoInventedRows() {
        var coverage=List.of(new EvidenceCoverage(S,EvidenceCoverage.Status.COLLECTED,false,List.of(new EvidenceCoverage.Source("snapshot","tc.eqp.getOperationalSnapshot",DomainFixtures.NOW,1)),List.of("설비 운영 상태"),List.of("Port 목록")),
            new EvidenceCoverage(N,EvidenceCoverage.Status.UNSUPPORTED,true,List.of(),List.of("정상 절차 문서"),List.of()),
            new EvidenceCoverage(C,EvidenceCoverage.Status.COMPARISON_SIDE_MISSING,false,List.of(),List.of(),List.of()));
        var block=new ReportSections(new UiSchemaValidator()).project(coverage,OwnerSystem.TC);
        assertThat(block.type()).isEqualTo("table");
        assertThat(block.dataRef()).isNull();
        var rows=block.content().get("rows");
        assertThat(rows.size()).isEqualTo(3);
        assertThat(rows.get(0).path("section").asString()).isEqualTo("관측 사실");
        assertThat(rows.get(0).path("missingItems").toString()).isEqualTo("[\"Port 목록\"]");
        assertThat(rows.get(0).path("observedAt").asString()).isEqualTo(DomainFixtures.NOW.toString());
        assertThat(rows.get(1).path("section").asString()).isEqualTo("미확인");
        assertThat(rows.get(1).path("missingItems").toString()).isEqualTo("[\"정상 절차 문서\"]");
        assertThat(rows.get(1).path("serverAdded").asBoolean()).isTrue();
        assertThat(rows.get(2).path("section").asString()).isEqualTo("비교");
        assertThat(rows.get(2).path("status").asString()).isEqualTo("COMPARISON_SIDE_MISSING");
    }
}
