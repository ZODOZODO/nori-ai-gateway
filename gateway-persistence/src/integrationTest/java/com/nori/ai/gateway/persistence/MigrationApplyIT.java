package com.nori.ai.gateway.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigrationApplyIT extends PgIntegrationSupport {
    @Test void migratorAppliesExactlyV1ToV3AndOwnsAllObjects() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false);
            org.flywaydb.core.Flyway.configure().dataSource(database.jdbcUrl(), "aigw_migrator", database.passwordFor("aigw_migrator"))
                .defaultSchema("gw_flyway").createSchemas(true).locations("classpath:db/migration/gateway").target("3").load().migrate();
            var checksums = new java.util.LinkedHashMap<String, Integer>();
            try (var connection = database.connection("aigw_app"); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    var versions = new java.util.ArrayList<String>(); while (rows.next()) versions.add(rows.getString(1));
                    assertThat(versions).containsExactly("1", "2", "3");
                }
                try (var rows = statement.executeQuery("SELECT version, checksum FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    while (rows.next()) checksums.put(rows.getString(1), rows.getInt(2));
                }
                try (var rows = statement.executeQuery("SELECT attnotnull FROM pg_attribute WHERE attrelid = 'gw.audit_event'::regclass AND attname IN ('owner_system','actor_ref')")) {
                    int count = 0; while (rows.next()) { count++; assertThat(rows.getBoolean(1)).isTrue(); } assertThat(count).isEqualTo(2);
                }
                try (var rows = statement.executeQuery("SELECT tablename, tableowner FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway')")) {
                    int count = 0; while (rows.next()) { count++; assertThat(rows.getString(2)).isEqualTo("aigw_migrator"); }
                    assertThat(count).isEqualTo(16);
                }
            }
            database.migrate(); database.migrate();
            try (var connection = database.connection("aigw_app"); var statement = connection.createStatement()) {
                try(var columns=statement.executeQuery("SELECT column_name,is_nullable,column_default FROM information_schema.columns WHERE table_schema='gw' AND table_name='job' ORDER BY ordinal_position")){
                    var names=new java.util.ArrayList<String>();int authority=0;
                    while(columns.next()){String name=columns.getString(1);names.add(name);if(java.util.Set.of("request_perm","request_pdg").contains(name)){authority++;assertThat(columns.getString(2)).isEqualTo("NO");assertThat(columns.getString(3)).isNull();}}
                    assertThat(authority).isEqualTo(2);
                    var backup=new java.util.HashSet<>(java.nio.file.Files.readAllLines(java.nio.file.Path.of(System.getProperty("aigw.rootDir"),"ops/backup/job-columns-v1.txt")));assertThat(backup).doesNotContain("delegation_id");backup.add("delegation_id");assertThat(names).containsExactlyInAnyOrderElementsOf(backup);
                }
                try (var rows = statement.executeQuery("SELECT version, checksum FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    var versions = new java.util.ArrayList<String>();
                    while (rows.next()) { String version = rows.getString(1); versions.add(version); if (checksums.containsKey(version)) assertThat(rows.getInt(2)).isEqualTo(checksums.get(version)); }
                    assertThat(versions).containsExactly("1", "2", "3", "3.1", "4");
                }
                try (var rows = statement.executeQuery("SELECT tableowner FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway')")) {
                    int count = 0; while (rows.next()) { count++; assertThat(rows.getString(1)).isEqualTo("aigw_migrator"); } assertThat(count).isEqualTo(16);
                }
            }
        }
    }
    @Test void missingExtensionFailsMigrationWithoutCreatingGatewayObjects() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false);
            try (var connection = database.connection("postgres"); var statement = connection.createStatement()) { statement.execute("DROP EXTENSION vector"); }
            assertThatThrownBy(database::migrate).isInstanceOf(RuntimeException.class).hasStackTraceContaining("필수 vector 확장 버전이 일치하지 않습니다");
            try (var connection = database.connection("postgres"); var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname IN ('gw','kn')")) {
                assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isZero();
            }
        }
    }
}
