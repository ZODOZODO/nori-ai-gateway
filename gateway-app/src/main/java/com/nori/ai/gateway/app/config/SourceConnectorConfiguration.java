package com.nori.ai.gateway.app.config;

import com.nori.ai.gateway.app.job.DirectSourceToolExecutor;
import com.nori.ai.gateway.core.authz.SourceAuthorizationStep;
import com.nori.ai.gateway.core.port.SourceRetentionReader;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.gitlab.*;
import com.nori.ai.gateway.integration.loki.*;
import com.nori.ai.gateway.integration.tempo.TempoTraceConnector;
import com.nori.ai.gateway.persistence.retention.SourceRetentionProbeRepository;
import com.nori.ai.gateway.persistence.retention.SourceRetentionReaderAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * 정본 Source 선언(`config/sources/tc-sources-v1.json`)을 한 번 검증한 immutable snapshot으로 실제 Source Bean을 조립한다.
 * 운영 YAML/env가 주소·검증 상태·GitLab 범위를 덮어쓰는 경로는 없다. 각 Source는 literal `serviceTargetVerified=true`이고
 * 같은 snapshot의 주소·필수 token 경로가 갖춰진 경우에만 Bean을 만든다. 필수 값의 부재·false·mount 미준비는 Bean 0이고,
 * 제공된 값의 타입·URI·경로 형식 오류만 고정 기동 오류다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LokiProperties.class)
@Import({SourceConnectorConfiguration.FactoryConfiguration.class, SourceConnectorConfiguration.Registrar.class})
public class SourceConnectorConfiguration {
    public static final String RESOURCE = "config/sources/tc-sources-v1.json";

    @Bean SourceRetentionProbeRepository sourceRetentionProbeRepository(JdbcClient jdbc, TransactionTemplate transactions) {
        return new SourceRetentionProbeRepository(jdbc, transactions);
    }
    @Bean SourceRetentionReader sourceRetentionReader(SourceRetentionProbeRepository probes) { return new SourceRetentionReaderAdapter(probes); }

