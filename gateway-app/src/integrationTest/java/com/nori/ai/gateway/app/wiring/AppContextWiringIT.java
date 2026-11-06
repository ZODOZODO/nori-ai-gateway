package com.nori.ai.gateway.app.wiring;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.app.config.CoreBeansConfiguration;
import com.nori.ai.gateway.integration.IntegrationModuleConfiguration;
import com.nori.ai.gateway.persistence.PersistenceModuleConfiguration;
import com.nori.ai.gateway.tcmock.TcMockModuleConfiguration;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.core.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.Tag("stage-F1")
@org.junit.jupiter.api.Tag("label-MOCK_API")
class AppContextWiringIT extends StackContextSupport {
    @Test void applicationImportsAllModulesAndRealP05CoreBeans() {
        start();
        assertThat(context.getEnvironment().getActiveProfiles()).contains("verify");
        for (Class<?> type : new Class<?>[]{CoreBeansConfiguration.class, IntegrationModuleConfiguration.class,
                PersistenceModuleConfiguration.class, TcMockModuleConfiguration.class, ManifestLoader.class,
                CapabilityRegistry.class, ContractVersionPolicy.class, PlanValidator.class, ToolDispatcher.class,
                ToolResultContractValidator.class, PlanInjectionSeam.class, DispatchInjectionSeam.class})
            assertThat(context.getBeansOfType(type)).hasSize(1);
        assertThat(Arrays.stream(CoreBeansConfiguration.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Bean.class))).hasSize(8);
        assertThat(context.getBean(PlanInjectionSeam.class)).isExactlyInstanceOf(PlanInjectionSeam.NoOp.class);
        assertThat(context.getBean(DispatchInjectionSeam.class)).isExactlyInstanceOf(DispatchInjectionSeam.NoOp.class);
        assertThat(context.getBeansOfType(ToolExecutor.class)).isEmpty();
        var loader=context.getBean(ManifestLoader.class);var manifests=loader.loadClasspath("manifests/tc/index.json");
        assertThat(manifests).hasSize(9);
        var now=Instant.now();var scope=new VerifiedSystemScope(OwnerSystem.TC,"service",new ActorRef("actor"),"actor",Set.of("EQP_READ","DLQ_READ","MODEL_READ","AI_LOG_READ","AI_TRACE_READ","AI_CODE_READ","AI_DOC_READ"),"digest",now.minusSeconds(1),now.plusSeconds(60),"request");
        var registry=context.getBean(CapabilityRegistry.class);var view=registry.view(scope);
        assertThat(view.ownerSystem()).isEqualTo(scope.boundSystem());
        var snapshot=manifests.stream().filter(m -> m.capabilityId().equals("tc.eqp.getOperationalSnapshot")).findFirst().orElseThrow();
        assertThat(view.find(snapshot.capabilityId())).contains(snapshot);
        assertThat(view.capabilities()).hasSize(6).allSatisfy(m -> {
            assertThat(m.ownerSystem()).isEqualTo(scope.boundSystem());assertThat(m.availability()).isEqualTo(Availability.IMPLEMENTED);
            assertThat(context.getBean(ContractVersionPolicy.class).accepts(m.contractVersion(),m.schemaDigest())).isTrue();
        });
        assertThat(view.find("tc.event.getRecent")).isEmpty();
        assertThat(view.find("tc.knowledge.search")).isEmpty();
        assertThat(registry.workflow(scope,TaskType.INCIDENT_ANALYSIS)).isPresent();
        for (Class<?> type : List.of(java.time.Clock.class, com.nori.ai.gateway.persistence.schema.SchemaVersionGuard.class,
                org.springframework.jdbc.core.simple.JdbcClient.class, org.springframework.transaction.support.TransactionTemplate.class,
                com.nori.ai.gateway.persistence.repository.ConversationRepository.class,
                com.nori.ai.gateway.persistence.repository.MessageRepository.class,
                com.nori.ai.gateway.persistence.repository.JobRepository.class,
                com.nori.ai.gateway.persistence.repository.JobEventRepository.class,
                com.nori.ai.gateway.persistence.repository.ResultRepository.class,
                com.nori.ai.gateway.persistence.repository.EvidenceRepository.class,
                com.nori.ai.gateway.persistence.repository.ToolExecutionRepository.class,
                com.nori.ai.gateway.persistence.repository.CacheRepository.class,
                com.nori.ai.gateway.persistence.repository.AuditRepository.class,
                com.nori.ai.gateway.persistence.maintenance.QueueMaintenanceRepository.class,
                com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository.class,
                com.nori.ai.gateway.persistence.maintenance.RecoveryMaintenanceRepository.class))
            assertThat(context.getBeansOfType(type)).hasSize(1);
        var conversations = context.getBean(com.nori.ai.gateway.persistence.repository.ConversationRepository.class);
        assertThat(conversations.insert(scope, "p07-wiring")).isTrue();
        assertThat(conversations.find(scope, "p07-wiring")).isPresent();
    }

    @Test void actualApplicationRejectsMissingHistoryBeforeLaterRunner() throws Exception {
        database = new com.nori.ai.gateway.persistence.testing.PgTestDatabase(System.getProperty("aigw.pgTestImage"),
            java.nio.file.Path.of(System.getProperty("aigw.rootDir"))).start();
        database.bootstrap(true);
        LaterRunner.invocations = 0;
        assertThatThrownBy(() -> startApplication(LaterRunner.class)).isInstanceOf(RuntimeException.class);
        assertThat(LaterRunner.invocations).isZero();
        database.migrate();
        startApplication(LaterRunner.class);
        assertThat(LaterRunner.invocations).isEqualTo(1);
        context.close(); context = null;
        try (var connection = database.connection("aigw_migrator"); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE gw_flyway.flyway_schema_history SET version = '10' WHERE version = '3'");
        }
        LaterRunner.invocations = 0;
        assertThatThrownBy(() -> startApplication(LaterRunner.class)).hasMessage("스키마 버전이 애플리케이션과 일치하지 않습니다");
        assertThat(LaterRunner.invocations).isZero();
    }

    static class LaterRunner implements org.springframework.boot.ApplicationRunner, org.springframework.core.Ordered {
        static int invocations;
        @Override public int getOrder() { return 0; }
        @Override public void run(org.springframework.boot.ApplicationArguments arguments) { invocations++; }
    }

    @Test void migrationOnlyMainAppliesOnceAndPropagatesWrongRoleProfileAndMigrationFailure() throws Exception {
        database = new com.nori.ai.gateway.persistence.testing.PgTestDatabase(System.getProperty("aigw.pgTestImage"),
            java.nio.file.Path.of(System.getProperty("aigw.rootDir"))).start();
        database.bootstrap(false);
        assertThatThrownBy(() -> new org.springframework.boot.builder.SpringApplicationBuilder(com.nori.ai.gateway.app.AiGatewayApplication.class)
            .web(org.springframework.boot.WebApplicationType.NONE).profiles("migrate")
            .properties(Map.of("spring.datasource.url",database.jdbcUrl(),"spring.datasource.username","aigw_migrator",
                "spring.datasource.password",database.passwordFor("aigw_migrator"))).run())
            .hasMessage("일반 애플리케이션에서는 자동 마이그레이션을 사용할 수 없습니다");
        try (var connection = database.connection("postgres"); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM pg_tables WHERE schemaname IN ('gw','gw_flyway')")) {
            assertThat(result.next()).isTrue(); assertThat(result.getInt(1)).isZero();
        }
        withMigrationProperties("aigw_app", () -> assertThatThrownBy(() -> com.nori.ai.gateway.app.config.MigrateApplication.main(
            new String[]{"--spring.profiles.active=migrate"})).hasRootCauseMessage("마이그레이션 전용 역할이 필요합니다"));
        withMigrationProperties("aigw_migrator", () -> assertThatThrownBy(() -> com.nori.ai.gateway.app.config.MigrateApplication.main(
            new String[0])).hasMessageContaining("마이그레이션 프로필이 필요합니다"));
        withMigrationProperties("aigw_migrator", () -> assertThatThrownBy(() -> com.nori.ai.gateway.app.config.MigrateApplication.main(
            new String[]{"--spring.profiles.active=migrate", "--spring.flyway.enabled=false"})).isInstanceOf(RuntimeException.class));
        try (var connection = database.connection("postgres"); var statement = connection.createStatement()) {
            statement.execute("DROP EXTENSION vector");
        }
        withMigrationProperties("aigw_migrator", () -> assertThatThrownBy(() -> com.nori.ai.gateway.app.config.MigrateApplication.main(
            new String[]{"--spring.profiles.active=migrate"})).isInstanceOf(RuntimeException.class));
        database.bootstrap(false);
        withMigrationProperties("aigw_migrator", () -> {
            com.nori.ai.gateway.app.config.MigrateApplication.main(new String[]{"--spring.profiles.active=migrate"});
            com.nori.ai.gateway.app.config.MigrateApplication.main(new String[]{"--spring.profiles.active=migrate"});
        });
        startApplication();
        assertThat(context.getBeansOfType(com.nori.ai.gateway.app.config.MigrateCommand.class)).isEmpty();
        assertThat(context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class)
            .sql("SELECT count(*) FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL")
            .query(Integer.class).single()).isEqualTo(3);
    }

    private void withMigrationProperties(String role, Runnable action) {
        var properties = Map.of("spring.datasource.url", database.jdbcUrl(), "spring.datasource.username", role,
            "spring.datasource.password", database.passwordFor(role));
        var previous = new HashMap<String, String>();
        properties.forEach((key, value) -> { previous.put(key, System.getProperty(key)); System.setProperty(key, value); });
        try { action.run(); }
        finally { previous.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }); }
    }
}
