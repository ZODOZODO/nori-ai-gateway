package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import static org.assertj.core.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class EvidenceBlockWriterTest {
    @TempDir Path directory;
    static final String SPEC = "a".repeat(64);
    static final EvidenceBlockWriter.Context CONTEXT = new EvidenceBlockWriter.Context("F4", "F4", "run-1", "parent-1", SPEC,
        CanonicalJson.MAPPER.createObjectNode().put("appImageDigest", "sha256:" + "b".repeat(64)).put("acceptanceImageDigest", "sha256:" + "c".repeat(64)));

    /** P01 LeafReportRoundTripFunctionalTest 와 같은 시험을 실제 Launcher·OTR 로 실행해 같은 판정을 얻는다. */
    @Test void mapsActualOtrLikeTheEvalExtractorAndNeverCopiesFailureText() throws Exception {
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("probe.ProbeTest"))
            .configurationParameter("junit.platform.reporting.open.xml.enabled", "true")
            .configurationParameter("junit.platform.reporting.output.dir", directory.toString()).build());
        var extraction = EvidenceBlockWriter.extract(directory, CONTEXT);
        Map<String,String> results = new HashMap<>();
        extraction.leaves().forEach(n -> results.put(n.path("leafId").asText(), n.path("result").asText()));
        assertThat(results).containsEntry("AC-24-JOB", "PASS").containsEntry("AC-24-AUTHZ", "FAIL").containsEntry("AC-24-LOT-ACTIVE", "NOT_RUN")
            .containsEntry("AC-24-LOT-MULTI", "NOT_RUN").containsEntry("AC-24-LOT-ENDED", "FAIL").doesNotContainKey("AC-24-LOT-UNKNOWN");
        assertThat(extraction.executed()).isEqualTo(5); assertThat(extraction.skipped()).isEqualTo(1); assertThat(extraction.aborted()).isEqualTo(1);
        for (JsonNode leaf : extraction.leaves()) {
            assertThat(leaf.path("producer").asText()).isEqualTo("ACCEPTANCE");
            assertThat(leaf.path("componentId").asText()).isEqualTo("ACC:F4");
            assertThat(leaf.path("specSha").asText()).isEqualTo(SPEC);
            assertThat(leaf.path("specShaSource").asText()).isEqualTo("FILE_HASHED");
            assertThat(leaf.path("specInputOrigin").asText()).isEqualTo("ACCEPTANCE_MOUNT");
            assertThat(leaf.path("labels").get(0).asText()).isEqualTo("MOCK_API");
            assertSealed(leaf);
        }
        var buffer = new ByteArrayOutputStream();
        EvidenceBlockWriter.write(new PrintStream(buffer, true, StandardCharsets.UTF_8), extraction.leaves());
        String block = buffer.toString(StandardCharsets.UTF_8);
        assertThat(block).doesNotContain("OTR_SENTINEL", "AssertionFailedError", "stackTrace", "ProbeTest.java:");
        assertThat(decode(block)).hasSize(5);
    }

    @Test void blockCarriesRunMeasurementAndRubricRecordsWithVerifiableHash() {
        var records = List.of(
            EvidenceBlockWriter.record(CONTEXT, Map.of("kind", "RUN", "status", "COMPLETED", "executedTestCount", 3)),
            EvidenceBlockWriter.record(CONTEXT, Map.of("kind", "MEASUREMENT", "measurementId", "f4:job-latency-samples", "values", Map.of("seconds", 12.5))),
            EvidenceBlockWriter.record(CONTEXT, Map.of("kind", "LEAF", "producer", "RUBRIC", "componentId", "RB:F4-RUBRIC", "leafId", "AC-02-MISSING", "result", "PASS", "answerSha256", "d".repeat(64))));
        var buffer = new ByteArrayOutputStream();
        EvidenceBlockWriter.write(new PrintStream(buffer, true, StandardCharsets.UTF_8), records);
        String[] lines = buffer.toString(StandardCharsets.UTF_8).split("\\R");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).matches("AIGW-EVIDENCE-BEGIN [0-9a-f]{64}"); assertThat(lines[2]).isEqualTo("AIGW-EVIDENCE-END");
        byte[] json = Base64.getDecoder().decode(lines[1]);
        assertThat(lines[0].substring(20)).isEqualTo(CanonicalJson.sha256(json));
        var decoded = decode(buffer.toString(StandardCharsets.UTF_8));
        assertThat(decoded).extracting(n -> n.path("kind").asText()).containsExactly("RUN", "MEASUREMENT", "LEAF");
        assertThat(decoded.get(2).path("producer").asText()).isEqualTo("RUBRIC");
        decoded.forEach(EvidenceBlockWriterTest::assertSealed);
        // 실행되지 않은 leaf 의 레코드는 만들지 않는다.
        assertThat(EvidenceBlockWriter.extract(directory, CONTEXT).leaves()).isEmpty();
    }

    @Test void spoolsSpecFailureRecordWithoutSpecSha() {
        var aborted = EvidenceBlockWriter.record(new EvidenceBlockWriter.Context("F1", "F1", "r", "p", null, null), Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "SPEC_INPUT_MISSING"));
        assertThat(aborted.has("specSha")).isFalse(); assertThat(aborted.has("specShaSource")).isFalse(); assertSealed(aborted);
    }

    /** eval io.Json 의 RFC 8785 시험 벡터와 같은 입력·기대값으로 두 구현의 정규화가 같음을 고정한다. */
    @Test void canonicalizationMatchesEvalVectors() throws Exception {
        String[][] vectors = {
            {"0000000000000000","0"},{"0000000000000001","5e-324"},{"7fefffffffffffff","1.7976931348623157e+308"},{"4340000000000000","9007199254740992"},
            {"4430000000000000","295147905179352830000"},{"44b52d02c7e14af6","1e+23"},{"444b1ae4d6e2ef50","1e+21"},{"3eb0c6f7a0b5ed8c","9.999999999999997e-7"},
            {"3eb0c6f7a0b5ed8d","0.000001"},{"41b3de4355555553","333333333.3333332"},{"becbf647612f3696","-0.0000033333333333333333"},{"43143ff3c1cb0959","1424953923781206.2"}
        };
        for (String[] vector : vectors) assertThat(CanonicalJson.string(DoubleNode.valueOf(Double.longBitsToDouble(Long.parseUnsignedLong(vector[0], 16))))).as(vector[0]).isEqualTo(vector[1]);
        assertThat(CanonicalJson.string(CanonicalJson.MAPPER.readTree("{\"z\":1e23,\"a\":0.000001,\"b\":-0.0}"))).isEqualTo("{\"a\":0.000001,\"b\":0,\"z\":1e+23}");
        assertThat(CanonicalJson.string(TextNode.valueOf("\u000f\\u000F"))).isEqualTo("\"\\u000f\\\\u000F\"");
        assertThatThrownBy(() -> CanonicalJson.string(TextNode.valueOf("\ud800"))).isInstanceOf(IllegalArgumentException.class);
    }

    static List<JsonNode> decode(String text) {
        var matcher = java.util.regex.Pattern.compile("(?m)^AIGW-EVIDENCE-BEGIN ([0-9a-f]{64})\\R([A-Za-z0-9+/=]+)\\RAIGW-EVIDENCE-END$").matcher(text);
        var records = new ArrayList<JsonNode>();
        while (matcher.find()) {
            byte[] json = Base64.getDecoder().decode(matcher.group(2));
            assertThat(CanonicalJson.sha256(json)).isEqualTo(matcher.group(1));
            try { CanonicalJson.MAPPER.readTree(json).forEach(records::add); } catch (IOException e) { throw new AssertionError(e); }
        }
        return records;
    }
    static void assertSealed(JsonNode record) {
        ObjectNode unsigned = ((ObjectNode) record).deepCopy(); unsigned.remove("contentSha256");
        assertThat(record.path("contentSha256").asText()).isEqualTo(CanonicalJson.sha256(CanonicalJson.bytes(unsigned)));
    }
}
