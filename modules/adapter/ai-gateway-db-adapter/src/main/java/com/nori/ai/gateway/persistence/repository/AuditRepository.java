package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class AuditRepository extends ScopedJdbc {
    public AuditRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    public record Event(String requestId, String conversationId, String jobId, String traceId, String eventType,
        String toolId, String sourceRef, String decision, String status, String errorCode,
        String timings, String tokenUsage, String versions, String reduction, String taskType) {
        /** V7 이전 필드만 쓰는 기존 호출자 형태다. task_type은 NULL이다. */
        public Event(String requestId, String conversationId, String jobId, String traceId, String eventType,
                String toolId, String sourceRef, String decision, String status, String errorCode,
                String timings, String tokenUsage, String versions, String reduction) {
            this(requestId, conversationId, jobId, traceId, eventType, toolId, sourceRef, decision, status, errorCode, timings, tokenUsage, versions, reduction, null);
        }
    }
    private static final String PARENTS = """
        (CAST(:conversation AS text) IS NULL OR EXISTS (SELECT 1 FROM gw.conversation c WHERE c.id = :conversation
            AND c.owner_system = :bound AND c.owner_actor_ref = :actor))
        AND (CAST(:job AS text) IS NULL OR EXISTS (SELECT 1 FROM gw.job j JOIN gw.conversation c ON c.id = j.conversation_id AND c.owner_system = j.owner_system
            WHERE j.id = :job AND j.owner_system = :bound AND j.actor_ref = :actor AND c.owner_actor_ref = :actor
            AND (CAST(:conversation AS text) IS NULL OR j.conversation_id = :conversation)))
        """;
    /** 인입 요청 행이다. service_ref는 검증된 범위의 인입 service 참조를 저장한다. */
    public boolean insert(VerifiedSystemScope scope, Event event) {
        return transaction(() -> fields(scoped(scope, """
            INSERT INTO gw.audit_event(recorded_at, owner_system, actor_ref, request_id, conversation_id, job_id, trace_id,
                event_type, tool_id, source_ref, decision, status, error_code, timings, token_usage, versions, reduction, service_ref, task_type)
            SELECT :now, :bound, :actor, :request, :conversation, :job, :trace, :type, :tool, :source, :decision,
                :status, :error, CAST(:timings AS jsonb), CAST(:tokens AS jsonb), CAST(:versions AS jsonb), CAST(:reduction AS jsonb), :service, :taskType WHERE
            """ + PARENTS), event).param("request", event.requestId()).param("trace", event.traceId())
            .param("service", scope.authenticatedServiceRef()).update() == 1);
    }
    /**
     * Worker 측 Job 행이다. request_id·trace_id는 같은 owner·actor·Job의 JOB_ACCEPTED 행에서 파생하고 service_ref는 NULL이다
     * (Worker 범위의 requestId=jobId·job-worker를 원래 요청·서비스로 저장하지 않는다). 원점이 정확히 1행이 아니면 저장 없이 false다.
     */
    public boolean insertForJob(VerifiedSystemScope scope, Event event) {
        if (event.jobId() == null || event.requestId() != null || event.traceId() != null) throw new IllegalArgumentException("Job 감사 원점 입력이 잘못되었습니다");
        // 원점 후보를 같은 문장 snapshot에서 세어 정확히 1행일 때만 저장합니다. 0행·중복이면 아무 행도 쓰지 않고 false입니다.
        return transaction(() -> fields(scoped(scope, """
            WITH origin AS (SELECT o.request_id, o.trace_id FROM gw.audit_event o
                WHERE o.event_type = 'JOB_ACCEPTED' AND o.job_id = :job AND o.owner_system = :bound AND o.actor_ref = :actor)
            INSERT INTO gw.audit_event(recorded_at, owner_system, actor_ref, request_id, conversation_id, job_id, trace_id,
                event_type, tool_id, source_ref, decision, status, error_code, timings, token_usage, versions, reduction, service_ref, task_type)
            SELECT :now, :bound, :actor, origin.request_id, :conversation, :job, origin.trace_id, :type, :tool, :source, :decision,
                :status, :error, CAST(:timings AS jsonb), CAST(:tokens AS jsonb), CAST(:versions AS jsonb), CAST(:reduction AS jsonb), NULL, :taskType
            FROM origin WHERE (SELECT count(*) FROM origin) = 1 AND
            """ + PARENTS), event).update() == 1);
    }
    private static JdbcClient.StatementSpec fields(JdbcClient.StatementSpec statement, Event event) {
        return statement.param("conversation", event.conversationId()).param("job", event.jobId())
            .param("type", event.eventType()).param("tool", event.toolId()).param("source", event.sourceRef())
            .param("decision", event.decision()).param("status", event.status()).param("error", event.errorCode())
            .param("timings", event.timings()).param("tokens", event.tokenUsage()).param("versions", event.versions()).param("reduction", event.reduction())
            .param("taskType", event.taskType());
    }
    public List<Event> findByRequest(VerifiedSystemScope scope, String requestId) {
        return transaction(() -> scoped(scope, """
            SELECT a.request_id, a.conversation_id, a.job_id, a.trace_id, a.event_type, a.tool_id, a.source_ref, a.decision,
                a.status, a.error_code, a.timings::text, a.token_usage::text, a.versions::text, a.reduction::text, a.task_type
            FROM gw.audit_event a WHERE a.request_id = :request AND a.owner_system = :bound AND a.actor_ref = :actor
                AND a.recorded_at > :cutoff
            ORDER BY a.id
            """).param("request", requestId).param("cutoff", Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(90)).truncatedTo(ChronoUnit.MICROS)))
            .query((rs, row) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15))).list());
    }

}
