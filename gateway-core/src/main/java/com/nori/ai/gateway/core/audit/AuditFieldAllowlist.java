package com.nori.ai.gateway.core.audit;

import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Spec §12.3 허용 목록의 일반 감사 저장 전 검사기다. 이벤트별 필드 집합만 받고, 식별자는 닫힌 형식, Tool·Source 참조는
 * 등록 Manifest의 canonical 값, 인입 service 참조는 같은 {@link ContentSanitizer}가 바꾸지 않는 값만 받는다.
 * 위반은 마스킹하지 않고 거부한다. 인증 거절(P08 전용 append)과 Job 종결(기존 committer)은 일반 경로가 아니다.
 */
public final class AuditFieldAllowlist {
    // 서버가 발급하는 식별자는 UUID다. 무작위 hex 숫자열이 mask-v1 전화 규칙과 우연히 겹칠 수 있어 형식만 검사한다.
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern TRACE = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SERVICE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9:._-]{0,63}");
    private final ContentSanitizer sanitizer;
    private final CapabilityRegistry registry;

    public AuditFieldAllowlist(ContentSanitizer sanitizer, CapabilityRegistry registry) {
        this.sanitizer = Objects.requireNonNull(sanitizer);
        this.registry = Objects.requireNonNull(registry);
    }

    public void requireGeneral(AuditEvent event) {
        Objects.requireNonNull(event);
        String service = event.scope().authenticatedServiceRef();
        switch (event.eventType()) {
            case API_REQUEST -> {
                requireService(service); requireUuid("requestId", event.scope().requestId()); requireTrace(event.traceId());
                absent(event.conversationId(), event.jobId(), event.toolId(), event.sourceRef(), event.taskType(), event.status(), event.errorCode(), event.decision(), event.latencyMs());
            }
            case JOB_ACCEPTED -> {
                requireService(service); requireUuid("requestId", event.scope().requestId()); requireTrace(event.traceId()); requireUuid("conversationId", event.conversationId()); requireUuid("jobId", event.jobId());
                absent(event.toolId(), event.sourceRef(), event.taskType(), event.status(), event.errorCode(), event.decision(), event.latencyMs());
            }
            case JOB_CLASSIFIED -> {
                requireJob(event); present("taskType", event.taskType());
                absent(event.traceId(), event.toolId(), event.sourceRef(), event.status(), event.errorCode(), event.decision(), event.latencyMs());
            }
            case TOOL_CALL -> {
                requireJob(event); requireTool(event, true); present("status", event.status()); present("latencyMs", event.latencyMs());
                if (event.latencyMs() < 0) throw rejected("latencyMs");
                absent(event.traceId(), event.taskType(), event.decision());
            }
            case TOOL_BLOCKED -> {
                requireJob(event); if (event.toolId() != null) requireTool(event, false); present("status", event.status());
                absent(event.traceId(), event.sourceRef(), event.taskType(), event.latencyMs());
            }
            case AUTH_REJECTED, JOB_TERMINAL -> throw rejected("eventType");
        }
    }
    private void requireJob(AuditEvent event) {
        requireUuid("conversationId", event.conversationId()); requireUuid("jobId", event.jobId());
    }
    /** Tool은 등록 Manifest의 canonical 값이고 bound owner의 것이어야 한다. sourceRef는 그 Manifest의 값만 허용한다. */
    private void requireTool(AuditEvent event, boolean sourceAllowed) {
        var manifest = registry.find(event.toolId()).orElseThrow(() -> rejected("toolId"));
        if (!manifest.toolId().equals(event.toolId()) || manifest.ownerSystem() != event.scope().boundSystem()) throw rejected("toolId");
        if (event.sourceRef() != null && (!sourceAllowed || !manifest.sourceAllowlistRef().equals(event.sourceRef()))) throw rejected("sourceRef");
    }
    private void requireService(String service) {
        if (!SERVICE.matcher(service).matches() || !sanitizer.sanitize(service).text().equals(service)) throw rejected("serviceRef");
    }
    private static void requireTrace(String traceId) {
        if (traceId == null || !TRACE.matcher(traceId).matches() || traceId.equals("0".repeat(32))) throw rejected("traceId");
    }
    private static void requireUuid(String field, String value) { if (value == null || !UUID.matcher(value).matches()) throw rejected(field); }
    private static void present(String field, Object value) { if (value == null) throw rejected(field); }
    private static void absent(Object... values) { for (Object value : values) if (value != null) throw rejected("field"); }
    private static IllegalArgumentException rejected(String field) { return new IllegalArgumentException("감사 허용 목록 밖 값입니다: " + field); }
}