    /** 선언 파일 한 노드의 검증된 연결 metadata다. */
    public record SourceNode(String sourceId, Optional<URI> baseUrl, boolean serviceTargetVerified, boolean tokenRequired, Optional<Path> tokenPath) {
        public SourceNode { Objects.requireNonNull(sourceId); Objects.requireNonNull(baseUrl); Objects.requireNonNull(tokenPath); }
        @Override public String toString() { return "SourceNode[" + sourceId + ", 보호됨]"; }
    }
    /** loki 노드의 선택 `retentionProbe` 선언이다. startAt은 승인 gate가 [startAt, 주기 now) 공식과 함께 확인한 시작 시각이다. */
    public record RetentionProbeDeclaration(Instant startAt, String approvalRef) {
        public RetentionProbeDeclaration { Objects.requireNonNull(startAt); Objects.requireNonNull(approvalRef); }
        @Override public String toString() { return "RetentionProbeDeclaration[보호됨]"; }
    }
    /** 한 번 검증한 선언 snapshot이다. 원문을 다시 읽거나 전역 cache에 두지 않는다. */
    public record SourceSnapshot(SourceNode loki, SourceNode tempo, SourceNode gitlab, GitLabSourcePolicy gitLabPolicy,
            List<GitLabSourcePolicy.Repository> repositories, List<GitLabSourcePolicy.LineageEntry> deployLineage,
            Optional<RetentionProbeDeclaration> lokiRetentionProbe) {
        public SourceSnapshot { repositories = List.copyOf(repositories); deployLineage = List.copyOf(deployLineage); Objects.requireNonNull(lokiRetentionProbe); }
        @Override public String toString() { return "SourceSnapshot[보호됨]"; }

        public static SourceSnapshot parse(String json) {
            JsonNode root;
            GitLabSourcePolicy policy;
            try { root = SchemaContracts.parse(json); policy = TcSourceAllowlist.parse(json); }
            catch (IllegalArgumentException failure) { throw invalid(); }
            List<GitLabSourcePolicy.Repository> repositories = new ArrayList<>();
            List<GitLabSourcePolicy.LineageEntry> lineage = new ArrayList<>();
            try {
                for (JsonNode repo : root.path("gitlab").path("repositories")) repositories.add(new GitLabSourcePolicy.Repository(repo.path("projectId").asLong(),
                    repo.path("sourceRepo").asString(), strings(repo.path("refs")), strings(repo.path("pathPrefixes")), repo.path("approvalRef").asString()));
                for (JsonNode entry : root.path("gitlab").path("deployLineage")) lineage.add(new GitLabSourcePolicy.LineageEntry(entry.path("service").asString(),
                    entry.path("execProjectId").asLong(), entry.path("packageJobName").asString(), entry.path("sourceRepo").asString(),
                    entry.path("siblingProjectId").asLong(), entry.path("method").asString(), entry.path("approvalRef").asString()));
            } catch (RuntimeException failure) { throw invalid(); }
            // TcSourceAllowlist가 검증한 같은 범위만 실행 범위로 투영합니다.
            for (var repo : repositories) if (!policy.repository(repo.sourceRepo()).equals(Optional.of(repo))) throw invalid();
            return new SourceSnapshot(node(root.path("loki"), "loki", false), node(root.path("tempo"), "tempo", false),
                node(root.path("gitlab"), "gitlab", true), policy, repositories, lineage, retentionProbe(root.path("loki").path("retentionProbe")));
        }
        private static Optional<RetentionProbeDeclaration> retentionProbe(JsonNode node) {
            if (node.isMissingNode() || node.isNull()) return Optional.empty();
            if (!node.isObject() || !Set.copyOf(node.propertyNames()).equals(Set.of("startAt", "approvalRef"))) throw invalid();
            String startAt = optionalText(node.path("startAt")).orElseThrow(SourceConnectorConfiguration::invalid);
            String approvalRef = optionalText(node.path("approvalRef")).orElseThrow(SourceConnectorConfiguration::invalid);
            if (!startAt.endsWith("Z")) throw invalid();
            try { return Optional.of(new RetentionProbeDeclaration(Instant.parse(startAt), approvalRef)); }
            catch (java.time.format.DateTimeParseException malformed) { throw invalid(); }
        }
        private static Set<String> strings(JsonNode node) {
            Set<String> values = new HashSet<>();
            for (JsonNode value : node) values.add(value.asString());
            return values;
        }
        private static SourceNode node(JsonNode node, String sourceId, boolean tokenRequired) {
            if (!node.isObject()) throw invalid();
            JsonNode verifiedNode = node.path("serviceTargetVerified");
            if (!verifiedNode.isMissingNode() && !verifiedNode.isNull() && !verifiedNode.isBoolean()) throw invalid();
            boolean verified = verifiedNode.isBoolean() && verifiedNode.booleanValue();
            Optional<URI> baseUrl = optionalText(node.path("baseUrl")).map(SourceSnapshot::baseUrl);
            Optional<Path> token = tokenRequired ? optionalText(node.path("readTokenFile")).map(SourceSnapshot::tokenPath) : Optional.empty();
            return new SourceNode(sourceId, baseUrl, verified, tokenRequired, token);
        }
        /** SourceHttpClient 생성자와 같은 규칙(http(s)·host·userInfo/query/fragment 없음·root path)이다. */
        private static URI baseUrl(String text) {
            URI uri;
            try { uri = new URI(text); } catch (java.net.URISyntaxException malformed) { throw invalid(); }
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw invalid();
            return uri;
        }
        private static Optional<String> optionalText(JsonNode node) {
            if (node.isMissingNode() || node.isNull()) return Optional.empty();
            if (!node.isString() || node.asString().isBlank()) throw invalid();
            return Optional.of(node.asString());
        }
        private static Path tokenPath(String text) {
            if (text.chars().anyMatch(c -> c < 32 || c == 127)) throw invalid();
            try {
                Path path = Path.of(text);
                if (!path.isAbsolute() || !path.normalize().equals(path)) throw invalid();
                return path;
            } catch (InvalidPathException malformed) { throw invalid(); }
        }
    }

    /** 확인된 Loki 주소의 같은 전송기로, 명시 확인된 범위마다 기존 탐침을 만든다. 범위 기본값은 두지 않는다. */
    public static final class LokiProbeFactory {
        private final SourceHttpClient http;
        public LokiProbeFactory(SourceHttpClient http) { this.http = Objects.requireNonNull(http); }
        public LokiRetentionProbe create(TimeWindow confirmedWindow) { return new LokiRetentionProbe(http, confirmedWindow); }
    }
    /** 승인 운영 구성이 명시 확인한 주기별 탐침 범위만 공급하는 입력이다. 이 파일은 값을 생성하지 않는다. */
    public static final class ProbeWindowInput {
        private final BiFunction<String, Instant, Optional<TimeWindow>> windows;
        public ProbeWindowInput(BiFunction<String, Instant, Optional<TimeWindow>> windows) { this.windows = Objects.requireNonNull(windows); }
        public Optional<TimeWindow> window(String sourceId, Instant now) { return Objects.requireNonNull(windows.apply(sourceId, now)); }
    }

