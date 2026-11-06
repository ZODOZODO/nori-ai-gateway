package com.nori.ai.gateway.persistence.queue.recovery;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class CacheInvalidationRepository {
    private final JdbcClient jdbc;
    public CacheInvalidationRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public void deleteAllCacheEntries() { jdbc.sql("DELETE FROM gw.cache_entry").update(); }
}
