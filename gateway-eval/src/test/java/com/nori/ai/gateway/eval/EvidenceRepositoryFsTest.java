package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.*;
import com.nori.ai.gateway.eval.spec.SpecBaseline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.eval.TestSupport.error;

class EvidenceRepositoryFsTest {
    @TempDir Path directory;

    GateEvaluator.GateReport evaluate(TestSupport fixture, EvidenceRepositoryFs repository) {
        return new GateEvaluator().evaluate("F1", "p", repository.readAll("F1"), fixture.catalog, SpecBaseline.parseChain(fixture.baseline));
    }

    @Test void operationalArtifactsDoNotChangeVerdictsOrStoredBytes() throws Exception {
        TestSupport f = new TestSupport(directory);
        EvidenceRepositoryFs repository = new EvidenceRepositoryFs(directory);
        List<ObjectNode> records = List.of(f.parent("F1", "p"), f.run("F1", "p", "r", "F1", "COMPLETED", "2026-10-29T00:00:01Z"),
            f.leaf("F1", "p", "r", "AC-36", 0, "PASS", "2026-10-29T00:00:01Z"),
            f.record(Map.of("kind", "MEASUREMENT", "measurementId", "ops:restore-rto", "stage", "F1", "runId", "m", "parentRunId", "p")));
        Map<Path, byte[]> original = new LinkedHashMap<>();
        for (ObjectNode record : records) { Path path = repository.store(record); original.put(path, Files.readAllBytes(path)); }
        var before = evaluate(f, repository);
        for (String path : List.of("gates/p06-hba.json", "gates/deploy/nested.json", "ops/parent-manifest.json", "image-digests.json", "backup.json", "AC-36.json")) {
            Path artifact = directory.resolve("evidence/F1/" + path); Files.createDirectories(artifact.getParent()); Files.writeString(artifact, "{invalid-operational-json");
        }
        assertThat(repository.readAll("F1")).containsExactlyInAnyOrderElementsOf(records);
        assertThat(evaluate(f, repository)).isEqualTo(before);
        for (var entry : original.entrySet()) {
            assertThat(Files.readAllBytes(entry.getKey())).isEqualTo(entry.getValue());
            assertThat(repository.store(Json.read(entry.getKey()))).isEqualTo(entry.getKey());
        }
        ObjectNode tampered = records.get(2).deepCopy(); tampered.put("result", "FAIL");
        Json.write(original.keySet().stream().filter(p -> p.getParent().endsWith("AC-36")).findFirst().orElseThrow(), tampered);
        error(() -> evaluate(f, repository), 5, "EVIDENCE_HASH_MISMATCH");
    }

    @Test void malformedJsonIsRejectedInEveryRecordNamespace() throws Exception {
        for (String namespace : List.of("runs", "measurements/probe", "AC-36", "ISO-01", "AC-99-UNKNOWN", "perf", "user-run")) {
            Path root = directory.resolve(namespace.replace('/', '_'));
            Path file = root.resolve("evidence/F1/" + namespace + "/nested/nonhash.json");
            Files.createDirectories(file.getParent()); Files.writeString(file, "{broken");
            error(() -> new EvidenceRepositoryFs(root).readAll("F1"), 2, "INVALID_JSON");
        }
    }

    @Test void nonhashFilenamesAndMissingKindOrUnknownLeafAreNotSilentlySkipped() throws Exception {
        TestSupport f = new TestSupport(directory);
        EvidenceRepositoryFs repository = new EvidenceRepositoryFs(directory);
        Path file = directory.resolve("evidence/F1/AC-99-UNKNOWN/nested/nonhash.json");
        ObjectNode leaf = f.leaf("F1", "p", "r", "AC-36", 0, "PASS", "2026-10-29T00:00:01Z");
        Json.write(file, leaf);
        assertThat(repository.readAll("F1")).containsExactly(leaf);
        ObjectNode noKind = leaf.deepCopy(); noKind.remove("kind"); EvidenceRecord.seal(noKind); Json.write(file, noKind);
        assertThatThrownBy(() -> evaluate(f, repository)).isInstanceOf(EvalException.class);
        leaf.put("leafId", "AC-99-UNKNOWN"); EvidenceRecord.seal(leaf); Json.write(file, leaf);
        error(() -> evaluate(f, repository), 2, "EVIDENCE_INVALID");
    }

    @Test void directUserRunIsReadWhileOpsRequiresExplicitIngest() throws Exception {
        TestSupport f = new TestSupport(directory);
        ObjectNode manifest = f.parent("F1", "p");
        ObjectNode user = f.record(Map.of("kind", "LEAF", "stage", "F2", "leafId", "AC-35-FW", "runId", "user", "parentRunId", "p",
            "producer", "USER_RUN", "componentId", "USER_RUN", "result", "PASS", "buildIdentity", f.identity("F2")));
        Path source = directory.resolve("evidence/F1/ops/parent.json"); Json.write(source, manifest);
        EvidenceRepositoryFs repository = new EvidenceRepositoryFs(directory);
        assertThat(repository.readAll("F1")).isEmpty();
        byte[] original = Files.readAllBytes(source);
        assertThat(f.cli("ingest", "--stage", "F1", "--parent-run-id", "p", "--source", "ops", "--input", source.toString())).isZero();
        assertThat(repository.readAll("F1")).containsExactly(manifest);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        Json.write(directory.resolve("evidence/F2/user-run/manual.json"), user);
        EvidenceRecord.validate(user, f.catalog);
        assertThat(repository.readAll("F2")).containsExactly(user);
    }
}
