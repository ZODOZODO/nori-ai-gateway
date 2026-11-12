package com.nori.ai.gateway.core.workflow;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 결속된 evidenceNeeds·answerMode 와 승인 프로필로 정한 근거 계획(Plan §16.3 R1·R3). {@code selectedOptionalSteps} 는 기존 workflow 의 선택 단계 중
 * 요구 근거를 제공해서 실행 대상이 된 단계이며, 필수 단계는 기존 기본 경로로 항상 실행한다.
 */
public record EvidencePlan(String profileVersion, AnswerMode answerMode, List<EvidenceRequirement> requirements, Set<String> selectedOptionalSteps) {
    public EvidencePlan {
        Objects.requireNonNull(profileVersion, "profileVersion"); Objects.requireNonNull(answerMode, "answerMode");
        requirements = List.copyOf(requirements); selectedOptionalSteps = Set.copyOf(selectedOptionalSteps);
    }
}
