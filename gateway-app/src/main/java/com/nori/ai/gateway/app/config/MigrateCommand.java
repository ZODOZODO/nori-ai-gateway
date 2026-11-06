package com.nori.ai.gateway.app.config;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;

@Profile("migrate")
public final class MigrateCommand implements ApplicationRunner, ExitCodeGenerator {
    private boolean completed;
    public MigrateCommand(org.flywaydb.core.Flyway flyway) { java.util.Objects.requireNonNull(flyway, "마이그레이션 구성이 필요합니다"); }
    @Override public void run(ApplicationArguments args) { completed = true; }
    @Override public int getExitCode() { return completed ? 0 : 1; }
    static void requireMigrator(DataSource dataSource, String[] profiles) {
        if (!Arrays.asList(profiles).contains("migrate")) throw new IllegalStateException("마이그레이션 프로필이 필요합니다");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT current_user")) {
            if (!result.next() || !"aigw_migrator".equals(result.getString(1)))
                throw new IllegalStateException("마이그레이션 전용 역할이 필요합니다");
        } catch (SQLException failure) { throw new IllegalStateException("마이그레이션 역할을 확인할 수 없습니다", failure); }
    }
}
