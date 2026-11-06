package com.nori.ai.gateway.integration.tempo;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.integration.loki.*;
import java.time.*;
import java.util.*;
import java.math.BigInteger;

public final class TempoTraceConnector {
    public static final String SOURCE_REF = "sources://TC/trace";
    public record Span(String service, String spanId, String name, String startTimeUnixNano, String endTimeUnixNano, String serviceVersion) {
        @Override public String toString() { return "Span[보호됨]"; }
    }
    public record Trace(String traceId, List<Span> spans) { public Trace { spans = List.copyOf(spans); } }
    public record TraceResult(ResultStatus status, List<Trace> traces, Set<Warning> warnings) {
        public TraceResult { traces = List.copyOf(traces); warnings = Set.copyOf(warnings); }
    }
    private final SourceHttpClient http;
    private final SourceReadAccess access;
    public TempoTraceConnector(SourceHttpClient http, SourceReadAccess access) { this.http = Objects.requireNonNull(http); this.access = Objects.requireNonNull(access); }
    public TraceResult search(SourceReadAccess.Request request,String service,TimeWindow window) {
        try{return searchGuarded(request,service,window);}
        catch(SourceReadAccess.GuardStopped stopped){throw stopped.rethrow();}
        catch(SourceReadAccess.Rejected rejected){return result(rejected.status(),List.of());}
    }
    private TraceResult searchGuarded(SourceReadAccess.Request request, String service, TimeWindow window) {
        if (!LokiSourcePolicy.APPS.contains(service)) return result(ResultStatus.DENIED, List.of());
        ResultStatus authorized = access.check(request, SOURCE_REF);
        if (authorized != ResultStatus.SUCCESS) return result(authorized, List.of());
        long deadline = http.deadline(Duration.ofSeconds(30));
        try {
            JsonNode body = http.json(http.uri(List.of("api", "search"), Map.of("q", "{resource.service.name=\"" + service + "\"}",
                "start", window.startAt().getEpochSecond(), "end", ceilSecond(window.endAt()))), null, deadline,()->access.require(request,SOURCE_REF)).body();
            if (!body.path("traces").isArray()) throw new SourceHttpClient.Failure(false, false);
            List<Trace> traces = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (JsonNode hit : body.path("traces")) {
                String id = hit.path("traceID").asText();
                if (!validTraceId(id)) throw new SourceHttpClient.Failure(false, false);
                if (!seen.add(id)) continue;
                authorized = access.check(request, SOURCE_REF);
                if (authorized != ResultStatus.SUCCESS) return result(authorized, List.of());
                Trace trace = load(request,id, deadline, Optional.of(window));
                if (!trace.spans().isEmpty()) traces.add(trace);
            }
            return result(traces.isEmpty() ? ResultStatus.EMPTY : ResultStatus.SUCCESS, traces);
        } catch(SourceReadAccess.GuardStopped | SourceReadAccess.Rejected guarded){throw guarded;}
        catch (RuntimeException failure) { return result(ResultStatus.SOURCE_UNAVAILABLE, List.of()); }
    }
    public TraceResult getTrace(SourceReadAccess.Request request,String traceId) {
        try{return getTraceGuarded(request,traceId);}
        catch(SourceReadAccess.GuardStopped stopped){throw stopped.rethrow();}
        catch(SourceReadAccess.Rejected rejected){return result(rejected.status(),List.of());}
    }
    private TraceResult getTraceGuarded(SourceReadAccess.Request request, String traceId) {
        if (!validTraceId(traceId)) return result(ResultStatus.DENIED, List.of());
        ResultStatus authorized = access.check(request, SOURCE_REF);
        if (authorized != ResultStatus.SUCCESS) return result(authorized, List.of());
        try {
            Trace trace = load(request,traceId, http.deadline(Duration.ofSeconds(30)), Optional.empty());
            return result(trace.spans().isEmpty() ? ResultStatus.EMPTY : ResultStatus.SUCCESS, trace.spans().isEmpty() ? List.of() : List.of(trace));
        } catch(SourceReadAccess.GuardStopped | SourceReadAccess.Rejected guarded){throw guarded;}
        catch (RuntimeException failure) { return result(ResultStatus.SOURCE_UNAVAILABLE, List.of()); }
    }
    private Trace load(SourceReadAccess.Request request,String id, long deadline, Optional<TimeWindow> window) {
        JsonNode body = http.json(http.uri(List.of("api", "traces", id), Map.of()), null, deadline,()->access.require(request,SOURCE_REF)).body();
        JsonNode batches = body.has("batches") ? body.path("batches") : body.path("resourceSpans");
        if (!batches.isArray()) throw new SourceHttpClient.Failure(false, false);
        List<Span> spans = new ArrayList<>();
        for (JsonNode batch : batches) {
            String service = attribute(batch.path("resource").path("attributes"), "service.name");
            if (!LokiSourcePolicy.APPS.contains(service)) continue;
            String version = attribute(batch.path("resource").path("attributes"), "service.version");
            JsonNode scopes = batch.has("scopeSpans") ? batch.path("scopeSpans") : batch.path("instrumentationLibrarySpans");
            if (!scopes.isArray()) throw new SourceHttpClient.Failure(false, false);
            for (JsonNode scope : scopes) {
                if (!scope.path("spans").isArray()) throw new SourceHttpClient.Failure(false, false);
                for (JsonNode span : scope.path("spans")) {
                    String start = span.path("startTimeUnixNano").asText(), end = span.path("endTimeUnixNano").asText();
                    if (!start.matches("[0-9]{1,30}") || !end.matches("[0-9]{1,30}") || new BigInteger(end).compareTo(new BigInteger(start)) < 0)
                        throw new SourceHttpClient.Failure(false, false);
                    // Tempo의 초 단위 검색은 넓혀 전송하고 실제 span은 원래 나노초 반개구간으로 거릅니다.
                    if (window.isPresent()) {
                        BigInteger from = new BigInteger(LokiLogConnector.nanos(window.get().startAt()));
                        BigInteger to = new BigInteger(LokiLogConnector.nanos(window.get().endAt()));
                        BigInteger spanFrom = new BigInteger(start), spanTo = new BigInteger(end);
                        boolean overlaps = spanFrom.compareTo(to) < 0 && (spanTo.compareTo(from) > 0 || (spanFrom.equals(spanTo) && spanFrom.compareTo(from) >= 0));
                        if (!overlaps) continue;
                    }
                    spans.add(new Span(service, span.path("spanId").asText(), span.path("name").asText(), start, end, version));
                }
            }
        }
        return new Trace(id, spans);
    }
    private static String attribute(JsonNode attributes, String key) {
        String found = null;
        for (JsonNode attribute : attributes) if (key.equals(attribute.path("key").asText())) {
            if (found != null || !attribute.path("value").path("stringValue").isTextual()) return "";
            found = attribute.path("value").path("stringValue").asText();
        }
        return found == null ? "" : found;
    }
    private static boolean validTraceId(String id) { return id != null && id.matches("[0-9a-f]{32}"); }
    private static long ceilSecond(Instant value) { return value.getNano() == 0 ? value.getEpochSecond() : Math.addExact(value.getEpochSecond(), 1); }
    private static TraceResult result(ResultStatus status, List<Trace> traces) {
        return new TraceResult(status, traces, status == ResultStatus.SUCCESS || status == ResultStatus.EMPTY ? Set.of(Warning.RETENTION_UNVERIFIED) : Set.of());
    }
}
