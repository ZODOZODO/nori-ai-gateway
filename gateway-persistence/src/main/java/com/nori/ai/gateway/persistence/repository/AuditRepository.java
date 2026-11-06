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
        String timings, String tokenUsage, String versions, String reduction) {}
    private static final String PARENTS = """
        (CAST(:conversation AS text) IS NULL OR EXISTS (SELECT 1 FROM gw.conversation c WHERE c.id = :conversation
            AND c.owner_system = :bound AND c.owner_actor_ref = :actor))
        AND (CAST(:job AS text) IS NULL OR EXISTS (SELECT 1 FROM gw.job j JOIN gw.conversation c ON c.id = j.conversation_id AND c.owner_system = j.owner_system
            WHERE j.id = :job AND j.owner_system = :bound AND j.actor_ref = :actor AND c.owner_actor_ref = :actor
            AND (CAST(:conversation AS text) IS NULL OR j.conversation_id = :conversation)))
        """;
    public boolean insert(VerifiedSystemScope scope, Event event) {
        return transaction(() -> scoped(scope, """
            INSERT INTO gw.audit_event(recorded_at, owner_system, actor_ref, request_id, conversation_id, job_id, trace_id,
                event_type, tool_id, source_ref, decision, status, error_code, timings, token_usage, versions, reduction)
            SELECT :now, :bound, :actor, :request, :conversation, :job, :trace, :type, :tool, :source, :decision,
                :status, :error, CAST(:timings AS jsonb), CAST(:tokens AS jsonb), CAST(:versions AS jsonb), CAST(:reduction AS jsonb) WHERE
            """ + PARENTS).param("request", event.requestId()).param("conversation", event.conversationId()).param("job", event.jobId())
            .param("trace", event.traceId()).param("type", event.eventType()).param("tool", event.toolId()).param("source", event.sourceRef())
            .param("decision", event.decision()).param("status", event.status()).param("error", event.errorCode())
            .param("timings", event.timings()).param("tokens", event.tokenUsage()).param("versions", event.versions()).param("reduction", event.reduction()).update() == 1);
    }
    public List<Event> findByRequest(VerifiedSystemScope scope, String requestId) {
        return transaction(() -> scoped(scope, """
            SELECT a.request_id, a.conversation_id, a.job_id, a.trace_id, a.event_type, a.tool_id, a.source_ref, a.decision,
                a.status, a.error_code, a.timings::text, a.token_usage::text, a.versions::text, a.reduction::text
            FROM gw.audit_event a WHERE a.request_id = :request AND a.owner_system = :bound AND a.actor_ref = :actor
                AND a.recorded_at > :cutoff
            ORDER BY a.id
            """).param("request", requestId).param("cutoff", Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(90)).truncatedTo(ChronoUnit.MICROS)))
            .query((rs, row) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14))).list());
    }

}
