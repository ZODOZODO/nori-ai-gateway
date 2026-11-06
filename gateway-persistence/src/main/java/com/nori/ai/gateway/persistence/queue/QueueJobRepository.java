package com.nori.ai.gateway.persistence.queue;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class QueueJobRepository {
    private final JdbcClient jdbc;
    private static final String COLUMNS = "j.owner_system,j.actor_ref,j.request_perm,j.request_pdg,j.id,j.created_at,j.conversation_id,c.expires_at,j.status";
    public QueueJobRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    public int count(String status) {
        if (!Set.of("QUEUED","RUNNING").contains(status)) throw new IllegalArgumentException("큐 상태가 잘못되었습니다");
        return jdbc.sql("SELECT count(*) FROM gw.job WHERE status=:status").param("status",status).query(Integer.class).single();
    }
    public List<JobScopeRecord> nonterminal() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM gw.job j JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system WHERE j.status IN ('QUEUED','RUNNING') ORDER BY j.seq")
            .query(QueueJobRepository::map).list();
    }
    public Optional<JobScopeRecord> nextQueued() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("디스패치 트랜잭션이 필요합니다");
        return jdbc.sql("SELECT " + COLUMNS + " FROM gw.job j JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system WHERE j.status='QUEUED' AND j.cancel_requested_at IS NULL ORDER BY j.seq LIMIT 1 FOR UPDATE OF j")
            .query(QueueJobRepository::map).optional();
    }
    public boolean start(String jobId, Instant now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("디스패치 트랜잭션이 필요합니다");
        return jdbc.sql("UPDATE gw.job SET status='RUNNING',started_at=:now WHERE id=:id AND status='QUEUED'")
            .param("id",jobId).param("now",Timestamp.from(now)).update() == 1;
    }
    private static JobScopeRecord map(ResultSet rs, int row) throws SQLException {
        return new JobScopeRecord(OwnerSystem.valueOf(rs.getString(1)),rs.getString(2),
            Set.copyOf(Arrays.asList((String[])rs.getArray(3).getArray())),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),
            rs.getString(7),rs.getTimestamp(8).toInstant(),rs.getString(9));
    }
}