    /** Source 요청의 세 low-cardinality 값과 고정 span 이름·정규 상태만 OTel로 보낸다. URI·query·예외 본문은 읽지 않는다. */
    public static final class SourceSpans implements ObservationHandler<ClientRequestObservationContext> {
        static final String SPAN_NAME = "aigw.source.request";
        private static final Set<String> KEYS = Set.of("source_id", "method", "status_class");
        private final Tracer tracer;
        public SourceSpans(Tracer tracer) { this.tracer = Objects.requireNonNull(tracer); }
        @Override public boolean supportsContext(Observation.Context context) { return context instanceof ClientRequestObservationContext; }
        @Override public void onStart(ClientRequestObservationContext context) {
            context.put(Span.class, tracer.spanBuilder(SPAN_NAME).setSpanKind(SpanKind.CLIENT).setNoParent().startSpan());
        }
        @Override public void onStop(ClientRequestObservationContext context) {
            Span span = context.getRequired(Span.class);
            String status = null;
            for (var value : context.getLowCardinalityKeyValues()) {
                if (!KEYS.contains(value.getKey())) continue;
                span.setAttribute(value.getKey(), value.getValue());
                if (value.getKey().equals("status_class")) status = value.getValue();
            }
            span.setStatus("2xx".equals(status) ? StatusCode.OK : StatusCode.ERROR);
            span.end();
        }
    }

    /** Registrar가 factoryBeanName으로 지정하는 명시 factory다. 인자는 실제 타입으로 autowire된다. */
    @Configuration(value = FactoryConfiguration.NAME, proxyBeanMethods = false)
    public static class FactoryConfiguration {
        public static final String NAME = "sourceConnectorFactories";
        public SourceReadAccess sourceReadAccess(SourceAuthorizationStep authorization, Clock clock) { return new SourceReadAccess(authorization, clock); }
        public SourceObservability sourceObservability(MeterRegistry meters, OpenTelemetry openTelemetry) {
            return SourceObservability.create(meters, new SourceSpans(openTelemetry.getTracer("com.nori.ai.gateway.source")));
        }
        public SourceObservability sourceObservability(MeterRegistry meters, OpenTelemetry openTelemetry, org.springframework.beans.factory.ObjectProvider<com.nori.ai.gateway.app.hooks.SourceRequestProbe> probes) {
            var probe=probes.getIfAvailable();if(probe==null)return sourceObservability(meters,openTelemetry);
            var spans=new SourceSpans(openTelemetry.getTracer("com.nori.ai.gateway.source"));
            return SourceObservability.create(meters,new ObservationHandler<ClientRequestObservationContext>() {
                @Override public boolean supportsContext(Observation.Context context){return context instanceof ClientRequestObservationContext;}
                @Override public void onStart(ClientRequestObservationContext context){spans.onStart(context);probe.onStart(context);}
                @Override public void onStop(ClientRequestObservationContext context){try{probe.onStop(context);}finally{spans.onStop(context);}}
            });
        }
        public SourceHttpClient sourceHttpClient(SourceNode node, SourceObservability observability) {
            return new SourceHttpClient(node.baseUrl().orElseThrow(), node.sourceId(), observability);
        }
        public LokiLogConnector lokiLogConnector(SourceHttpClient http, SourceReadAccess access, SourceRetentionReader retention, LokiProperties properties, Clock clock) {
            return new LokiLogConnector(http, access, retention, properties, clock);
        }
        public LokiProbeFactory lokiProbeFactory(SourceHttpClient http) { return new LokiProbeFactory(http); }
        public TempoTraceConnector tempoTraceConnector(SourceHttpClient http, SourceReadAccess access) { return new TempoTraceConnector(http, access); }
        public GitLabCodeConnector gitLabCodeConnector(SourceHttpClient http, SourceReadAccess access, SourceSnapshot snapshot) {
            return new GitLabCodeConnector(http, access, snapshot.gitLabPolicy(), new GitLabReadTokenFile(snapshot.gitlab().tokenPath().orElseThrow()));
        }
        public GitLabPipelineLineageReader gitLabPipelineLineageReader(SourceHttpClient http, SourceReadAccess access, SourceSnapshot snapshot) {
            return new GitLabPipelineLineageReader(http, access, snapshot.gitLabPolicy(), new GitLabReadTokenFile(snapshot.gitlab().tokenPath().orElseThrow()));
        }
    }

