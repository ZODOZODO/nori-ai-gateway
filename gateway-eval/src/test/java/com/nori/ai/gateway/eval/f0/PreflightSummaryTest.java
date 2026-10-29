package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PreflightSummaryTest {
    @TempDir Path directory;

    int preflight(F0TestRoot f, OllamaStub stub, String name) { return f.exec("preflight", "--endpoint", stub.endpoint(), "--out", "eval/f0/results/20261101T000000Z/" + name); }

    @Test void writesOnlyAllowedSummaryFieldsEvenWhenResponsesCarrySentinels() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            assertThat(preflight(f, stub, "preflight.json")).isZero();
            JsonNode summary = f.json("eval/f0/results/20261101T000000Z/preflight.json");
            assertThat(GoldenSet.keys(summary)).isEqualTo(Set.of("models", "residentCount", "ok", "reason"));
            summary.path("models").forEach(m -> assertThat(GoldenSet.keys(m)).isEqualTo(Set.of("name", "digest", "context_length")));
            assertThat(summary.path("ok").asBoolean()).isTrue(); assertThat(summary.path("residentCount").asInt()).isEqualTo(2);
            assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
            assertThat(stub.requests).extracting(OllamaStub.Request::path).containsOnly("/api/ps", "/api/tags");
            assertThat(preflight(f, stub, "preflight.json")).isEqualTo(2); assertThat(f.lastError).isEqualTo("OUTPUT_EXISTS");
        }
    }
    @Test void notResidentOrGenerationContextNot4096ExitsFourButEmbeddingContextIsOnlyRecorded() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            ObjectNode onlyGeneration = (ObjectNode)OllamaStub.models(4096, 8192, OllamaStub.GEN_DIGEST); ((com.fasterxml.jackson.databind.node.ArrayNode)onlyGeneration.path("models")).remove(1);
            stub.ps = onlyGeneration; assertThat(preflight(f, stub, "a.json")).isEqualTo(4); assertThat(f.json("eval/f0/results/20261101T000000Z/a.json").path("reason").asText()).isEqualTo("MODEL_NOT_RESIDENT");
            stub.ps = OllamaStub.models(8192, 8192, OllamaStub.GEN_DIGEST); assertThat(preflight(f, stub, "b.json")).isEqualTo(4); assertThat(f.json("eval/f0/results/20261101T000000Z/b.json").path("reason").asText()).isEqualTo("CONTEXT_LENGTH_MISMATCH");
            stub.ps = OllamaStub.models(null, 8192, OllamaStub.GEN_DIGEST); assertThat(preflight(f, stub, "c.json")).isEqualTo(4);
            stub.ps = OllamaStub.models(4096, 512, OllamaStub.GEN_DIGEST); assertThat(preflight(f, stub, "d.json")).isZero();
            JsonNode recorded = f.json("eval/f0/results/20261101T000000Z/d.json");
            assertThat(recorded.path("ok").asBoolean()).isTrue(); assertThat(recorded.path("models").get(1).path("context_length").asInt()).isEqualTo(512);
            stub.ps = OllamaStub.models(4096, 8192, OllamaStub.GEN_DIGEST); stub.tags = OllamaStub.tagModels("9".repeat(64));
            assertThat(preflight(f, stub, "e.json")).isEqualTo(4); assertThat(f.json("eval/f0/results/20261101T000000Z/e.json").path("reason").asText()).isEqualTo("MODEL_DIGEST_MISMATCH");
            assertThat(stub.inference()).isEmpty();
        }
    }
    @Test void measurementNeverSendsInferenceWhenPreflightFailsOrModelLeavesMemory() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.ps = OllamaStub.models(8192, 8192, OllamaStub.GEN_DIGEST);
            assertThat(F0RunTest.runB(f, stub, "20261101T000000Z")).isEqualTo(4);
            assertThat(stub.inference()).isEmpty();
            assertThat(f.root.resolve("eval/f0/results/20261101T000000Z/B/run-binding.json")).doesNotExist();
        }
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            int[] chats = {0};
            var original = stub.chat;
            stub.chat = body -> { if (++chats[0] == 3) stub.ps = OllamaStub.models(4096, 8192, "3".repeat(64)); return original.apply(body); };
            assertThat(F0RunTest.runB(f, stub, "20261101T000001Z")).isEqualTo(5); assertThat(f.lastError).isEqualTo("MODEL_NOT_RESIDENT");
            assertThat(stub.inference()).hasSize(3);
            JsonNode partial = f.json("eval/f0/results/20261101T000001Z/B/f0-results.json");
            assertThat(partial.path("complete").asBoolean()).isFalse(); assertThat(partial.path("abortReason").asText()).isEqualTo("MODEL_NOT_RESIDENT"); assertThat(partial.path("attempts")).hasSize(3);
        }
    }
}
