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
    @Test void disabledWithoutHooksRejectsAdmissionWithRetryAfterAndKeepsOneClock() {
        start();
        assertThat(context.getBeansOfType(java.time.Clock.class)).hasSize(1);
        assertThat(context.getBean(com.nori.ai.gateway.core.job.InferenceReadiness.class).ready()).isFalse();
        var now=context.getBean(java.time.Clock.class).instant();
        var scope=new VerifiedSystemScope(OwnerSystem.TC,"fixture",new ActorRef("actor"),"actor",Set.of(),"a".repeat(64),now,now.plusSeconds(60),"wiring");
        assertThatThrownBy(()->context.getBean(com.nori.ai.gateway.app.job.JobAdmissionService.class).accept(scope,
            new com.nori.ai.gateway.app.job.JobAdmissionService.AdmissionRequest("/v1/query",null,"질문",Map.of(),"wiring",UUID.randomUUID().toString().replace("-",""))))
            .isInstanceOfSatisfying(com.nori.ai.gateway.app.job.JobAdmissionService.Rejected.class,e->{assertThat(e.status()).isEqualTo(503);assertThat(e.error()).isEqualTo("INFERENCE_UNAVAILABLE");assertThat(e.retryAfter()).isEqualTo(60);});
    }
    private String credentialFailure;
    private final String credentialSentinel = "credential-sentinel-" + UUID.randomUUID();
    @Override protected void prepareSecurityCredentials() {
        super.prepareSecurityCredentials();
        if (credentialFailure == null) return;
        try {
            switch (credentialFailure) {
                case "missing" -> java.nio.file.Files.delete(securityDirectory.resolve("service-tokens.json"));
                case "malformed" -> java.nio.file.Files.writeString(securityDirectory.resolve("service-tokens.json"),"{\"" + credentialSentinel);
                case "empty" -> java.nio.file.Files.writeString(securityDirectory.resolve("jwks.json"),"{\"keys\":[]}");
                case "private" -> java.nio.file.Files.writeString(securityDirectory.resolve("jwks.json"),"{\"keys\":[{\"extra\":[{\"d\":\"" + credentialSentinel + "\"}]}]}");
                default -> throw new IllegalArgumentException("알 수 없는 시험 자격 모드입니다");
            }
        } catch (java.io.IOException failure) { throw new IllegalStateException("시험 자격 변형에 실패했습니다"); }
    }

    @Test @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void actualApplicationStartupRejectsMissingMalformedEmptyAndNestedPrivateCredentials(org.springframework.boot.test.system.CapturedOutput output) {
        database = new com.nori.ai.gateway.persistence.testing.PgTestDatabase(System.getProperty("aigw.pgTestImage"),java.nio.file.Path.of(System.getProperty("aigw.rootDir"))).start();
        database.bootstrap(true); database.migrate();
        for (String mode : List.of("missing","malformed","empty","private")) {
            credentialFailure = mode; LaterRunner.invocations = 0;
            assertThatThrownBy(() -> startApplication(LaterRunner.class)).hasStackTraceContaining("인입 인증 자격 파일이 잘못되었습니다");
            assertThat(LaterRunner.invocations).isZero();
            assertThat(output.getAll().contains(credentialSentinel)).as("기동 오류 자격 원문 잔류 건수").isFalse();
        }
        credentialFailure = null; startApplication(LaterRunner.class);
        assertThat(LaterRunner.invocations).isEqualTo(1);
    }
    @Test void applicationImportsAllModulesAndRealP05CoreBeans() {
        start();
        assertThat(context.getEnvironment().getActiveProfiles()).contains("verify");
        for (Class<?> type : new Class<?>[]{CoreBeansConfiguration.class, IntegrationModuleConfiguration.class,
                PersistenceModuleConfiguration.class, TcMockModuleConfiguration.class, ManifestLoader.class,
                CapabilityRegistry.class, ContractVersionPolicy.class, PlanValidator.class, ToolDispatcher.class,
                ToolResultContractValidator.class, PlanInjectionSeam.class, DispatchInjectionSeam.class})
            assertThat(context.getBeansOfType(type)).hasSize(1);
        assertThat(Arrays.stream(CoreBeansConfiguration.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Bean.class))).hasSize(11);
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
        for (Class<?> type : List.of(com.nori.ai.gateway.app.security.IngressSecurityConfiguration.class,
                com.nori.ai.gateway.app.security.SecurityCredentialFiles.class,com.nori.ai.gateway.app.security.ActorAssertionVerifier.class,
                com.nori.ai.gateway.app.security.ScopeAssembler.class,com.nori.ai.gateway.app.security.RouteClassifier.class,
                com.nori.ai.gateway.app.security.BodySystemFieldGuard.class,com.nori.ai.gateway.app.security.AuthRejectionAuditor.class,
                com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository.class,
                org.springframework.boot.web.servlet.FilterRegistrationBean.class)) assertThat(context.getBeansOfType(type)).hasSize(1);
        var clock = context.getBean(java.time.Clock.class);
        for (Class<?> type : List.of(com.nori.ai.gateway.app.security.ActorAssertionVerifier.class,com.nori.ai.gateway.persistence.audit.auth.AuthRejectionAppendRepository.class)) {
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(type),"clock")).isSameAs(clock);
        }
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(conversations,"clock")).isSameAs(clock);
        assertThat(context.getBeansOfType(com.nori.ai.gateway.app.security.ServiceTokenAuthFilter.class)).isEmpty();
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
            String latest;
            try (var rows = statement.executeQuery("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")) {
                assertThat(rows.next()).isTrue(); latest = rows.getString(1); assertThat(latest).isEqualTo("4");
            }
            try (var update = connection.prepareStatement("UPDATE gw_flyway.flyway_schema_history SET version = '10' WHERE version = ?")) { update.setString(1,latest); assertThat(update.executeUpdate()).isEqualTo(1); }
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
        prepareSecurityCredentials();
        assertThatThrownBy(() -> new org.springframework.boot.builder.SpringApplicationBuilder(com.nori.ai.gateway.app.AiGatewayApplication.class)
            .web(org.springframework.boot.WebApplicationType.NONE).profiles("migrate")
            .properties(Map.of("spring.datasource.url",database.jdbcUrl(),"spring.datasource.username","aigw_migrator",
                "spring.datasource.password",database.passwordFor("aigw_migrator"))).properties(securityProperties()).run())
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
            .query(Integer.class).single()).isEqualTo(5);
        assertThat(context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class).sql("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")
            .query(String.class).list()).containsExactly("1","2","3","3.1","4");
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
