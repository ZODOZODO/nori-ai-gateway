package com.nori.ai.gateway.app.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MetricLabelCardinalityTest {
    private static final com.nori.ai.gateway.core.registry.CapabilityRegistry REGISTRY = new com.nori.ai.gateway.core.registry.CapabilityRegistry(
        new com.nori.ai.gateway.core.registry.ManifestLoader().loadClasspath("manifests/tc/index.json"), new com.nori.ai.gateway.core.registry.ManifestLoader().loadWorkflowsClasspath("workflows/tc/index.json"),
        java.util.Set.of(com.nori.ai.gateway.core.registry.ImplementationPhase.PHASE_1), new com.nori.ai.gateway.core.registry.ContractVersionPolicy());
    private static final List<String> SECTION_10_2 = List.of("aigw.jobs.accepted", "aigw.jobs.rejected", "aigw.jobs.terminal", "aigw.job.queue.wait",
        "aigw.job.execution", "aigw.job.e2e", "aigw.queue.depth", "aigw.running.jobs", "aigw.generation.inflight", "aigw.llm.call", "aigw.llm.tokens",
        "aigw.llm.budget.reduction", "aigw.llm.retry", "aigw.tool.call", "aigw.source.authz", "aigw.inference.readiness.state", "aigw.inference.alert",
        "aigw.knowledge.search", "aigw.ingest.events", "aigw.ingest.tx",
        "aigw.retention.purged.rows", "aigw.sse.connections", "aigw.sse.closed");

    @Test void catalogCoversSection10AndHasNoHighCardinalityIdentifierLabels() {
        assertThat(MetricsCatalog.specs().keySet()).containsAll(SECTION_10_2);
        for (var spec : MetricsCatalog.specs().values()) {
            for (var label : spec.labels().entrySet()) {
                assertThat(label.getKey()).as(spec.name()).doesNotContainPattern("(?i)(request|conversation|job|trace|span|actor|message|evidence)_?id|^id$");
                // 어떤 라벨도 서버 식별자 형식의 값을 받지 않습니다.
                assertThat(label.getValue().matcher(UUID.randomUUID().toString()).matches()).as(spec.name() + "." + label.getKey()).isFalse();
                assertThat(label.getValue().matcher("0".repeat(32)).matches()).as(spec.name() + "." + label.getKey()).isFalse();
            }
        }
    }

    @Test void filterDeniesUnknownNamesKeysAndValuesAndAcceptsOnlyClosedEnumValues() {
        var registry = new SimpleMeterRegistry();
        registry.config().meterFilter(MetricsCatalog.filter(REGISTRY));
        Counter.builder("aigw.jobs.terminal").tags("owner_system", "TC", "job_status", "SUCCEEDED", "error_code", "QUEUE_TIMEOUT").register(registry).increment();
        Counter.builder("aigw.jobs.terminal").tags("owner_system", "TC", "job_status", "SUCCEEDED", "error_code", "QUEUE_TIMEOUT", "job_id", UUID.randomUUID().toString()).register(registry).increment();
        Counter.builder("aigw.jobs.terminal").tags("owner_system", "OTHER", "job_status", "SUCCEEDED", "error_code", "QUEUE_TIMEOUT").register(registry).increment();
        Counter.builder("aigw.llm.retry").tags("call_type", "plan", "reason", "anything").register(registry).increment();
        Counter.builder("aigw.unlisted").register(registry).increment();
        Counter.builder("jvm.threads.live").tags("id", "free text").register(registry).increment();
        Counter.builder(MetricsCatalog.RETENTION_PURGED_ROWS).tag("kind", "conversation").register(registry).increment();
        Counter.builder("hikaricp.connections.pending").tag("pool", "HikariPool-1").register(registry).increment();
        assertThat(registry.getMeters().stream().map(meter -> meter.getId().getName() + meter.getId().getTags()))
            .containsExactlyInAnyOrder("aigw.jobs.terminal[tag(error_code=QUEUE_TIMEOUT), tag(job_status=SUCCEEDED), tag(owner_system=TC)]",
                MetricsCatalog.RETENTION_PURGED_ROWS + "[tag(kind=conversation)]", "hikaricp.connections.pending[tag(pool=HikariPool-1)]");
    }

    @Test void inferenceReadinessAlertAndRejectionLabelsIncludeTheDigestMismatchAndNothingElse() {
        var registry = new SimpleMeterRegistry();
        registry.config().meterFilter(MetricsCatalog.filter(REGISTRY));
        Counter.builder("aigw.jobs.rejected").tags("owner_system", "TC", "reason", "MODEL_DIGEST_MISMATCH").register(registry).increment();
        io.micrometer.core.instrument.Gauge.builder("aigw.inference.readiness.state", () -> 1).tag("state", "MODEL_DIGEST_MISMATCH").register(registry);
        io.micrometer.core.instrument.Gauge.builder("aigw.inference.readiness.state", () -> 0).tag("state", "READY").register(registry);
        io.micrometer.core.instrument.Gauge.builder("aigw.inference.alert", () -> 1).tag("state", "MODEL_DIGEST_MISMATCH").register(registry);
        // 경보는 비READY 상태만, 사유(ENDPOINT_ERROR)는 state 라벨 값이 아니다.
        io.micrometer.core.instrument.Gauge.builder("aigw.inference.alert", () -> 0).tag("state", "READY").register(registry);
        io.micrometer.core.instrument.Gauge.builder("aigw.inference.readiness.state", () -> 0).tag("state", "ENDPOINT_ERROR").register(registry);
        Counter.builder("aigw.jobs.rejected").tags("owner_system", "TC", "reason", "MODEL_DIGEST").register(registry).increment();
        assertThat(registry.getMeters().stream().map(meter -> meter.getId().getName() + meter.getId().getTags())).containsExactlyInAnyOrder(
            "aigw.jobs.rejected[tag(owner_system=TC), tag(reason=MODEL_DIGEST_MISMATCH)]", "aigw.inference.readiness.state[tag(state=MODEL_DIGEST_MISMATCH)]",
            "aigw.inference.readiness.state[tag(state=READY)]", "aigw.inference.alert[tag(state=MODEL_DIGEST_MISMATCH)]");
    }

    @Test void sourceTimerKeepsOnlyTheThreeSourceTagsAndUsesTheCatalogBuckets() {
        var registry = new SimpleMeterRegistry();
        registry.config().meterFilter(MetricsCatalog.filter(REGISTRY));
        Timer.builder(MetricsCatalog.SOURCE_REQUESTS).tags("source_id", "loki", "method", "GET", "status_class", "2xx").register(registry).record(Duration.ofMillis(20));
        Timer.builder(MetricsCatalog.SOURCE_REQUESTS).tags("source_id", "loki", "method", "GET", "status_class", "2xx", "uri", "/loki/api/v1/query_range?query=x").register(registry).record(Duration.ofMillis(20));
        var timers = registry.find(MetricsCatalog.SOURCE_REQUESTS).timers();
        assertThat(timers).singleElement().satisfies(timer -> {
            assertThat(timer.getId().getTags()).extracting(tag -> tag.getKey()).containsExactlyInAnyOrder("source_id", "method", "status_class");
            assertThat(timer.takeSnapshot().histogramCounts()).hasSize(MetricsCatalog.BUCKET_SECONDS.size());
        });
        assertThat(Set.copyOf(MetricsCatalog.BUCKET_SECONDS)).hasSize(17).contains(0.05, 240.0);
    }

    @Test void toolIdMustBeARegisteredCanonicalToolAndTheMeterTypeMustMatchTheDeclaration() {
        var registry = new SimpleMeterRegistry();
        registry.config().meterFilter(MetricsCatalog.filter(REGISTRY));
        assertThat(REGISTRY.find("tc.logs.search")).isPresent();
        assertThat(REGISTRY.find("tc.secretValue")).isEmpty();
        Timer.builder("aigw.tool.call").tags("tool_id", "tc.logs.search", "status", "SUCCESS").register(registry).record(Duration.ofMillis(5));
        // 형식은 맞지만 등록되지 않은 이름·대소문자만 다른 이름은 거부합니다.
        Timer.builder("aigw.tool.call").tags("tool_id", "tc.secretValue", "status", "SUCCESS").register(registry).record(Duration.ofMillis(5));
        Timer.builder("aigw.tool.call").tags("tool_id", "TC.LOGS.SEARCH", "status", "SUCCESS").register(registry).record(Duration.ofMillis(5));
        Timer.builder("aigw.tool.call").tags("tool_id", "tc.logs.search", "status", "NOT_A_STATUS").register(registry).record(Duration.ofMillis(5));
        // 선언과 다른 meter type은 같은 이름·라벨이어도 거부합니다. 같은 id의 Timer가 이미 있으면 Micrometer가 filter 전에
        // type 충돌 예외를 내므로, 이 사례는 그 Timer가 없는 새 registry에서 filter 거부를 확인합니다.
        var fresh = new SimpleMeterRegistry();
        fresh.config().meterFilter(MetricsCatalog.filter(REGISTRY));
        Counter.builder("aigw.tool.call").tags("tool_id", "tc.logs.search", "status", "SUCCESS").register(fresh).increment();
        assertThat(fresh.getMeters()).isEmpty();
        Timer.builder(MetricsCatalog.RETENTION_PURGE_FAILURES).register(registry).record(Duration.ofMillis(1));
        io.micrometer.core.instrument.Gauge.builder(MetricsCatalog.AUDIT_WRITE_FAILURES, () -> 1).register(registry);
        Counter.builder(MetricsCatalog.SOURCE_REQUESTS).tags("source_id", "loki", "method", "GET", "status_class", "2xx").register(registry).increment();
        Counter.builder(MetricsCatalog.AUDIT_WRITE_FAILURES).register(registry).increment();
        assertThat(registry.getMeters().stream().map(meter -> meter.getId().getType() + ":" + meter.getId().getName() + meter.getId().getTags()))
            .containsExactlyInAnyOrder("TIMER:aigw.tool.call[tag(status=SUCCESS), tag(tool_id=tc.logs.search)]", "COUNTER:" + MetricsCatalog.AUDIT_WRITE_FAILURES + "[]");
    }
}
