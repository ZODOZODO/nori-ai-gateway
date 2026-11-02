package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class F0BoundaryCasesV2Test {
    @TempDir Path directory;

    static ObjectNode limits() {
        ObjectNode limits = ArgLimitsSchemaTest.confirmedLimits(256).put("schema", "f0-arg-limits-v2");
        for (String field : List.of("from", "to")) ArgLimitsSchemaTest.field(limits, field).put("format", "DATETIME:uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSxxx").put("maxChars", 35);
        return limits;
    }

    @Test void selectedV2LimitsGenerateAllFullLengthUnicodeAndEscapingCasesThroughTheActualCli() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); f.write("eval/f0/v2/arg-limits-v2.json", Json.canonicalString(limits()));
        assertThat(f.exec("cases", "--limits", "eval/f0/v2/arg-limits-v2.json", "--fixture", "eval/golden/tc-registry-fixture-v1.json", "--out", "eval/f0/v2/output-boundary-cases-v2.jsonl")).isZero();
        List<JsonNode> rows = BoundaryCases.load(f.root.resolve("eval/f0/v2/output-boundary-cases-v2.jsonl"));
        assertThat(rows).hasSize(9);
        Set<String> ids = new HashSet<>();
        for (JsonNode row : rows) {
            ids.add(row.path("caseId").asText()); JsonNode expected = row.path("expectedResolvedOutput");
            assertThat(Json.parse(row.path("expectedJson").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8))).isEqualTo(row.path("expectedOutput"));
            if (row.path("caseId").asText().equals("NORMAL-01")) { assertThat(expected.path("from").asText()).hasSize(16); continue; }
            assertThat(expected.path("from").asText()).hasSize(35).endsWith(".123456789+09:00");
            assertThat(expected.path("to").asText()).hasSize(35).endsWith(".123456789+09:00");
            if (!expected.path("q").isNull()) {
                String q = expected.path("q").asText(), eqp = expected.path("eqp").asText();
                assertThat(q.codePointCount(0, q.length())).isEqualTo(256);
                assertThat(eqp.codePointCount(0, eqp.length())).isEqualTo(64);
                assertThat(QRefContext.extract(row.path("question").asText()).resolve(row.path("expectedOutput").path("qRef"))).isEqualTo(q);
            }
        }
        assertThat(ids).contains("NORMAL-PRECISE", "MAX-SUPPLEMENTARY", "ESCAPE-CONTROL");
        JsonNode supplementary = rows.stream().filter(r -> r.path("caseId").asText().equals("MAX-SUPPLEMENTARY")).findFirst().orElseThrow();
        assertThat(supplementary.path("expectedResolvedOutput").path("q").asText().length()).isGreaterThan(256);
        JsonNode control = rows.stream().filter(r -> r.path("caseId").asText().equals("ESCAPE-CONTROL")).findFirst().orElseThrow();
        assertThat(control.path("expectedResolvedOutput").path("q").asText()).isEqualTo("\u0001".repeat(256));
        JsonNode escape = rows.stream().filter(r -> r.path("caseId").asText().equals("ESCAPE-01")).findFirst().orElseThrow();
        assertThat(escape.path("expectedResolvedOutput").path("q").asText()).contains("\"", "\\", "\u0001");
        assertThat(escape.path("expectedJson").asText()).contains("\"qRef\":\"Q1\"").doesNotContain("\"q\":");
    }

    @Test void v2RequiresApprovedLengthsAndV1RegenerationRemainsByteIdentical() throws Exception {
        assertThat(ArgLimits.parse(limits()).versioned()).isTrue();
        for (String field : List.of("eqp", "q", "from", "to")) {
            ObjectNode altered = limits(); ArgLimitsSchemaTest.field(altered, field).put("maxChars", 1);
            assertThatThrownBy(() -> ArgLimits.parse(altered)).isInstanceOf(EvalException.class);
        }
        Path root = Path.of(System.getProperty("aigw.rootDir"));
        ArgLimits legacy = ArgLimits.load(root.resolve("eval/f0/arg-limits-v1.json"));
        assertThat(legacy.versioned()).isFalse();
        BoundaryCases.Result regenerated = BoundaryCases.generate(legacy, RegistryFixture.load(root.resolve("eval/golden/tc-registry-fixture-v1.json")));
        assertThat(F0TestRoot.cases(regenerated)).isEqualTo(Files.readString(root.resolve("eval/f0/output-boundary-cases.jsonl")));
    }

    @Test void repositoryV2LimitsCiteTheActualApprovedSchemaLines() throws Exception {
        Path root = Path.of(System.getProperty("aigw.rootDir"));
        ArgLimits approved = ArgLimits.load(root.resolve("eval/f0/v2/arg-limits-v2.json"));
        assertThat(approved.versioned()).isTrue(); assertThat(approved.unresolved()).isEmpty();
        for (String field : List.of("from", "to", "q")) for (JsonNode evidence : approved.get(field).evidence()) {
            assertThat(evidence.path("kind").asText()).isEqualTo("SCHEMA");
            String ref = evidence.path("ref").asText(); int colon = ref.lastIndexOf(':');
            String line = Files.readAllLines(root.getParent().resolve(ref.substring(0, colon)), java.nio.charset.StandardCharsets.UTF_8).get(Integer.parseInt(ref.substring(colon + 1)) - 1);
            if (field.equals("q")) assertThat(line).contains("최대 256 Unicode 코드포인트", "원문 코드포인트 수");
            else assertThat(line).contains("최장 35 CODEPOINT", "나노초", "같은 instant", "엄격한 실제 달력");
        }
    }
}
