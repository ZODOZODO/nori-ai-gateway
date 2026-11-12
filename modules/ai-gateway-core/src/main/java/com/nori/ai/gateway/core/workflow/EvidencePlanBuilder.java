package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.plan.PlanStep;
import com.nori.ai.gateway.core.registry.CapabilityManifest;
import com.nori.ai.gateway.core.registry.RegistryView;
import com.nori.ai.gateway.core.registry.SideEffects;
import com.nori.ai.gateway.core.registry.WorkflowDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 결속된 근거 요구를 승인 프로필·Registry view·기존 workflow 로 조합한다(SPEC §28.2 "실행 단계는 서버가 승인한 시스템별 근거 프로필과 Registry로 선택",
 * Plan §16.3 R1·R3). taskType Manifest 는 기본 경로·필수 의존성의 자료이고 선택 단계의 상한이 아니다.
 * <ul>
 * <li>Manifest 의 필수 단계는 그대로 유지한다. 프로필 항목의 Capability 가 workflow 단계면 그 단계를 제공자로 쓰고, 선택 단계면 실행 대상으로 고른다.</li>
 * <li>workflow 밖 항목은 Registry view 활성·READ·승인 argument 결속·선행 근거가 모두 갖춰질 때만 단계로 만든다. 프로필 {@code required}=true 는 1차 계획,
 *     false 는 근거가 비었을 때만 쓰는 보완 round 후보다. 선행 근거 결과 필드를 입력으로 받는 workflow 밖 항목은 그 바인딩을 만드는 workflow 단계가 없으므로 입력 부족이다.</li>
 * <li>실행할 수 없는 항목은 단계를 만들지 않고 사유(비활성·입력 부족·옵션 차단)로 남긴다. LLM 출력은 도구를 고르지 않는다.</li>
 * </ul>
 */
public final class EvidencePlanBuilder {
    /** 승인 프로필 항목의 PlanStep 을 서버 검증 값(검증 리소스·해석된 시간창·결속 q)으로 만든다. 요청 옵션 판단도 같은 서버 입력으로 한다. */
    public interface StepFactory {
        boolean blocked(CapabilityManifest manifest);
        /** 승인 argument 를 하나라도 결속할 수 없으면 empty. */
        Optional<PlanStep> create(String stepId, EvidencePlanProfile.Entry entry, CapabilityManifest manifest, List<String> dependsOn);
    }

    private final EvidencePlanProfile profile;

    public EvidencePlanBuilder(EvidencePlanProfile profile) { this.profile = Objects.requireNonNull(profile, "profile"); }

    public EvidencePlanProfile profile() { return profile; }

