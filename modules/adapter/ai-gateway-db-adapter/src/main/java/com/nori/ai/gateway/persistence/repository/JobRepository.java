package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class JobRepository extends ScopedJdbc implements com.nori.ai.gateway.core.port.JobRequestInputReader {
    private final tools.jackson.databind.json.JsonMapper json=com.nori.ai.gateway.core.registry.SchemaContracts.JSON;
    public JobRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    // 일시 위임 증표는 INSERT 입력만 받으며 조회 DTO에 포함하지 않습니다.
    public record NewJob(String id, String conversationId, String idempotencyKey, String requestDigest, String expectedPath,
        String requestMessageId, Map<String,Object> requestFields) {
        public NewJob { requestFields=com.nori.ai.gateway.core.job.JobRequestInput.immutableFields(requestFields); }
        @Override public String toString() { return "NewJob[보호됨]"; }
    }
    public record Job(String id, long seq, String conversationId, String ownerSystem, String actorRef, String status,
        Instant createdAt, Instant startedAt, Instant terminalAt, String errorCode, Instant cancelRequestedAt,
        String cancelReason, String resultRef, String idempotencyKey, String requestDigest, String expectedPath) {}
    private static final String COLUMNS = "j.id, j.seq, j.conversation_id, j.owner_system, j.actor_ref, j.status, j.created_at, "
        + "j.started_at, j.terminal_at, j.error_code, j.cancel_requested_at, j.cancel_reason, j.result_ref, j.idempotency_key, j.request_digest, j.expected_path";
    public boolean insert(VerifiedSystemScope scope, NewJob job, String delegationId) {
        return transaction(() -> scoped(scope, """
            INSERT INTO gw.job(id, conversation_id, owner_system, actor_ref, status, created_at, idempotency_key,
                request_digest, expected_path, delegation_id, request_perm, request_pdg, request_message_id, request_input)
            SELECT :id, :conversation, :bound, :actor, 'QUEUED', :now, :key, :digest, :path, :delegation, :permissions, :pdg, :message, CAST(:input AS jsonb) WHERE EXISTS (
            """ + CONVERSATION + ") AND EXISTS (SELECT 1 FROM gw.message m WHERE m.id=:message AND m.conversation_id=:conversation AND m.owner_system=:bound AND m.role='user')")
            .param("id", job.id()).param("conversation", job.conversationId())
            .param("key", job.idempotencyKey()).param("digest", job.requestDigest()).param("path", job.expectedPath())
            .param("delegation", delegationId).param("permissions", new org.springframework.jdbc.support.SqlArrayValue("text", scope.perm().stream().sorted().toArray()))
            .param("pdg", scope.pdg()).param("message", job.requestMessageId())
            .param("input", json.writeValueAsString(job.requestFields())).update() == 1);
    }
    @Override public Optional<com.nori.ai.gateway.core.job.JobRequestInput> findRequestInput(VerifiedSystemScope scope, String jobId) {
        return transaction(() -> scoped(scope,"""
            SELECT j.request_message_id,j.seq,m.content_masked,j.request_input::text FROM gw.job j
            JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system
            JOIN gw.message m ON m.id=j.request_message_id AND m.conversation_id=j.conversation_id AND m.owner_system=j.owner_system
            WHERE j.id=:job AND j.owner_system=:bound AND j.actor_ref=:actor AND m.owner_system=:bound AND m.role='user'
            AND c.owner_system=:bound AND c.owner_actor_ref=:actor AND c.expires_at>:now
            """).param("job",jobId).query((rs,row) -> {
                Map<String,Object> fields=json.readValue(rs.getString(4),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
                return new com.nori.ai.gateway.core.job.JobRequestInput(rs.getString(1),rs.getLong(2),rs.getString(3),fields);
            }).optional());
    }
    public Optional<Job> find(VerifiedSystemScope scope, String jobId) {
        return findMatching(scope, "j.id = :value", jobId);
    }
    public Optional<Job> findByIdempotencyKey(VerifiedSystemScope scope, String key) {
        return findMatching(scope, "j.idempotency_key = :value", key);
    }
    private Optional<Job> findMatching(VerifiedSystemScope scope, String predicate, String value) {
        return transaction(() -> scoped(scope, "SELECT " + COLUMNS + " FROM gw.job j JOIN gw.conversation c "
            + "ON c.id = j.conversation_id AND c.owner_system = j.owner_system WHERE " + predicate
            + " AND j.owner_system = :bound AND j.actor_ref = :actor AND c.owner_system = :bound AND c.owner_actor_ref = :actor AND c.expires_at > :now")
            .param("value", value).query((rs, row) -> new Job(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getTimestamp(7).toInstant(), instant(rs.getTimestamp(8)), instant(rs.getTimestamp(9)), rs.getString(10), instant(rs.getTimestamp(11)),
                rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16))).optional());
    }
    public boolean requestCancel(VerifiedSystemScope scope, String jobId, String reason) {
        return transaction(() -> scoped(scope, """
            UPDATE gw.job SET cancel_requested_at = :now, cancel_reason = :reason WHERE id = :job
            AND owner_system = :bound AND actor_ref = :actor AND status IN ('QUEUED','RUNNING') AND EXISTS (
            """ + JOB + ")").param("job", jobId).param("reason", reason).update() == 1);
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

}
