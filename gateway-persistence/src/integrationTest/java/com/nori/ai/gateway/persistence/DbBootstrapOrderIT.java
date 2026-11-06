package com.nori.ai.gateway.persistence;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DbBootstrapOrderIT extends PgIntegrationSupport {
    @Test void canonicalBootstrapMigrationAppAndRestrictedBackupAcceptanceRoles() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(true); database.bootstrap(true);
            try (var connection = database.connection("postgres"); var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname IN ('gw','kn')")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
            }
            database.migrate();
            try (var context = application(database); var connection = database.connection("aigw_app"); var statement = connection.createStatement()) {
                assertThat(context.getBean(com.nori.ai.gateway.persistence.schema.SchemaVersionGuard.class)).isNotNull();
                statement.execute("INSERT INTO gw.conversation VALUES ('c','TC','actor',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + interval '1 day','ACTIVE')");
                assertThat(statement.executeUpdate("UPDATE gw.conversation SET status = 'ACTIVE' WHERE id = 'c'")).isEqualTo(1);
                statement.execute("INSERT INTO gw.context VALUES ('c','TC','{}',CURRENT_TIMESTAMP)");
                assertThat(statement.executeUpdate("DELETE FROM gw.context WHERE conversation_id = 'c'")).isEqualTo(1);
                for (String sql : List.of("CREATE TABLE gw.forbidden(id integer)", "CREATE TABLE kn.forbidden(id integer)",
                        "ALTER TABLE gw.conversation ADD COLUMN forbidden integer", "DROP TABLE gw.conversation",
                        "TRUNCATE gw.conversation CASCADE", "CREATE TEMP TABLE forbidden(id integer)")) denied(statement, sql);
            }
            var tables = new ArrayList<String>();
            try (var connection = database.connection("postgres"); var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT schemaname || '.' || tablename FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway') ORDER BY 1")) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            assertThat(tables).hasSize(16);
            try (var connection = database.connection("aigw_backup"); var statement = connection.createStatement()) {
                for (String table : tables) {
                    try (var result = statement.executeQuery("SELECT count(*) FROM " + table)) { assertThat(result.next()).isTrue(); }
                    if (!table.equals("gw.backup_run")) denied(statement, "INSERT INTO " + table + " DEFAULT VALUES");
                    denied(statement, "DELETE FROM " + table);
                    denied(statement, "TRUNCATE " + table + " CASCADE");
                }
                statement.execute("INSERT INTO gw.backup_run(started_at,outcome) VALUES (CURRENT_TIMESTAMP,'STARTED')");
                denied(statement, "UPDATE gw.backup_run SET outcome = 'CHANGED'");
                denied(statement, "CREATE TABLE gw.forbidden(id integer)");
            }
            try (var connection = database.connection("aigw_acceptance_ro"); var statement = connection.createStatement()) {
                statement.execute("SET default_transaction_read_only = off");
                for (String table : tables) {
                    try (var result = statement.executeQuery("SELECT count(*) FROM " + table)) { assertThat(result.next()).isTrue(); }
                    denied(statement, "INSERT INTO " + table + " DEFAULT VALUES");
                    denied(statement, "DELETE FROM " + table);
                }
                for (String sql : List.of("UPDATE gw.conversation SET status = 'CHANGED'", "TRUNCATE gw.conversation CASCADE",
                        "CREATE TABLE gw.forbidden(id integer)", "ALTER TABLE gw.conversation ADD COLUMN forbidden integer",
                        "DROP TABLE gw.conversation", "CREATE TEMP TABLE forbidden(id integer)")) denied(statement, sql);
                try (var result = statement.executeQuery("SELECT pg_has_role(current_user,'pg_read_all_data','MEMBER')")) {
                    assertThat(result.next()).isTrue(); assertThat(result.getBoolean(1)).isTrue();
                }
            }
            try (var connection = database.connection("postgres"); var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE p07_outside");
                for (String role : List.of("aigw_app", "aigw_backup")) for (String table : tables) {
                    try (var result = statement.executeQuery("SELECT has_table_privilege('" + role + "','" + table + "','TRUNCATE,REFERENCES,TRIGGER')")) {
                        assertThat(result.next()).isTrue(); assertThat(result.getBoolean(1)).isFalse();
                    }
                }
            }
            assertThat(database.acceptsViaUnix()).isTrue();
            for (String name : List.of("postgres", "template1", "p07_outside")) for (boolean unix : List.of(false, true))
                assertThat(database.rejectsOtherDatabase(name, unix)).as("HBA %s unix=%s", name, unix).isTrue();
            database.bootstrap(false); database.bootstrap(false);
            try (var connection = database.connection("postgres"); var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM pg_roles WHERE rolname = 'aigw_acceptance_ro'")) {
                assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isZero();
            }
            String bootstrap = Files.readString(Path.of(System.getProperty("aigw.rootDir"), "ops/postgres/bootstrap.sql"));
            assertThat(bootstrap).doesNotContainPattern("(?i)\\b(?:gw|kn|gw_flyway)\\.");
        }
    }
    private void denied(Statement statement, String sql) {
        assertThatThrownBy(() -> statement.execute(sql)).as(sql).isInstanceOfSatisfying(SQLException.class,
            failure -> assertThat(failure.getSQLState()).isEqualTo("42501"));
    }
}
