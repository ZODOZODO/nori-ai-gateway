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

}
