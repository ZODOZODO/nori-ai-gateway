package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.result.ToolResultEnvelope;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import com.nori.ai.gateway.core.workflow.EvidencePlanProfile;
import com.nori.ai.gateway.core.workflow.WorkflowOutcome;
import java.time.Instant;
import java.util.List;
import static com.nori.ai.gateway.core.workflow.EvidencePlanProfile.InputSource.*;

/** 근거 계획 단위 시험 공통: app workflows/tc/evidence-profile-v1.json 과 같은 항목 구성의 프로필과 단계 결과 대역. */
final class EvidenceFixtures {
    private EvidenceFixtures() {}

    static EvidencePlanProfile.Entry entry(String id, EvidenceNeed need, String capability, List<String> dependsOn, List<String> report, List<String> unavailable) {
        List<EvidencePlanProfile.Input> inputs = dependsOn.isEmpty() ? List.of(new EvidencePlanProfile.Input("eqp", ARGUMENT, null, null))
            : List.of(new EvidencePlanProfile.Input("q", STEP_FIELD, dependsOn.getFirst(), "items"));
        return new EvidencePlanProfile.Entry(id, need, capability, false, inputs, dependsOn, report, unavailable);
    }

    static EvidencePlanProfile tcProfile() {
        return new EvidencePlanProfile(OwnerSystem.TC, "tc-evidence-profile-v1", List.of(
            entry("tc.state.snapshot", EvidenceNeed.S, "tc.eqp.getOperationalSnapshot", List.of(), List.of("설비 운영 상태"), List.of("Port 목록", "진행 LOT")),
            entry("tc.history.commLogs", EvidenceNeed.H, "tc.commLogs.search", List.of(), List.of("설비 통신 기록"), List.of()),
            entry("tc.history.logs", EvidenceNeed.H, "tc.logs.search", List.of(), List.of("애플리케이션 로그"), List.of()),
            entry("tc.history.trace", EvidenceNeed.H, "tc.trace.search", List.of(), List.of("처리 Trace"), List.of()),
            entry("tc.history.dlq", EvidenceNeed.H, "tc.dlq.list", List.of(), List.of("DLQ 기록"), List.of()),
            entry("tc.history.event", EvidenceNeed.H, "tc.event.getRecent", List.of(), List.of("최근 설비 이벤트"), List.of()),
            entry("tc.normal.knowledge", EvidenceNeed.N, "tc.knowledge.search", List.of(), List.of("정상 절차·선행 조건 문서"), List.of()),
            entry("tc.normal.model", EvidenceNeed.N, "tc.model.read", List.of(), List.of("적용 모델 정의"), List.of()),
            entry("tc.impl.git", EvidenceNeed.I, "tc.git.searchCode", List.of("tc.history.logs"), List.of("당시 구현 코드"), List.of("당시 배포·설정 버전")),
            entry("tc.action.knowledge", EvidenceNeed.R, "tc.knowledge.search", List.of(), List.of("확인·조치 절차 문서"), List.of())));
    }

    static WorkflowOutcome.Stage stage(String stepId, String capability, ResultStatus status, boolean observed, int count) {
        Instant at = observed ? DomainFixtures.NOW : null;
        var result = new ToolResultEnvelope(null, capability, "1.0", null, OwnerSystem.TC, status, null, List.of(), at, null, List.of(), List.of(), null, null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of());
        return new WorkflowOutcome.Stage(stepId, result, observed, count);
    }
}
