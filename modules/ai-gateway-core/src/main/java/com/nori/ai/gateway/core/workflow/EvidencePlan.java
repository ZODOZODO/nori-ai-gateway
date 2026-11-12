package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.plan.PlanStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 결속된 evidenceNeeds·answerMode 와 승인 프로필로 정한 근거 계획(Plan §16.3 R1·R3, SPEC §28.2·§28.8).
 * {@code profileMappedSteps} = Capability 가 승인 프로필 항목에 있는 Manifest 단계(필수 여부와 무관하게 이 계획에서는 프로필이 실행을 정한다),
 * {@code selectedWorkflowSteps} = 그중 결속 근거의 프로필 필수 항목이 골라 1차에 실행할 단계(프로필에 매핑되지 않은 Manifest 필수 단계는 항상 실행),
 * {@code addedSteps} = workflow 밖 승인 프로필 필수 항목으로 1차 계획에 더한 단계, {@code supplementSteps} = 프로필 선택 항목으로 근거가 비었을 때만 보완 round 1회에 쓰는 후보.
 * 모든 단계는 같은 WorkflowExecutor·PlanValidator·Dispatcher 경로로 실행한다.
 */
public record EvidencePlan(String profileVersion, AnswerMode answerMode, List<EvidenceRequirement> requirements, Set<String> profileMappedSteps, Set<String> selectedWorkflowSteps,
        List<ProfileStep> addedSteps, List<ProfileStep> supplementSteps) {
    /** 승인 프로필 항목 하나의 서버 조립 단계. 인자는 승인 argument 이름에 서버 검증 값만 넣는다. */
    public record ProfileStep(EvidenceNeed need, String entryId, PlanStep step) {
        public ProfileStep { Objects.requireNonNull(need); Objects.requireNonNull(entryId); Objects.requireNonNull(step); }
        public EvidenceRequirement.Provider provider() { return new EvidenceRequirement.Provider(entryId, step.stepId(), step.capabilityId()); }
    }
    public EvidencePlan {
        Objects.requireNonNull(profileVersion, "profileVersion"); Objects.requireNonNull(answerMode, "answerMode");
        requirements = List.copyOf(requirements); profileMappedSteps = Set.copyOf(profileMappedSteps); selectedWorkflowSteps = Set.copyOf(selectedWorkflowSteps);
        if (!profileMappedSteps.containsAll(selectedWorkflowSteps)) throw new IllegalArgumentException("프로필에 매핑되지 않은 단계를 고를 수 없습니다");
        addedSteps = List.copyOf(addedSteps); supplementSteps = List.copyOf(supplementSteps);
    }

    /** 이 계획에서 실행하지 않을 Manifest 단계(프로필에 매핑됐지만 고르지 않음). */
    public boolean deselected(String stepId) { return profileMappedSteps.contains(stepId) && !selectedWorkflowSteps.contains(stepId); }

    /** 보완 round 에서 실행한 단계를 해당 근거의 제공자로 더한 계획(Coverage 재평가용). */
    public EvidencePlan withSupplement(List<ProfileStep> executed) {
        List<EvidenceRequirement> next = new ArrayList<>();
        for (EvidenceRequirement requirement : requirements) {
            var value = requirement;
            for (ProfileStep step : executed) if (step.need() == requirement.need()) value = value.withProvider(step.provider());
            next.add(value);
        }
        return new EvidencePlan(profileVersion, answerMode, next, profileMappedSteps, selectedWorkflowSteps, addedSteps, supplementSteps);
    }
}
