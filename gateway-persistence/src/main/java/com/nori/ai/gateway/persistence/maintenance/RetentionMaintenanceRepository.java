package com.nori.ai.gateway.persistence.maintenance;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

public final class RetentionMaintenanceRepository {
    /** 한 트랜잭션의 후보 행 수를 제한하는 기술 초기값이다. 관측·성능을 보장한 수치가 아니다. */
    public static final int DEFAULT_PURGE_BATCH_SIZE = 100;
    private static final String NO_ACTIVE_JOB = "NOT EXISTS (SELECT 1 FROM gw.job j WHERE j.conversation_id = c.id AND j.status IN ('QUEUED','RUNNING'))";
    private final JdbcClient jdbc; private final TransactionTemplate transactions; private final Clock clock;
    public RetentionMaintenanceRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc; this.transactions = transactions; this.clock = clock;
    }
    public int purgeExpiredConversations() {
        int total = 0;
        for (int deleted; (deleted = purgeExpiredConversationsBatch(DEFAULT_PURGE_BATCH_SIZE)) > 0;) total += deleted;
        return total;
    }
    public int purgeExpiredAudit() {
        int total = 0;
        for (int deleted; (deleted = purgeExpiredAuditBatch(DEFAULT_PURGE_BATCH_SIZE)) > 0;) total += deleted;
        return total;
    }
    public int purgeExpiredConversationsBatch(int batchSize) {
        requirePositive(batchSize);
        return transactions.execute(status -> jdbc.sql("WITH candidate AS (SELECT c.id FROM gw.conversation c WHERE c.expires_at <= :now AND " + NO_ACTIVE_JOB
                + " ORDER BY c.expires_at, c.id LIMIT :batch FOR UPDATE SKIP LOCKED) "
                + "DELETE FROM gw.conversation c USING candidate d WHERE c.id = d.id AND c.expires_at <= :now AND " + NO_ACTIVE_JOB)
            // 저장 정밀도보다 작은 현재 시각을 미래로 반올림하면 미만료 행이 삭제됩니다.
            .param("now", Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))).param("batch", batchSize).update());
    }
    public int purgeExpiredAuditBatch(int batchSize) {
        requirePositive(batchSize);
        return transactions.execute(status -> jdbc.sql("WITH candidate AS (SELECT a.id FROM gw.audit_event a WHERE a.recorded_at <= :cutoff"
                + " ORDER BY a.recorded_at, a.id LIMIT :batch FOR UPDATE SKIP LOCKED) "
                + "DELETE FROM gw.audit_event a USING candidate d WHERE a.id = d.id AND a.recorded_at <= :cutoff")
            .param("cutoff", Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(90)).truncatedTo(ChronoUnit.MICROS))).param("batch", batchSize).update());
    }
    /** 남은 대화 중 가장 오래된 만료 시각 하나만 읽는다(24h 잔류 판정용, 기존 expires_at 인덱스). 대화가 없으면 비어 있다. */
    public Optional<Instant> findOldestConversationExpiry() {
        return jdbc.sql("SELECT MIN(expires_at) FROM gw.conversation").query((rs, row) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant)).single();
    }
    private static void requirePositive(int batchSize) { if (batchSize <= 0) throw new IllegalArgumentException("정리 한도는 양수여야 합니다"); }
}
