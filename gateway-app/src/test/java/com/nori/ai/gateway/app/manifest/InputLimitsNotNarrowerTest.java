package com.nori.ai.gateway.app.manifest;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class InputLimitsNotNarrowerTest {
    private static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    private final ManifestLoader loader = new ManifestLoader();
    private final List<CapabilityManifest> manifests = loader.loadClasspath("manifests/tc/index.json");
    private final InputValidator validator = new InputValidator();
    private JsonNode args(String q) { return SchemaContracts.JSON.createObjectNode().put("eqp", "설비").put("q", q); }
    @Test void allActualManifestLimitsMatchProtectedConfirmedFieldsAndSchemas() throws Exception {
        var protectedLimits=SchemaContracts.parse(Files.readString(ROOT.resolve("eval/f0/v2/arg-limits-v2.json"))).get("fields");
        assertThat(manifests).hasSize(9);
        for (var manifest:manifests) {
            assertThat(manifest.riskLevel()).isEqualTo(RiskLevel.READ);assertThat(manifest.sideEffects()).isEqualTo(SideEffects.NONE);
            for (JsonNode field:protectedLimits) {
                String name=field.get("field").asString();if(name.equals("t"))continue;
                assertThat(field.get("status").asString()).isEqualTo("CONFIRMED");
                var limit=manifest.inputLimits().get(name);assertThat(limit).isNotNull();
                assertThat(limit.maxChars()).isEqualTo(field.get("maxChars").asInt());
                assertThat(limit.charUnit()).isEqualTo(field.get("charUnit").asString());assertThat(limit.status()).isEqualTo("CONFIRMED");
                assertThat(limit.format()).isEqualTo(field.get("format").asString());
                assertThat(manifest.inputSchema().get("properties").get(name).get("maxLength").asInt()).isEqualTo(limit.maxChars());
            }
        }
    }
    @Test void qBoundaryCountsDecodedCodepointsAndPreservesOriginalIncludingEscapes() {
        for (var manifest:manifests) {
            for(String q:List.of("가".repeat(256),"😀".repeat(256)," \t원문\n\"\\é ")) {
                var input=args(q);String before=input.toString();
                assertThat(validator.validate(manifest,input,ZoneOffset.UTC)).isNull();assertThat(input.toString()).isEqualTo(before);
            }
            for(String q:List.of("가".repeat(257),"😀".repeat(257))) {
                var fail=validator.validate(manifest,args(q),ZoneOffset.UTC);assertThat(fail.reason()).isEqualTo(RejectReason.INPUT_BUDGET_EXCEEDED);assertThat(fail.fields()).containsExactly("q");
            }
            var decoded=SchemaContracts.parse("{\"eqp\":\"설비\",\"q\":\""+"\\ud83d\\ude00".repeat(256)+"\"}");
            assertThat(decoded.get("q").asString()).isEqualTo("😀".repeat(256));assertThat(validator.validate(manifest,decoded,ZoneOffset.UTC)).isNull();
            decoded=SchemaContracts.parse("{\"eqp\":\"설비\",\"q\":\""+"\\ud83d\\ude00".repeat(257)+"\"}");
            assertThat(validator.validate(manifest,decoded,ZoneOffset.UTC).reason()).isEqualTo(RejectReason.INPUT_BUDGET_EXCEEDED);
            assertThat(validator.validate(manifest,SchemaContracts.parse("{\"eqp\":\"설비\",\"qRef\":\"Q1\"}"),ZoneOffset.UTC).reason()).isEqualTo(RejectReason.PLAN_INVALID);
            assertThat(validator.validate(manifest,args(""),ZoneOffset.UTC).reason()).isEqualTo(RejectReason.PLAN_INVALID);
            assertThat(validator.validate(manifest,SchemaContracts.parse("{\"eqp\":\"설비\",\"q\":null}"),ZoneOffset.UTC)).isNull();
        }
    }
    @Test void equipmentBoundaryCountsSupplementaryCodepoints() {
        for (var manifest:manifests) {
            var a=SchemaContracts.JSON.createObjectNode().put("eqp","😀".repeat(64));
            assertThat(validator.validate(manifest,a,ZoneOffset.UTC)).isNull();
            a.put("eqp","😀".repeat(65));assertThat(validator.validate(manifest,a,ZoneOffset.UTC).reason()).isEqualTo(RejectReason.PLAN_INVALID);
        }
    }
    @Test void acceptsCompleteV2TimeGrammarAndRejectsImpossibleDatesAndUnsupportedOffsets() {
        List<String> valid=List.of("2024-02-29T12:34","2024-02-29T12:34Z","2024-02-29T12:34+09:00","2024-02-29T12:34:56",
            "2024-02-29T12:34:56.1Z","2024-02-29T12:34:56.123456789+09:00","2024-02-29T12:34:56.123456789-18:00");
        List<String> invalid=List.of("2023-02-29T12:34","2024-04-31T12:34","2024-02-29T24:00","2024-02-29T12:60",
            "2024-02-29T12:34:60Z","2024-02-29T12:34.1Z","2024-02-29T12:34:56.1234567890Z","2024-02-29T12:34+0900",
            "2024-02-29T12:34+09","2024-02-29T12:34+09:00:01","2024-02-29T12:34+18:01","2024-02-29T12:34+19:00","2024-2-29T12:34");
        for(var manifest:manifests) {
            for(String value:valid) {var a=SchemaContracts.JSON.createObjectNode().put("eqp","설비").put("from",value);assertThat(validator.validate(manifest,a,ZoneOffset.UTC)).as(value).isNull();}
            for(String value:invalid) {var a=SchemaContracts.JSON.createObjectNode().put("eqp","설비").put("from",value);assertThat(validator.validate(manifest,a,ZoneOffset.UTC)).as(value).isNotNull();}
        }
        String longest=valid.get(5);assertThat(longest.codePointCount(0,longest.length())).isEqualTo(35);
        assertThat(validator.parseTime(longest,ZoneOffset.UTC)).isEqualTo(Instant.parse("2024-02-29T03:34:56.123456789Z"));
    }
    @Test void timeComparisonPreservesNanosecondsAndEquivalentInstants() {
        var manifest=manifests.getFirst();
        var args=SchemaContracts.JSON.createObjectNode().put("eqp","설비").put("from","2024-02-29T12:34:56.123456789+09:00").put("to","2024-02-29T03:34:56.123456790Z");
        assertThat(validator.validate(manifest,args,ZoneOffset.UTC)).isNull();
        args.put("to","2024-02-29T03:34:56.123456789Z");assertThat(validator.validate(manifest,args,ZoneOffset.UTC)).isNotNull();
        args.put("to","2024-02-29T03:34:56.123456788Z");assertThat(validator.validate(manifest,args,ZoneOffset.UTC)).isNotNull();
    }
}
