package com.nori.ai.gateway.persistence;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 운영 순서 재현(Plan §8 2026-11-10): 서버 담당자 선행조건(DB·vector 0.8.7·DB 를 소유하는 단일 계정) → 외부 Flyway migrate
 * (filesystem flyway/sql, 이력 gw_flyway) → 같은 계정으로 persistence 컨텍스트·SchemaVersionGuard 기동.
 */
class DbBootstrapOrderIT extends PgIntegrationSupport {
    @Test void serverPrerequisitesThenExternalMigrationThenSameAccountPersistenceContext() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(); database.bootstrap();
            try (var connection = database.connection("postgres"); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway')")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
                }
                try (var rows = statement.executeQuery("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = current_database()")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo(database.user());
                }
            }
            try (var connection = database.connection(database.user()); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT rolsuper, has_database_privilege(current_user, current_database(), 'CREATE') FROM pg_roles WHERE rolname = current_user")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isFalse(); assertThat(rows.getBoolean(2)).isTrue();
                }
                try (var rows = statement.executeQuery("SELECT extversion FROM pg_extension WHERE extname = 'vector'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("0.8.7");
                }
                // DbSystemIdentifierReader 가 쓰는 함수는 서버에서 PUBLIC 실행이 가능하다(GRANT 불필요, 2026-11-10 확인).
                try (var rows = statement.executeQuery("SELECT system_identifier FROM pg_control_system()")) { assertThat(rows.next()).isTrue(); }
            }
            database.migrate();
            try (var context = application(database); var connection = database.connection(database.user()); var statement = connection.createStatement()) {
                assertThat(context.getBean(com.nori.ai.gateway.persistence.schema.SchemaVersionGuard.class)).isNotNull();
                assertThat(context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class).sql("SELECT current_user").query(String.class).single()).isEqualTo(database.user());
                statement.execute("INSERT INTO gw.conversation VALUES ('c','TC','actor',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + interval '1 day','ACTIVE')");
                assertThat(statement.executeUpdate("UPDATE gw.conversation SET status = 'ACTIVE' WHERE id = 'c'")).isEqualTo(1);
                statement.execute("INSERT INTO gw.context VALUES ('c','TC','{}',CURRENT_TIMESTAMP)");
                assertThat(statement.executeUpdate("DELETE FROM gw.context WHERE conversation_id = 'c'")).isEqualTo(1);
                try (var rows = statement.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")) {
                    var versions = new ArrayList<String>(); while (rows.next()) versions.add(rows.getString(1));
                    assertThat(versions).containsExactly("1", "2", "3", "3.1", "4", "5", "7");
                }
                try (var rows = statement.executeQuery("SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway')")) {
                    var owners = new ArrayList<String>(); while (rows.next()) owners.add(rows.getString(1));
                    assertThat(owners).containsExactly(database.user());
                }
            }
            String bootstrap = Files.readString(Path.of(System.getProperty("aigw.rootDir"), "ops/postgres/bootstrap.sql"));
            assertThat(bootstrap).doesNotContainPattern("(?i)\\b(?:gw|kn|gw_flyway)\\.");
        }
    }
}
