package com.nori.ai.gateway.core.workflow;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 분류 wire v3 {@code n} 의 근거 종류(SPEC §28.2). 시스템 독립 의미 코드이며 업무 키워드·도구 ID 를 갖지 않는다.
 * 선언 순서 S,H,N,I,C,R 이 정규화 순서다(§28.3 ②).
 */
public enum EvidenceNeed {
    /** 현재 상태. */ S,
    /** 실제 발생 이력. */ H,
    /** 정상 절차·선행 조건. */ N,
    /** 구현·적용 버전. */ I,
    /** 기대와 실제의 파생 비교(새 원천 아님). */ C,
    /** 확인·조치 절차. */ R;

    /** 정규화 순서의 코드 이름 목록. */
    public static List<String> valuesInOrder(Set<EvidenceNeed> needs) {
        return (needs.isEmpty() ? EnumSet.noneOf(EvidenceNeed.class) : EnumSet.copyOf(needs)).stream().map(Enum::name).toList();
    }

    /**
     * JobContext 분류 맵의 코드 이름 목록을 되읽는다. 분류 wire v3 결속 값이 아니면(키 부재) null 이고, 형식이 다르면 거부한다.
     * 결속 값은 서버가 만든 것이므로 형식 위반은 내부 결함이다.
     */
    public static List<EvidenceNeed> fromJobValue(Object value) {
        if (value == null) return null;
        if (!(value instanceof List<?> names)) throw new IllegalArgumentException("근거 요구 결속 형식이 잘못되었습니다");
        EnumSet<EvidenceNeed> needs = EnumSet.noneOf(EvidenceNeed.class);
        for (Object name : names) {
            if (!(name instanceof String text) || !needs.add(valueOf(text))) throw new IllegalArgumentException("근거 요구 결속 형식이 잘못되었습니다");
        }
        return List.copyOf(needs);
    }
}
