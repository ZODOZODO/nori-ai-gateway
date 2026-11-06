package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class MessageRepository extends ScopedJdbc {
    public MessageRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    public record Message(String id, String role, String contentMasked, String resourceRefs, String toolExecutionRefs, String responseRef, String resolvedTimeWindow) {}
    public boolean insert(VerifiedSystemScope scope, String conversationId, Message message) {
        return transaction(() -> scoped(scope, """
            INSERT INTO gw.message(id, conversation_id, owner_system, role, created_at, content_masked, resource_refs,
                tool_execution_refs, response_ref, resolved_time_window)
            SELECT :id, :conversation, :bound, :role, :now, :content, CAST(:resources AS jsonb), CAST(:tools AS jsonb),
                :response, CAST(:window AS jsonb) WHERE EXISTS (
            """ + CONVERSATION + ")").param("conversation", conversationId).param("id", message.id()).param("role", message.role())
            .param("content", message.contentMasked()).param("resources", message.resourceRefs()).param("tools", message.toolExecutionRefs())
            .param("response", message.responseRef()).param("window", message.resolvedTimeWindow()).update() == 1);
    }
    public List<Message> findByConversation(VerifiedSystemScope scope, String conversationId) {
        return transaction(() -> scoped(scope, """
            SELECT m.id, m.role, m.content_masked, m.resource_refs::text, m.tool_execution_refs::text, m.response_ref,
                m.resolved_time_window::text FROM gw.message m WHERE m.conversation_id = :conversation AND m.owner_system = :bound
                AND EXISTS (
            """ + CONVERSATION + ") ORDER BY m.created_at, m.id").param("conversation", conversationId)
            .query((rs, row) -> new Message(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7))).list());
    }
    public boolean updateResolvedTimeWindow(VerifiedSystemScope scope,String jobId,String requestMessageId,Map<String,Object> resolvedTimeWindow) {
        requireJob(scope,jobId); validateWindow(resolvedTimeWindow);
        return transaction(() -> scoped(scope,"""
            UPDATE gw.message m SET resolved_time_window=CAST(:window AS jsonb)
            FROM gw.job j JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system
            WHERE j.id=:job AND j.request_message_id=:message AND j.owner_system=:bound AND j.actor_ref=:actor
              AND m.id=j.request_message_id AND m.conversation_id=j.conversation_id AND m.owner_system=:bound AND m.role='user'
              AND c.owner_system=:bound AND c.owner_actor_ref=:actor AND c.expires_at>:now
            """).param("job",jobId).param("message",requestMessageId)
            .param("window",com.nori.ai.gateway.core.registry.SchemaContracts.JSON.writeValueAsString(resolvedTimeWindow)).update()==1);
    }
    public Optional<Map<String,Object>> findPreviousResolvedTimeWindow(VerifiedSystemScope scope,String jobId,String requestMessageId) {
        requireJob(scope,jobId);
        return transaction(() -> scoped(scope,"""
            WITH current_anchor AS (
              SELECT j.id,j.seq,j.conversation_id,j.owner_system,j.actor_ref
              FROM gw.job j
              JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system
              JOIN gw.message m ON m.id=j.request_message_id AND m.conversation_id=j.conversation_id AND m.owner_system=j.owner_system AND m.role='user'
              WHERE j.id=:job AND j.request_message_id=:message AND j.owner_system=:bound AND j.actor_ref=:actor
                AND c.owner_system=:bound AND c.owner_actor_ref=:actor AND c.expires_at>:now
            )
            SELECT a.id AS current_job_id,p.id AS previous_job_id,p.request_message_id AS previous_request_message_id,
                   pm.id AS matched_previous_message_id,pm.resolved_time_window::text AS window_json
            FROM current_anchor a
            LEFT JOIN LATERAL (
              SELECT previous.id,previous.request_message_id FROM gw.job previous
              WHERE previous.conversation_id=a.conversation_id AND previous.owner_system=a.owner_system
                AND previous.actor_ref=a.actor_ref AND previous.seq<a.seq
              ORDER BY previous.seq DESC LIMIT 1
            ) p ON true
            LEFT JOIN gw.message pm ON pm.id=p.request_message_id AND pm.conversation_id=a.conversation_id
              AND pm.owner_system=a.owner_system AND pm.role='user'
            """).param("job",jobId).param("message",requestMessageId).query((rs,row) -> {
                if (rs.getString("previous_job_id")==null) return Optional.<Map<String,Object>>empty();
                if (rs.getString("matched_previous_message_id")==null) throw new IllegalStateException("이전 요청 연결이 잘못되었습니다");
                String value=rs.getString("window_json");
                if (value==null) return Optional.<Map<String,Object>>empty();
                var map=com.nori.ai.gateway.core.registry.SchemaContracts.JSON.readValue(value,new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
                validateWindow(map);
                return Optional.of(com.nori.ai.gateway.core.job.JobRequestInput.immutableFields(map));
            }).optional().orElseThrow(()->new IllegalStateException("작업 입력 연결을 확인할 수 없습니다")));
    }
    private void requireJob(VerifiedSystemScope scope,String jobId) {
        if (scope==null || !scope.requestId().equals(jobId)) throw new IllegalArgumentException("작업 입력 연결이 다릅니다");
    }
    private void validateWindow(Map<String,Object> window) {
        if (window==null || !window.keySet().equals(Set.of("from","to")) || !(window.get("from") instanceof String from) || !(window.get("to") instanceof String to)) throw new IllegalArgumentException("저장 시간 구간이 잘못되었습니다");
        var validator=new com.nori.ai.gateway.core.plan.InputValidator();
        // 확정 저장값은 offset을 포함하므로 현재 요청의 지역 시간대로 재해석하지 않습니다.
        if (!validator.parseTime(from,null).isBefore(validator.parseTime(to,null))) throw new IllegalArgumentException("저장 시간 구간이 잘못되었습니다");
    }

}
