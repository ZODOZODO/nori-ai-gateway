package com.nori.ai.gateway.persistence.maintenance;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class RecoveryMaintenanceRepository {
    private final JdbcClient jdbc; private final TransactionTemplate transactions;
    public RecoveryMaintenanceRepository(JdbcClient jdbc, TransactionTemplate transactions) { this.jdbc = jdbc; this.transactions = transactions; }
    public Optional<String> databaseSystemIdentifier() {
        return transactions.execute(status -> jdbc.sql("SELECT db_system_identifier FROM gw.instance_epoch WHERE id = 1").query(String.class).optional());
    }
}
