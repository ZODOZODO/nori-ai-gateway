package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.SpecBinding;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

public final class EvidenceRecord {
    private static final Set<String> FORBIDDEN = Set.of("answer", "prompt", "raw", "token", "jws", "dlg", "message", "stackTrace");
    private static final Set<String> FIELDS = Set.of(
        "kind", "stage", "suite", "producer", "componentId", "runId", "parentRunId", "specSha", "specShaSource", "specInputOrigin", "recordedAt", "contentSha256",
        "leafId", "measurementId", "result", "status", "labels", "environment", "reasonCode", "buildIdentity", "measurementSpecSha", "rawRunId", "rawResultsSha256",
        "fixtureId", "jobId", "answerSha256", "rubricTargets", "reviewerRef", "reviewedAt", "verdict", "rubricVersion", "outagePhase", "values", "metrics", "resources", "samples",
        "taskPath", "module", "inputFingerprint", "executedTestCount", "passed", "failed", "skipped", "aborted", "skippedTestCount", "executedClasses", "otrSha256",
        "sourceFile", "sourceFileSha256", "originalContentSha256", "criteriaSha256", "decision", "baselineSeq", "acceptedCount", "preStartTerminalCount", "rejectedCount",
        "terminalCounts", "backfillActive", "queueTimeoutCount", "workerInterruptedCount", "fixedQueryExpectedSha256", "fixedQueryActualSha256");
    private EvidenceRecord() {}
    public static ObjectNode create(SpecBinding binding, Map<String,?> fields) {
        ObjectNode node = Json.MAPPER.valueToTree(fields);
        node.put("specSha", binding.specSha()); node.put("specShaSource", binding.specShaSource()); node.put("specInputOrigin", binding.specInputOrigin());
        if (!node.has("recordedAt")) node.put("recordedAt", Instant.now().toString());
        return seal(node);
    }
    public static ObjectNode seal(ObjectNode node) {
        node.remove("contentSha256"); node.put("contentSha256", Json.sha(Json.canonical(node))); return node;
    }
    public static void validate(JsonNode node, LeafCatalog catalog) {
        rejectForbidden(node);
        if (!node.isObject()) throw new EvalException(2, "EVIDENCE_INVALID");
        ObjectNode unsigned = ((ObjectNode)node).deepCopy(); unsigned.remove("contentSha256");
        if (!node.path("contentSha256").asText().equals(Json.sha(Json.canonical(unsigned)))) throw new EvalException(5, "EVIDENCE_HASH_MISMATCH");
        for (var fields = node.fieldNames(); fields.hasNext();) if (!FIELDS.contains(fields.next())) throw new EvalException(2, "EVIDENCE_FIELD_INVALID");
        String kind = Json.required(node, "kind");
        // SPEC 결속 위반은 빈 값·선언값이어도 일반 식별자 오류보다 먼저 SPEC_SHA_UNVERIFIED로 거부합니다.
        boolean abortedInput = kind.equals("RUN") && node.path("status").asText().equals("ABORTED") && Set.of("SPEC_INPUT_MISSING", "SPEC_BASELINE_MISMATCH").contains(node.path("reasonCode").asText());
        if (!abortedInput && (!node.path("specSha").asText().matches("[0-9a-f]{64}") || !node.path("specShaSource").asText().equals("FILE_HASHED"))) throw new EvalException(2, "SPEC_SHA_UNVERIFIED");
        validateMetadata(node, "");
        try { Instant.parse(Json.required(node, "recordedAt")); }
        catch (DateTimeParseException e) { throw new EvalException(2, "EVIDENCE_TIMESTAMP_INVALID"); }
        for (String key : List.of("runId", "parentRunId", "suite", "componentId", "fixtureId", "jobId", "reasonCode")) if (node.hasNonNull(key) && !node.path(key).asText().matches("[A-Za-z0-9_:.@-]{1,128}")) throw new EvalException(2, "EVIDENCE_IDENTIFIER_INVALID");
        for (JsonNode label : node.path("labels")) if (!label.isTextual() || !Set.of("MOCK_API", "LLM_LIVE", "LLM_STUB", "TC_LIVE", "WM_LIVE", "HRM_LIVE").contains(label.asText())) throw new EvalException(2, "EVIDENCE_LABEL_INVALID");
        if (!node.path("stage").asText().matches("F[0-4]") || node.path("runId").asText().isBlank()) throw new EvalException(2, "EVIDENCE_INVALID");
        if (kind.equals("LEAF")) {
            String leaf = Json.required(node, "leafId");
            if (!catalog.contains(leaf) || !Set.of("PASS", "FAIL", "NOT_RUN").contains(node.path("result").asText()) || node.has("measurementId")) throw new EvalException(2, "EVIDENCE_INVALID");
            String producer = Json.required(node, "producer"), component = Json.required(node, "componentId");
            if (!Set.of("F0_REPORT", "SPEC_RECORD", "JUNIT_OTR", "ACCEPTANCE", "USER_RUN", "RUBRIC", "PERF", "OPS").contains(producer)) throw new EvalException(2, "EVIDENCE_INVALID");
            // 권위 밖 생산자도 정보로 보존하되, 자기 생산자와 다른 권위 component 사칭은 거부합니다.
            for (JsonNode expected : catalog.components(node.path("stage").asText(), leaf)) {
                if (expected.path("componentId").asText().equals(component) && !expected.path("producer").asText().equals(producer)) throw new EvalException(2, "EVIDENCE_COMPONENT_INVALID");
            }
        } else if (kind.equals("MEASUREMENT")) {
            if (node.has("leafId") || !catalog.measurement(node.path("measurementId").asText())) throw new EvalException(2, "MEASUREMENT_INVALID");
        } else if (kind.equals("RUN")) {
            if (node.has("leafId") || !Set.of("STARTED", "COMPLETED", "ABORTED", "REFERENCE", "PARENT").contains(node.path("status").asText())) throw new EvalException(2, "RUN_INVALID");
        } else throw new EvalException(2, "EVIDENCE_KIND_INVALID");
    }
    // 증거 문자열 값은 식별자·해시·사유 코드 형식만 허용해 원문 유입을 막습니다.
    private static void validateMetadata(JsonNode node, String key) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> { if (!e.getKey().matches("[A-Za-z0-9_:.@-]{1,128}")) throw new EvalException(2, "EVIDENCE_FIELD_INVALID"); validateMetadata(e.getValue(), e.getKey()); });
        } else if (node.isArray()) node.forEach(n -> validateMetadata(n, key));
        else if (node.isTextual()) {
            String value = node.asText();
            if (key.equals("sourceFile")) { if (value.isBlank() || value.length() > 4096 || value.chars().anyMatch(c -> c < 32)) throw new EvalException(2, "EVIDENCE_IDENTIFIER_INVALID"); }
            else if (!value.matches("[A-Za-z0-9_:.@+\\[\\]-]{1,128}")) throw new EvalException(2, "EVIDENCE_IDENTIFIER_INVALID");
        }
    }
    public static void rejectForbidden(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> { if (FORBIDDEN.contains(e.getKey())) throw new EvalException(2, "EVIDENCE_FORBIDDEN_FIELD"); rejectForbidden(e.getValue()); });
        } else if (node.isArray()) node.forEach(EvidenceRecord::rejectForbidden);
    }
}
