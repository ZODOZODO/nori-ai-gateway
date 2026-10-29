package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.SpecBinding;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PerfReporter {
    public ObjectNode report(String stage, String leaf, String parent, List<Path> inputs, Path criteriaFile, Path output,
                             Path root, SpecBinding binding, LeafCatalog catalog, JsonNode parentManifest) {
        if (!Set.of("F3", "F4").contains(stage) || !Set.of("AC-23-PERF", "AC-23-LOAD", "AC-24-PERF").contains(leaf) || !parent.matches("[A-Za-z0-9_.:-]{1,128}")) throw new EvalException(2, "PERF_INPUT_INVALID");
        Path target = output.resolve(leaf + "-" + parent.replace(':', '_') + ".json");
        try { Files.deleteIfExists(target); } catch (IOException e) { throw new EvalException(5, "OUTPUT_CLEANUP_FAILED"); }
        if (!Files.isRegularFile(criteriaFile)) return null;
        Path normalized = criteriaFile.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize().resolve("docs/gates"))) throw new EvalException(2, "PERF_CRITERIA_UNAPPROVED");
        JsonNode criteria = readCriteria(criteriaFile);
        if (!criteria.path("baselineSeq").isIntegralNumber() || criteria.path("baselineSeq").asInt() != com.nori.ai.gateway.eval.spec.SpecBaseline.parseChain(root.resolve("docs/gates/spec-baseline.md")).seq() || !criteria.path("approved").asBoolean() || !criteria.path("specSha").asText().equals(binding.specSha()) || !criteria.path("leafId").asText().equals(leaf)) throw new EvalException(2, "PERF_CRITERIA_UNAPPROVED");
        String approval = Json.required(criteria, "approvalRef"); Path approvalFile = root.resolve(approval).toAbsolutePath().normalize();
        if (!approvalFile.startsWith(root.toAbsolutePath().normalize().resolve("docs/gates")) || !Files.isRegularFile(approvalFile)) throw new EvalException(2, "PERF_CRITERIA_UNAPPROVED");
        Set<String> measurements = new HashSet<>(); for (JsonNode component : catalog.components(stage, leaf)) if (component.path("producer").asText().equals("PERF")) { measurements.add(component.path("measurementId").asText()); component.path("measurementIds").forEach(n -> measurements.add(n.asText())); }
        Map<String,List<Long>> groups = new TreeMap<>(), queueGroups = new TreeMap<>(); Map<String,Set<String>> sampleKeys = new TreeMap<>(); List<JsonNode> selected = new ArrayList<>(); Map<String,Long> resources = new TreeMap<>();
        boolean invalidPopulation = false, checksComplete = true, checksPass = true;
        for (Path input : inputs) for (Path path : files(input)) {
            // 집계값 입력은 일반 필드 오류가 아니라 원시 표본 요구 위반으로 먼저 거부합니다.
            JsonNode record = Json.read(path); rejectAggregates(record); EvidenceRecord.validate(record, catalog);
            if (!record.path("kind").asText().equals("MEASUREMENT") || !record.path("parentRunId").asText().equals(parent) || !measurements.contains(record.path("measurementId").asText())) continue;
            if (leaf.equals("AC-23-LOAD") && !record.path("backfillActive").asBoolean()) continue;
            if (!record.path("specSha").asText().equals(binding.specSha()) || !BuildIdentity.matches(parentManifest.path("buildIdentity"), record.path("buildIdentity"), "PERF", stage)) throw new EvalException(3, "IDENTITY_MISMATCH");
            selected.add(record);
            if (record.path("preStartTerminalCount").asInt() != 0 || record.path("rejectedCount").asInt() != 0) invalidPopulation = true;
            if (stage.equals("F4") && (!record.has("preStartTerminalCount") || !record.has("rejectedCount") || !record.path("acceptedCount").isIntegralNumber() || !record.path("terminalCounts").isObject())) checksComplete = false;
            if (leaf.equals("AC-23-LOAD")) {
                for (String check : List.of("rejectedCount", "queueTimeoutCount", "workerInterruptedCount")) {
                    if (!record.path(check).isIntegralNumber()) checksComplete = false;
                    else if (record.path(check).asLong() != 0) checksPass = false;
                }
                String expectedOrder = record.path("fixedQueryExpectedSha256").asText(), actualOrder = record.path("fixedQueryActualSha256").asText();
                if (!expectedOrder.matches("[0-9a-f]{64}") || !actualOrder.matches("[0-9a-f]{64}")) checksComplete = false;
                else if (!expectedOrder.equals(actualOrder)) checksPass = false;
            }
            for (var it = record.path("resources").fields(); it.hasNext();) {
                var entry = it.next(); if (!entry.getValue().isIntegralNumber() || !entry.getValue().canConvertToLong() || entry.getValue().asLong() < 0) throw new EvalException(2, "PERF_RAW_SAMPLES_REQUIRED");
                resources.merge(entry.getKey(), entry.getValue().asLong(), Math::max);
            }
            int sampleCount = 0; Map<String,Long> observedTerminal = new HashMap<>();
            for (JsonNode sample : record.path("samples")) {
                if (!sample.path("latencyMs").isIntegralNumber() || !sample.path("latencyMs").canConvertToLong() || sample.path("latencyMs").asLong() < 0 || !sample.path("round").isIntegralNumber() || sample.path("round").asInt() < 1 || !sample.path("path").asText().matches("[A-Za-z0-9_-]{1,64}")) throw new EvalException(2, "PERF_RAW_SAMPLES_REQUIRED");
                String key = sample.path("path").asText();
                if (stage.equals("F4")) {
                    if (!Set.of("A", "B").contains(sample.path("variant").asText()) || !Set.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED", "TIMED_OUT").contains(sample.path("terminalStatus").asText())) throw new EvalException(2, "PERF_RAW_SAMPLES_REQUIRED");
                    key = sample.path("variant").asText() + ":" + key; observedTerminal.merge(sample.path("terminalStatus").asText(), 1L, Long::sum);
                }
                if (stage.equals("F4")) {
                    if (!sample.path("queueWaitMs").isIntegralNumber() || !sample.path("queueWaitMs").canConvertToLong() || sample.path("queueWaitMs").asLong() < 0 || !sample.path("fixtureId").asText().matches("[A-Za-z0-9_.:-]{1,128}")) checksComplete = false;
                    else queueGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(sample.path("queueWaitMs").asLong());
                    if (!sampleKeys.computeIfAbsent(key, k -> new HashSet<>()).add(sample.path("fixtureId").asText() + ":" + sample.path("round").asInt())) checksComplete = false;
                }
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(sample.path("latencyMs").asLong()); sampleCount++;
            }
            if (stage.equals("F4")) {
                if (record.path("acceptedCount").asInt(-1) != sampleCount + record.path("preStartTerminalCount").asInt()) checksComplete = false;
                for (String terminal : List.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED", "TIMED_OUT")) if (!record.path("terminalCounts").has(terminal) || record.path("terminalCounts").path(terminal).asLong() != observedTerminal.getOrDefault(terminal, 0L)) checksComplete = false;
            }
        }
        if (selected.isEmpty() || groups.isEmpty()) return null;
        if (!criteria.path("thresholds").isObject() || criteria.path("thresholds").isEmpty()) throw new EvalException(2, "PERF_CRITERIA_INVALID");
        Map<String,Object> metrics = new TreeMap<>(); boolean within = true, complete = checksComplete;
        for (var iterator = criteria.path("thresholds").fields(); iterator.hasNext();) {
            var entry = iterator.next(); List<Long> values = groups.getOrDefault(entry.getKey(), List.of()); JsonNode threshold = entry.getValue();
            if (!threshold.path("p95MaxMs").isIntegralNumber() || !threshold.path("minSamples").isIntegralNumber() || threshold.path("minSamples").asInt() < 1) throw new EvalException(2, "PERF_CRITERIA_INVALID");
            if (values.size() < threshold.path("minSamples").asInt() || (stage.equals("F4") && threshold.path("minSamples").asInt() < 20)) complete = false;
            if (!values.isEmpty()) {
                long p50 = percentile(values, 50), p95 = percentile(values, 95); boolean met = p95 <= threshold.path("p95MaxMs").asLong(); within &= met;
                Map<String,Object> metric = new LinkedHashMap<>(Map.of("sampleCount", values.size(), "p50Ms", p50, "p95Ms", p95, "withinBudget", met));
                List<Long> queue = queueGroups.getOrDefault(entry.getKey(), List.of());
                if (!queue.isEmpty()) { metric.put("queueWaitSampleCount", queue.size()); metric.put("queueWaitP50Ms", percentile(queue, 50)); metric.put("queueWaitP95Ms", percentile(queue, 95)); }
                metrics.put(entry.getKey(), metric);
            }
        }
        if (!criteria.path("thresholds").properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()).containsAll(groups.keySet())) throw new EvalException(2, "PERF_PATH_UNREGISTERED");
        List<String> requiredResources = leaf.equals("AC-23-PERF") ? List.of("chunkCount", "indexingMs", "dbBytes", "indexBytes", "memoryBytes")
            : leaf.equals("AC-23-LOAD") ? List.of("backfillMaxTxMs", "lockWaitMs", "poolWaitMs", "cpuMillis", "ioBytes") : List.of();
        for (String key : requiredResources) {
            if (!resources.containsKey(key) || !criteria.path("resourceMax").path(key).isIntegralNumber()) complete = false;
            else if (resources.get(key) > criteria.path("resourceMax").path(key).asLong()) checksPass = false;
        }
        if (stage.equals("F3") && (!groups.containsKey("VECTOR") || !groups.containsKey("KEYWORD"))) complete = false;
        if (stage.equals("F4")) {
            List<String> variants = leaf.equals("AC-24-PERF") && criteria.path("candidateA").asBoolean() ? List.of("A", "B") : List.of("B");
            for (String variant : variants) for (String route : List.of("UNSUPPORTED", "SIMPLE", "INCIDENT")) if (!groups.containsKey(variant + ":" + route) || !criteria.path("thresholds").has(variant + ":" + route)) complete = false;
        }
        if (leaf.equals("AC-24-PERF") && criteria.path("candidateA").asBoolean()) for (String route : List.of("UNSUPPORTED", "SIMPLE", "INCIDENT")) if (!Objects.equals(sampleKeys.get("A:" + route), sampleKeys.get("B:" + route))) complete = false;
        String result = !checksPass ? "FAIL" : !complete || invalidPopulation ? "NOT_RUN" : leaf.equals("AC-24-PERF") ? "PASS" : within ? "PASS" : "FAIL";
        List<String> labels = new ArrayList<>(); catalog.leaf(leaf).path("labelsByStage").path(stage).forEach(n -> labels.add(n.asText()));
        Map<String,Object> fields = new LinkedHashMap<>(); fields.put("kind", "LEAF"); fields.put("stage", stage); fields.put("leafId", leaf); fields.put("producer", "PERF"); fields.put("componentId", "PF");
        fields.put("runId", UUID.randomUUID().toString()); fields.put("parentRunId", parent); fields.put("labels", labels); fields.put("result", result); fields.put("metrics", metrics); fields.put("resources", resources);
        fields.put("buildIdentity", parentManifest.path("buildIdentity")); fields.put("rawResultsSha256", Json.sha(Json.canonical(Json.MAPPER.valueToTree(selected)))); fields.put("criteriaSha256", Json.sha(Json.bytes(criteriaFile)));
        boolean candidateWithin = true;
        if (criteria.path("candidateA").asBoolean()) for (String route : List.of("UNSUPPORTED", "SIMPLE", "INCIDENT")) {
            List<Long> values = groups.getOrDefault("A:" + route, List.of());
            if (values.isEmpty() || percentile(values, 95) > criteria.path("thresholds").path("A:" + route).path("p95MaxMs").asLong()) candidateWithin = false;
        }
        if (leaf.equals("AC-24-PERF")) fields.put("decision", criteria.path("candidateA").asBoolean() && criteria.path("f0AccuracyQualified").asBoolean() && candidateWithin && result.equals("PASS") ? "PROMOTE_A" : "KEEP_B");
        ObjectNode record = EvidenceRecord.create(binding, fields); EvidenceRecord.validate(record, catalog); Json.write(target, record); return record;
    }
    public static long percentile(List<Long> samples, int percentage) {
        if (samples.isEmpty() || percentage < 1 || percentage > 100) throw new EvalException(2, "PERF_RAW_SAMPLES_REQUIRED");
        List<Long> sorted = new ArrayList<>(samples); Collections.sort(sorted); return sorted.get((int)Math.ceil(sorted.size() * percentage / 100.0) - 1);
    }
    private static List<Path> files(Path input) {
        if (!Files.isDirectory(input)) return List.of(input);
        try (var files = Files.walk(input)) { return files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().toList(); }
        catch (IOException e) { throw new EvalException(2, "PERF_INPUT_MISSING"); }
    }
    private static JsonNode readCriteria(Path path) {
        String text = new String(Json.bytes(path), java.nio.charset.StandardCharsets.UTF_8);
        if (text.stripLeading().startsWith("{")) return Json.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var matcher = java.util.regex.Pattern.compile("(?s)```json\\s*\\n(.*?)\\n```").matcher(text);
        if (!matcher.find()) throw new EvalException(2, "PERF_CRITERIA_INVALID");
        String block = matcher.group(1); if (matcher.find()) throw new EvalException(2, "PERF_CRITERIA_INVALID");
        return Json.parse(block.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void rejectAggregates(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(e -> { if (Set.of("histogram", "average", "mean", "p50", "p95").contains(e.getKey())) throw new EvalException(2, "PERF_RAW_SAMPLES_REQUIRED"); rejectAggregates(e.getValue()); });
        else if (node.isArray()) node.forEach(PerfReporter::rejectAggregates);
    }
}
