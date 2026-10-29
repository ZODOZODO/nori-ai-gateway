package com.nori.ai.gateway.eval.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.*;
import javax.xml.stream.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class OtrLeafExtractor {
    private static final String EVENTS = "https://schemas.opentest4j.org/reporting/events/0.2.0";
    private static final String JUNIT = "https://schemas.junit.org/open-test-reporting";
    private static final String CORE = "https://schemas.opentest4j.org/reporting/core/0.2.0";
    private static final String JAVA = "https://schemas.opentest4j.org/reporting/java/0.2.0";
    private static final class TestEvent {
        String type = "", className = "", status = "";
        final Set<String> tags = new TreeSet<>();
    }
    public ObjectNode extract(Path directory, String task, String module, Path baseline, String spec,
                              Path output, String currentFingerprint, boolean requireExecuted, LeafCatalog catalog, Map<String,String> env) {
        try { Files.deleteIfExists(output); } catch (IOException e) { throw new EvalException(5, "OUTPUT_CLEANUP_FAILED"); }
        SpecBinding binding = new SpecGuard(env).check(spec, null, baseline);
        Path bindingFile = directory.resolve("run-binding.json");
        if (!Files.isRegularFile(bindingFile)) { if (requireExecuted) throw new EvalException(4, "NO_TESTS_EXECUTED"); return null; }
        JsonNode run = Json.read(bindingFile);
        if (!run.path("inputFingerprint").asText().equals(currentFingerprint) || !run.path("specSha").asText().equals(binding.specSha()) || run.path("recordedAt").asText().isBlank()) throw new EvalException(4, "STALE_TEST_EVIDENCE");
        List<Path> xml;
        try (var files = Files.walk(directory)) { xml = files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".xml")).sorted().toList(); }
        catch (IOException e) { throw new EvalException(4, "OTR_INPUT_INVALID"); }
        List<TestEvent> events = new ArrayList<>();
        for (Path file : xml) events.addAll(parse(file));
        int passed = 0, failed = 0, skipped = 0, aborted = 0;
        Set<String> classes = new TreeSet<>(); Map<String,List<String>> outcomes = new TreeMap<>(); Map<String,Set<String>> labels = new TreeMap<>();
        for (TestEvent event : events) {
            if (!event.type.equals("TEST")) continue;
            switch (event.status) { case "SUCCESSFUL" -> passed++; case "FAILED" -> failed++; case "SKIPPED" -> skipped++; case "ABORTED" -> aborted++; default -> throw new EvalException(4, "OTR_INCOMPLETE"); }
            if (!event.status.equals("SKIPPED") && !event.className.isBlank()) classes.add(event.className);
            Set<String> stages = new TreeSet<>(), eventLabels = new TreeSet<>();
            for (String tag : event.tags) { if (tag.matches("stage-F[0-4]")) stages.add(tag.substring(6)); if (tag.startsWith("label-")) eventLabels.add(tag.substring(6)); }
            if (stages.isEmpty()) stages.add("F0");
            for (String tag : event.tags) if (tag.matches("^(AC|ISO)-[0-9]{2}(-[A-Z0-9]+)*$")) {
                if (!catalog.contains(tag)) throw new EvalException(4, "UNKNOWN_LEAF_TAG");
                for (String stage : stages) {
                    String key = stage + "/" + tag + "/" + String.join(",", eventLabels);
                    outcomes.computeIfAbsent(key, k -> new ArrayList<>()).add(event.status.equals("SUCCESSFUL") ? "PASS" : event.status.equals("FAILED") ? "FAIL" : "NOT_RUN");
                    labels.computeIfAbsent(key, k -> new TreeSet<>()).addAll(eventLabels);
                }
            }
        }
        int executed = passed + failed + aborted;
        if (requireExecuted && executed == 0) throw new EvalException(4, "NO_TESTS_EXECUTED");
        Map<String,Object> identity = new LinkedHashMap<>(); identity.put("gitSha", null); identity.put("inputFingerprint", run.path("inputFingerprint").asText()); identity.put("runtimeClasspathSha256", null);
        String git = env.get("AIGW_GIT_SHA"); if (git != null && git.matches("[0-9a-f]{40}")) identity.put("gitSha", git);
        String runtime = env.get("AIGW_RUNTIME_CLASSPATH_SHA256"); if (runtime != null && runtime.matches("[0-9a-f]{64}")) identity.put("runtimeClasspathSha256", runtime);
        List<ObjectNode> records = new ArrayList<>();
        for (var entry : outcomes.entrySet()) {
            String[] parts = entry.getKey().split("/", 3);
            records.add(EvidenceRecord.create(binding, Map.ofEntries(Map.entry("kind", "LEAF"), Map.entry("stage", parts[0]), Map.entry("leafId", parts[1]),
                Map.entry("labels", labels.get(entry.getKey())), Map.entry("environment", "local"), Map.entry("producer", "JUNIT_OTR"), Map.entry("componentId", "IT"),
                Map.entry("taskPath", task), Map.entry("runId", run.path("runId").asText()), Map.entry("result", GateEvaluator.and(entry.getValue())),
                Map.entry("recordedAt", run.path("recordedAt").asText()),
                Map.entry("inputFingerprint", run.path("inputFingerprint").asText()), Map.entry("buildIdentity", identity))));
        }
        List<String> hashes = xml.stream().map(p -> Json.sha(Json.bytes(p))).toList();
        Map<String,Object> summaryFields = new LinkedHashMap<>();
        summaryFields.put("kind", "RUN"); summaryFields.put("status", "COMPLETED"); summaryFields.put("stage", "F0"); summaryFields.put("producer", "JUNIT_OTR");
        summaryFields.put("runId", run.path("runId").asText()); summaryFields.put("taskPath", task); summaryFields.put("module", module);
        summaryFields.put("recordedAt", run.path("recordedAt").asText());
        summaryFields.put("executedTestCount", executed); summaryFields.put("passed", passed); summaryFields.put("failed", failed); summaryFields.put("skipped", skipped); summaryFields.put("aborted", aborted); summaryFields.put("skippedTestCount", skipped + aborted);
        summaryFields.put("executedClasses", classes); summaryFields.put("otrSha256", Json.sha(Json.canonical(Json.MAPPER.valueToTree(hashes))));
        summaryFields.put("inputFingerprint", run.path("inputFingerprint").asText()); summaryFields.put("buildIdentity", identity);
        ObjectNode summary = EvidenceRecord.create(binding, summaryFields);
        ObjectNode result = Json.MAPPER.createObjectNode(); result.set("summary", summary); result.set("records", Json.MAPPER.valueToTree(records));
        Json.write(output, result); return result;
    }
    private List<TestEvent> parse(Path file) {
        XMLInputFactory factory = XMLInputFactory.newFactory(); factory.setProperty(XMLInputFactory.SUPPORT_DTD, false); factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        Map<String,TestEvent> nodes = new LinkedHashMap<>(); TestEvent active = null; boolean started = false;
        try (InputStream input = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) throw new EvalException(4, "OTR_INPUT_INVALID");
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String local = reader.getLocalName(), namespace = reader.getNamespaceURI();
                        if (EVENTS.equals(namespace) && local.equals("started")) {
                            String id = reader.getAttributeValue(null, "id"); if (id == null || nodes.containsKey(id)) throw new EvalException(4, "OTR_INPUT_INVALID");
                            active = new TestEvent(); nodes.put(id, active); started = true;
                        } else if (EVENTS.equals(namespace) && local.equals("finished")) {
                            active = nodes.get(reader.getAttributeValue(null, "id")); started = false;
                        } else if (active != null && started && JUNIT.equals(namespace) && local.equals("type")) active.type = reader.getElementText();
                        else if (active != null && started && CORE.equals(namespace) && local.equals("tag")) active.tags.add(reader.getElementText());
                        else if (active != null && started && JAVA.equals(namespace) && (local.equals("methodSource") || local.equals("classSource"))) active.className = Optional.ofNullable(reader.getAttributeValue(null, "className")).orElse("");
                        else if (active != null && !started && CORE.equals(namespace) && local.equals("result")) active.status = Optional.ofNullable(reader.getAttributeValue(null, "status")).orElse("");
                        // 실패 본문·스택·attachment·ReportEntry는 내용을 읽지 않고 이벤트만 건너뜁니다.
                    } else if (event == XMLStreamConstants.END_ELEMENT && EVENTS.equals(reader.getNamespaceURI()) && Set.of("started", "finished").contains(reader.getLocalName())) active = null;
                }
            } finally { reader.close(); }
            return new ArrayList<>(nodes.values());
        } catch (IOException | XMLStreamException e) { throw new EvalException(4, "OTR_INPUT_INVALID"); }
    }
}
