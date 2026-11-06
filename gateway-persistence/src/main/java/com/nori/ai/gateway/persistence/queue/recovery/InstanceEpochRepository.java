package com.nori.ai.gateway.persistence.queue.recovery;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class InstanceEpochRepository {
    public record EpochSnapshot(String dbSystemIdentifier, Instant recordedAt, Instant restoreCompletedAt) {}
    private final JdbcClient jdbc;
    public InstanceEpochRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Optional<EpochSnapshot> find() {
        return jdbc.sql("SELECT db_system_identifier,recorded_at,restore_completed_at FROM gw.instance_epoch WHERE id=1")
            .query((rs,n)->new EpochSnapshot(rs.getString(1),rs.getTimestamp(2).toInstant(),rs.getTimestamp(3)==null?null:rs.getTimestamp(3).toInstant())).optional();
    }
    public void insertIfAbsent(String identifier,Instant now) {
        jdbc.sql("INSERT INTO gw.instance_epoch(id,db_system_identifier,recorded_at) VALUES (1,:id,:now) ON CONFLICT (id) DO NOTHING")
            .param("id",identifier).param("now",Timestamp.from(now)).update();
    }
    public void markRestoreCompleted(String identifier,Instant now,Instant backupPointAt) {
        jdbc.sql("UPDATE gw.instance_epoch SET db_system_identifier=:id,recorded_at=:now,restore_completed_at=:now,restored_backup_point_at=:point WHERE id=1")
            .param("id",identifier).param("now",Timestamp.from(now)).param("point",Timestamp.from(backupPointAt)).update();
    }
}
