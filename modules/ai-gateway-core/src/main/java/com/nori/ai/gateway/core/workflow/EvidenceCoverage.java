package com.nori.ai.gateway.core.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 요구 근거 하나의 서버 수집 상태(SPEC §28.8). 사실 자료의 상태이며 LLM 의 자신감 수치가 아니다. ResultStatus 를 늘리지 않는다(§28.2).
 * 자료 없음·미지원·권한 거부·Source 장애·보존 범위 밖·미실행·관측 불가를 구분한다.
 */
public record EvidenceCoverage(EvidenceNeed need, Status status, boolean serverAdded, List<Source> sources, List<String> reportItems, List<String> unavailableItems) {
    public enum Status {
        /** 제공 단계가 관측 행을 돌려줬다. */ COLLECTED,
        /** 제공 단계가 관측했지만 행이 0개다(원천 완전성이 확인된 범위에서만 "발생하지 않음"의 근거). */ NO_DATA,
        /** 승인 프로필에 제공자가 없거나 이번 workflow·Registry 에서 실행할 수 없다. */ UNSUPPORTED,
        /** 제공 단계가 인가 거부로 끝났다. */ DENIED,
        /** 제공 단계의 Source 가 사용 불가였다. */ SOURCE_UNAVAILABLE,
        /** 보존 범위 밖이다. */ OUT_OF_RETENTION,
        /** 제공 단계를 실행하지 않았다(입력 없음·선택 생략·계획 거부). */ NOT_EXECUTED,
        /** C: 비교할 기대(N/I)·실제(S/H) 근거 중 한쪽이 없다. */ COMPARISON_SIDE_MISSING,
        /** C: 양쪽 근거는 있으나 승인 프로필에 비교 조건 정의가 없어 판정하지 않는다(문자열 차이로 원인을 확정하지 않음). */ NO_APPROVED_CONDITION
    }
    /** 근거를 준 단계(관측 시각 포함). Source 원문은 담지 않는다. */
    public record Source(String stepId, String capabilityId, Instant observedAt, int count) {
        public Source { Objects.requireNonNull(stepId); Objects.requireNonNull(capabilityId); }
    }
    public EvidenceCoverage {
        Objects.requireNonNull(need); Objects.requireNonNull(status);
        sources = List.copyOf(sources); reportItems = List.copyOf(reportItems); unavailableItems = List.copyOf(unavailableItems);
    }
    public boolean collected() { return status == Status.COLLECTED; }
}
