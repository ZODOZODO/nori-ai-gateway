package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.xml.stream.*;

/**
 * 수용 회차의 정제 증거(§4.4). JUnit Open Test Reporting XML 에서 tag·결과·클래스 이름만 읽어 §4.2 대응표로 LEAF 레코드를 만들고,
 * 레코드는 stdout 의 {@code AIGW-EVIDENCE-BEGIN <sha256>} / base64 / {@code AIGW-EVIDENCE-END} 블록으로만 내보낸다.
 * 실패 메시지·스택·attachment 는 읽지 않는다. eval {@code OtrLeafExtractor} 와 같은 규칙이며 같은 입력의 시험으로 일치를 고정한다.
 */
public final class EvidenceBlockWriter {
    public static final String ORIGIN = "ACCEPTANCE_MOUNT";
    private static final String EVENTS = "https://schemas.opentest4j.org/reporting/events/0.2.0";
    private static final String JUNIT = "https://schemas.junit.org/open-test-reporting";
    private static final String CORE = "https://schemas.opentest4j.org/reporting/core/0.2.0";
    private static final String JAVA = "https://schemas.opentest4j.org/reporting/java/0.2.0";
    private static final String LEAF_TAG = "^(AC|ISO)-[0-9]{2}(-[A-Z0-9]+)*$";

    /** 한 회차의 실행 집계와 LEAF 레코드. 실행 수 = 성공 + 실패 + 중단(비활성 제외). */
    public record Extraction(List<ObjectNode> leaves, int executed, int passed, int failed, int skipped, int aborted) {
        public Extraction { leaves = List.copyOf(leaves); }
    }
    /** 레코드 공통 문맥. specSha 가 null 이면 SPEC 입력 실패 회차다. */
    public record Context(String stage, String suite, String runId, String parentRunId, String specSha, JsonNode buildIdentity) {}

    private static final class TestEvent { String type = "", className = "", status = ""; final Set<String> tags = new TreeSet<>(); }

    public static ObjectNode record(Context context, Map<String,?> fields) {
        ObjectNode node = CanonicalJson.MAPPER.valueToTree(fields);
        if (context.stage() != null) node.put("stage", context.stage());
        if (context.suite() != null) node.put("suite", context.suite());
        if (context.runId() != null) node.put("runId", context.runId());
        if (context.parentRunId() != null) node.put("parentRunId", context.parentRunId());
        if (!node.has("producer")) node.put("producer", "ACCEPTANCE");
        if (context.specSha() != null) {
            node.put("specSha", context.specSha()); node.put("specShaSource", "FILE_HASHED"); node.put("specInputOrigin", ORIGIN);
        }
        if (context.buildIdentity() != null && context.buildIdentity().isObject()) node.set("buildIdentity", context.buildIdentity());
        if (!node.has("recordedAt")) node.put("recordedAt", Instant.now().toString());
        return CanonicalJson.seal(node);
    }

    public static void write(PrintStream out, List<? extends JsonNode> records) {
        byte[] json = CanonicalJson.bytes(CanonicalJson.MAPPER.valueToTree(records));
        out.println("AIGW-EVIDENCE-BEGIN " + CanonicalJson.sha256(json));
        out.println(Base64.getEncoder().encodeToString(json));
        out.println("AIGW-EVIDENCE-END");
        out.flush();
    }

