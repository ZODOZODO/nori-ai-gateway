package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.SpecBinding;
import java.io.IOException;
import java.time.*;
import java.util.*;

public final class ManualRecorder {
    private final LeafCatalog catalog;
    private final EvidenceRepositoryFs repository;
    public ManualRecorder(LeafCatalog catalog, EvidenceRepositoryFs repository) { this.catalog = catalog; this.repository = repository; }
    public ObjectNode record(String mode, String stage, String leaf, String run, String parent, List<String> supplied, SpecBinding binding) {
        if (!Set.of("user-run", "rubric").contains(mode) || !(mode.equals("rubric") ? Set.of("F1", "F2", "F4") : Set.of("F2", "F4")).contains(stage)) throw new EvalException(2, "UNKNOWN_COMMAND");
        catalog.leaf(leaf); Map<String,Object> fields = new LinkedHashMap<>();
        JsonNode schema;
        if (mode.equals("user-run")) {
            try (var input = ManualRecorder.class.getResourceAsStream("/com/nori/ai/gateway/eval/user-run-schema-v1.json")) {
                if (input == null) throw new EvalException(2, "USER_RUN_SCHEMA_MISSING"); schema = Json.parse(input.readAllBytes()).path(leaf);
            } catch (IOException e) { throw new EvalException(2, "USER_RUN_SCHEMA_MISSING"); }
            if (!schema.isObject()) throw new EvalException(2, "USER_RUN_LEAF_FORBIDDEN");
        } else schema = Json.MAPPER.valueToTree(Map.of("reviewerRef", "identifier", "reviewedAt", "instant", "verdict", "verdict", "answerSha256", "sha", "fixtureId", "identifier", "jobId", "identifier", "rubricVersion", "identifier"));
        for (String pair : supplied) {
            int separator = pair.indexOf('='); if (separator < 1) throw new EvalException(2, "USER_RUN_FIELD_INVALID");
            String key = pair.substring(0, separator), value = pair.substring(separator + 1);
            if (!schema.has(key) || fields.containsKey(key) || value.length() > 128 || value.contains("\n") || value.contains("\r")) throw new EvalException(2, "USER_RUN_FIELD_INVALID");
            fields.put(key, typed(schema.path(key).asText(), value));
        }
        if (fields.size() != schema.size()) throw new EvalException(2, "USER_RUN_FIELD_MISSING");
        String verdict;
        if (mode.equals("rubric")) verdict = (String) fields.get("verdict");
        else verdict = switch (leaf) {
            case "AC-35-FW" -> Boolean.TRUE.equals(fields.get("tcpConnectFailed")) && Long.valueOf(0).equals(fields.get("proxyReceived")) && fields.get("sourceHostClass").equals("LAN") ? "PASS" : "FAIL";
            case "AC-35-LOCAL" -> ((List<?>)fields.get("listenAddresses")).equals(List.of("127.0.0.1:11434")) ? "PASS" : "FAIL";
            case "AC-35-TOKEN" -> Long.valueOf(401).equals(fields.get("httpStatus")) && Long.valueOf(0).equals(fields.get("forwardedRequests")) && fields.get("sourceHostClass").equals("APPROVED_NODE") ? "PASS" : "FAIL";
            default -> "NOT_RUN";
        };
        JsonNode manifest = repository.readAll(stage).stream().filter(n -> n.path("status").asText().equals("PARENT") && n.path("parentRunId").asText().equals(parent)).findFirst().orElseThrow(() -> new EvalException(2, "PARENT_MANIFEST_MISSING"));
        List<String> labels = new ArrayList<>(); catalog.leaf(leaf).path("labelsByStage").path(stage).forEach(n -> labels.add(n.asText()));
        Map<String,Object> record = new LinkedHashMap<>(); record.put("kind", "LEAF"); record.put("stage", stage); record.put("leafId", leaf); record.put("parentRunId", parent); record.put("runId", run);
        record.put("producer", mode.equals("rubric") ? "RUBRIC" : "USER_RUN"); record.put("componentId", mode.equals("rubric") ? "RB:" + stage + "-RUBRIC" : "UR");
        if (mode.equals("rubric")) { record.put("suite", stage + "-RUBRIC"); record.putAll(fields); } else record.put("values", fields);
        record.put("labels", labels); record.put("result", verdict); record.put("buildIdentity", manifest.path("buildIdentity"));
        ObjectNode result = EvidenceRecord.create(binding, record); EvidenceRecord.validate(result, catalog); repository.store(result); return result;
    }
    private Object typed(String type, String value) {
        try {
            return switch (type) {
                case "boolean" -> { if (!Set.of("true", "false").contains(value)) throw invalid(); yield Boolean.parseBoolean(value); }
                case "integer" -> { if (!value.matches("[0-9]{1,12}")) throw invalid(); yield Long.parseLong(value); }
                case "instant" -> { Instant.parse(value); yield value; }
                case "identifier" -> { if (!value.matches("[A-Za-z0-9_.:@-]{1,128}")) throw invalid(); yield value; }
                case "sha" -> { if (!value.matches("[0-9a-f]{64}")) throw invalid(); yield value; }
                case "verdict" -> { if (!Set.of("PASS", "FAIL").contains(value)) throw invalid(); yield value; }
                case "host-class" -> { if (!Set.of("LAN", "APPROVED_NODE", "OTHER").contains(value)) throw invalid(); yield value; }
                case "addresses", "readiness" -> {
                    JsonNode values = Json.parse(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); if (!values.isArray()) throw invalid();
                    List<String> list = new ArrayList<>(); for (JsonNode item : values) {
                        if (!item.isTextual() || !(type.equals("addresses") ? item.asText().matches("(?:127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::\\]):[0-9]{1,5}") : Set.of("READY", "MODEL_LOADING", "INFERENCE_UNAVAILABLE", "MODEL_CONTEXT_MISMATCH").contains(item.asText()))) throw invalid();
                        list.add(item.asText());
                    } yield list;
                }
                default -> throw invalid();
            };
        } catch (java.time.format.DateTimeParseException | NumberFormatException e) { throw invalid(); }
    }
    private static EvalException invalid() { return new EvalException(2, "USER_RUN_FIELD_INVALID"); }
}
