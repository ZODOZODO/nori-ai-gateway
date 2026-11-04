package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 공유 추론 서버 겹침 제외(SPEC §16 F0, 2026-11-04 사용자 결정 — 겹친 호출만 제외, 회차 전체 무효 아님). 문항 회차의 시도(재시도 포함) 중 하나라도
 * 송신~수신 구간(callWindows)이 NORI 밖 요청 구간과 겹치면 그 회차의 지연 표본만 지연 집계에서 뺍니다. 정확도 채점은 다루지 않습니다.
 * 구간은 모두 UTC instant(`Z`)이며 양 끝을 포함하는 닫힌 구간으로 교차를 봅니다. 외부 구간 목록은 서버 GIN 로그에서 사용자가 만든 파일이며
 * 이 클래스는 로그를 읽거나 수집하지 않습니다.
 */
public final class SharedServerOverlap {
    public static final String SCHEMA = "f0-share-intervals-v1";
    public record Interval(Instant start, Instant end) {
        boolean overlaps(Interval other) { return !start.isAfter(other.end) && !other.start.isAfter(end); }
    }
    /** latency 값은 제외 뒤 표본이 없으면 null. byExpectedT 는 문항 id → 기대 t 가 주어진 표본만 셉니다. */
    public record Summary(int samples, int excluded, double excludedRatio, int keptSamples, Long latencyP50Ms, Long latencyP95Ms, Map<String,Long> latencyP95MsByExpectedT) {}
    private SharedServerOverlap() {}

    /** {"schema":"f0-share-intervals-v1","intervals":[{"start":"…Z","end":"…Z"}, …]} — start ≤ end, UTC 표기만. */
    public static List<Interval> load(Path file) {
        JsonNode root = Json.read(file);
        if (!root.path("schema").asText().equals(SCHEMA) || !root.path("intervals").isArray()) throw new EvalException(2, "SHARE_INTERVALS_INVALID");
        List<Interval> intervals = new ArrayList<>();
        for (JsonNode node : root.path("intervals")) {
            Interval interval = interval(node.path("start"), node.path("end"));
            if (interval == null) throw new EvalException(2, "SHARE_INTERVALS_INVALID");
            intervals.add(interval);
        }
        return List.copyOf(intervals);
    }
    /** 한 결과 행(골든 회차 또는 경계 회차)의 시도 구간. v4 결과가 아니거나 구간이 깨졌으면 판정하지 않고 거부합니다. */
    static List<Interval> windows(JsonNode row) {
        JsonNode windows = row.path("callWindows");
        if (!windows.isArray() || windows.isEmpty()) throw new EvalException(2, "CALL_WINDOWS_MISSING");
        List<Interval> result = new ArrayList<>();
        for (JsonNode window : windows) {
            Interval interval = interval(window.path("sentAt"), window.path("receivedAt"));
            if (interval == null) throw new EvalException(2, "CALL_WINDOWS_MISSING");
            result.add(interval);
        }
        return result;
    }
    public static boolean overlapped(JsonNode row, List<Interval> external) {
        for (Interval call : windows(row)) for (Interval other : external) if (call.overlaps(other)) return true;
        return false;
    }
    public static Summary summarize(List<JsonNode> rows, Map<String,String> expectedTByQuestion, List<Interval> external) {
        int excluded = 0; List<Long> kept = new ArrayList<>(); Map<String,List<Long>> byT = new TreeMap<>();
        for (JsonNode row : rows) {
            if (!row.path("latencyMs").isIntegralNumber() || row.path("latencyMs").asLong() < 0) throw new EvalException(2, "LATENCY_INVALID");
            if (overlapped(row, external)) { excluded++; continue; }
            long ms = row.path("latencyMs").asLong(); kept.add(ms);
            String t = expectedTByQuestion.get(row.path("questionId").asText(""));
            if (t != null) byT.computeIfAbsent(t, k -> new ArrayList<>()).add(ms);
        }
        Map<String,Long> p95ByT = new TreeMap<>(); byT.forEach((t, values) -> p95ByT.put(t, MetricsAggregator.nearestRank(values, 95)));
        return new Summary(rows.size(), excluded, rows.isEmpty() ? 0.0 : (double)excluded / rows.size(), kept.size(),
            kept.isEmpty() ? null : MetricsAggregator.nearestRank(kept, 50), kept.isEmpty() ? null : MetricsAggregator.nearestRank(kept, 95), p95ByT);
    }
    private static Interval interval(JsonNode start, JsonNode end) {
        if (!start.isTextual() || !end.isTextual() || !start.textValue().endsWith("Z") || !end.textValue().endsWith("Z")) return null;
        try {
            Instant s = Instant.parse(start.textValue()), e = Instant.parse(end.textValue());
            return s.isAfter(e) ? null : new Interval(s, e);
        } catch (DateTimeParseException ex) { return null; }
    }
}
