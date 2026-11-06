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
            event.latencyMs() == null ? null : "{\"latencyMs\":" + event.latencyMs() + "}", null, null, null, name(event.taskType()));
    }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