    public EvidencePlan plan(WorkflowDefinition definition, RegistryView view, List<EvidenceNeed> needs, Set<EvidenceNeed> serverAdded, AnswerMode answerMode, StepFactory steps) {
        Objects.requireNonNull(definition); Objects.requireNonNull(view); Objects.requireNonNull(answerMode); Objects.requireNonNull(steps);
        if (definition.ownerSystem() != profile.ownerSystem() || view.ownerSystem() != profile.ownerSystem()) throw new IllegalArgumentException("근거 프로필 소유 범위가 다릅니다");
        if (!needs.containsAll(serverAdded)) throw new IllegalArgumentException("서버 보완 코드가 요구 근거에 없습니다");
        List<EvidenceRequirement> requirements = new ArrayList<>();
        Set<String> selected = new LinkedHashSet<>();
        Map<String,String> plannedByCapability = new HashMap<>(), stepByEntry = new HashMap<>();
        List<EvidencePlan.ProfileStep> added = new ArrayList<>(), supplement = new ArrayList<>();
        for (EvidenceNeed need : needs) {
            if (need == EvidenceNeed.C) {
                requirements.add(new EvidenceRequirement(need, serverAdded.contains(need), List.of(), List.of(), List.of(EvidenceNeed.N, EvidenceNeed.I, EvidenceNeed.S, EvidenceNeed.H), List.of(), List.of()));
                continue;
            }
            List<EvidenceRequirement.Provider> providers = new ArrayList<>();
            List<EvidenceRequirement.Unavailable> unavailable = new ArrayList<>();
            List<String> report = new ArrayList<>(), missing = new ArrayList<>();
            for (var entry : profile.entriesFor(need)) {
                report.addAll(entry.reportItems()); missing.addAll(entry.unavailableItems());
                var manifest = view.find(entry.capabilityId());
                if (manifest.isEmpty()) { unavailable.add(unavailable(entry, EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE)); continue; }
                requireRead(manifest.get());
                var workflowStep = definition.steps().stream().filter(value -> entry.capabilityId().equals(value.capabilityId())).findFirst();
                if (workflowStep.isPresent()) {
                    providers.add(new EvidenceRequirement.Provider(entry.id(), workflowStep.get().stepId(), entry.capabilityId()));
                    if (workflowStep.get().optional()) selected.add(workflowStep.get().stepId());
                    stepByEntry.put(entry.id(), workflowStep.get().stepId());
                    continue;
                }
                String planned = plannedByCapability.get(entry.capabilityId());
                if (planned != null) { providers.add(new EvidenceRequirement.Provider(entry.id(), planned, entry.capabilityId())); stepByEntry.put(entry.id(), planned); continue; }
                if (steps.blocked(manifest.get())) { unavailable.add(unavailable(entry, EvidenceRequirement.UnavailableReason.OPTION_BLOCKED)); continue; }
                var step = predecessors(entry, stepByEntry).flatMap(dependsOn -> steps.create(entry.id(), entry, manifest.get(), dependsOn));
                if (step.isEmpty()) { unavailable.add(unavailable(entry, EvidenceRequirement.UnavailableReason.INPUT_UNAVAILABLE)); continue; }
                var profileStep = new EvidencePlan.ProfileStep(need, entry.id(), step.get());
                if (entry.required()) {
                    added.add(profileStep); providers.add(profileStep.provider());
                    plannedByCapability.put(entry.capabilityId(), entry.id()); stepByEntry.put(entry.id(), entry.id());
                } else supplement.add(profileStep);
            }
            requirements.add(new EvidenceRequirement(need, serverAdded.contains(need), providers, unavailable, List.of(), distinct(report), distinct(missing)));
        }
        // 같은 조회를 두 번 만들지 않는다: 1차에 계획된 Capability 와 이미 고른 후보는 보완 후보에서 뺀다.
        Set<String> candidates = new LinkedHashSet<>();
        List<EvidencePlan.ProfileStep> distinctSupplement = new ArrayList<>();
        for (var step : supplement)
            if (!plannedByCapability.containsKey(step.step().capabilityId()) && candidates.add(step.step().capabilityId())) distinctSupplement.add(step);
        return new EvidencePlan(profile.version(), answerMode, requirements, selected, added, distinctSupplement);
    }

    /**
     * workflow 밖 항목의 선행 근거 단계. 선행 근거 결과 필드 바인딩(STEP_FIELD), 아직 계획되지 않은 선행 근거, 선행 근거가 있는 보완 후보(보완 round 는 별도 계획으로
     * 준비해 1차 단계를 선행으로 가리킬 수 없음)는 empty(입력 부족)다.
     */
    private static Optional<List<String>> predecessors(EvidencePlanProfile.Entry entry, Map<String,String> stepByEntry) {
        if (entry.inputs().stream().anyMatch(input -> input.source() == EvidencePlanProfile.InputSource.STEP_FIELD)) return Optional.empty();
        if (!entry.required() && !entry.dependsOn().isEmpty()) return Optional.empty();
        List<String> steps = new ArrayList<>();
        for (String dependency : entry.dependsOn()) {
            String step = stepByEntry.get(dependency);
            if (step == null) return Optional.empty();
            steps.add(step);
        }
        return Optional.of(List.copyOf(steps));
    }

    /** 프로필 loader 가 기동 시 거부하지만, 다른 경로로 만든 프로필이 업무 변경 Capability 를 계획에 넣지 못하게 한 번 더 막는다. */
    private static void requireRead(CapabilityManifest manifest) {
        if (!"READ".equals(manifest.operation()) || manifest.sideEffects() != SideEffects.NONE) throw new IllegalStateException("승인 근거 프로필 항목이 READ 가 아닙니다");
    }

    private static EvidenceRequirement.Unavailable unavailable(EvidencePlanProfile.Entry entry, EvidenceRequirement.UnavailableReason reason) {
        return new EvidenceRequirement.Unavailable(entry.id(), entry.capabilityId(), reason);
    }

    private static List<String> distinct(List<String> values) { return List.copyOf(new LinkedHashSet<>(values)); }
}
