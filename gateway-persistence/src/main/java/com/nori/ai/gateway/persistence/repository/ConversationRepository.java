package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class ConversationRepository extends ScopedJdbc {
    public ConversationRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    public record Conversation(String id, String ownerSystem, String ownerActorRef, Instant createdAt, Instant updatedAt, Instant expiresAt, String status) {}
    public boolean insert(VerifiedSystemScope scope, String id) {
        Instant now = clock.instant();
        return transaction(() -> scoped(scope, """
            INSERT INTO gw.conversation(id, owner_system, owner_actor_ref, created_at, updated_at, expires_at, status)
            VALUES (:id, :bound, :actor, :now, :now, :expires, 'ACTIVE')
            """).param("id", id).param("now", Timestamp.from(now)).param("expires", Timestamp.from(now.plus(java.time.Duration.ofDays(30)))).update() == 1);
    }
    public Optional<Conversation> find(VerifiedSystemScope scope, String id) {
        return transaction(() -> scoped(scope, """
            SELECT id, owner_system, owner_actor_ref, created_at, updated_at, expires_at, status FROM gw.conversation
            WHERE id = :id AND owner_system = :bound AND owner_actor_ref = :actor AND expires_at > :now
            """).param("id", id).query((rs, row) -> new Conversation(rs.getString("id"), rs.getString("owner_system"),
                rs.getString("owner_actor_ref"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(), rs.getString("status"))).optional());
    }
    public boolean touch(VerifiedSystemScope scope, String id) {
        return transaction(() -> scoped(scope, """
            UPDATE gw.conversation SET updated_at = :now WHERE id = :id AND owner_system = :bound
            AND owner_actor_ref = :actor AND expires_at > :now
            """).param("id", id).update() == 1);
    }
    public boolean saveContext(VerifiedSystemScope scope, String conversationId, String payload) {
        return save(scope, "context", conversationId, payload);
    }
    public Optional<String> findContext(VerifiedSystemScope scope, String conversationId) {
        return findPayload(scope, "context", conversationId);
    }
    public boolean saveSummary(VerifiedSystemScope scope, String conversationId, String payload) {
        return save(scope, "conversation_summary", conversationId, payload);
    }
    public Optional<String> findSummary(VerifiedSystemScope scope, String conversationId) {
        return findPayload(scope, "conversation_summary", conversationId);
    }
    private boolean save(VerifiedSystemScope scope, String table, String conversationId, String payload) {
        return transaction(() -> scoped(scope, "INSERT INTO gw." + table + " (conversation_id, owner_system, payload, updated_at) "
            + "SELECT :conversation, :bound, CAST(:payload AS jsonb), :now WHERE EXISTS (" + CONVERSATION + ") "
            + "ON CONFLICT (conversation_id) DO UPDATE SET payload = EXCLUDED.payload, updated_at = EXCLUDED.updated_at "
            + "WHERE gw." + table + ".owner_system = :bound")
            .param("conversation", conversationId).param("payload", payload).update() == 1);
    }
    private Optional<String> findPayload(VerifiedSystemScope scope, String table, String conversationId) {
        return transaction(() -> scoped(scope, "SELECT p.payload::text FROM gw." + table + " p WHERE p.conversation_id = :conversation "
            + "AND p.owner_system = :bound AND EXISTS (" + CONVERSATION + ")")
            .param("conversation", conversationId).query(String.class).optional());
    }

}
