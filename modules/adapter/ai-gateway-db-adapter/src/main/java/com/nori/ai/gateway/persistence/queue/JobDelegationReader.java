package com.nori.ai.gateway.persistence.queue;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JobDelegationReader {
    private final JdbcClient jdbc;
    public JobDelegationReader(JdbcClient jdbc) { this.jdbc = jdbc; }
    public Optional<String> forWorker(String jobId) {
        return jdbc.sql("SELECT delegation_id FROM gw.job WHERE id=:id AND status='RUNNING'")
            .param("id",jobId).query(String.class).optional();
    }
}