    public static Extraction extract(Path otrDirectory, Context context) {
        List<Path> xml;
        try (var files = Files.walk(otrDirectory)) { xml = files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".xml")).sorted().toList(); }
        catch (IOException e) { throw new IllegalStateException("OTR_INPUT_INVALID"); }
        int passed = 0, failed = 0, skipped = 0, aborted = 0;
        Map<String,List<String>> outcomes = new TreeMap<>(); Map<String,Set<String>> labels = new TreeMap<>();
        for (Path file : xml) for (TestEvent event : parse(file)) {
            if (!event.type.equals("TEST")) continue;
            switch (event.status) {
                case "SUCCESSFUL" -> passed++; case "FAILED" -> failed++; case "SKIPPED" -> skipped++; case "ABORTED" -> aborted++;
                default -> throw new IllegalStateException("OTR_INCOMPLETE");
            }
            Set<String> stages = new TreeSet<>(), eventLabels = new TreeSet<>();
            for (String tag : event.tags) { if (tag.matches("stage-F[0-4]")) stages.add(tag.substring(6)); if (tag.startsWith("label-")) eventLabels.add(tag.substring(6)); }
            if (stages.isEmpty()) stages.add(context.stage());
            for (String tag : event.tags) if (tag.matches(LEAF_TAG)) for (String stage : stages) {
                String key = stage + "/" + tag + "/" + String.join(",", eventLabels);
                outcomes.computeIfAbsent(key, k -> new ArrayList<>()).add(event.status.equals("SUCCESSFUL") ? "PASS" : event.status.equals("FAILED") ? "FAIL" : "NOT_RUN");
                labels.computeIfAbsent(key, k -> new TreeSet<>()).addAll(eventLabels);
            }
        }
        var leaves = new ArrayList<ObjectNode>();
        for (var entry : outcomes.entrySet()) {
            String[] parts = entry.getKey().split("/", 3);
            var leafContext = new Context(parts[0], context.suite(), context.runId(), context.parentRunId(), context.specSha(), context.buildIdentity());
            var fields = new LinkedHashMap<String,Object>();
            fields.put("kind", "LEAF"); fields.put("leafId", parts[1]); fields.put("labels", labels.get(entry.getKey())); fields.put("environment", "verify");
            fields.put("componentId", "ACC:" + context.suite()); fields.put("result", and(entry.getValue()));
            leaves.add(record(leafContext, fields));
        }
        return new Extraction(leaves, passed + failed + aborted, passed, failed, skipped, aborted);
    }

    /** 같은 leaf 의 여러 시험: 하나라도 FAIL 이면 FAIL, FAIL 없이 NOT_RUN 이 있으면 NOT_RUN, 전부 PASS 면 PASS. */
    static String and(List<String> outcomes) {
        if (outcomes.contains("FAIL")) return "FAIL";
        if (outcomes.isEmpty() || outcomes.contains("NOT_RUN")) return "NOT_RUN";
        return "PASS";
    }

    private static List<TestEvent> parse(Path file) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false); factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        Map<String,TestEvent> nodes = new LinkedHashMap<>(); Set<String> finished = new HashSet<>(); TestEvent active = null; boolean started = false;
        try (InputStream input = Files.newInputStream(file)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) throw new IllegalStateException("OTR_INPUT_INVALID");
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String local = reader.getLocalName(), namespace = reader.getNamespaceURI();
                        if (EVENTS.equals(namespace) && local.equals("started")) {
                            String id = reader.getAttributeValue(null, "id"); if (id == null || nodes.containsKey(id)) throw new IllegalStateException("OTR_INPUT_INVALID");
                            active = new TestEvent(); nodes.put(id, active); started = true;
                        } else if (EVENTS.equals(namespace) && local.equals("finished")) {
                            // 같은 id 의 두 번째 finished·시작 없는 finished 는 앞 결과(예: FAILED)를 덮을 수 있으므로 입력 전체를 거부한다.
                            String id = reader.getAttributeValue(null, "id");
                            if (id == null || !nodes.containsKey(id) || !finished.add(id)) throw new IllegalStateException("OTR_INPUT_INVALID");
                            active = nodes.get(id); started = false;
                        } else if (active != null && started && JUNIT.equals(namespace) && local.equals("type")) active.type = reader.getElementText();
                        else if (active != null && started && CORE.equals(namespace) && local.equals("tag")) active.tags.add(reader.getElementText());
                        else if (active != null && started && JAVA.equals(namespace) && (local.equals("methodSource") || local.equals("classSource")))
                            active.className = Optional.ofNullable(reader.getAttributeValue(null, "className")).orElse("");
                        else if (active != null && !started && CORE.equals(namespace) && local.equals("result"))
                            active.status = Optional.ofNullable(reader.getAttributeValue(null, "status")).orElse("");
                    } else if (event == XMLStreamConstants.END_ELEMENT && EVENTS.equals(reader.getNamespaceURI()) && Set.of("started", "finished").contains(reader.getLocalName())) active = null;
                }
            } finally { reader.close(); }
            return new ArrayList<>(nodes.values());
        } catch (IOException | XMLStreamException e) { throw new IllegalStateException("OTR_INPUT_INVALID"); }
    }
}
