package com.nori.ai.gateway.acceptance;

import com.nori.ai.gateway.acceptance.loki.LokiDirectQuery;
import com.nori.ai.gateway.integration.loki.LokiSourcePolicy;
import com.nori.ai.gateway.integration.source.SourceHttpClient;
import com.nori.ai.gateway.integration.source.SourceObservability;
import com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import org.opentest4j.TestAbortedException;

/** F2 suite 진입점. 배포 입력(F1 과 같은 env·Secret 파일)만 소비하고 로컬 fixture 나 임시 자격으로 fallback 하지 않는다. */
public final class F2Runtime {
    static final Set<String> READINESS = Set.of("AC-33-LOADING", "AC-33-PERSIST", "AC-33-CTX", "AC-33-DIGEST", "AC-44");
    private F2Runtime() {}

    public static void run(String leaf) {
        var config = F1Runtime.config(); var clients = F1Runtime.clients(config, true);
        String url = config.env().get("AIGW_LOKI_READ_URL");
        boolean readback = url != null && !url.isBlank();
        if (READINESS.contains(leaf)) {
            F2ReadinessChecks.AlertLog alerts = readback ? alerts(URI.create(url), host(config)) : F2ReadinessChecks.unavailableAlerts();
            new F2ReadinessChecks(clients.gateway(), clients.hooks(), clients.oracle(), alerts).run(leaf);
            return;
        }
        Optional<LokiDirectQuery> loki = readback ? Optional.of(new LokiDirectQuery(new SourceHttpClient(URI.create(url), LokiSourcePolicy.SOURCE_REF, SourceObservability.noop()))) : Optional.empty();
        Optional<String> egress = Optional.ofNullable(config.env().get("AIGW_EGRESS_PROBE_TARGETS_JSON")).filter(value -> !value.isBlank());
        new F2JobChecks(clients.gateway(), clients.hooks(), clients.oracle(), loki, egress, F2Runtime::countTokens).run(leaf);
    }

    /** 같은 회차 LLM_LIVE leaf Job 의 원시 지연 표본을 MEASUREMENT 로 남긴다. 모은 Job 이 없으면 측정은 NOT_RUN 이다. */
    public static void recordLatencySamples() {
        var observations = F2LiveSamples.snapshot();
        if (observations.isEmpty()) throw new TestAbortedException("LIVE_SAMPLES_EMPTY");
        var clients = F1Runtime.clients(F1Runtime.config(), true);
        var timings = clients.oracle().jobTimings(observations.stream().map(F2LiveSamples.Observation::jobId).distinct().toList());
        AcceptanceMeasurements.add(F2LiveSamples.MEASUREMENT_ID, F2LiveSamples.measurement(observations, timings));
    }

    /** 분류 wire 답변 토큰을 생성 모델과 같은 pin tokenizer 로 센다. 수용 pod 에서 tokenizer 를 열 수 없으면 빈 값(그 사례 NOT_RUN)이다. */
    static Optional<Integer> countTokens(String text) {
        HfTokenizerCounter counter;
        try { counter = new HfTokenizerCounter(); }
        catch (UnsatisfiedLinkError | ExceptionInInitializerError unavailable) { return Optional.empty(); }
        // pin 불일치·오프라인 위반은 하네스 결함이라 그대로 던지고, native 엔진 적재 실패만 관측 불가로 본다.
        catch (IllegalStateException failure) { if ("TOKENIZER_LOAD_FAILED".equals(failure.getMessage())) return Optional.empty(); throw failure; }
        try (counter) { return Optional.of(counter.countText(text)); }
    }

    private static String host(AcceptanceConfig config) {
        String base = config.env().get("AIGW_GATEWAY_BASE_URL");
        if (base == null || base.isBlank()) throw new TestAbortedException("VERIFY_CREDENTIALS_MISSING");
        return URI.create(base).getHost().split("\\.")[0];
    }
    private static F2ReadinessChecks.AlertLog alerts(URI loki, String release) {
        var readback = new GatewayLogReadback(HttpClient.newHttpClient(), loki);
        return (code, since) -> {
            var observation = readback.count(release, since, Instant.now().plusNanos(1), Set.of(code));
            if (observation.limited()) throw new TestAbortedException("LOKI_READBACK_INCOMPLETE");
            return observation.sentinelLines();
        };
    }
}
