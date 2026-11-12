package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.registry.RegistryView;
import com.nori.ai.gateway.core.registry.WorkflowDefinition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 결속된 근거 요구를 승인 프로필·Registry view·기존 workflow 단계로 조합한다(SPEC §28.2 "실행 단계는 서버가 승인한 시스템별 근거 프로필과 Registry로 선택",
 * Plan §16.3 R1·R3). 단계를 새로 만들지 않고 기존 workflow 단계만 고르며, 필수 단계는 기본 경로로 유지한다.
 * 선택 단계는 그 Capability 가 요구 근거의 승인 제공자이고 Registry view 에서 활성일 때만 고른다. LLM 출력은 도구를 고르지 않는다.
 */
public final class EvidencePlanBuilder {
    private final EvidencePlanProfile profile;

    public EvidencePlanBuilder(EvidencePlanProfile profile) { this.profile = Objects.requireNonNull(profile, "profile"); }

    public EvidencePlanProfile profile() { return profile; }

    public EvidencePlan plan(WorkflowDefinition definition, RegistryView view, List<EvidenceNeed> needs, Set<EvidenceNeed> serverAdded, AnswerMode answerMode) {
        Objects.requireNonNull(definition); Objects.requireNonNull(view); Objects.requireNonNull(answerMode);
        if (definition.ownerSystem() != profile.ownerSystem() || view.ownerSystem() != profile.ownerSystem()) throw new IllegalArgumentException("근거 프로필 소유 범위가 다릅니다");
        if (!needs.containsAll(serverAdded)) throw new IllegalArgumentException("서버 보완 코드가 요구 근거에 없습니다");
        List<EvidenceRequirement> requirements = new ArrayList<>();
        Set<String> selected = new LinkedHashSet<>();
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
                var step = definition.steps().stream().filter(value -> entry.capabilityId().equals(value.capabilityId())).findFirst();
                if (step.isEmpty()) { unavailable.add(new EvidenceRequirement.Unavailable(entry.id(), entry.capabilityId(), EvidenceRequirement.UnavailableReason.NOT_IN_WORKFLOW)); continue; }
                if (view.find(entry.capabilityId()).isEmpty()) { unavailable.add(new EvidenceRequirement.Unavailable(entry.id(), entry.capabilityId(), EvidenceRequirement.UnavailableReason.CAPABILITY_INACTIVE)); continue; }
                providers.add(new EvidenceRequirement.Provider(entry.id(), step.get().stepId(), entry.capabilityId()));
                if (step.get().optional()) selected.add(step.get().stepId());
            }
            requirements.add(new EvidenceRequirement(need, serverAdded.contains(need), providers, unavailable, List.of(), distinct(report), distinct(missing)));
        }
        return new EvidencePlan(profile.version(), answerMode, requirements, selected);
    }

    private static List<String> distinct(List<String> values) { return List.copyOf(new LinkedHashSet<>(values)); }
}
