package com.nori.ai.gateway.persistence.queue;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class JobEventPublisher {
    public record Published(long eventId, String jobId, String type, String payload) {}
    public record TerminalCommitted(String jobId) {}
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;
    public JobEventPublisher(JdbcClient jdbc, Clock clock, ApplicationEventPublisher publisher) { this.jdbc=jdbc; this.clock=clock; this.publisher=publisher; }
    public void publish(long eventId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("발행 전에 트랜잭션이 끝나야 합니다");
        List<Published> events = jdbc.sql("SELECT id,job_id,type,payload::text FROM gw.job_event WHERE id=:id AND published_at IS NULL AND NOT external_suppressed")
            .param("id",eventId).query((rs,n) -> new Published(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4))).list();
        for (Published event : events) {
            publisher.publishEvent(event);
            jdbc.sql("UPDATE gw.job_event SET published_at=:now WHERE id=:id AND published_at IS NULL")
                .param("id",event.eventId()).param("now",Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))).update();
        }
    }
    public void terminalCommitted(String jobId) { publisher.publishEvent(new TerminalCommitted(jobId)); }
    public void publishQueued(String jobId) {
        for(Long id:jdbc.sql("SELECT id FROM gw.job_event WHERE job_id=:id AND type='job.queued' AND published_at IS NULL AND NOT external_suppressed").param("id",jobId).query(Long.class).list())publish(id);
    }
    public void republishTerminal() {
        for (Long id : jdbc.sql("SELECT id FROM gw.job_event WHERE published_at IS NULL AND NOT external_suppressed AND type IN ('job.completed','job.partial','job.failed','job.cancelled') ORDER BY id").query(Long.class).list()) publish(id);
    }
}
