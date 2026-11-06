package com.nori.ai.gateway.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigrationApplyIT extends PgIntegrationSupport {
    @Test void migratorAppliesExactlyV1ToV3AndOwnsAllObjects() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false); database.migrate(); database.migrate();
            try (var connection = database.connection("aigw_app"); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    var versions = new java.util.ArrayList<String>(); while (rows.next()) versions.add(rows.getString(1));
                    assertThat(versions).containsExactly("1", "2", "3");
                }
                try (var rows = statement.executeQuery("SELECT tablename, tableowner FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway')")) {
                    int count = 0; while (rows.next()) { count++; assertThat(rows.getString(2)).isEqualTo("aigw_migrator"); }
                    assertThat(count).isEqualTo(16);
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
