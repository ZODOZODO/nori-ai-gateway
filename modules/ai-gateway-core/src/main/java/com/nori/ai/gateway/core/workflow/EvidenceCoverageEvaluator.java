package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.result.ResultStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 실행 결과로 요구 근거별 Coverage 를 만들고(SPEC §28.8), 근거 보완 round 후보를 판단한다(§28.8 추가 조회 ①~③, Plan §16.3 R3).
 * Coverage 는 서버 단계 결과만으로 정하며 LLM 판단·관측값 생성은 없다. C 는 승인 비교 조건이 없으면 판정하지 않는다.
 */
public final class EvidenceCoverageEvaluator {
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
                    if (stage.count() > 0) sources.add(new EvidenceCoverage.Source(stage.stepId(), provider.capabilityId(), stage.result().observedAt(), stage.count()));
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
        else if (requirement.providers().isEmpty()) status = EvidenceCoverage.Status.UNSUPPORTED;
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
     * 근거 보완 round 후보(SPEC §28.8 ①): 요구 근거가 비었고(NO_DATA·NOT_EXECUTED) 승인 프로필의 같은 근거 항목 중 활성·미실행이며 입력이 모두 준비된 READ 만.
     * 실패·인가 거부·보존 부족은 재검색으로 숨기지 않으므로 후보가 아니다. round 한도(1)와 호출 한도는 {@link ToolCallBudget} 가 함께 막는다.
     * 반환 목록이 비면 보완 round 를 만들지 않는다.
     */
    public List<EvidencePlanProfile.Entry> supplement(EvidencePlanProfile profile, List<EvidenceCoverage> coverage, Predicate<String> activeCapability,
            Set<String> executedCapabilities, Predicate<EvidencePlanProfile.Entry> inputsReady, ToolCallBudget budget) {
        List<EvidencePlanProfile.Entry> candidates = new ArrayList<>();
        for (EvidenceCoverage value : coverage) {
            if (value.status() != EvidenceCoverage.Status.NO_DATA && value.status() != EvidenceCoverage.Status.NOT_EXECUTED) continue;
            for (var entry : profile.entriesFor(value.need()))
                if (activeCapability.test(entry.capabilityId()) && !executedCapabilities.contains(entry.capabilityId()) && inputsReady.test(entry) && !candidates.contains(entry))
                    candidates.add(entry);
        }
        if (candidates.isEmpty() || !budget.tryStartSupplementRound()) return List.of();
        return List.copyOf(candidates);
    }
}
