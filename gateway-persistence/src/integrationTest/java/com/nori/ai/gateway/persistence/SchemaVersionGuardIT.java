package com.nori.ai.gateway.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import static org.assertj.core.api.Assertions.*;

class SchemaVersionGuardIT extends PgIntegrationSupport {
    static class SubsequentRunner {
        static int calls;
        @Bean ApplicationRunner afterSchema() { return args -> calls++; }
    }
    @Test void realRunnerRejectsEmptyHistoryAndNumericVersionMismatchBeforeLaterRunner() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false); SubsequentRunner.calls = 0;
            assertThatThrownBy(() -> application(database, SubsequentRunner.class)).isInstanceOf(RuntimeException.class);
            assertThat(SubsequentRunner.calls).isZero();
            org.flywaydb.core.Flyway.configure().dataSource(database.jdbcUrl(), "aigw_migrator", database.passwordFor("aigw_migrator"))
                .defaultSchema("gw_flyway").createSchemas(true).locations("classpath:db/migration/gateway").target("3").load().migrate();
            assertThatThrownBy(() -> application(database, SubsequentRunner.class)).hasMessage("스키마 버전이 애플리케이션과 일치하지 않습니다");
            assertThat(SubsequentRunner.calls).isZero();
            database.migrate();
            try (var context = application(database, SubsequentRunner.class)) { assertThat(SubsequentRunner.calls).isEqualTo(1); }
            try (var connection = database.connection("aigw_migrator"); var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE gw_flyway.flyway_schema_history SET version = '10' WHERE version = '5'");
            }
            SubsequentRunner.calls = 0;
            assertThatThrownBy(() -> application(database, SubsequentRunner.class)).hasMessage("스키마 버전이 애플리케이션과 일치하지 않습니다");
            assertThat(SubsequentRunner.calls).isZero();
            try (var connection = database.connection("aigw_migrator"); var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM gw_flyway.flyway_schema_history WHERE version IS NOT NULL");
            }
            assertThatThrownBy(() -> application(database, SubsequentRunner.class)).hasMessage("성공한 스키마 이력이 없습니다");
            assertThat(SubsequentRunner.calls).isZero();
        }
    }
}
