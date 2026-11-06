package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class CacheRepository extends ScopedJdbc {
    public CacheRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    public record Entry(String capabilityId, String value, String sourceDeps, Instant expiresAt) {}
    public boolean put(VerifiedSystemScope scope, String conversationId, String keyDigest, Entry entry) {
        return transaction(() -> scoped(scope, """
            INSERT INTO gw.cache_entry(key_digest, owner_system, actor_ref, pdg, conversation_id, capability_id, value, source_deps, expires_at)
            SELECT :key, :bound, :actor, :pdg, :conversation, :capability, CAST(:value AS jsonb), CAST(:deps AS jsonb),
                LEAST(:expires, c.expires_at)
            FROM gw.conversation c WHERE c.id = :conversation AND c.owner_system = :bound
                AND c.owner_actor_ref = :actor AND c.expires_at > :now AND :expires > :now
            """ + " ON CONFLICT (key_digest, owner_system, actor_ref, pdg) DO UPDATE SET "
            + "conversation_id = EXCLUDED.conversation_id, capability_id = EXCLUDED.capability_id, value = EXCLUDED.value, "
            + "source_deps = EXCLUDED.source_deps, expires_at = EXCLUDED.expires_at")
            .param("key", keyDigest).param("pdg", scope.pdg()).param("conversation", conversationId).param("capability", entry.capabilityId())
            // 요청한 캐시 수명을 넘기지 않도록 JDBC의 마이크로초 올림 전에 만료 시각을 내립니다.
            .param("value", entry.value()).param("deps", entry.sourceDeps())
            .param("expires", Timestamp.from(entry.expiresAt().truncatedTo(ChronoUnit.MICROS))).update() == 1);
    }
    public Optional<Entry> find(VerifiedSystemScope scope, String conversationId, String keyDigest) {
        return transaction(() -> scoped(scope, """
            SELECT e.capability_id, e.value::text, e.source_deps::text, e.expires_at FROM gw.cache_entry e
            WHERE e.key_digest = :key AND e.conversation_id = :conversation AND e.owner_system = :bound
            AND e.actor_ref = :actor AND e.pdg = :pdg AND e.expires_at > :now AND EXISTS (
            """ + CONVERSATION + ")").param("conversation", conversationId).param("key", keyDigest).param("pdg", scope.pdg())
            .query((rs, row) -> new Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant())).optional());
    }

}
