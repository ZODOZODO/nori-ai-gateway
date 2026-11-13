package com.nori.ai.gateway.acceptance;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * F2 LLM_LIVE leaf 가 만든 Job 의 ID 와 정제 관찰(상태·건수·코드)만 launcher 한 회차 동안 모은다(Plan P21 N1).
 * 원시 지연 표본은 {@link OracleQueries#jobTimings} 의 created/started/terminal 시각에서 밀리초 정수로만 만들고, p50/p95 는 계산하지 않는다(perf report 의 nearest-rank 몫).
 */
public final class F2LiveSamples {
    public static final String MEASUREMENT_ID = "f2:llm-latency-samples";
    public record Observation(String leafId, String fixtureId, String jobId, Map<String,Object> values) {
        public Observation { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
    }
    private static final ThreadLocal<List<Observation>> CURRENT = ThreadLocal.withInitial(ArrayList::new);
    private F2LiveSamples() {}
    public static void clear() { CURRENT.remove(); }
    public static void add(String leaf, String fixture, String job, Map<String,Object> values) {
        if (job == null || !job.matches("[A-Za-z0-9_:-]{1,160}") || !fixture.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException("LIVE_SAMPLE_INVALID");
        CURRENT.get().add(new Observation(leaf, fixture, job, values));
    }
    public static List<Observation> snapshot() { return List.copyOf(CURRENT.get()); }

    /** 모은 Job 의 저장 시각만으로 MEASUREMENT 값을 만든다. terminal 이 아닌 Job·시각 결손은 표본을 만들지 않고 실패다. */
    public static Map<String,Object> measurement(List<Observation> observations, List<OracleQueries.JobTiming> timings) {
        if (observations.isEmpty()) throw new IllegalArgumentException("LIVE_SAMPLES_EMPTY");
        var byJob = new HashMap<String,OracleQueries.JobTiming>(); timings.forEach(timing -> byJob.put(timing.jobId(), timing));
        var samples = new ArrayList<Map<String,Object>>(); var rounds = new HashMap<String,Integer>(); var recorded = new ArrayList<Map<String,Object>>();
        for (var observation : observations) {
            var timing = byJob.get(observation.jobId());
            if (timing == null || timing.startedAt().isEmpty() || timing.terminalAt().isEmpty()) throw new IllegalStateException("LIVE_SAMPLE_TIMING_MISSING");
            int round = rounds.merge(observation.leafId(), 1, Integer::sum);
            Instant created = timing.createdAt(), started = timing.startedAt().get(), terminal = timing.terminalAt().get();
            samples.add(sample(observation, round, "QUEUE", created, started));
            samples.add(sample(observation, round, "EXECUTION", started, terminal));
            samples.add(sample(observation, round, "E2E", created, terminal));
            var values = new LinkedHashMap<String,Object>(); values.put("path", observation.leafId()); values.put("fixtureId", observation.fixtureId()); values.put("jobStatus", timing.status()); values.putAll(observation.values());
            recorded.add(values);
        }
        return Map.of("samples", List.copyOf(samples), "observations", List.copyOf(recorded));
    }
    private static Map<String,Object> sample(Observation observation, int round, String callType, Instant from, Instant to) {
        long millis = Duration.between(from, to).toMillis();
        if (millis < 0) throw new IllegalStateException("LIVE_SAMPLE_TIMING_INVALID");
        var value = new LinkedHashMap<String,Object>(); value.put("path", observation.leafId()); value.put("fixtureId", observation.fixtureId()); value.put("round", round); value.put("callType", callType); value.put("latencyMs", millis);
        return value;
    }
}