    /** snapshot을 한 번 검증해 등록하고, ready Source의 concrete BeanDefinition만 등록한다. 기동 중 Source I/O는 없다. */
    public static class Registrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {
        public static final String SNAPSHOT = "sourceSnapshot";
        private final String declared;
        private ResourceLoader resources;
        public Registrar() { this.declared = null; }
        /** 시험과 독립 조립용: classpath 파일 대신 같은 검증을 받을 선언 문자열을 직접 받는다. */
        public Registrar(String declared) { this.declared = Objects.requireNonNull(declared); }
        @Override public void setResourceLoader(ResourceLoader resourceLoader) { this.resources = resourceLoader; }
        @Override public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            SourceSnapshot snapshot = SourceSnapshot.parse(declared != null ? declared : readDeclared());
            register(registry, SNAPSHOT, instance(SourceSnapshot.class, snapshot));
            boolean loki = ready(snapshot.loki()), tempo = ready(snapshot.tempo()), gitlab = ready(snapshot.gitlab());
            if (!loki && !tempo && !gitlab) return;
            register(registry, "sourceReadAccess", factory("sourceReadAccess"));
            register(registry, "sourceObservability", factory("sourceObservability"));
            if (loki) {
                register(registry, "lokiSourceHttpClient", factory("sourceHttpClient", snapshot.loki(), ref("sourceObservability")));
                register(registry, "lokiLogConnector", factory("lokiLogConnector", ref("lokiSourceHttpClient"), ref("sourceReadAccess")));
                register(registry, "lokiProbeFactory", factory("lokiProbeFactory", ref("lokiSourceHttpClient")));
                snapshot.lokiRetentionProbe().ifPresent(declared -> register(registry, "lokiProbeWindowInput",
                    instance(ProbeWindowInput.class, probeWindows(declared.startAt()))));
            }
            if (tempo) {
                register(registry, "tempoSourceHttpClient", factory("sourceHttpClient", snapshot.tempo(), ref("sourceObservability")));
                register(registry, "tempoTraceConnector", factory("tempoTraceConnector", ref("tempoSourceHttpClient"), ref("sourceReadAccess")));
            }
            if (gitlab) {
                // 실행 범위는 같은 snapshot 목록 그대로이며 GitLab Connector가 있을 때만 의미가 있습니다.
                register(registry, "sourceGitExecutionScopes", instance(DirectSourceToolExecutor.GitExecutionScopes.class,
                    new DirectSourceToolExecutor.GitExecutionScopes(snapshot.repositories(), snapshot.deployLineage())));
                register(registry, "gitlabSourceHttpClient", factory("sourceHttpClient", snapshot.gitlab(), ref("sourceObservability")));
                register(registry, "gitLabCodeConnector", factory("gitLabCodeConnector", ref("gitlabSourceHttpClient"), ref("sourceReadAccess"), ref(SNAPSHOT)));
                register(registry, "gitLabPipelineLineageReader", factory("gitLabPipelineLineageReader", ref("gitlabSourceHttpClient"), ref("sourceReadAccess"), ref(SNAPSHOT)));
            }
        }
        private static boolean ready(SourceNode node) {
            if (!node.serviceTargetVerified() || node.baseUrl().isEmpty()) return false;
            if (!node.tokenRequired()) return true;
            // 필수 Secret mount가 아직 없으면 Bean을 만들지 않고 실행 경계에서 실패하게 둡니다(파일 본문은 읽지 않음).
            return node.tokenPath().map(Files::isRegularFile).orElse(false);
        }
        /** 네 앱의 보존 SourceId에 대해 startAt < now인 주기만 새 [startAt, now) 창을 만든다. 이전 창은 보관하지 않는다. */
        static ProbeWindowInput probeWindows(Instant startAt) {
            Set<String> sourceIds = Set.copyOf(LokiSourcePolicy.APPS.stream().map(LokiSourcePolicy::retentionSourceId).toList());
            return new ProbeWindowInput((sourceId, now) -> sourceIds.contains(sourceId) && startAt.isBefore(now) ? Optional.of(new TimeWindow(startAt, now)) : Optional.empty());
        }
        private String readDeclared() {
            if (resources == null) throw invalid();
            try (InputStream input = resources.getResource("classpath:" + RESOURCE).getInputStream()) {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(input.readAllBytes())).toString();
            } catch (CharacterCodingException malformed) { throw invalid(); }
            catch (IOException missing) { throw invalid(); }
        }
        private static void register(BeanDefinitionRegistry registry, String name, RootBeanDefinition definition) {
            if (registry.containsBeanDefinition(name)) throw new IllegalStateException("Source 조립 Bean이 중복되었습니다: " + name);
            registry.registerBeanDefinition(name, definition);
        }
        private static <T> RootBeanDefinition instance(Class<T> type, T value) { return new RootBeanDefinition(type, () -> value); }
        private static RootBeanDefinition factory(String method, Object... explicitArguments) {
            var definition = new RootBeanDefinition();
            definition.setFactoryBeanName(FactoryConfiguration.NAME);
            definition.setFactoryMethodName(method);
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
            for (int i = 0; i < explicitArguments.length; i++) definition.getConstructorArgumentValues().addIndexedArgumentValue(i, explicitArguments[i]);
            return definition;
        }
        private static RuntimeBeanReference ref(String name) { return new RuntimeBeanReference(name); }
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Source 선언 구성이 잘못되었습니다"); }
}
