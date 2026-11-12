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

    private static final List<String> WINDOW = List.of("eqp", "from", "to");

    /** 입력은 ARGUMENT 이름 목록, 선행 근거가 있으면 첫 선행 근거의 items 를 q 로 받는다(app 프로필의 git 항목과 같은 형태). */
    static EvidencePlanProfile.Entry entry(String id, EvidenceNeed need, String capability, boolean required, List<String> arguments, List<String> dependsOn, List<String> report, List<String> unavailable) {
        List<EvidencePlanProfile.Input> inputs = dependsOn.isEmpty() ? arguments.stream().map(name -> new EvidencePlanProfile.Input(name, ARGUMENT, null, null)).toList()
            : List.of(new EvidencePlanProfile.Input("q", STEP_FIELD, dependsOn.getFirst(), "items"));
        return new EvidencePlanProfile.Entry(id, need, capability, required, inputs, dependsOn, report, unavailable);
    }
    static EvidencePlanProfile.Entry entry(String id, EvidenceNeed need, String capability, List<String> dependsOn, List<String> report, List<String> unavailable) {
        return entry(id, need, capability, false, List.of("eqp"), dependsOn, report, unavailable);
    }

    /** app workflows/tc/evidence-profile-v1.json 과 같은 항목·필수 여부·입력. */
    static EvidencePlanProfile tcProfile() {
        return new EvidencePlanProfile(OwnerSystem.TC, "tc-evidence-profile-v1", List.of(
            entry("tc.state.snapshot", EvidenceNeed.S, "tc.eqp.getOperationalSnapshot", true, List.of("eqp"), List.of(), List.of("설비 운영 상태"), List.of("Port 목록", "진행 LOT")),
            entry("tc.history.commLogs", EvidenceNeed.H, "tc.commLogs.search", true, WINDOW, List.of(), List.of("설비 통신 기록"), List.of()),
            entry("tc.history.logs", EvidenceNeed.H, "tc.logs.search", true, WINDOW, List.of(), List.of("애플리케이션 로그"), List.of()),
            entry("tc.history.trace", EvidenceNeed.H, "tc.trace.search", false, WINDOW, List.of(), List.of("처리 Trace"), List.of()),
            entry("tc.history.dlq", EvidenceNeed.H, "tc.dlq.list", false, WINDOW, List.of(), List.of("DLQ 기록"), List.of()),
            entry("tc.history.event", EvidenceNeed.H, "tc.event.getRecent", false, List.of("eqp"), List.of(), List.of("최근 설비 이벤트"), List.of()),
            entry("tc.normal.knowledge", EvidenceNeed.N, "tc.knowledge.search", true, List.of("eqp"), List.of(), List.of("정상 절차·선행 조건 문서"), List.of()),
            entry("tc.normal.model", EvidenceNeed.N, "tc.model.read", true, List.of(), List.of(), List.of("적용 모델 정의"), List.of()),
            entry("tc.impl.git", EvidenceNeed.I, "tc.git.searchCode", true, List.of(), List.of("tc.history.logs"), List.of("당시 구현 코드"), List.of("당시 배포·설정 버전")),
            entry("tc.impl.deployTrace", EvidenceNeed.I, "tc.trace.search", true, WINDOW, List.of(), List.of("당시 배포 서비스 버전"), List.of()),
            entry("tc.action.knowledge", EvidenceNeed.R, "tc.knowledge.search", true, List.of("eqp"), List.of(), List.of("확인·조치 절차 문서"), List.of())));
    }

    static WorkflowOutcome.Stage stage(String stepId, String capability, ResultStatus status, boolean observed, int count) {
        Instant at = observed ? DomainFixtures.NOW : null;
        var result = new ToolResultEnvelope(null, capability, "1.0", null, OwnerSystem.TC, status, null, List.of(), at, null, List.of(), List.of(), null, null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of());
        return new WorkflowOutcome.Stage(stepId, result, observed, count);
    }
}
