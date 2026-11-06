package com.nori.ai.gateway.persistence.queue.recovery;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class DbSystemIdentifierReader {
    private final JdbcClient jdbc;
    public DbSystemIdentifierReader(JdbcClient jdbc) { this.jdbc=jdbc; }
    public String read() { return jdbc.sql("SELECT system_identifier::text FROM pg_control_system()").query(String.class).single(); }
}
