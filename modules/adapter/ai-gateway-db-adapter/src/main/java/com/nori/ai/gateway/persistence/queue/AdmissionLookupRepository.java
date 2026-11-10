package com.nori.ai.gateway.persistence.queue;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class AdmissionLookupRepository {
    private final JdbcClient jdbc;
    public AdmissionLookupRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public record Existing(String jobId, String conversationId, String requestDigest, Instant expiresAt, String requestMessageId) {}
    public Optional<Existing> find(VerifiedSystemScope scope, String key) {
        return jdbc.sql("""
            SELECT j.id, j.conversation_id, j.request_digest, c.expires_at, j.request_message_id FROM gw.job j
            JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system
            WHERE j.owner_system=:owner AND j.actor_ref=:actor AND j.idempotency_key=:key
            AND c.owner_system=:owner AND c.owner_actor_ref=:actor
            """).param("owner",scope.boundSystem().name()).param("actor",scope.actorRef().value()).param("key",key)
            .query((rs,n) -> new Existing(rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getString(5))).optional();
    }
}
