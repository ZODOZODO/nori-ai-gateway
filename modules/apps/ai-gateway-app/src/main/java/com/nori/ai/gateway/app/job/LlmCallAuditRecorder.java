package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.observability.AuditRecorder;
import com.nori.ai.gateway.core.audit.AuditEvent;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.llm.LlmCallAudit;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * LLM 호출 시도 감사(LLM_CALL)를 기존 일반 감사 경로({@link AuditRecorder#writeForJob})로 쓴다. 저장 실패는 Tool 감사와 같이 재전파한다.
 * 모델·Prompt 버전은 배포 설정이 고른 생성 모델 이름과 호출 유형별 Prompt 자원 이름이다. 원문은 받지 않는다.
 */
public final class LlmCallAuditRecorder implements LlmCallAudit {
    private final AuditRecorder audit;
    private final String model;
    private final Map<CallType,String> prompts;

    public LlmCallAuditRecorder(AuditRecorder audit, String model, Map<CallType,String> prompts) {
        this.audit = Objects.requireNonNull(audit, "audit");
        this.model = Objects.requireNonNull(model, "model");
        this.prompts = new EnumMap<>(Objects.requireNonNull(prompts, "prompts"));
    }

    @Override public void record(Attempt attempt) {
        String prompt = prompts.get(attempt.callType());
        if (prompt == null) throw new IllegalArgumentException("Prompt 버전이 등록되지 않은 호출 유형입니다: " + attempt.callType());
        var call = new AuditEvent.LlmCall(attempt.callType(), attempt.sent(), attempt.inputTokens(), attempt.maxTokens(), attempt.promptTokens(), attempt.completionTokens(),
            attempt.reduction(), model, prompt);
        audit.writeForJob(AuditEvent.llmCall(attempt.scope(), attempt.conversationId(), attempt.jobId(), attempt.status(), attempt.errorCode(), attempt.latencyMs(), call));
    }
}
