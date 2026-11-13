package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
import static org.assertj.core.api.Assertions.*;

/** 배포 launcher 가 정본 catalog 의 F2·F2-OUTAGE leaf 를 정확히 발견하고, 태그(suite·stage·label·단계)가 catalog component 와 gate 라벨 규칙에 맞는지 고정한다. */
class F2SuiteTest {
    static JsonNode catalog() throws Exception {
        return CanonicalJson.MAPPER.readTree(Files.readString(Path.of(System.getProperty("aigw.rootDir"), "gateway-eval/src/main/resources/com/nori/ai/gateway/eval/leaf-catalog-v2.json")));
    }
    static List<TestIdentifier> discover(String expression) {
        var plan = LauncherFactory.create().discover(LauncherDiscoveryRequestBuilder.request().selectors(selectPackage(AcceptanceMain.SUITE_PACKAGE)).filters(TagFilter.includeTags(expression)).build());
        return plan.getRoots().stream().flatMap(root -> plan.getDescendants(root).stream()).filter(TestIdentifier::isTest).toList();
    }
    static List<String> tags(TestIdentifier test) { return test.getTags().stream().map(org.junit.platform.engine.TestTag::getName).toList(); }
    static List<String> strings(JsonNode node) { var values = new ArrayList<String>(); node.forEach(value -> values.add(value.asText())); return values; }
    static JsonNode entry(JsonNode catalog, String leaf) {
        for (String section : List.of("leaves", "atoms")) for (var value : catalog.path(section)) if (value.path("id").asText().equals(leaf)) return value;
        throw new AssertionError(leaf);
    }
    /** GateEvaluator.labelsMatch 와 같은 규칙: catalog 라벨 전부 포함, MOCK_API 계열 추가 0, LIVE↔STUB 혼동 0. */
    static void assertLabels(String leaf, List<String> labels, List<String> expected) {
        assertThat(labels).as(leaf).containsAll(expected);
        for (String label : List.of("MOCK_API", "TC_LIVE", "WM_LIVE", "HRM_LIVE")) if (labels.contains(label)) assertThat(expected).as(leaf).contains(label);
        if (expected.contains("LLM_LIVE")) assertThat(labels).as(leaf).doesNotContain("LLM_STUB");
        if (expected.contains("LLM_STUB")) assertThat(labels).as(leaf).doesNotContain("LLM_LIVE");
    }

    @Test void productionLauncherDiscoversExactlyTheCatalogF2LeavesWithCatalogLabelsAndOneMeasurementTest() throws Exception {
        var catalog = catalog(); var tests = discover("suite-F2");
        assertThat(tests).hasSize(23);
        var actual = new TreeSet<String>(); int measurements = 0;
        for (var test : tests) {
            var tags = tags(test); assertThat(tags).contains("suite-F2", "stage-F2").doesNotContain("suite-F2-OUTAGE");
            var leaves = tags.stream().filter(tag -> tag.matches("(AC|ISO)-[0-9]{2}.*")).toList();
            if (leaves.isEmpty()) { assertThat(tags).contains("measurement-f2-llm-latency-samples").noneMatch(tag -> tag.startsWith("label-")); measurements++; continue; }
            assertThat(leaves).hasSize(1); String leaf = leaves.getFirst(); assertThat(actual.add(leaf)).isTrue();
            var component = entry(catalog, leaf).path("components").path("F2");
            assertThat(component.findValuesAsText("componentId")).contains("ACC:F2").doesNotContain("ACC:F2-OUTAGE");
            var labels = tags.stream().filter(tag -> tag.startsWith("label-")).map(tag -> tag.substring(6)).toList();
            assertLabels(leaf, labels, strings(entry(catalog, leaf).path("labelsByStage").path("F2")));
            if (F2Runtime.READINESS.contains(leaf)) assertThat(labels).as(leaf + " readiness stub 수단").contains("LLM_STUB");
        }
        assertThat(measurements).isOne();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(strings(catalog.path("producerIndex").path("F2")));
    }

