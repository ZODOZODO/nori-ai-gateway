package com.nori.ai.gateway.app.ops.backup;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 백업 Job 이 남긴 {@code gw.backup_run} 으로 백업 상태 두 지표를 노출한다(Plan P15·§11.6 AIGW-BACKUP-FAILED·STALE).
 * 성공 이력이 없으면 마지막 성공 시각은 NaN 이며 성공으로 표시하지 않는다.
 */
public final class BackupStatusMetrics {
    static final String LAST_SUCCESS = "aigw.backup.last.success.timestamp";
    static final String LAST_RUN_FAILED = "aigw.backup.last.run.failed";
    private final JdbcClient jdbc;

    public BackupStatusMetrics(JdbcClient jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        Gauge.builder(LAST_SUCCESS, this, BackupStatusMetrics::lastSuccessEpochSeconds).baseUnit("seconds").register(meters);
        Gauge.builder(LAST_RUN_FAILED, this, BackupStatusMetrics::lastRunFailed).register(meters);
    }

    double lastSuccessEpochSeconds() {
        OffsetDateTime finished = jdbc.sql("SELECT max(finished_at) FROM gw.backup_run WHERE outcome = 'SUCCESS'").query(OffsetDateTime.class).optional().orElse(null);
        return finished == null ? Double.NaN : finished.toInstant().getEpochSecond();
    }

    double lastRunFailed() {
        String outcome = jdbc.sql("SELECT outcome FROM gw.backup_run ORDER BY id DESC LIMIT 1").query(String.class).optional().orElse(null);
        return "FAILED".equals(outcome) ? 1 : 0;
    }
}
