package com.nori.ai.gateway.core.knowledge;

import com.nori.ai.gateway.core.plan.ResourceRef;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Knowledge 단계의 서버 내부 실행 입력(SPEC §28.4, Plan §16.3 R1·R2). 의미 입력 = 현재 Job 의 마스킹된 질문 + 검증된 대상 참조 + 요구 근거 종류이며,
 * 정확 q(qRef 복원 원문)와는 다른 필드로 둔다(같은 문자열 필드로 덮어쓰지 않음). 사용자·LLM 이 지정한 semanticQuery·Source·owner·SQL 은 담지 않는다.
 * 실행 메모리에만 두며 로그·감사·캐시에 저장하지 않으므로 {@link #toString()} 은 값을 보호한다.
 *
 * @param requestId 이 값을 만든 Job(= scope requestId). Knowledge Tool 은 {@link #requireBoundTo} 로 결속을 재검사한다.
 * @param stepId 선택 단계 ID
 * @param verifiedTargets PlanValidator ⑧ 를 통과한 리소스 참조(모델/버전 참조 포함)
 */
public record KnowledgeSearchContext(String requestId, String stepId, OwnerSystem ownerSystem, String maskedQuestion, Optional<String> exactQuery,
        List<ResourceRef> verifiedTargets, List<EvidenceNeed> evidenceNeeds) {
    public KnowledgeSearchContext {
        Objects.requireNonNull(requestId, "requestId"); Objects.requireNonNull(stepId, "stepId"); Objects.requireNonNull(ownerSystem, "ownerSystem");
        Objects.requireNonNull(maskedQuestion, "maskedQuestion"); Objects.requireNonNull(exactQuery, "exactQuery");
        verifiedTargets = List.copyOf(verifiedTargets); evidenceNeeds = List.copyOf(evidenceNeeds);
        if (requestId.isBlank() || stepId.isBlank() || maskedQuestion.isBlank()) throw new IllegalArgumentException("Knowledge 실행 입력이 비었습니다");
    }

    /** 다른 Job·다른 단계·다른 시스템의 입력이면 거부한다(최근 대화로 보충하지 않음). */
    public void requireBoundTo(String expectedRequestId, String expectedStepId, OwnerSystem expectedOwner) {
        if (!requestId.equals(expectedRequestId) || !stepId.equals(expectedStepId) || ownerSystem != expectedOwner)
            throw new IllegalArgumentException("Knowledge 실행 입력의 결속이 다릅니다");
    }

    /** 의미 검색 입력(질문 + 대상 참조 + 근거 종류). 정확 q 는 포함하지 않는다. 임베딩·Reranker 입력 예산 검사는 호출 측(P23) 몫이며 절삭하지 않는다. */
    public String semanticInput() {
        StringBuilder text = new StringBuilder(maskedQuestion);
        for (ResourceRef target : verifiedTargets) text.append('\n').append(target.type()).append(' ').append(target.ref());
        if (!evidenceNeeds.isEmpty()) text.append('\n').append(EvidenceNeed.valuesInOrder(java.util.Set.copyOf(evidenceNeeds)));
        return text.toString();
    }

    @Override public String toString() { return "KnowledgeSearchContext[보호됨]"; }
}
