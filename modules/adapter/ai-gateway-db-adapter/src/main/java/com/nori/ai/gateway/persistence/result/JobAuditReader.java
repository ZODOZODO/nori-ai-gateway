package com.nori.ai.gateway.persistence.result;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JobAuditReader {
    private final JdbcClient jdbc;private final Clock clock;
    public JobAuditReader(JdbcClient jdbc,Clock clock) { this.jdbc=jdbc;this.clock=clock; }
    public List<Map<String,Object>> find(VerifiedSystemScope scope,String jobId) {
        var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return jdbc.sql("""
            SELECT a.event_type,a.status,a.error_code,a.recorded_at FROM gw.audit_event a
            JOIN gw.job j ON j.id=a.job_id AND j.owner_system=a.owner_system
            JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system
            WHERE a.job_id=:job AND a.owner_system=:owner AND a.actor_ref=:actor AND j.actor_ref=:actor
            AND c.owner_actor_ref=:actor AND c.owner_system=:owner AND c.expires_at>:now AND a.recorded_at>:cutoff ORDER BY a.id
            """).param("job",jobId).param("owner",scope.boundSystem().name()).param("actor",scope.actorRef().value())
            .param("now",Timestamp.from(now)).param("cutoff",Timestamp.from(now.minus(java.time.Duration.ofDays(90))))
            .query((rs,n)->{var row=new LinkedHashMap<String,Object>();row.put("eventType",rs.getString(1));row.put("status",rs.getString(2));row.put("errorCode",rs.getString(3));row.put("recordedAt",rs.getTimestamp(4).toInstant());return (Map<String,Object>)row;}).list();
    }
}
