package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.report.LeafCatalog;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import static org.assertj.core.api.Assertions.*;

class LeafCatalogTest {
    @Test void preservesAllIdsCountsParentsAndSharedRelationships() {
        LeafCatalog catalog = LeafCatalog.load(null);
        assertThat(catalog.data().path("parents").size()).isEqualTo(61); assertThat(catalog.data().path("leaves").size()).as("seq16: 기존 214 + 신규 8").isEqualTo(222); assertThat(catalog.data().path("atoms").size()).isEqualTo(23);
        Map<String,Integer> expected = Map.ofEntries(Map.entry("AC-02",2),Map.entry("AC-03",2),Map.entry("AC-10",2),Map.entry("AC-15",2),Map.entry("AC-16",2),Map.entry("AC-17",5),Map.entry("AC-18",8),Map.entry("AC-19",3),Map.entry("AC-20",2),Map.entry("AC-21",2),Map.entry("AC-22",6),Map.entry("AC-23",30),Map.entry("AC-24",26),Map.entry("AC-25",11),Map.entry("AC-26",9),Map.entry("AC-27",6),Map.entry("AC-28",14),Map.entry("AC-30",3),Map.entry("AC-32",4),Map.entry("AC-33",4),Map.entry("AC-35",1),Map.entry("AC-37",4),Map.entry("AC-38",3),Map.entry("AC-39",3),Map.entry("AC-42",2),Map.entry("AC-43",2),Map.entry("AC-46",3),Map.entry("ISO-04",20),Map.entry("ISO-05",2),Map.entry("ISO-06",6),Map.entry("ISO-07",4),Map.entry("ISO-08",4),Map.entry("ISO-10",3),Map.entry("ISO-11",10),Map.entry("ISO-12",2),Map.entry("ISO-13",3),Map.entry("ISO-14",4),Map.entry("ISO-15",3));
        expected.forEach((parent,count) -> assertThat(java.util.stream.StreamSupport.stream(catalog.data().path("leaves").spliterator(),false).filter(n -> n.path("parentIds").path(0).asText().equals(parent)).count()).as(parent).isEqualTo(count.longValue()));
        assertThat(catalog.all().stream().map(n -> n.path("id").asText()).distinct().count()).isEqualTo(245);
        assertThat(catalog.leaf("AC-03-RES").path("parentIds").toString()).contains("AC-24");
        assertThat(catalog.leaf("AC-40").path("parentIds").toString()).contains("AC-25");
        assertThat(catalog.leaf("ISO-05-MESSAGE").path("parentIds").toString()).contains("AC-03");
        assertThat(catalog.leaf("ISO-08-RECONNECT").path("parentIds").toString()).contains("AC-18");
        // Plan §12.4 ⓕ 결정: AC-33-DIGEST 는 AC-33-CTX 와 같은 F2·F4 수용 component 를 쓴다.
        assertThat(catalog.components("F2", "AC-33-DIGEST").stream().map(n -> n.path("componentId").asText())).containsExactly("ACC:F2");
        assertThat(catalog.components("F4", "AC-33-DIGEST").stream().map(n -> n.path("componentId").asText())).containsExactly("ACC:F4");
        // Plan §16.9 seq16: 신규 8 leaf 의 단계별 component, ISO-06 H 2개의 F3·F4 필수 이동, F5a component 0.
        assertThat(catalog.data().path("revision").asText()).isEqualTo("seq16");
        assertThat(catalog.components("F2", "AC-32-NEEDS").stream().map(n -> n.path("componentId").asText())).containsExactly("IT", "ACC:F2");
        assertThat(catalog.components("F4", "AC-32-NEEDS").stream().map(n -> n.path("componentId").asText())).containsExactly("IT", "ACC:F4");
        assertThat(catalog.components("F2", "AC-32-NEEDS").getFirst().path("task").asText()).isEqualTo(":apps:ai-gateway-app:integrationTest");
        for (String id : List.of("AC-23-QUERYINPUT", "AC-23-HYBRID", "AC-23-RERANK", "AC-23-CONTEXT"))
            assertThat(catalog.components("F3", id).stream().map(n -> n.path("componentId").asText())).as(id).containsExactly("IT", "ACC:F3");
        assertThat(catalog.components("F3", "AC-23-QUALITY").stream().map(n -> n.path("componentId").asText())).containsExactly("PF", "RB:F3-QUALITY");
        assertThat(catalog.components("F3", "AC-23-QUALITY").getFirst().path("measurementIds").toString()).contains("f3:quality-samples", "f3:reranker-latency-samples", "f3:reranker-resource-samples");
        assertThat(catalog.components("F4", "AC-24-EVIDENCEFLOW").stream().map(n -> n.path("componentId").asText())).containsExactly("IT", "ACC:F4");
        assertThat(catalog.components("F4", "AC-24-REPORT").stream().map(n -> n.path("componentId").asText())).containsExactly("IT", "ACC:F4", "RB:F4-RUBRIC");
        for (String id : List.of("ISO-06-H-MISSING", "ISO-06-H-TAMPER")) {
            assertThat(catalog.components("F3", id).stream().map(n -> n.path("componentId").asText())).as(id).containsExactly("ACC:F3");
            assertThat(catalog.components("F4", id).stream().map(n -> n.path("componentId").asText())).as(id).containsExactly("ACC:F4");
        }
        for (String id : List.of("AC-32-NEEDS", "AC-24-EVIDENCEFLOW", "AC-24-REPORT")) {
            assertThat(catalog.leaf(id).path("gates").toString()).as(id).contains("F5a");
            assertThat(catalog.components("F5a", id)).as(id).isEmpty();
        }
        // 기존 214 leaf ID 보존(신규 8 은 추가만).
        assertThat(catalog.all().stream().map(n -> n.path("id").asText())).contains("AC-32-SCHEMA", "AC-32-LENGTH", "AC-32-RETRYOK", "AC-23-NW", "AC-23-LOAD", "AC-24-BACKUP-ALERT", "AC-33-DIGEST");
        assertThat(catalog.required("F2")).as("P21 seq16 필수 24 = 이력 23 + AC-32-NEEDS").hasSize(24);
    }
    @Test void matchesApprovedAuthorityTableForEveryStageAndComponent() throws Exception {
        LeafCatalog catalog = LeafCatalog.load(null); String contract;
        try (var stream = getClass().getResourceAsStream("/policy/plan-catalog-contract.txt")) { assertThat(stream).isNotNull(); contract = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        String authority = contract.substring(contract.indexOf("### 12.4")); Map<String,Map<String,Set<String>>> expected = new TreeMap<>();
        for (String line : authority.split("\\R")) if (line.matches("\\| F[0-4] \\|.*")) {
            String[] fields = line.split("\\|", -1); String stage = fields[1].strip(); Set<String> ids = new HashSet<>(); Matcher matcher = Pattern.compile("(?:AC|ISO)-[0-9]{2}(?:-[A-Z0-9]+)*").matcher(fields[2]);
            while (matcher.find()) { String id = matcher.group(); if (catalog.contains(id)) ids.add(id); else catalog.all().stream().filter(n -> n.path("parentIds").path(0).asText().equals(id)).forEach(n -> ids.add(n.path("id").asText())); }
            Set<String> components = new TreeSet<>(); Matcher component = Pattern.compile("`(F0R|SPR|IT|PF|UR|(?:ACC|RB|OP):[^`]+)`").matcher(fields[3]);
            while (component.find()) components.add(component.group(1));
            for (String id : ids) expected.computeIfAbsent(stage, k -> new TreeMap<>()).put(id, components);
        }
        // v4.1(2026-11-05) 이관 행: 현재 단계는 F5a 이지만 catalog 의 F0 component 는 "종전 권위 producer 이력"(F0R·SPR)으로 보존한다(§12.4 머리말, F5a 정합은 §13.1).
        for (String line : authority.split("\\R")) if (line.startsWith("| F5a (이관·후속) |")) {
            String[] fields = line.split("\\|", -1); Set<String> components = new TreeSet<>();
            Matcher history = Pattern.compile("`(F0R|SPR)`").matcher(fields[3].substring(fields[3].indexOf("종전 권위 producer 이력")));
            while (history.find()) components.add(history.group(1));
            Matcher matcher = Pattern.compile("(?:AC|ISO)-[0-9]{2}(?:-[A-Z0-9]+)*").matcher(fields[2]);
            while (matcher.find()) expected.computeIfAbsent("F0", k -> new TreeMap<>()).put(matcher.group(), components);
        }
        Map<String,String> producer = Map.of("F0R","F0_REPORT","SPR","SPEC_RECORD","IT","JUNIT_OTR","PF","PERF","UR","USER_RUN","ACC","ACCEPTANCE","RB","RUBRIC","OP","OPS");
        for (String stage : List.of("F0","F1","F2","F3","F4")) {
            assertThat(catalog.required(stage).stream().map(n -> n.path("id").asText())).containsExactlyInAnyOrderElementsOf(expected.get(stage).keySet());
            for (var entry : expected.get(stage).entrySet()) {
                List<JsonNode> actual = catalog.components(stage, entry.getKey());
                assertThat(actual.stream().map(n -> n.path("componentId").asText())).containsExactlyInAnyOrderElementsOf(entry.getValue());
                for (JsonNode n : actual) {
                    assertThat(n.path("producer").asText()).isEqualTo(producer.get(n.path("componentId").asText().split(":")[0]));
                    String key = n.has("suite") ? n.get("suite").asText() : n.has("task") ? n.get("task").asText() : n.path("measurementId").asText();
                    assertThat(key).isNotBlank(); assertThat(catalog.data().path("producerIndex").path(key).toString()).contains(entry.getKey());
                }
            }
        }
        for (JsonNode n : catalog.all()) for (String stage : List.of("F5a","F5b","R1","R2")) assertThat(catalog.components(stage, n.path("id").asText())).isEmpty();
    }
}
