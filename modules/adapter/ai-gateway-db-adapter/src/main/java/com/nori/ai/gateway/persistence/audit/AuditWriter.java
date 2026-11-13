package com.nori.ai.gateway.persistence.audit;

import com.nori.ai.gateway.core.audit.AuditEvent;
import com.nori.ai.gateway.core.audit.AuditFieldAllowlist;
import com.nori.ai.gateway.persistence.repository.AuditRepository;
import java.util.Objects;

/**
 * 일반 감사 writer다. 검증된 신원의 기존 scope 저장소만 쓰며, 허용 목록 검사를 통과하지 못한 값과
 * 인증 거절(P08 전용 append 경로)·Job 종결(기존 committer)은 저장 전에 거부한다.
 */
public final class AuditWriter {
    private final AuditRepository repository;
    private final AuditFieldAllowlist allowlist;

    public AuditWriter(AuditRepository repository, AuditFieldAllowlist allowlist) {
        this.repository = Objects.requireNonNull(repository);
        this.allowlist = Objects.requireNonNull(allowlist);
    }

    /** 인입 요청 범위의 행이다. requestId는 검증된 범위의 인입 requestId다. */
    public void write(AuditEvent event) {
        allowlist.requireGeneral(event);
        if (!repository.insert(event.scope(), row(event, event.scope().requestId()))) throw new IllegalStateException("감사 부모 결속을 확인할 수 없습니다");
    }
    /** Worker 범위의 Job 행이다. requestId·traceId는 저장소가 같은 Job의 JOB_ACCEPTED에서 파생한다. */
    public void writeForJob(AuditEvent event) {
        allowlist.requireGeneral(event);
        if (!repository.insertForJob(event.scope(), row(event, null))) throw new IllegalStateException("감사 접수 원점을 확인할 수 없습니다");
    }
    private static AuditRepository.Event row(AuditEvent event, String requestId) {
        return new AuditRepository.Event(requestId, event.conversationId(), event.jobId(), event.traceId(), event.eventType().name(),
            event.toolId(), event.sourceRef(), name(event.decision()), name(event.status()), name(event.errorCode()),
            event.latencyMs() == null ? null : "{\"latencyMs\":" + event.latencyMs() + "}", tokens(event.llmCall()), versions(event.llmCall()), reduction(event.llmCall()), name(event.taskType()));
    }
    /** LLM_CALL 의 token_usage. 값은 허용 목록이 검사한 정수·enum·불리언뿐이라 그대로 직렬화한다. */
    static String tokens(AuditEvent.LlmCall call) {
        if (call == null) return null;
        return "{\"callType\":\"" + call.callType().name() + "\",\"sent\":" + call.sent() + ",\"inputTokens\":" + call.inputTokens() + ",\"maxTokens\":" + call.maxTokens()
            + ",\"promptTokens\":" + call.promptTokens() + ",\"completionTokens\":" + call.completionTokens() + "}";
    }
    /** LLM_CALL 의 versions. 모델·Prompt 식별자는 허용 목록의 닫힌 형식(따옴표·역슬래시 없음)이다. */
    static String versions(AuditEvent.LlmCall call) {
        return call == null ? null : "{\"model\":\"" + call.model() + "\",\"prompt\":\"" + call.promptVersion() + "\"}";
    }
    /** LLM_CALL 의 reduction: 적용한 축소 단계 번호와 단계별 전후 입력 토큰 수(원문 없음). 단계가 없으면 빈 배열이다. */
    static String reduction(AuditEvent.LlmCall call) {
        if (call == null) return null;
        var steps = new java.util.StringJoiner(",", "[", "]");
        for (var step : call.reduction()) steps.add("{\"stage\":" + step.stage() + ",\"beforeTokens\":" + step.beforeTokens() + ",\"afterTokens\":" + step.afterTokens() + "}");
        return steps.toString();
    }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
