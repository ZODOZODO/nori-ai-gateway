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
    static final class BoundaryClock extends java.time.Clock {
        java.time.Instant now=java.time.Instant.parse("2040-01-01T00:00:00Z");
        public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
        public java.time.Clock withZone(java.time.ZoneId zone){return this;}
        public java.time.Instant instant(){return now;}
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class BoundaryClockConfiguration { @Bean BoundaryClock boundaryClock(){return new BoundaryClock();} }
    @Test void actualCoreBeansShareGatewayClockAndRejectAtAndAfterItsExactExpiry() {
        start(BoundaryClockConfiguration.class);var clock=context.getBean(BoundaryClock.class);
        var validator=context.getBean(PlanValidator.class);var dispatcher=context.getBean(ToolDispatcher.class);
        assertThat(context.getBeansOfType(java.time.Clock.class)).hasSize(1);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(validator,"clock")).isSameAs(clock);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(dispatcher,"clock")).isSameAs(clock);
        var scope=new VerifiedSystemScope(OwnerSystem.TC,"fixture",new ActorRef("actor"),"actor",Set.of("EQP_READ"),"a".repeat(64),clock.now.minusSeconds(1),clock.now.plusSeconds(60),"clock-boundary");
        var plan=new PlanEnvelope(scope.requestId(),"conversation","질문",List.of(),TaskType.GENERAL_QUERY,List.of());
        var step=new PlanStep("one","tc.eqp.getOperationalSnapshot",List.of(),List.of(new ResourceRef("EQP","EQP_001")),SchemaContracts.parse("{\"eqp\":\"EQP_001\"}"));
        for(long nanos:new long[]{-1,0,1}){
            clock.now=scope.expiresAt().plusNanos(nanos);var reads=new java.util.concurrent.atomic.AtomicInteger();
            ResourceResolver resolver=new ResourceResolver(){
                public ResourceState resolve(ResourceRef resource,VerifiedSystemScope actual){reads.incrementAndGet();return new ResourceState(OwnerSystem.TC,"EQP",true);}
                public SourceBinding source(CapabilityManifest manifest,VerifiedSystemScope actual){return new SourceBinding(OwnerSystem.TC,manifest.sourceAllowlistRef(),true);}
                public CurrentAuthorization authorize(CapabilityManifest manifest,PlanStep step,VerifiedSystemScope actual){return new CurrentAuthorization(OwnerSystem.TC,manifest.sourceAllowlistRef(),actual.actorRef(),actual.pdg(),true,actual.perm(),Set.of("READ"));}
                public java.time.ZoneId timezone(VerifiedSystemScope actual){return java.time.ZoneOffset.UTC;}
            };
            assertThat(validator.validate(plan,scope,null).accepted()).isEqualTo(nanos<0);
            var result=dispatcher.dispatch(step,scope,new ExecutionContext(resolver,trace->{}));
            assertThat(reads.get()).isEqualTo(nanos<0?1:0);
            assertThat(result.errorCode()).isEqualTo(nanos<0?null:com.nori.ai.gateway.core.result.ErrorCode.SYSTEM_SCOPE_MISMATCH);
        }
    }
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
                ToolResultContractValidator.class, PlanInjectionSeam.class, DispatchInjectionSeam.class,
                com.nori.ai.gateway.core.result.ResultAccessEvaluator.class,com.nori.ai.gateway.core.result.KnowledgeDocumentStateReader.class,
                com.nori.ai.gateway.core.result.EvidenceSourceReader.class,com.nori.ai.gateway.core.expiry.ExpiryCheckpoint.class})
            assertThat(context.getBeansOfType(type)).hasSize(1);
        for(Class<?> type:List.of(com.nori.ai.gateway.core.workflow.LogSummarizer.class,com.nori.ai.gateway.core.blocks.GeneralQueryHelpBuilder.class,com.nori.ai.gateway.core.evidence.EvidenceAssembler.class,com.nori.ai.gateway.core.workflow.WorkflowExecutor.class,com.nori.ai.gateway.app.job.WorkflowExecutionAdapter.class))assertThat(context.getBeansOfType(type)).hasSize(1);
        assertThat(context.getBeansOfType(com.nori.ai.gateway.core.workflow.ResultSourceDepsRecorder.class)).isEmpty();
        var actual=context.getBean(com.nori.ai.gateway.core.workflow.WorkflowExecutor.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(actual,"summarizer")).isSameAs(context.getBean(com.nori.ai.gateway.core.workflow.LogSummarizer.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(actual,"help")).isSameAs(context.getBean(com.nori.ai.gateway.core.blocks.GeneralQueryHelpBuilder.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(com.nori.ai.gateway.app.job.WorkflowExecutionAdapter.class),"evidenceAssembler")).isSameAs(context.getBean(com.nori.ai.gateway.core.evidence.EvidenceAssembler.class));
        assertThat(context.getBean(PlanInjectionSeam.class)).isExactlyInstanceOf(PlanInjectionSeam.NoOp.class);
        assertThat(context.getBean(DispatchInjectionSeam.class)).isExactlyInstanceOf(DispatchInjectionSeam.NoOp.class);
        assertThat(context.getBeansOfType(ToolExecutor.class).values()).extracting(ToolExecutor::toolKind).containsExactlyInAnyOrder(ToolKind.BUSINESS,ToolKind.LOG,ToolKind.TRACE,ToolKind.GIT);
        var loader=context.getBean(ManifestLoader.class);var manifests=loader.loadClasspath("manifests/tc/index.json");
        assertThat(manifests).hasSize(10);
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
    @Test void preCallBudgetAssertCountsAGoldenRowWithTheActualTokenizerBean() {
        start();
        for (Class<?> type : List.of(com.nori.ai.gateway.core.budget.TokenBudgetPolicy.class, com.nori.ai.gateway.core.budget.PromptReducer.class,
                com.nori.ai.gateway.core.budget.PreCallBudgetAssert.class, com.nori.ai.gateway.core.budget.TruncationDetector.class,
                com.nori.ai.gateway.core.budget.PromptTokenCounting.class, com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter.class,
                com.nori.ai.gateway.integration.tokenizer.QwenChatTemplateRenderer.class)) assertThat(context.getBeansOfType(type)).as(type.getName()).hasSize(1);
        var guard = context.getBean(com.nori.ai.gateway.core.budget.PreCallBudgetAssert.class);
        var counting = context.getBean(com.nori.ai.gateway.core.budget.PromptTokenCounting.class);
        var policy = context.getBean(com.nori.ai.gateway.core.budget.TokenBudgetPolicy.class);
        assertThat(counting).isExactlyInstanceOf(com.nori.ai.gateway.integration.tokenizer.PromptTokenCounter.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(guard, "counter")).isSameAs(counting);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(guard, "policy")).isSameAs(policy);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(com.nori.ai.gateway.core.budget.PromptReducer.class), "counter")).isSameAs(counting);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(counting, "tokenizer")).isSameAs(context.getBean(com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(counting, "renderer")).isSameAs(context.getBean(com.nori.ai.gateway.integration.tokenizer.QwenChatTemplateRenderer.class));
        assertThat(policy.budget(com.nori.ai.gateway.core.budget.CallType.COMPOSER).requestMaxTokens()).isEqualTo(5_248);
        // P03 v4 token-golden TG01-KO(20261104T044320Z-diag-v2-t0-b): Ollama usage.prompt_tokens = 40
        var golden = List.of(new com.nori.ai.gateway.core.budget.ChatMessage(com.nori.ai.gateway.core.budget.ChatMessage.Role.SYSTEM, "너는 TC 설비 도우미다."),
            new com.nori.ai.gateway.core.budget.ChatMessage(com.nori.ai.gateway.core.budget.ChatMessage.Role.USER, "포토 설비의 오늘 상태를 간단히 알려줘."));
        assertThat(guard.check(com.nori.ai.gateway.core.budget.CallType.CLASSIFY, golden, 5_120)).isEqualTo(new com.nori.ai.gateway.core.budget.PreCallBudgetAssert.Allowed(40));
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
                assertThat(rows.next()).isTrue(); latest = rows.getString(1); assertThat(latest).isEqualTo("7");
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
            .query(Integer.class).single()).isEqualTo(7);
        assertThat(context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class).sql("SELECT version FROM gw_flyway.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank")
            .query(String.class).list()).containsExactly("1","2","3","3.1","4","5","7");
    }

    private void withMigrationProperties(String role, Runnable action) {
        var properties = Map.of("spring.datasource.url", database.jdbcUrl(), "spring.datasource.username", role,
            "spring.datasource.password", database.passwordFor(role));
        var previous = new HashMap<String, String>();
        properties.forEach((key, value) -> { previous.put(key, System.getProperty(key)); System.setProperty(key, value); });
        try { action.run(); }
        finally { previous.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }); }
    }

    // ---- P14 Source 생산 조립: 같은 Registrar·factory에 명시 snapshot을 공급한다(운영 classpath 파일·전역 cache·env override 없음) ----
    private static final String APPS = "[\"tc-gateway\",\"tc-business\",\"tc-ui-backend\",\"tc-ui-front\"]";
    private static String sourceDeclaration(String loki, String tempo, String gitlab) {
        return "{\"ownerSystem\":\"TC\",\"loki\":{\"sourceAllowlistRef\":\"sources://TC/logs\",\"apps\":" + APPS + ",\"namespace\":\"tc\",\"retentionPolicy\":\"UNBOUNDED_UNVERIFIED\"" + loki + "},"
            + "\"tempo\":{\"sourceAllowlistRef\":\"sources://TC/trace\",\"services\":" + APPS + ",\"retentionPolicy\":\"UNVERIFIED\"" + tempo + "},"
            + "\"gitlab\":{\"sourceAllowlistRef\":\"sources://TC/git\",\"repositories\":[{\"projectId\":22,\"sourceRepo\":\"nori-tc-gateway\",\"refs\":[\"main\"],\"pathPrefixes\":[\"src/main\"],\"approvalRef\":\"local-test-approval\"}],"
            + "\"deployLineage\":[{\"service\":\"tc-gateway\",\"execProjectId\":11,\"packageJobName\":\"package\",\"sourceRepo\":\"nori-tc-gateway\",\"siblingProjectId\":22,\"method\":\"SIBLING_CLONE_LOG\",\"approvalRef\":\"local-test-approval\"}]" + gitlab + "}}";
    }
    private static String target(boolean verified, String base) { return ",\"baseUrl\":\"" + base + "\",\"serviceTargetVerified\":" + verified; }
    private org.springframework.context.annotation.AnnotationConfigApplicationContext sourceChild(String declaration) {
        var child = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        child.setParent(context);
        child.register(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.FactoryConfiguration.class);
        new com.nori.ai.gateway.app.config.SourceConnectorConfiguration.Registrar(declaration)
            .registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata.introspect(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.class), child);
        child.refresh();
        return child;
    }
    private static final List<Class<?>> SOURCE_BEANS = List.of(com.nori.ai.gateway.integration.loki.LokiLogConnector.class, com.nori.ai.gateway.integration.tempo.TempoTraceConnector.class,
        com.nori.ai.gateway.integration.gitlab.GitLabCodeConnector.class, com.nori.ai.gateway.integration.gitlab.GitLabPipelineLineageReader.class,
        com.nori.ai.gateway.integration.loki.SourceHttpClient.class, com.nori.ai.gateway.integration.loki.SourceReadAccess.class, com.nori.ai.gateway.integration.loki.SourceObservability.class,
        com.nori.ai.gateway.app.config.SourceConnectorConfiguration.LokiProbeFactory.class, com.nori.ai.gateway.app.config.SourceConnectorConfiguration.ProbeWindowInput.class,
        com.nori.ai.gateway.app.job.DirectSourceToolExecutor.GitExecutionScopes.class);

    @Test void productionSourceDeclarationRegistersNoSourceBeanAndOnlyTheActualRetentionReader() throws Exception {
        var declared = java.nio.file.Path.of(System.getProperty("aigw.rootDir"), "gateway-app/src/main/resources/config/sources/tc-sources-v1.json");
        byte[] before = java.nio.file.Files.readAllBytes(declared);
        start();
        for (Class<?> type : SOURCE_BEANS) assertThat(context.getBeansOfType(type)).as(type.getName()).isEmpty();
        assertThat(context.getBeansOfType(com.nori.ai.gateway.core.port.SourceRetentionReader.class).values()).singleElement()
            .isInstanceOf(com.nori.ai.gateway.persistence.retention.SourceRetentionReaderAdapter.class);
        var snapshot = context.getBean(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.SourceSnapshot.class);
        assertThat(snapshot.loki().serviceTargetVerified()).isFalse();
        assertThat(snapshot.tempo().baseUrl()).isEmpty();
        assertThat(snapshot.gitlab().baseUrl()).isEmpty();
        assertThat(snapshot.gitlab().tokenPath()).isEmpty();
        assertThat(snapshot.repositories()).isEmpty();
        assertThat(snapshot.toString()).doesNotContain("monitoring", "http");
        var scheduler = context.getBean(com.nori.ai.gateway.app.ops.SourceRetentionProbeScheduler.class);
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (!scheduler.started() && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(scheduler.started()).isTrue();
        Thread.sleep(1500);
        // factory·확인 범위 입력이 없으므로 탐침 생성·측정·저장은 없습니다(Gate 미확정 NOT_RUN).
        assertThat(context.getBean(org.springframework.jdbc.core.simple.JdbcClient.class).sql("SELECT count(*) FROM gw.source_retention_probe").query(Integer.class).single()).isZero();
        assertThat(java.nio.file.Files.readAllBytes(declared)).isEqualTo(before);
    }

    @Test void sameRegistrarBuildsOnlyVerifiedCompleteSourcesAndRejectsActiveMalformedDeclarationsWithoutSourceIo() throws Exception {
        start();
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                hits.incrementAndGet();
                byte[] body = "{\"status\":\"success\",\"data\":{\"resultType\":\"streams\",\"result\":[]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var token = securityDirectory.resolve("gitlab-read-token");
            java.nio.file.Files.writeString(token, UUID.randomUUID().toString());
            String tokenJson = ",\"readTokenFile\":\"" + token.toString().replace("\\", "\\\\") + "\"";
            try (var child = sourceChild(sourceDeclaration(target(true, base), target(true, base), target(true, base) + tokenJson))) {
                for (Class<?> type : SOURCE_BEANS) if (type != com.nori.ai.gateway.app.config.SourceConnectorConfiguration.ProbeWindowInput.class)
                    assertThat(child.getBeansOfType(type)).as(type.getName()).hasSize(type == com.nori.ai.gateway.integration.loki.SourceHttpClient.class ? 3 : 1);
                assertThat(child.getBeansOfType(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.ProbeWindowInput.class)).isEmpty();
                var loki = child.getBean(com.nori.ai.gateway.integration.loki.LokiLogConnector.class);
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(loki, "http")).isSameAs(child.getBean("lokiSourceHttpClient"));
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(loki, "access")).isSameAs(child.getBean(com.nori.ai.gateway.integration.loki.SourceReadAccess.class));
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(loki, "retention")).isSameAs(context.getBean(com.nori.ai.gateway.core.port.SourceRetentionReader.class));
                var scopes = child.getBean(com.nori.ai.gateway.app.job.DirectSourceToolExecutor.GitExecutionScopes.class);
                assertThat(scopes.repositories()).singleElement().satisfies(repo -> assertThat(repo.sourceRepo()).isEqualTo("nori-tc-gateway"));
                assertThat(scopes.deployLineage()).hasSize(1);
                assertThat(hits).as("기동 중 Source I/O 0").hasValue(0);
                var now = context.getBean(java.time.Clock.class).instant();
                assertThat(child.getBean(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.LokiProbeFactory.class)
                    .create(new com.nori.ai.gateway.core.retention.TimeWindow(now.minusSeconds(3600), now)).measure("tc-gateway")).isEmpty();
                assertThat(hits.get()).isPositive();
            }
            int afterPositive = hits.get();
            // 필수 값 부재(verified=true지만 주소·token 경로 없음 포함)·false·불완전(필수 token mount 없음)은 Bean 0·Source I/O 0입니다.
            for (String declaration : List.of(sourceDeclaration(target(false, base), "", ""), sourceDeclaration("", target(false, base), ""),
                    sourceDeclaration(",\"serviceTargetVerified\":true", ",\"serviceTargetVerified\":true", ",\"serviceTargetVerified\":true"),
                    sourceDeclaration("", "", target(true, base)),
                    sourceDeclaration(target(false, base) + ",\"retentionProbe\":{\"startAt\":\"2026-11-01T00:00:00Z\",\"approvalRef\":\"local-gate-approval\"}", "", ""),
                    sourceDeclaration("", "", target(true, base) + ",\"readTokenFile\":\"" + securityDirectory.resolve("missing-token").toString().replace("\\", "\\\\") + "\""))) {
                try (var child = sourceChild(declaration)) {
                    for (Class<?> type : SOURCE_BEANS) assertThat(child.getBeansOfType(type)).as(type.getName()).isEmpty();
                }
            }
            // retentionProbe가 null이면 부재와 같으므로 ready Loki의 Bean만 있고 확인 범위 입력은 0입니다.
            try (var child = sourceChild(sourceDeclaration(target(true, base) + ",\"retentionProbe\":null", "", ""))) {
                assertThat(child.getBeansOfType(com.nori.ai.gateway.integration.loki.LokiLogConnector.class)).hasSize(1);
                assertThat(child.getBeansOfType(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.ProbeWindowInput.class)).isEmpty();
            }
            // 제공된 값의 타입·URI(SourceHttpClient와 같은 규칙)·경로·retentionProbe 형식 오류만 고정 비민감 기동 오류이며 주소·경로를 추정하지 않습니다.
            String probe = ",\"retentionProbe\":";
            for (String declaration : List.of(sourceDeclaration(",\"baseUrl\":\"" + base + "\",\"serviceTargetVerified\":\"true\"", "", ""),
                    sourceDeclaration("", "", target(true, base) + ",\"readTokenFile\":\"relative/read-token\""), sourceDeclaration(target(true, "http://exa mple"), "", ""),
                    sourceDeclaration(target(true, "ftp://127.0.0.1:1"), "", ""), sourceDeclaration(target(false, "ftp://127.0.0.1:1"), "", ""),
                    sourceDeclaration("", target(true, "http://user@127.0.0.1:1"), ""), sourceDeclaration(target(true, base + "/loki"), "", ""),
                    sourceDeclaration(target(true, base + "/?q=1"), "", ""), sourceDeclaration(target(true, base + "#fragment"), "", ""), sourceDeclaration(",\"baseUrl\":7", "", ""),
                    sourceDeclaration(target(true, base) + probe + "\"2026-11-01T00:00:00Z\"", "", ""),
                    sourceDeclaration(target(true, base) + probe + "{\"startAt\":\"2026-11-01T00:00:00\",\"approvalRef\":\"local-gate-approval\"}", "", ""),
                    sourceDeclaration(target(true, base) + probe + "{\"startAt\":\"2026-11-01T09:00:00+09:00\",\"approvalRef\":\"local-gate-approval\"}", "", ""),
                    sourceDeclaration(target(true, base) + probe + "{\"startAt\":\"not-a-time\",\"approvalRef\":\"local-gate-approval\"}", "", ""),
                    sourceDeclaration(target(true, base) + probe + "{\"startAt\":\"2026-11-01T00:00:00Z\",\"approvalRef\":\"  \"}", "", ""),
                    sourceDeclaration(target(true, base) + probe + "{\"startAt\":\"2026-11-01T00:00:00Z\"}", "", ""),
                    sourceDeclaration(target(true, base) + probe + "{\"startAt\":\"2026-11-01T00:00:00Z\",\"approvalRef\":\"local-gate-approval\",\"endAt\":\"2026-11-02T00:00:00Z\"}", "", "")))
                assertThatThrownBy(() -> sourceChild(declaration).close()).isInstanceOf(IllegalStateException.class).hasMessage("Source 선언 구성이 잘못되었습니다");
            assertThat(hits).as("음성·오류 경로의 Source I/O 0").hasValue(afterPositive);
            // 명시 승인 선언의 [startAt, 주기 now) 창만 네 앱 보존 SourceId에 공급하고 같은 snapshot의 Loki 전송기로 실제 측정합니다.
            var now = context.getBean(java.time.Clock.class).instant();
            var startAt = now.minus(java.time.Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            try (var child = sourceChild(sourceDeclaration(target(true, base) + probe + "{\"startAt\":\"" + startAt + "\",\"approvalRef\":\"local-gate-approval\"}", "", ""))) {
                var input = child.getBean(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.ProbeWindowInput.class);
                assertThat(hits).as("기동 중 Source I/O 0").hasValue(afterPositive);
                for (String app : com.nori.ai.gateway.integration.loki.LokiSourcePolicy.APPS) {
                    String sourceId = com.nori.ai.gateway.integration.loki.LokiSourcePolicy.retentionSourceId(app);
                    assertThat(input.window(sourceId, now)).contains(new com.nori.ai.gateway.core.retention.TimeWindow(startAt, now));
                    assertThat(input.window(sourceId, now.plus(java.time.Duration.ofHours(24)))).contains(new com.nori.ai.gateway.core.retention.TimeWindow(startAt, now.plus(java.time.Duration.ofHours(24))));
                    assertThat(input.window(sourceId, startAt)).as("startAt이 주기 now보다 앞서지 않으면 창 0").isEmpty();
                    assertThat(input.window(sourceId, startAt.minusSeconds(1))).isEmpty();
                }
                assertThat(input.window("loki:other-app", now)).isEmpty();
                var window = input.window(com.nori.ai.gateway.integration.loki.LokiSourcePolicy.retentionSourceId("tc-gateway"), now).orElseThrow();
                assertThat(child.getBean(com.nori.ai.gateway.app.config.SourceConnectorConfiguration.LokiProbeFactory.class).create(window).measure("tc-gateway")).isEmpty();
                assertThat(hits.get()).isGreaterThan(afterPositive);
            }
            int afterProbe = hits.get();
            var duplicate = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
            var ready = sourceDeclaration(target(true, base), "", "");
            new com.nori.ai.gateway.app.config.SourceConnectorConfiguration.Registrar(ready).registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata.introspect(Object.class), duplicate);
            assertThatThrownBy(() -> new com.nori.ai.gateway.app.config.SourceConnectorConfiguration.Registrar(ready).registerBeanDefinitions(org.springframework.core.type.AnnotationMetadata.introspect(Object.class), duplicate))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("중복");
            assertThat(hits).as("중복 등록 오류 경로의 Source I/O 0").hasValue(afterProbe);
        } finally { server.stop(0); }
    }
    private static String rootMessage(Throwable failure) { Throwable root = failure; while (root.getCause() != null) root = root.getCause(); return root.getMessage(); }
}
