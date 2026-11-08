package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ArgLimitsSchemaTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    @TempDir Path directory;

    static ObjectNode confirmedLimits(int qMax) {
        ObjectNode root = Json.MAPPER.createObjectNode(); ArrayNode fields = root.putArray("fields");
        add(fields, "t", "ENUM:KQ|LQ|INC|GQ|UNS", 3, "SCHEMA", "spec/fixture.md:1");
        add(fields, "eqp", "TC_EQP_ID", 64, "CODE", "fixture/TcEqpEntity.java:56");
        add(fields, "from", "DATETIME:uuuu-MM-dd'T'HH:mm", 16, "SCHEMA", "fixture/time.md:3");
        add(fields, "to", "DATETIME:uuuu-MM-dd'T'HH:mm", 16, "SCHEMA", "fixture/time.md:3");
        add(fields, "q", "TEXT", qMax, "API_DOC", "https://example.invalid/fixture-doc");
        return root;
    }
    static void add(ArrayNode fields, String field, String format, int max, String kind, String ref) {
        ObjectNode n = fields.addObject(); n.put("field", field); n.put("format", format); n.put("maxChars", max); n.put("charUnit", "CODEPOINT"); n.put("status", "CONFIRMED");
        n.putArray("evidence").addObject().put("kind", kind).put("ref", ref);
    }
    static ObjectNode field(ObjectNode root, String name) { for (JsonNode n : root.path("fields")) if (n.path("field").asText().equals(name)) return (ObjectNode)n; throw new AssertionError(); }
    static void rejects(JsonNode limits, String code) { assertThatThrownBy(() -> ArgLimits.parse(limits)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo(code)); }

    @Test void unresolvedFieldsRefuseMaxAndEscapeButKeepNormalAndBlockedMarker() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        Files.delete(f.root.resolve("eval/f0/output-boundary-cases.jsonl"));
        assertThat(f.exec("cases", "--limits", "eval/f0/arg-limits-v1.json", "--fixture", "eval/golden/tc-registry-fixture-v1.json", "--out", "eval/f0/output-boundary-cases.jsonl")).isEqualTo(2);
        assertThat(f.lastError).isEqualTo("ARG_LIMITS_UNRESOLVED");
        List<JsonNode> rows = BoundaryCases.load(f.root.resolve("eval/f0/output-boundary-cases.jsonl"));
        assertThat(rows).extracting(r -> r.path("kind").asText()).containsExactly("NORMAL");
        JsonNode blocked = f.json("eval/f0/output-boundary-cases.blocked.json");
        assertThat(blocked.path("unresolvedFields")).extracting(JsonNode::asText).containsExactly("from", "to", "q");
        assertThat(blocked.path("blockedKinds")).extracting(JsonNode::asText).containsExactly("MAX", "ESCAPE");
        // NORMAL = SPEC §14.5 예시 형태(RFC 8785 기대 JSON)
        assertThat(rows.get(0).path("expectedJson").asText()).isEqualTo("{\"eqp\":\"PHOTO_001\",\"from\":\"2026-10-03T00:00\",\"q\":null,\"t\":\"INC\",\"to\":\"2026-10-04T00:00\"}");
        assertThat(GoldenSet.keys(rows.get(0))).isEqualTo(Set.of("caseId", "kind", "expectedOutput", "expectedJson", "question", "context"));
        assertThat(rows.get(0).path("context").path("timeWindow").isNull()).isTrue(); assertThat(rows.get(0).path("context").path("selectedResourceRefs")).isEmpty();
    }
    @Test void confirmedWithoutEvidenceOrWithMalformedEvidenceOrInconsistentFormatIsRejected() {
        ObjectNode noEvidence = confirmedLimits(40); ((ArrayNode)field(noEvidence, "q").path("evidence")).removeAll(); rejects(noEvidence, "ARG_LIMITS_UNSUPPORTED_CONFIRMED");
        ObjectNode badRef = confirmedLimits(40); ((ObjectNode)field(badRef, "eqp").path("evidence").get(0)).put("ref", "TcEqpEntity"); rejects(badRef, "ARG_LIMITS_UNSUPPORTED_CONFIRMED");
        ObjectNode badKind = confirmedLimits(40); ((ObjectNode)field(badKind, "eqp").path("evidence").get(0)).put("kind", "GUESS"); rejects(badKind, "ARG_LIMITS_UNSUPPORTED_CONFIRMED");
        ObjectNode httpDoc = confirmedLimits(40); ((ObjectNode)field(httpDoc, "q").path("evidence").get(0)).put("ref", "http://insecure.example/doc"); rejects(httpDoc, "ARG_LIMITS_UNSUPPORTED_CONFIRMED");
        ObjectNode unresolvedWithMax = confirmedLimits(40); field(unresolvedWithMax, "q").put("status", "UNRESOLVED").put("unresolvedReason", "X"); rejects(unresolvedWithMax, "ARG_LIMITS_INVALID");
        ObjectNode unresolvedNoReason = confirmedLimits(40); field(unresolvedNoReason, "q").put("status", "UNRESOLVED").putNull("maxChars"); rejects(unresolvedNoReason, "ARG_LIMITS_INVALID");
        ObjectNode formatMismatch = confirmedLimits(40); field(formatMismatch, "from").put("maxChars", 19); rejects(formatMismatch, "ARG_LIMITS_FORMAT_MISMATCH");
        ObjectNode missingField = confirmedLimits(40); ((ArrayNode)missingField.path("fields")).remove(4); rejects(missingField, "ARG_LIMITS_INVALID");
        ObjectNode zero = confirmedLimits(40); field(zero, "q").put("maxChars", 0); rejects(zero, "ARG_LIMITS_INVALID");
    }
    @Test void confirmedLimitsProduceLosslessMaxAndEscapeWithTemplateQuestionAndRegisteredMaxEqp() {
        RegistryFixture fixture = RegistryFixture.load(ROOT.resolve("eval/golden/tc-registry-fixture-v1.json"));
        BoundaryCases.Result result = BoundaryCases.generate(ArgLimits.parse(confirmedLimits(200)), fixture);
        assertThat(result.unresolved()).isEmpty();
        assertThat(result.rows()).extracting(r -> r.path("kind").asText()).containsExactly("NORMAL", "MAX", "MAX", "MAX", "MAX", "ESCAPE");
        for (JsonNode row : result.rows()) {
            JsonNode e = row.path("expectedOutput");
            assertThat(row.path("expectedJson").asText()).isEqualTo(Json.canonicalString(e));
            assertThat(row.path("question").asText()).startsWith(e.path("eqp").asText() + " 설비에서 ");
            if (!e.path("q").isNull()) assertThat(row.path("question").asText()).contains("\"" + e.path("q").asText() + "\"");
            if (row.path("kind").asText().equals("MAX")) {
                String eqp = e.path("eqp").asText(), q = e.path("q").asText();
                assertThat(eqp.codePointCount(0, eqp.length())).isEqualTo(64); assertThat(fixture.resources().eqpExists(eqp)).isTrue();
                assertThat(q.codePointCount(0, q.length())).isEqualTo(200); assertThat(e.path("from").asText()).hasSize(16);
            }
        }
        Set<String> scripts = new HashSet<>(); result.rows().stream().filter(r -> r.path("kind").asText().equals("MAX")).forEach(r -> scripts.add(r.path("caseId").asText()));
        assertThat(scripts).containsExactlyInAnyOrder("MAX-MIXED", "MAX-HANGUL", "MAX-DIGIT", "MAX-ASCII");
        JsonNode escape = result.rows().getLast();
        String q = escape.path("expectedOutput").path("q").asText();
        assertThat(q).contains("\"", "\\", "\u0001");
        assertThat(escape.path("question").asText()).contains(q);
        assertThat(escape.path("expectedJson").asText()).contains("\\\"", "\\\\", "\\u0001");
        ObjectNode tooShort = confirmedLimits(2); assertThatThrownBy(() -> BoundaryCases.generate(ArgLimits.parse(tooShort), fixture)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("ARG_LIMITS_TOO_SMALL_FOR_ESCAPE"));
        ObjectNode wideEqp = confirmedLimits(40); field(wideEqp, "eqp").put("maxChars", 65);
        assertThatThrownBy(() -> BoundaryCases.generate(ArgLimits.parse(wideEqp), fixture)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("FIXTURE_MAX_EQP_MISSING"));
    }
    @Test void repositoryLimitsMatchInvestigatedCodeEvidenceAndHumanDocument() throws Exception {
        ArgLimits limits = ArgLimits.load(ROOT.resolve("eval/f0/arg-limits-v1.json"));
        assertThat(limits.unresolved()).containsExactly("from", "to", "q");
        assertThat(limits.get("eqp").maxChars()).isEqualTo(64); assertThat(limits.get("t").maxChars()).isEqualTo(3);
        ArgLimitsSpecEvidence.historical(ROOT, false);
        ArgLimitsSpecEvidence.classification(ArgLimitsSpecEvidence.current(ROOT), limits.get("t"));
        // TC 리소스의 CODE·DB SCHEMA 근거는 기존 정본 경로:줄 검증을 유지한다.
        Path workspace = ROOT.getParent();
        for (String field : List.of("eqp")) for (JsonNode e : limits.get(field).evidence()) {
            String ref = e.path("ref").asText(); int colon = ref.lastIndexOf(':');
            List<String> lines = Files.readAllLines(workspace.resolve(ref.substring(0, colon)), StandardCharsets.UTF_8);
            String line = lines.get(Integer.parseInt(ref.substring(colon + 1)) - 1);
            if (field.equals("eqp") && ref.endsWith("TcEqpEntity.java:56")) assertThat(line).contains("eqp_id").contains("length = 64");
            if (field.equals("eqp") && ref.endsWith("V1__baseline.sql:420")) assertThat(line).contains("eqp_id").contains("VARCHAR(64)");
            if (field.equals("eqp") && ref.endsWith("EqpCreateRequest.java:33")) { assertThat(line).contains("@NotBlank"); assertThat(lines.get(Integer.parseInt(ref.substring(colon + 1)))).contains("String eqpId"); }
        }
        String doc = Files.readString(ROOT.resolve("docs/gates/f0/arg-limits.md"), StandardCharsets.UTF_8);
        for (String field : ArgLimits.FIELDS) {
            ArgLimits.Limit limit = limits.get(field);
            String row = doc.lines().filter(l -> l.startsWith("| " + field + " |")).findFirst().orElseThrow();
            assertThat(row).contains("| " + limit.status() + " |");
            if (limit.confirmed()) { assertThat(row).contains("| " + limit.maxChars() + " |"); limit.evidence().forEach(e -> assertThat(row).contains(e.path("ref").asText())); }
        }
        // 실제 경계 케이스 파일은 현재 limits·fixture 로 결정론 재생성한 결과와 같습니다.
        BoundaryCases.Result regenerated = BoundaryCases.generate(limits, RegistryFixture.load(ROOT.resolve("eval/golden/tc-registry-fixture-v1.json")));
        assertThat(Files.readString(ROOT.resolve("eval/f0/output-boundary-cases.jsonl"), StandardCharsets.UTF_8)).isEqualTo(F0TestRoot.cases(regenerated));
        assertThat(Json.read(ROOT.resolve("eval/f0/output-boundary-cases.blocked.json")).path("unresolvedFields")).extracting(JsonNode::asText).containsExactly("from", "to", "q");
    }
    @Test void classificationEvidenceRejectsMovedEnumAndDuplicateSection() {
        String current = ArgLimitsSpecEvidence.current(ROOT);
        ArgLimits.Limit limit = ArgLimits.load(ROOT.resolve("eval/f0/arg-limits-v1.json")).get("t");
        assertThatThrownBy(() -> ArgLimitsSpecEvidence.classification(current.replace("`t` ∈ KQ/LQ/INC/GQ/UNS =", "`t` ∈ KQ/LQ/INC/GQ =") + "\nKQ/LQ/INC/GQ/UNS", limit)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ArgLimitsSpecEvidence.classification(current + "\n" + ArgLimitsSpecEvidence.section(current), limit)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> ArgLimitsSpecEvidence.classification(current.replace("### 14.5 ", "### 99.5 "), limit)).isInstanceOf(AssertionError.class);
    }
    @Test void currentSpecUsesTheExistingResolverAndRejectsInvalidPropertyWithoutSiblingFallback() throws Exception {
        String current = ArgLimitsSpecEvidence.current(ROOT);
        Path approved = new com.nori.ai.gateway.eval.spec.SpecInputResolver(System.getenv()).resolve(null, System.getProperty("aigw.specPath"));
        assertThat(ArgLimitsSpecEvidence.current(ROOT, Map.of(), approved.toString())).isEqualTo(current);
        Path changed = directory.resolve("changed-spec.md"); Files.writeString(changed, current + "\n변조\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ArgLimitsSpecEvidence.current(ROOT, Map.of("AIGW_SPEC_FILE", approved.toString()), changed.toString()))
            .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("SPEC_BASELINE_MISMATCH"));
        assertThatThrownBy(() -> ArgLimitsSpecEvidence.current(ROOT, Map.of("AIGW_SPEC_FILE", approved.toString()), directory.resolve("missing.md").toString()))
            .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("SPEC_INPUT_MISSING"));
    }
}
