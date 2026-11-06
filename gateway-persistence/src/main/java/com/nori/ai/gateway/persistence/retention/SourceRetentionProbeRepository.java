package com.nori.ai.gateway.persistence.retention;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** 보존 탐침의 실제 관측값만 저장하는 유지보수 저장소다. 호출자는 탐침 스케줄러와 읽기 어댑터로 닫혀 있다. */
public final class SourceRetentionProbeRepository {
    private static final Pattern SOURCE_ID = Pattern.compile("sources://[A-Z]{2,3}(?:/[A-Za-z0-9._-]{1,64}){1,4}");
    public record Probe(String sourceId, Instant verifiedOldestAt, Instant probeLimitAt, Instant probedAt) {}
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public SourceRetentionProbeRepository(JdbcClient jdbc, TransactionTemplate transactions) {
        this.jdbc = Objects.requireNonNull(jdbc); this.transactions = Objects.requireNonNull(transactions);
    }
    public void upsert(String sourceId, Instant verifiedOldestAt, Instant probeLimitAt, Instant probedAt) {
        requireSource(sourceId);
        Objects.requireNonNull(verifiedOldestAt); Objects.requireNonNull(probeLimitAt); Objects.requireNonNull(probedAt);
        if (verifiedOldestAt.isBefore(probeLimitAt)) throw new IllegalArgumentException("탐침 범위가 잘못되었습니다");
        int updated = transactions.execute(status -> jdbc.sql("""
            INSERT INTO gw.source_retention_probe(source_id, verified_oldest_at, probe_limit_at, probed_at)
            VALUES (:source, :oldest, :limit, :probed)
            ON CONFLICT (source_id) DO UPDATE SET verified_oldest_at = EXCLUDED.verified_oldest_at,
                probe_limit_at = EXCLUDED.probe_limit_at, probed_at = EXCLUDED.probed_at
            """).param("source", sourceId).param("oldest", micros(verifiedOldestAt)).param("limit", micros(probeLimitAt))
            .param("probed", micros(probedAt)).update());
        if (updated != 1) throw new IllegalStateException("보존 탐침 저장 건수가 잘못되었습니다");
    }
    public Optional<Probe> find(String sourceId) {
        requireSource(sourceId);
        return transactions.execute(status -> jdbc.sql("""
            SELECT source_id, verified_oldest_at, probe_limit_at, probed_at FROM gw.source_retention_probe WHERE source_id = :source
            """).param("source", sourceId).query((rs, row) -> new Probe(rs.getString(1), instant(rs.getTimestamp(2)),
                instant(rs.getTimestamp(3)), rs.getTimestamp(4).toInstant())).optional());
    }
    private static void requireSource(String sourceId) {
        if (sourceId == null || !SOURCE_ID.matcher(sourceId).matches()) throw new IllegalArgumentException("Source 식별자가 잘못되었습니다");
    }
    // 저장 정밀도보다 작은 값을 반올림하면 실제 관측보다 늦은 시각이 저장됩니다.
    private static Timestamp micros(Instant value) { return Timestamp.from(value.truncatedTo(ChronoUnit.MICROS)); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
