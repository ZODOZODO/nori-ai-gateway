package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class ToolExecutionRepository extends ScopedJdbc {
    public ToolExecutionRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    public record Entry(String id, String capabilityId, String status, String payload) {}
    public boolean insert(VerifiedSystemScope scope, String jobId, Entry value) {
        return transaction(() -> scoped(scope, "INSERT INTO gw.tool_execution(id, job_id, owner_system, capability_id, status, payload, created_at) "
            + "SELECT :id, :job, :bound, :capability, :status, CAST(:payload AS jsonb), :now WHERE EXISTS (" + JOB + ")")
            .param("job", jobId).param("id", value.id()).param("capability", value.capabilityId()).param("status", value.status()).param("payload", value.payload()).update() == 1);
    }
    public Optional<Entry> find(VerifiedSystemScope scope, String jobId, String id) {
        return transaction(() -> scoped(scope, "SELECT e.id, capability_id, status, e.payload::text FROM gw.tool_execution e "
            + "WHERE e.id = :id AND e.job_id = :job AND e.owner_system = :bound AND EXISTS (" + JOB + ")")
            .param("job", jobId).param("id", id).query((rs, row) -> new Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))).optional());
    }

}
