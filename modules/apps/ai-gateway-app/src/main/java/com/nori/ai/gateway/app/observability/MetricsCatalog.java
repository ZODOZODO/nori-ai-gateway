package com.nori.ai.gateway.app.observability;

import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.result.JobStatus;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Plan §10.2 지표의 이름·라벨·버킷 정본이다. 이름은 Micrometer 표기이며 Prometheus 변환 이름은 `.`→`_`와 단위/`_total` 접미사다.
 * 라벨 값은 닫힌 집합만 허용한다. 값 집합을 아직 정하지 않은 라벨은 생산 phase가 정할 때까지 어떤 값도 허용하지 않는다(fail-closed).
 * `tool_id`는 형식이 아니라 같은 {@link CapabilityRegistry}에 등록된 canonical toolId만 받는다.
 * {@link #filter(CapabilityRegistry)}는 목록 밖 지표·선언과 다른 meter type·라벨·값을 내보내기 전에 거부한다.
 */
public final class MetricsCatalog {
    public record Spec(String name, Meter.Type type, Map<String,Pattern> labels) {
        public Spec { labels = Map.copyOf(labels); }
        public boolean accepts(String key, String value) {
            Pattern pattern = labels.get(key);
            return pattern != null && value != null && pattern.matcher(value).matches();
        }
    }
    public static final List<Double> BUCKET_SECONDS = List.of(0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0, 120.0, 180.0, 240.0);
    public static final String RETENTION_PURGED_ROWS = "aigw.retention.purged.rows";
    public static final String RETENTION_PURGE_FAILURES = "aigw.retention.purge.failures";
    public static final String RETENTION_PROBE_FAILURES = "aigw.retention.probe.failures";
    public static final String AUDIT_WRITE_FAILURES = "aigw.audit.write.failures";
    public static final String SOURCE_REQUESTS = "aigw.source.requests";
    private static final Pattern NONE = Pattern.compile("(?!)");
    // 등록 Tool 소속으로 따로 검사하는 라벨이다. 형식 Pattern으로는 어떤 값도 받지 않는다.
    private static final String TOOL_ID = "tool_id";
    private static final Pattern POOL = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Map<String,Spec> SPECS = Stream.of(
        spec("aigw.jobs.accepted", Meter.Type.COUNTER, "owner_system", owners()),
        spec("aigw.jobs.rejected", Meter.Type.COUNTER, "owner_system", owners(), "reason",
            of("QUEUE_FULL", "MODEL_LOADING", "MODEL_CONTEXT_MISMATCH", "MODEL_DIGEST_MISMATCH", "INFERENCE_UNAVAILABLE", "UNAUTHENTICATED", "SYSTEM_SCOPE_MISMATCH")),
        spec("aigw.jobs.terminal", Meter.Type.COUNTER, "owner_system", owners(), "job_status", names(JobStatus.values()), "error_code", names(ErrorCode.values())),
        spec("aigw.job.queue.wait", Meter.Type.TIMER, "owner_system", owners()),
        spec("aigw.job.execution", Meter.Type.TIMER, "owner_system", owners(), "task_type", names(TaskType.values()), "job_status", names(JobStatus.values())),
        spec("aigw.job.e2e", Meter.Type.TIMER, "owner_system", owners(), "job_status", names(JobStatus.values())),
        spec("aigw.queue.depth", Meter.Type.GAUGE), spec("aigw.running.jobs", Meter.Type.GAUGE), spec("aigw.generation.inflight", Meter.Type.GAUGE),
        spec("aigw.llm.call", Meter.Type.TIMER, "call_type", of("classify", "compose", "plan", "embed"), "outcome", NONE),
        spec("aigw.llm.tokens", Meter.Type.COUNTER, "call_type", of("classify", "compose", "plan", "embed"), "kind", of("prompt", "completion")),
        spec("aigw.llm.budget.reduction", Meter.Type.COUNTER, "call_type", of("classify", "compose", "plan", "embed"), "step", of("1", "2", "3", "4")),
        spec("aigw.llm.retry", Meter.Type.COUNTER, "call_type", of("classify", "compose", "plan", "embed"), "reason", NONE),
        spec("aigw.tool.call", Meter.Type.TIMER, TOOL_ID, NONE, "status", names(ResultStatus.values())),
        spec("aigw.source.authz", Meter.Type.COUNTER, "source", NONE, "decision", of("ALLOW", "DENIED", "UNAVAILABLE")),
        spec("aigw.inference.readiness.state", Meter.Type.GAUGE, "state", of("READY", "MODEL_LOADING", "MODEL_CONTEXT_MISMATCH", "MODEL_DIGEST_MISMATCH", "INFERENCE_UNAVAILABLE")),
        spec("aigw.inference.alert", Meter.Type.GAUGE, "state", of("MODEL_LOADING", "MODEL_CONTEXT_MISMATCH", "MODEL_DIGEST_MISMATCH", "INFERENCE_UNAVAILABLE")),
        spec("aigw.knowledge.search", Meter.Type.TIMER, "mode", of("vector", "keyword", "identifier"), "status", NONE),
        spec("aigw.ingest.events", Meter.Type.COUNTER, "outcome", of("INDEXED", "SKIPPED_SAME_KEY", "ACL_UPDATED", "CATALOG_REMOVED", "TOMBSTONED",
            "RECREATED", "STALE_DISCARDED", "SOURCE_INCONSISTENT", "UNSUPPORTED_FORMAT", "FAILED")),
        spec("aigw.ingest.tx", Meter.Type.TIMER),
        spec(RETENTION_PURGED_ROWS, Meter.Type.COUNTER, "kind", of("conversation", "audit")),
        // §11.6 AIGW-RETENTION-PURGE-FAILED 판정과 P14 탐침 실패 보고용 무라벨 지표입니다.
        spec(RETENTION_PURGE_FAILURES, Meter.Type.COUNTER),
        spec(RETENTION_PROBE_FAILURES, Meter.Type.COUNTER),
        // §10.2 aigw_audit_write_failures_total: 일반 감사 저장 실패를 라벨 없이 셉니다.
        spec(AUDIT_WRITE_FAILURES, Meter.Type.COUNTER),
        spec("aigw.sse.connections", Meter.Type.GAUGE),
        spec("aigw.sse.closed", Meter.Type.COUNTER, "reason", of("EXPIRED", "CLIENT", "TERMINAL")),
        // P12 Source 전용 관측의 세 low-cardinality 태그입니다.
        spec(SOURCE_REQUESTS, Meter.Type.TIMER, "source_id", of("loki", "tempo", "gitlab"), "method", of("GET"),
            "status_class", of("1xx", "2xx", "3xx", "4xx", "5xx", "IO_ERROR")))
        .collect(Collectors.toUnmodifiableMap(Spec::name, spec -> spec));
    private static final String HIKARI_PREFIX = "hikaricp.";

    private MetricsCatalog() {}
    public static Map<String,Spec> specs() { return SPECS; }

    public static boolean accepts(Meter.Id id, CapabilityRegistry registry) {
        Spec spec = SPECS.get(id.getName());
        if (spec == null) return id.getName().startsWith(HIKARI_PREFIX) && id.getTags().stream().allMatch(tag -> tag.getKey().equals("pool") && POOL.matcher(tag.getValue()).matches());
        if (spec.type() != id.getType()) return false;
        return id.getTags().stream().allMatch(tag -> tag.getKey().equals(TOOL_ID) && spec.labels().containsKey(TOOL_ID)
            ? registry.find(tag.getValue()).filter(manifest -> manifest.toolId().equals(tag.getValue())).isPresent()
            : spec.accepts(tag.getKey(), tag.getValue()));
    }
    public static MeterFilter filter(CapabilityRegistry registry) {
        Objects.requireNonNull(registry);
        return new MeterFilter() {
            @Override public MeterFilterReply accept(Meter.Id id) { return accepts(id, registry) ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY; }
            @Override public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                Spec spec = SPECS.get(id.getName());
                if (spec == null || spec.type() != Meter.Type.TIMER) return config;
                double[] nanos = BUCKET_SECONDS.stream().mapToDouble(seconds -> Duration.ofMillis(Math.round(seconds * 1000)).toNanos()).toArray();
                return DistributionStatisticConfig.builder().serviceLevelObjectives(nanos).build().merge(config);
            }
        };
    }
    private static Spec spec(String name, Meter.Type type, Object... labels) {
        var map = new LinkedHashMap<String,Pattern>();
        for (int i = 0; i < labels.length; i += 2) map.put((String) labels[i], (Pattern) labels[i + 1]);
        return new Spec(name, type, map);
    }
    private static Pattern owners() { return names(OwnerSystem.values()); }
    private static Pattern names(Enum<?>[] values) { return of(Arrays.stream(values).map(Enum::name).toArray(String[]::new)); }
    private static Pattern of(String... values) { return Pattern.compile(Arrays.stream(values).map(Pattern::quote).collect(Collectors.joining("|", "(?:", ")"))); }
}
