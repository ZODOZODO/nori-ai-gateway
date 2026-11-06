package com.nori.ai.gateway.persistence.maintenance;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class QueueMaintenanceRepository {
    private final JdbcClient jdbc;
    public QueueMaintenanceRepository(JdbcClient jdbc) { this.jdbc = Objects.requireNonNull(jdbc); }
    // 행 잠금은 호출자의 접수/디스패치 트랜잭션이 끝날 때까지 유지되어야 합니다.
    public void lockControl() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("큐 잠금에 트랜잭션이 필요합니다");
        jdbc.sql("SELECT id FROM gw.queue_control WHERE id = 1 FOR UPDATE").query(Integer.class).single();
    }
}
