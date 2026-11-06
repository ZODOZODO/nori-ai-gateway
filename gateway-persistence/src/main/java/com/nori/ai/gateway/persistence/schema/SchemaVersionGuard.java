package com.nori.ai.gateway.persistence.schema;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class SchemaVersionGuard implements ApplicationRunner, Ordered {
    private static final Pattern VERSION = Pattern.compile("V([0-9]+(?:[._][0-9]+)*)__.+\\.sql");
    private final JdbcClient jdbc;
    public SchemaVersionGuard(JdbcClient jdbc) { this.jdbc = jdbc; }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override public void run(ApplicationArguments args) { verify(); }
    public void verify() {
        var expected = latestClasspathVersion();
        var actual = jdbc.sql("SELECT version FROM gw_flyway.flyway_schema_history WHERE success = true AND version IS NOT NULL")
            .query(String.class).list().stream().map(MigrationVersion::fromVersion).max(MigrationVersion::compareTo)
            .orElseThrow(() -> new IllegalStateException("성공한 스키마 이력이 없습니다"));
        if (!expected.equals(actual)) throw new IllegalStateException("스키마 버전이 애플리케이션과 일치하지 않습니다");
    }
    public static MigrationVersion latestClasspathVersion() {
        try {
            return latestVersion(Arrays.stream(new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/gateway/V*__*.sql"))
                .map(org.springframework.core.io.Resource::getFilename).toList());
        } catch (IOException failure) { throw new IllegalStateException("마이그레이션 버전을 읽을 수 없습니다", failure); }
    }
    static MigrationVersion latestVersion(java.util.List<String> names) {
        return names.stream().map(VERSION::matcher).filter(java.util.regex.Matcher::matches)
            .map(match -> MigrationVersion.fromVersion(match.group(1))).max(MigrationVersion::compareTo)
            .orElseThrow(() -> new IllegalStateException("클래스패스 마이그레이션이 없습니다"));
    }
}