    @Test void liveMeasurementLeavesRunBeforeStubAndReadinessStubLeavesRunLast() throws Exception {
        var order = new TreeMap<Integer,String>();
        for (var method : com.nori.ai.gateway.acceptance.suite.F2Suite.class.getDeclaredMethods()) {
            var annotation = method.getAnnotation(Order.class); if (annotation == null) continue;
            String tag = Arrays.stream(method.getAnnotationsByType(org.junit.jupiter.api.Tag.class)).map(org.junit.jupiter.api.Tag::value).filter(value -> value.matches("(AC|ISO)-[0-9]{2}.*|measurement-.*")).findFirst().orElseThrow();
            assertThat(order.put(annotation.value(), tag)).isNull();
        }
        var sequence = List.copyOf(order.values());
        assertThat(sequence.subList(0, 4)).containsExactlyInAnyOrder("AC-22-LIVE", "AC-29", "AC-38-F2", "AC-31");
        assertThat(sequence.get(4)).isEqualTo("measurement-f2-llm-latency-samples");
        assertThat(sequence.subList(sequence.size() - 3, sequence.size())).containsExactlyInAnyOrder("AC-33-CTX", "AC-33-DIGEST", "AC-33-PERSIST");
    }

    @Test void outageSuiteRunsOnlyTheLeafOfTheRequestedPhaseMatchingTheCatalogComponent() throws Exception {
        var catalog = catalog();
        assertThat(discover("suite-F2-OUTAGE")).hasSize(2);
        assertThat(strings(catalog.path("producerIndex").path("F2-OUTAGE"))).containsExactlyInAnyOrder("AC-44", "AC-33-LOADING");
        for (String phase : List.of("STOPPED", "LOADING")) {
            var tests = discover(AcceptanceMain.tags("F2-OUTAGE", Optional.of(phase)));
            assertThat(tests).as(phase).singleElement().satisfies(test -> {
                var tags = tags(test); String leaf = tags.stream().filter(tag -> tag.matches("(AC|ISO)-[0-9]{2}.*")).findFirst().orElseThrow();
                var component = entry(catalog, leaf).path("components").path("F2").get(0);
                assertThat(component.path("componentId").asText()).isEqualTo("ACC:F2-OUTAGE");
                assertThat(component.path("outagePhase").asText()).isEqualTo(phase);
                assertThat(tags).contains("suite-F2-OUTAGE", "stage-F2", "outage-" + phase, "label-MOCK_API", "label-LLM_STUB");
                assertLabels(leaf, tags.stream().filter(tag -> tag.startsWith("label-")).map(tag -> tag.substring(6)).toList(), strings(entry(catalog, leaf).path("labelsByStage").path("F2")));
            });
        }
        assertThatThrownBy(() -> AcceptanceMain.tags("F2-OUTAGE", Optional.empty())).hasMessage("OUTAGE_PHASE_MISSING");
        assertThat(AcceptanceMain.tags("F2", Optional.of("STOPPED"))).isEqualTo("suite-F2");
    }

    @Test void rubricComponentLeavesAreExactlyThePreregisteredFreeTextLeaves() throws Exception {
        var catalog = catalog();
        assertThat(strings(catalog.path("producerIndex").path("F2-RUBRIC"))).containsExactlyInAnyOrderElementsOf(F2Fixture.FREE_TEXT_LEAVES);
        for (String leaf : F2Fixture.FREE_TEXT_LEAVES) {
            assertThat(entry(catalog, leaf).path("components").path("F2").findValuesAsText("componentId")).containsExactly("ACC:F2", "RB:F2-RUBRIC");
            assertThat(F2Fixture.fixtures(leaf)).as(leaf).isNotEmpty();
        }
        assertThat(F2Fixture.fixtures("AC-22-LIVE")).hasSize(3).allSatisfy(fixture -> assertThat(fixture.composerScript()).isEmpty());
        assertThat(catalog.path("measurementIds").toString()).contains(F2LiveSamples.MEASUREMENT_ID);
    }
}
