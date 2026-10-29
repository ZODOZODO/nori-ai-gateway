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
        assertThat(catalog.data().path("parents").size()).isEqualTo(61); assertThat(catalog.data().path("leaves").size()).isEqualTo(216); assertThat(catalog.data().path("atoms").size()).isEqualTo(23);
        Map<String,Integer> expected = Map.ofEntries(Map.entry("AC-02",2),Map.entry("AC-03",2),Map.entry("AC-10",2),Map.entry("AC-15",2),Map.entry("AC-16",2),Map.entry("AC-17",5),Map.entry("AC-18",8),Map.entry("AC-19",3),Map.entry("AC-20",2),Map.entry("AC-21",2),Map.entry("AC-22",6),Map.entry("AC-23",25),Map.entry("AC-24",24),Map.entry("AC-25",11),Map.entry("AC-26",9),Map.entry("AC-27",6),Map.entry("AC-28",14),Map.entry("AC-30",3),Map.entry("AC-32",3),Map.entry("AC-33",3),Map.entry("AC-35",4),Map.entry("AC-37",4),Map.entry("AC-38",3),Map.entry("AC-39",3),Map.entry("AC-42",2),Map.entry("AC-43",2),Map.entry("AC-46",3),Map.entry("ISO-04",20),Map.entry("ISO-05",2),Map.entry("ISO-06",6),Map.entry("ISO-07",4),Map.entry("ISO-08",4),Map.entry("ISO-10",3),Map.entry("ISO-11",10),Map.entry("ISO-12",2),Map.entry("ISO-13",3),Map.entry("ISO-14",4),Map.entry("ISO-15",3));
        expected.forEach((parent,count) -> assertThat(java.util.stream.StreamSupport.stream(catalog.data().path("leaves").spliterator(),false).filter(n -> n.path("parentIds").path(0).asText().equals(parent)).count()).as(parent).isEqualTo(count.longValue()));
        assertThat(catalog.all().stream().map(n -> n.path("id").asText()).distinct().count()).isEqualTo(239);
        assertThat(catalog.leaf("AC-03-RES").path("parentIds").toString()).contains("AC-24");
        assertThat(catalog.leaf("AC-40").path("parentIds").toString()).contains("AC-25");
        assertThat(catalog.leaf("ISO-05-MESSAGE").path("parentIds").toString()).contains("AC-03");
        assertThat(catalog.leaf("ISO-08-RECONNECT").path("parentIds").toString()).contains("AC-18");
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
