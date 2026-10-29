package com.nori.ai.gateway.eval.f3;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalCli;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.EvidenceRecord;
import com.nori.ai.gateway.eval.spec.SpecGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EmbeddingProfileTest {
    @TempDir Path root;

    ObjectNode measurement(Map<String,Object> values) throws IOException {
        Path spec = root.resolve("spec.md"); Files.writeString(spec, "fixture spec", StandardCharsets.UTF_8);
        Path baseline = root.resolve("docs/gates/spec-baseline.md"); Files.createDirectories(baseline.getParent());
        Files.writeString(baseline, "```json\n[{\"seq\":1,\"sha256\":\"" + Json.sha(Files.readAllBytes(spec)) + "\",\"prevSha256\":null,\"approvedAt\":\"2026-10-29\",\"approvalRef\":\"fixture\",\"changedSections\":[\"ALL\"],\"affectedLeaves\":\"ALL\",\"measurementAffecting\":true}]\n```\n", StandardCharsets.UTF_8);
        var binding = new SpecGuard(Map.of()).check(spec.toString(), null, baseline);
        return EvidenceRecord.create(binding, Map.of("kind", "MEASUREMENT", "measurementId", EmbeddingProfile.MEASUREMENT_ID, "stage", "F3", "runId", "probe-1", "parentRunId", "F3-PROBE-1", "values", values));
    }
    int cli(Path evidence) { return new EvalCli(Map.of(), new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), root).execute("f3", "embedding-profile", "--evidence", root.relativize(evidence).toString(), "--out", "out/embedding-profile.json"); }
    Path write(String name, Object record) { Path file = root.resolve(name); Json.write(file, record); return file; }

    @Test void buildsProfileFromVerifiedMeasurementOnly() throws Exception {
        assertThat(cli(write("ok.json", measurement(Map.of("dimension", 1024, "modelDigest", "sha256:" + "a".repeat(64)))))).isZero();
        var profile = Json.read(root.resolve("out/embedding-profile.json"));
        assertThat(profile.path("profileId").asText()).isEqualTo("bge-m3@" + "a".repeat(64) + "/1024/cosine");
        assertThat(profile.path("dimension").asInt()).isEqualTo(1024);
    }
    @Test void rejectsTamperedWrongKindOrMalformedValues() throws Exception {
        ObjectNode tampered = measurement(Map.of("dimension", 1024, "modelDigest", "a".repeat(64))); ((ObjectNode)tampered.path("values")).put("dimension", 768);
        assertThat(cli(write("tampered.json", tampered))).isEqualTo(2);
        for (Map<String,Object> values : List.of(Map.<String,Object>of("dimension", 0, "modelDigest", "a".repeat(64)), Map.<String,Object>of("dimension", "1024", "modelDigest", "a".repeat(64)), Map.<String,Object>of("dimension", 1024, "modelDigest", "latest")))
            assertThat(cli(write("bad.json", measurement(values)))).as(values.toString()).isEqualTo(2);
        ObjectNode other = measurement(Map.of("dimension", 1024, "modelDigest", "a".repeat(64))); other.put("measurementId", "f3:index-resources"); EvidenceRecord.seal(other);
        assertThat(cli(write("other.json", other))).isEqualTo(2);
        ObjectNode declared = measurement(Map.of("dimension", 1024, "modelDigest", "a".repeat(64))); declared.put("specShaSource", "DECLARED"); EvidenceRecord.seal(declared);
        assertThat(cli(write("declared.json", declared))).isEqualTo(2);
        assertThat(root.resolve("out/embedding-profile.json")).doesNotExist();
    }
    // Plan P02: 정수 차원 > 0·digest 형식만 검사(상한 없음). 정수 표현 가능성은 유지 — 소수·long overflow 거부.
    @Test void dimensionHasNoArbitraryUpperBoundButMustBeASafePositiveInteger() throws Exception {
        String digest = "b".repeat(64);
        for (long dimension : List.of(65537L, 1L << 20, Long.MAX_VALUE)) {
            var profile = EmbeddingProfile.build(measurement(Map.of("dimension", dimension, "modelDigest", digest)), com.nori.ai.gateway.eval.report.LeafCatalog.load(null));
            assertThat(profile.get("profileId")).as(String.valueOf(dimension)).isEqualTo("bge-m3@" + digest + "/" + dimension + "/cosine");
            assertThat(profile.get("dimension")).isEqualTo(dimension);
        }
        List<Object> bad = List.of(0, -1, 1024.5, 1024.0, new java.math.BigInteger("9223372036854775808"), new java.math.BigInteger("18446744073709552640"));
        for (Object dimension : bad) {
            ObjectNode record = measurement(Map.of("dimension", 1, "modelDigest", digest));
            ((ObjectNode) record.path("values")).set("dimension", Json.MAPPER.valueToTree(dimension)); EvidenceRecord.seal(record);
            assertThatThrownBy(() -> EmbeddingProfile.build(record, com.nori.ai.gateway.eval.report.LeafCatalog.load(null))).as(String.valueOf(dimension))
                .isInstanceOfSatisfying(com.nori.ai.gateway.eval.EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PROFILE_DIMENSION_INVALID"));
        }
    }
}
