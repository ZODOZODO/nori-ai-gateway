package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.result.ResultStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 실행 결과로 요구 근거별 Coverage 를 만들고(SPEC §28.8), 근거 보완 round 후보를 판단한다(§28.8 추가 조회 ①~③, Plan §16.3 R3).
 * Coverage 는 서버 단계 결과만으로 정하며 LLM 판단·관측값 생성은 없다. C 는 승인 비교 조건이 없으면 판정하지 않는다.
 */
public final class EvidenceCoverageEvaluator {
    private static final Set<EvidenceCoverage.Status> SUPPLEMENTABLE = EnumSet.of(EvidenceCoverage.Status.NO_DATA, EvidenceCoverage.Status.NOT_EXECUTED, EvidenceCoverage.Status.UNSUPPORTED);
    private static final Set<ResultStatus> OBSERVED = Set.of(ResultStatus.SUCCESS, ResultStatus.EMPTY, ResultStatus.PARTIAL, ResultStatus.STALE, ResultStatus.CONFLICT);

    public List<EvidenceCoverage> evaluate(EvidencePlan plan, WorkflowOutcome outcome) {
        Objects.requireNonNull(plan); Objects.requireNonNull(outcome);
        Map<EvidenceNeed,EvidenceCoverage> byNeed = new EnumMap<>(EvidenceNeed.class);
        List<EvidenceCoverage> result = new ArrayList<>();
        for (EvidenceRequirement requirement : plan.requirements()) {
            if (requirement.need() == EvidenceNeed.C) continue;
            var coverage = source(requirement, outcome);
            byNeed.put(requirement.need(), coverage);
        }
        for (EvidenceRequirement requirement : plan.requirements())
            result.add(requirement.need() == EvidenceNeed.C ? comparison(requirement, byNeed) : byNeed.get(requirement.need()));
        return List.copyOf(result);
    }

    private static EvidenceCoverage source(EvidenceRequirement requirement, WorkflowOutcome outcome) {
        List<EvidenceCoverage.Source> sources = new ArrayList<>();
        boolean observedEmpty = false, denied = false, unavailable = false, retention = false;
        for (var provider : requirement.providers()) {
            for (var stage : outcome.stages()) {
                if (!stage.stepId().equals(provider.stepId())) continue;
                var status = stage.result().status();
                if (stage.observed() && OBSERVED.contains(status)) {
                    // EMPTY 는 행 수와 무관하게 자료 없음이다(출처별 행을 돌려주는 통신 로그처럼 빈 관측도 행을 가질 수 있음).
                    if (status != ResultStatus.EMPTY && stage.count() > 0) sources.add(new EvidenceCoverage.Source(stage.stepId(), provider.capabilityId(), stage.result().observedAt(), stage.count()));
                    else observedEmpty = true;
                }
                else if (status == ResultStatus.DENIED) denied = true;
                else if (status == ResultStatus.SOURCE_UNAVAILABLE || status == ResultStatus.FAILED) unavailable = true;
                else if (status == ResultStatus.OUT_OF_RETENTION) retention = true;
            }
        }
        EvidenceCoverage.Status status;
        if (!sources.isEmpty()) status = EvidenceCoverage.Status.COLLECTED;
        else if (observedEmpty) status = EvidenceCoverage.Status.NO_DATA;
        else if (requirement.providers().isEmpty()) status = requirement.unavailable().stream().allMatch(value -> value.reason() == EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE)
            ? EvidenceCoverage.Status.UNSUPPORTED : EvidenceCoverage.Status.NOT_EXECUTED;
        else if (denied) status = EvidenceCoverage.Status.DENIED;
        else if (unavailable) status = EvidenceCoverage.Status.SOURCE_UNAVAILABLE;
        else if (retention) status = EvidenceCoverage.Status.OUT_OF_RETENTION;
        else status = EvidenceCoverage.Status.NOT_EXECUTED;
        return new EvidenceCoverage(requirement.need(), status, requirement.serverAdded(), sources, requirement.reportItems(), requirement.unavailableItems());
    }

    /** C 는 양쪽 근거가 모두 수집된 경우에도 승인 비교 조건(v1 프로필에 없음)이 없으면 충족/불충족을 만들지 않는다. */
    private static EvidenceCoverage comparison(EvidenceRequirement requirement, Map<EvidenceNeed,EvidenceCoverage> byNeed) {
        boolean expected = collected(byNeed, EvidenceNeed.N) || collected(byNeed, EvidenceNeed.I);
        boolean actual = collected(byNeed, EvidenceNeed.S) || collected(byNeed, EvidenceNeed.H);
        var status = expected && actual ? EvidenceCoverage.Status.NO_APPROVED_CONDITION : EvidenceCoverage.Status.COMPARISON_SIDE_MISSING;
        return new EvidenceCoverage(EvidenceNeed.C, status, requirement.serverAdded(), List.of(), List.of(), List.of());
    }

    private static boolean collected(Map<EvidenceNeed,EvidenceCoverage> byNeed, EvidenceNeed need) {
        var coverage = byNeed.get(need);
        return coverage != null && coverage.collected();
    }

    /**
     * 근거 보완 round 후보(SPEC §28.8 ①): 요구 근거가 비었고(NO_DATA·NOT_EXECUTED·UNSUPPORTED) 근거 계획이 미리 준비한 승인 보완 후보(Registry 활성·READ·입력 결속 완료) 중
     * 아직 계획되지 않은 Capability 만. 같은 실행 입력의 동일 조회를 반복하지 않는다. 실패·인가 거부·보존 부족은 재검색으로 숨기지 않으므로 후보가 아니다.
     * round 한도(1)는 {@link ToolCallBudget} 로 세며, 후보가 없으면 round 를 쓰지 않는다. 호출 한도는 실제 dispatcher 직전에 같은 budget 으로 검사한다.
     */
    public List<EvidencePlan.ProfileStep> supplement(EvidencePlan plan, List<EvidenceCoverage> coverage, Set<String> plannedCapabilities, ToolCallBudget budget) {
        Objects.requireNonNull(plan); Objects.requireNonNull(coverage); Objects.requireNonNull(plannedCapabilities); Objects.requireNonNull(budget);
        Set<EvidenceNeed> empty = EnumSet.noneOf(EvidenceNeed.class);
        for (EvidenceCoverage value : coverage) if (SUPPLEMENTABLE.contains(value.status())) empty.add(value.need());
        List<EvidencePlan.ProfileStep> candidates = new ArrayList<>();
        Set<String> chosen = new HashSet<>();
        for (var step : plan.supplementSteps())
            if (empty.contains(step.need()) && !plannedCapabilities.contains(step.step().capabilityId()) && chosen.add(step.step().capabilityId())) candidates.add(step);
        if (candidates.isEmpty() || !budget.tryStartSupplementRound()) return List.of();
        return List.copyOf(candidates);
    }
}
