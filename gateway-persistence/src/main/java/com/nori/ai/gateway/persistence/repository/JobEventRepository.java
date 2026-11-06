package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class JobEventRepository extends ScopedJdbc {
    public JobEventRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    public record Event(long id, String type, String payload, boolean externalSuppressed) {}
    public boolean insert(VerifiedSystemScope scope, String jobId, String type, String payload, boolean externalSuppressed) {
        return transaction(() -> scoped(scope, """
            INSERT INTO gw.job_event(job_id, owner_system, type, payload, created_at, external_suppressed)
            SELECT :job, :bound, :type, CAST(:payload AS jsonb), :now, :suppressed WHERE EXISTS (
            """ + JOB + ")").param("job", jobId).param("type", type).param("payload", payload).param("suppressed", externalSuppressed).update() == 1);
    }
    public List<Event> findAfter(VerifiedSystemScope scope, String jobId, long afterId) {
        return transaction(() -> scoped(scope, """
            SELECT e.id, e.type, e.payload::text, e.external_suppressed FROM gw.job_event e
            WHERE e.job_id = :job AND e.owner_system = :bound AND e.id > :after AND NOT e.external_suppressed AND EXISTS (
            """ + JOB + ") ORDER BY e.id").param("job", jobId).param("after", afterId)
            .query((rs, row) -> new Event(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).list());
    }

}
