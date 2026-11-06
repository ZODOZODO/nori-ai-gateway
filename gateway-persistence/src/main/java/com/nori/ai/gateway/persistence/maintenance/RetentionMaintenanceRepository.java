package com.nori.ai.gateway.persistence.maintenance;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class RetentionMaintenanceRepository {
    private final JdbcClient jdbc; private final TransactionTemplate transactions; private final Clock clock;
    public RetentionMaintenanceRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc; this.transactions = transactions; this.clock = clock;
    }
    public int purgeExpiredConversations() {
        return transactions.execute(status -> jdbc.sql("DELETE FROM gw.conversation c WHERE c.expires_at <= :now "
            + "AND NOT EXISTS (SELECT 1 FROM gw.job j WHERE j.conversation_id = c.id AND j.status IN ('QUEUED','RUNNING'))")
            // 저장 정밀도보다 작은 현재 시각을 미래로 반올림하면 미만료 행이 삭제됩니다.
            .param("now", Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))).update());
    }
    public int purgeExpiredAudit() {
        return transactions.execute(status -> jdbc.sql("DELETE FROM gw.audit_event WHERE recorded_at <= :cutoff")
            .param("cutoff", Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(90)).truncatedTo(ChronoUnit.MICROS))).update());
    }
}
