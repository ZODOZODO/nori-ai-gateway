package com.nori.ai.gateway.core.workflow;

import java.util.List;
import java.util.Objects;

/**
 * 요구 근거 하나와 그 대상(SPEC §28.2·§28.8). {@code providers} 는 이번 실행 계획(1차 또는 보완 round)에서 이 근거를 제공할 단계, {@code unavailable} 은 승인 프로필에는 있으나
 * 이번 Registry view·입력·요청 옵션으로 실행할 수 없는 항목(사유 포함)이다. C 는 원천 단계 없이 {@code dependsOn} 근거로만 판단한다.
 */
public record EvidenceRequirement(EvidenceNeed need, boolean serverAdded, List<Provider> providers, List<Unavailable> unavailable,
        List<EvidenceNeed> dependsOn, List<String> reportItems, List<String> unavailableItems) {
    /** 이번 계획에 선택된 제공 단계. */
    public record Provider(String entryId, String stepId, String capabilityId) {
        public Provider { Objects.requireNonNull(entryId); Objects.requireNonNull(stepId); Objects.requireNonNull(capabilityId); }
    }
    /** CAPABILITY_INACTIVE = 현재 Registry view 에 없음(미등록·PLANNED·권한 밖), INPUT_UNAVAILABLE = 승인 바인딩으로 만들 입력·선행 근거 없음, OPTION_BLOCKED = 요청 옵션으로 차단. */
    public enum UnavailableReason { CAPABILITY_INACTIVE, INPUT_UNAVAILABLE, OPTION_BLOCKED }
    public record Unavailable(String entryId, String capabilityId, UnavailableReason reason) {
        public Unavailable { Objects.requireNonNull(entryId); Objects.requireNonNull(capabilityId); Objects.requireNonNull(reason); }
    }
    public EvidenceRequirement {
        Objects.requireNonNull(need, "need");
        providers = List.copyOf(providers); unavailable = List.copyOf(unavailable); dependsOn = List.copyOf(dependsOn);
        reportItems = List.copyOf(reportItems); unavailableItems = List.copyOf(unavailableItems);
        if (need == EvidenceNeed.C ? !providers.isEmpty() || dependsOn.isEmpty() : !dependsOn.isEmpty()) throw new IllegalArgumentException("근거 요구 형식이 잘못되었습니다");
    }

    EvidenceRequirement withProvider(Provider provider) {
        var next = new java.util.ArrayList<>(providers); next.add(provider);
        return new EvidenceRequirement(need, serverAdded, next, unavailable, dependsOn, reportItems, unavailableItems);
    }
}
