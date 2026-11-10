package com.nori.ai.gateway.app.config;

import javax.sql.DataSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

// 일반 앱의 서비스/runner 그래프 없이 DDL 전용 컨텍스트만 구성합니다.
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
@Import(MigrateCommand.class)
public class MigrateApplication {
    @Bean FlywayMigrationStrategy checkedMigration(DataSource dataSource, Environment environment) {
        return flyway -> {
            MigrateCommand.requireMigrator(dataSource, environment.getActiveProfiles());
            flyway.migrate();
        };
    }
    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(MigrateApplication.class).web(WebApplicationType.NONE).run(args)) {
            MigrateCommand.requireMigrator(context.getBean(DataSource.class), context.getEnvironment().getActiveProfiles());
            if (context.getBean(MigrateCommand.class).getExitCode() != 0) throw new IllegalStateException("마이그레이션이 완료되지 않았습니다");
        }
    }
}
