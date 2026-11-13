package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.SpecBaseline;
import java.nio.file.*;
import java.util.*;
import java.time.Instant;

public final class GateEvaluator {
    public record ComponentVerdict(String componentId, String result, String runId, String reasonCode) {}
    public record LeafVerdict(String leafId, String result, List<ComponentVerdict> components) {}
    public record GateReport(String stage, String parentRunId, int baselineSeq, String result, List<LeafVerdict> leaves,
                             Map<String,String> stageParents, Map<String,String> allParents, int informationalRecordCount) {}
    public GateReport evaluate(String stage, String parentRunId, List<JsonNode> records, LeafCatalog catalog, SpecBaseline chain) {
        if (!stage.matches("F[0-4]") || parentRunId == null || parentRunId.isBlank()) throw new EvalException(2, "GATE_INPUT_MISSING");
        for (JsonNode record : records) EvidenceRecord.validate(record, catalog);
        List<JsonNode> bound = records.stream().filter(n -> n.path("stage").asText().equals(stage) && n.path("parentRunId").asText().equals(parentRunId)).toList();
        JsonNode parent = latest(bound.stream().filter(n -> n.path("kind").asText().equals("RUN") && n.path("status").asText().equals("PARENT") && n.path("runId").asText().equals(parentRunId)).toList());
        List<LeafVerdict> leaves = new ArrayList<>();
        for (JsonNode leaf : catalog.required(stage)) {
            String id = leaf.path("id").asText(); List<ComponentVerdict> components = new ArrayList<>();
            for (JsonNode component : catalog.components(stage, id)) components.add(select(stage, id, component, bound, parent, chain, catalog));
            leaves.add(new LeafVerdict(id, and(components.stream().map(ComponentVerdict::result).toList()), components));
        }
        Map<String,String> stageParents = new LinkedHashMap<>(), allParents = new LinkedHashMap<>();
        Map<String,String> outcomes = new HashMap<>(); leaves.forEach(l -> outcomes.put(l.leafId(), l.result()));
        for (JsonNode parentDefinition : catalog.data().path("parents")) {
            String id = parentDefinition.path("id").asText();
            List<String> current = new ArrayList<>(), all = new ArrayList<>();
            if (parentDefinition.path("atomic").asBoolean()) { all.add(outcomes.getOrDefault(id, "NOT_RUN")); if (outcomes.containsKey(id)) current.add(outcomes.get(id)); }
            else for (JsonNode child : parentDefinition.path("children")) { all.add(outcomes.getOrDefault(child.asText(), "NOT_RUN")); if (outcomes.containsKey(child.asText())) current.add(outcomes.get(child.asText())); }
            if (!current.isEmpty()) stageParents.put(id, and(current));
            allParents.put(id, and(all));
        }
        return new GateReport(stage, parentRunId, chain.seq(), and(leaves.stream().map(LeafVerdict::result).toList()), List.copyOf(leaves), stageParents, allParents, records.size() - bound.size());
    }
    private ComponentVerdict select(String stage, String leaf, JsonNode component, List<JsonNode> records, JsonNode parent, SpecBaseline chain, LeafCatalog catalog) {
        String componentId = component.path("componentId").asText(), producer = component.path("producer").asText();
        if (parent == null) return missing(componentId, "PARENT_MANIFEST_MISSING", "");
        JsonNode selected;
        if (producer.equals("ACCEPTANCE")) {
            List<JsonNode> attempts = records.stream().filter(n -> n.path("kind").asText().equals("RUN") && n.path("suite").equals(component.path("suite")) && phaseMatches(component, n) && !n.path("status").asText().equals("PARENT")).toList();
            JsonNode lastAttempt = latest(attempts);
            if (lastAttempt == null) return missing(componentId, "COMPONENT_MISSING", "");
            JsonNode unknownAbort = latest(records.stream().filter(n -> n.path("kind").asText().equals("RUN") && n.path("status").asText().equals("ABORTED") && n.path("suite").asText().isBlank()).toList());
            if (unknownAbort != null && !Instant.parse(lastAttempt.path("recordedAt").asText()).isAfter(Instant.parse(unknownAbort.path("recordedAt").asText()))) return missing(componentId, "ACCEPTANCE_EVIDENCE_MISSING", unknownAbort.path("runId").asText());
            String run = lastAttempt.path("runId").asText();
            if (!lastAttempt.path("status").asText().equals("COMPLETED")) return missing(componentId, lastAttempt.path("reasonCode").asText("RUN_INCOMPLETE"), run);
            selected = latest(records.stream().filter(n -> authority(n, leaf, component) && labelsMatch(n, catalog.leaf(leaf).path("labelsByStage").path(stage)) && n.path("runId").asText().equals(run)).toList());
        } else if (producer.equals("JUNIT_OTR")) {
            JsonNode reference = latest(records.stream().filter(n -> n.path("kind").asText().equals("RUN") && n.path("status").asText().equals("REFERENCE") && n.path("taskPath").equals(component.path("task"))).toList());
            if (reference == null) return missing(componentId, "COMPONENT_MISSING", "");
            if (!BuildIdentity.matches(parent.path("buildIdentity"), reference.path("buildIdentity"), producer, stage)) return missing(componentId, "IDENTITY_MISMATCH", reference.path("runId").asText());
            Path source = Path.of(reference.path("sourceFile").asText());
            if (!Files.isRegularFile(source) || !Json.sha(Json.bytes(source)).equals(reference.path("sourceFileSha256").asText())) return missing(componentId, "EVIDENCE_HASH_MISMATCH", reference.path("runId").asText());
            JsonNode original = Json.read(source);
            if (!original.path("summary").path("contentSha256").equals(reference.path("originalContentSha256"))) return missing(componentId, "EVIDENCE_HASH_MISMATCH", reference.path("runId").asText());
            List<JsonNode> sourceRecords = new ArrayList<>(); original.path("records").forEach(sourceRecords::add);
            for (JsonNode n : sourceRecords) EvidenceRecord.validate(n, catalog);
            selected = latest(sourceRecords.stream().filter(n -> n.path("leafId").asText().equals(leaf) && n.path("runId").equals(reference.path("runId"))).toList());
        } else if (producer.equals("RUBRIC")) return rubric(stage, leaf, component, records, parent, chain, catalog);
        else selected = latest(records.stream().filter(n -> authority(n, leaf, component) && labelsMatch(n, catalog.leaf(leaf).path("labelsByStage").path(stage))).toList());
        if (selected == null) return missing(componentId, "COMPONENT_MISSING", "");
        return verdict(stage, leaf, componentId, producer, selected, parent, chain, catalog);
    }
    /** 고른 기록 하나의 기준선·이미지 identity·라벨 확인 뒤 그 기록의 판정. */
    private ComponentVerdict verdict(String stage, String leaf, String componentId, String producer, JsonNode selected, JsonNode parent, SpecBaseline chain, LeafCatalog catalog) {
        String run = selected.path("runId").asText();
        if (!chain.eligible(selected.path("specSha").asText(), leaf)) return missing(componentId, "SPEC_BASELINE_STALE", run);
        if (!BuildIdentity.matches(parent.path("buildIdentity"), selected.path("buildIdentity"), producer, stage)) return missing(componentId, "IDENTITY_MISMATCH", run);
        Set<String> labels = new HashSet<>(); selected.path("labels").forEach(n -> labels.add(n.asText()));
        for (JsonNode expected : catalog.leaf(leaf).path("labelsByStage").path(stage)) if (!labels.contains(expected.asText())) return missing(componentId, "LABEL_MISMATCH", run);
        return new ComponentVerdict(componentId, selected.path("result").asText(), run, selected.path("reasonCode").asText(""));
    }
    /**
     * RUBRIC 구성 요소: 같은 자동 suite 의 최신 회차(COMPLETED)가 이 leaf 에 남긴 rubric 대상 {leafId, fixtureId, answerSha256} 마다 그 fixture·답변 SHA 에 결속된
     * RUBRIC 기록(같은 대상 중 최신)이 있어야 한다. 대상별 판정은 AND 다 — 하나라도 결속 기록이 없으면 NOT_RUN, 하나라도 FAIL 이면 FAIL.
     */
    private ComponentVerdict rubric(String stage, String leaf, JsonNode component, List<JsonNode> records, JsonNode parent, SpecBaseline chain, LeafCatalog catalog) {
        String componentId = component.path("componentId").asText();
        List<JsonNode> judged = records.stream().filter(n -> authority(n, leaf, component) && labelsMatch(n, catalog.leaf(leaf).path("labelsByStage").path(stage))).toList();
        if (judged.isEmpty()) return missing(componentId, "COMPONENT_MISSING", "");
        String judgedRun = latest(judged).path("runId").asText();
        String automaticSuite = component.path("suite").asText().replace("-RUBRIC", "");
        JsonNode current = latest(records.stream().filter(n -> n.path("kind").asText().equals("RUN") && n.path("suite").asText().equals(automaticSuite)).toList());
        if (current == null || !current.path("status").asText().equals("COMPLETED")) return missing(componentId, "RUBRIC_TARGET_MISMATCH", judgedRun);
        List<ComponentVerdict> targets = new ArrayList<>();
        for (JsonNode target : current.path("rubricTargets")) {
            if (!target.path("leafId").asText().equals(leaf)) continue;
            JsonNode bound = latest(judged.stream().filter(n -> n.path("fixtureId").equals(target.path("fixtureId")) && n.path("answerSha256").equals(target.path("answerSha256")) && n.path("answerSha256").asText().matches("[0-9a-f]{64}")).toList());
            targets.add(bound == null ? missing(componentId, "RUBRIC_TARGET_MISMATCH", judgedRun) : verdict(stage, leaf, componentId, "RUBRIC", bound, parent, chain, catalog));
        }
        if (targets.isEmpty()) return missing(componentId, "RUBRIC_TARGET_MISMATCH", judgedRun);
        String result = and(targets.stream().map(ComponentVerdict::result).toList());
        return targets.stream().filter(target -> target.result().equals(result)).findFirst().orElseThrow();
    }
    private static boolean authority(JsonNode n, String leaf, JsonNode component) {
        return n.path("kind").asText().equals("LEAF") && n.path("leafId").asText().equals(leaf) && n.path("producer").equals(component.path("producer")) && n.path("componentId").equals(component.path("componentId")) && (!component.has("suite") || n.path("suite").equals(component.path("suite"))) && phaseMatches(component, n);
    }
    private static boolean phaseMatches(JsonNode expected, JsonNode observed) { return !expected.has("outagePhase") || expected.path("outagePhase").equals(observed.path("outagePhase")); }
    private static boolean labelsMatch(JsonNode record, JsonNode required) {
        Set<String> actual = new HashSet<>(), expected = new HashSet<>(); record.path("labels").forEach(n -> actual.add(n.asText())); required.forEach(n -> expected.add(n.asText()));
        if (!actual.containsAll(expected)) return false;
        for (String label : List.of("MOCK_API", "TC_LIVE", "WM_LIVE", "HRM_LIVE")) if (actual.contains(label) && !expected.contains(label)) return false;
        if (expected.contains("LLM_LIVE") && actual.contains("LLM_STUB")) return false;
        return !expected.contains("LLM_STUB") || !actual.contains("LLM_LIVE");
    }
    private static JsonNode latest(List<JsonNode> nodes) { return nodes.stream().max(Comparator.comparing((JsonNode n) -> Instant.parse(n.path("recordedAt").asText())).thenComparing(n -> n.path("runId").asText())).orElse(null); }
    private static ComponentVerdict missing(String id, String reason, String run) { return new ComponentVerdict(id, "NOT_RUN", run, reason); }
    public static String and(List<String> outcomes) { if (outcomes.contains("FAIL")) return "FAIL"; if (outcomes.isEmpty() || outcomes.contains("NOT_RUN")) return "NOT_RUN"; return "PASS"; }
}
