package com.nori.ai.gateway.integration.loki;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.port.SourceRetentionReader;
import com.nori.ai.gateway.core.retention.*;
import com.nori.ai.gateway.core.result.*;
import java.time.*;
import java.util.*;
import java.math.BigInteger;

public final class LokiLogConnector {
    public record LogQuery(TimeWindow window, List<String> terms, int limit) {
        public LogQuery { Objects.requireNonNull(window); terms = List.copyOf(terms); if (limit <= 0) throw new IllegalArgumentException("조회 건수가 잘못되었습니다"); }
        @Override public String toString() { return "LogQuery[보호됨]"; }
    }
    public record LogLine(String app, Instant occurredAt, String text) {
        @Override public String toString() { return "LogLine[보호됨]"; }
    }
    public record LogResult(ResultStatus status, List<LogLine> items, List<TimeWindow> missingRanges,
            Set<Warning> warnings, int rejectedLabels) {
        public LogResult { items = List.copyOf(items); missingRanges = List.copyOf(missingRanges); warnings = Set.copyOf(warnings); }
        @Override public String toString() { return "LogResult[status=" + status + ", count=" + items.size() + "]"; }
    }
    record Batch(List<LogLine> items, int rawCount, Instant oldestAt, int rejectedLabels) {}
    private final SourceHttpClient http;
    private final SourceReadAccess access;
    private final SourceRetentionReader retention;
    private final LokiProperties properties;
    private final Clock clock;
    public LokiLogConnector(SourceHttpClient http, SourceReadAccess access, SourceRetentionReader retention, LokiProperties properties, Clock clock) {
        this.http = Objects.requireNonNull(http); this.access = Objects.requireNonNull(access); this.retention = Objects.requireNonNull(retention);
        this.properties = Objects.requireNonNull(properties); this.clock = Objects.requireNonNull(clock);
    }
    public LogResult search(SourceReadAccess.Request request, LogQuery query) {
        Objects.requireNonNull(query);
        if (query.limit() > properties.maxLinesTotal()) throw new IllegalArgumentException("조회 건수가 전체 상한을 초과했습니다");
        ResultStatus granted = access.check(request, LokiSourcePolicy.SOURCE_REF);
        if (granted != ResultStatus.SUCCESS) return result(granted);
        long deadline = http.deadline(properties.stepDeadline());
        var items = new ArrayList<LogLine>(); var missing = new ArrayList<TimeWindow>(); var warnings = new HashSet<Warning>();
        TimeWindow window = query.window();
        Map<String, TimeWindow> appWindows = new LinkedHashMap<>();
        try {
            Instant classifiedAt = clock.instant();
            for (String app : LokiSourcePolicy.APPS) {
                var oldest = retention.find(LokiSourcePolicy.retentionSourceId(app));
                var classification = new RetentionClassifier().classify(window, oldest.<SourceRetentionAttributes>map(
                    value -> new SourceRetentionAttributes.VerifiedOldest(value.verifiedOldestAt())).orElseGet(SourceRetentionAttributes.Unverified::new), classifiedAt);
                if (classification instanceof RetentionClassifier.OutOfRetention outside) missing.addAll(outside.missingRanges());
                if (classification instanceof RetentionClassifier.Partial partial) {
                    appWindows.put(app, partial.queryWindow()); missing.addAll(partial.missingRanges()); warnings.addAll(partial.warnings());
                }
                if (classification instanceof RetentionClassifier.Full full) { appWindows.put(app, full.queryWindow()); warnings.addAll(full.warnings()); }
            }
            if (appWindows.isEmpty()) return new LogResult(ResultStatus.OUT_OF_RETENTION, List.of(), List.of(window), Set.of(), 0);
            Instant earliest = appWindows.values().stream().map(TimeWindow::startAt).min(Comparator.naturalOrder()).orElseThrow();
            window = new TimeWindow(earliest, window.endAt());
        } catch (RuntimeException failure) { return result(ResultStatus.SOURCE_UNAVAILABLE); }
        int rejected = 0;
        Instant end = window.endAt();
        while (end.isAfter(window.startAt())) {
            Instant candidate = end.minus(properties.splitInterval());
            Instant start = candidate.isBefore(window.startAt()) ? window.startAt() : candidate;
            for (String app : LokiSourcePolicy.APPS) {
                TimeWindow appWindow = appWindows.get(app);
                if (appWindow == null || !appWindow.startAt().isBefore(end)) continue;
                Instant appStart = appWindow.startAt().isAfter(start) ? appWindow.startAt() : start;
                TimeWindow subwindow = new TimeWindow(appStart, end);
                if (items.size() >= query.limit() || http.expired(deadline)) {
                    missing.add(new TimeWindow(window.startAt(), end));
                    return finish(items, missing, warnings, rejected);
                }
                // 긴 조회 중 폐기된 현재 권한도 다음 Source 호출 전에 다시 확인합니다.
                ResultStatus authorized = access.check(request, LokiSourcePolicy.SOURCE_REF);
                if (authorized != ResultStatus.SUCCESS) return result(authorized);
                try {
                    Batch batch = query(http, app, query.terms(), subwindow, properties.limitPerQuery(), "backward", deadline);
                    rejected += batch.rejectedLabels();
                    if (batch.rawCount() >= properties.limitPerQuery() && batch.oldestAt() != null && appStart.isBefore(batch.oldestAt()))
                        missing.add(new TimeWindow(appStart, batch.oldestAt()));
                    int available = query.limit() - items.size();
                    List<LogLine> sorted = batch.items().stream().sorted(Comparator.comparing(LogLine::occurredAt).reversed()).toList();
                    items.addAll(sorted.subList(0, Math.min(available, sorted.size())));
                    if (sorted.size() > available) {
                        Instant retainedOldest = items.getLast().occurredAt();
                        if (appStart.isBefore(retainedOldest)) missing.add(new TimeWindow(appStart, retainedOldest));
                    }
                } catch (SourceHttpClient.Failure failure) {
                    if (!failure.deadline()) return result(ResultStatus.SOURCE_UNAVAILABLE);
                    missing.add(new TimeWindow(window.startAt(), end));
                    return finish(items, missing, warnings, rejected);
                } catch (RuntimeException failure) { return result(ResultStatus.SOURCE_UNAVAILABLE); }
            }
            end = start;
        }
        return finish(items, missing, warnings, rejected);
    }
    private static LogResult result(ResultStatus status) { return new LogResult(status, List.of(), List.of(), Set.of(), 0); }
    private static LogResult finish(List<LogLine> items, List<TimeWindow> missing, Set<Warning> warnings, int rejected) {
        var merged = new ArrayList<TimeWindow>();
        for (TimeWindow current : missing.stream().sorted(Comparator.comparing(TimeWindow::startAt)).toList()) {
            if (merged.isEmpty() || merged.getLast().endAt().isBefore(current.startAt())) merged.add(current);
            else { TimeWindow last = merged.removeLast(); merged.add(new TimeWindow(last.startAt(), last.endAt().isAfter(current.endAt()) ? last.endAt() : current.endAt())); }
        }
        var sorted = items.stream().sorted(Comparator.comparing(LogLine::occurredAt).reversed()).toList();
        return new LogResult(merged.isEmpty() ? (items.isEmpty() ? ResultStatus.EMPTY : ResultStatus.SUCCESS) : ResultStatus.PARTIAL, sorted, merged, warnings, rejected);
    }
    static Batch query(SourceHttpClient http, String app, List<String> terms, TimeWindow window, int limit, String direction, long deadline) {
        var uri = http.uri(List.of("loki", "api", "v1", "query_range"), Map.of("query", LokiSourcePolicy.selector(app, terms),
            "start", nanos(window.startAt()), "end", nanos(window.endAt()), "limit", limit, "direction", direction));
        JsonNode root = http.json(uri, null, deadline).body();
        if (!"success".equals(root.path("status").asText()) || !"streams".equals(root.path("data").path("resultType").asText())
                || !root.path("data").path("result").isArray()) throw new SourceHttpClient.Failure(false, false);
        List<LogLine> items = new ArrayList<>(); int count = 0, rejected = 0; Instant oldest = null;
        for (JsonNode stream : root.path("data").path("result")) {
            JsonNode labels = stream.path("stream"); JsonNode values = stream.path("values");
            if (!values.isArray()) throw new SourceHttpClient.Failure(false, false);
            boolean allowed = app.equals(labels.path("app").asText()) && "tc".equals(labels.path("namespace").asText());
            for (JsonNode row : values) {
                if (!row.isArray() || row.size() < 2 || !row.get(0).isTextual() || !row.get(1).isTextual()) throw new SourceHttpClient.Failure(false, false);
                Instant at = instant(row.get(0).asText());
                if (at.isBefore(window.startAt()) || !at.isBefore(window.endAt())) throw new SourceHttpClient.Failure(false, false);
                count++; if (oldest == null || at.isBefore(oldest)) oldest = at;
                if (allowed) items.add(new LogLine(app, at, row.get(1).asText())); else rejected++;
            }
        }
        if (count > limit) throw new SourceHttpClient.Failure(false, false);
        return new Batch(List.copyOf(items), count, oldest, rejected);
    }
    public static String nanos(Instant instant) { return BigInteger.valueOf(instant.getEpochSecond()).multiply(BigInteger.valueOf(1_000_000_000L)).add(BigInteger.valueOf(instant.getNano())).toString(); }
    private static Instant instant(String nanos) {
        BigInteger[] split = new BigInteger(nanos).divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
        return Instant.ofEpochSecond(split[0].longValueExact(), split[1].longValueExact());
    }
}
