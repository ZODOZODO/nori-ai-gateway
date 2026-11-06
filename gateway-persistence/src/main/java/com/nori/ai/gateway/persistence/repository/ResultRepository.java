package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class ResultRepository extends ScopedJdbc {
    public ResultRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) { super(jdbc, transactions, clock); }

    public record Result(String status, String errorCode, String payload, String clarification, String resultSourceDeps) {}
    public boolean insert(VerifiedSystemScope scope, String jobId, Result result) {
        return transaction(() -> scoped(scope, """
            INSERT INTO gw.result(job_id, owner_system, status, error_code, payload, clarification, result_source_deps, created_at)
            SELECT :job, :bound, :status, :error, CAST(:payload AS jsonb), CAST(:clarification AS jsonb), CAST(:deps AS jsonb), :now WHERE EXISTS (
            """ + JOB + ")").param("job", jobId).param("status", result.status()).param("error", result.errorCode())
            .param("payload", result.payload()).param("clarification", result.clarification()).param("deps", result.resultSourceDeps()).update() == 1);
    }
    public Optional<Result> find(VerifiedSystemScope scope, String jobId) {
        return transaction(() -> scoped(scope, """
            SELECT r.status, r.error_code, r.payload::text, r.clarification::text, r.result_source_deps::text FROM gw.result r
            WHERE r.job_id = :job AND r.owner_system = :bound AND EXISTS (
            """ + JOB + ")").param("job", jobId).query((rs, row) -> new Result(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))).optional());
    }

}
