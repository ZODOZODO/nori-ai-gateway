package com.nori.ai.gateway.core.workflow;

import java.util.List;
import java.util.Objects;

/**
 * 요구 근거 하나와 그 대상(SPEC §28.2·§28.8). {@code providers} 는 이번 실행 계획에서 이 근거를 제공할 단계, {@code unavailable} 은 승인 프로필에는 있으나
 * 이번 workflow·Registry view 에서 실행할 수 없는 항목(사유 포함)이다. C 는 원천 단계 없이 {@code dependsOn} 근거로만 판단한다.
 */
public record EvidenceRequirement(EvidenceNeed need, boolean serverAdded, List<Provider> providers, List<Unavailable> unavailable,
        List<EvidenceNeed> dependsOn, List<String> reportItems, List<String> unavailableItems) {
    /** 이번 계획에 선택된 제공 단계. */
    public record Provider(String entryId, String stepId, String capabilityId) {
        public Provider { Objects.requireNonNull(entryId); Objects.requireNonNull(stepId); Objects.requireNonNull(capabilityId); }
    }
    public enum UnavailableReason { NOT_IN_WORKFLOW, CAPABILITY_INACTIVE }
    public record Unavailable(String entryId, String capabilityId, UnavailableReason reason) {
        public Unavailable { Objects.requireNonNull(entryId); Objects.requireNonNull(capabilityId); Objects.requireNonNull(reason); }
    }
    public EvidenceRequirement {
        Objects.requireNonNull(need, "need");
        providers = List.copyOf(providers); unavailable = List.copyOf(unavailable); dependsOn = List.copyOf(dependsOn);
        reportItems = List.copyOf(reportItems); unavailableItems = List.copyOf(unavailableItems);
        if (need == EvidenceNeed.C ? !providers.isEmpty() || dependsOn.isEmpty() : !dependsOn.isEmpty()) throw new IllegalArgumentException("근거 요구 형식이 잘못되었습니다");
    }
}
