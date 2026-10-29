package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class F0RunTest {
    @TempDir Path directory;

    static int runB(F0TestRoot f, OllamaStub stub, String run) { return run(f, stub, run, "B", "eval/f0/prompts/classify-v1.txt", "2"); }
    static int run(F0TestRoot f, OllamaStub stub, String run, String path, String prompt, String repeats) {
        return f.exec("run", "--path", path, "--endpoint", stub.endpoint(), "--golden", "eval/golden/tc-f0-v1.jsonl", "--fixture", "eval/golden/tc-registry-fixture-v1.json",
            "--prompt", prompt, "--repeats", repeats, "--run-id", run, "--out", "eval/f0/results/" + run + "/" + path);
    }

    @Test void roundRobinRunWritesBindingFirstSanitizedRowsAndCompleteDenominator() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            var correct = StubAnswers.correct(f.root);
            stub.chat = body -> { var answer = correct.apply(body); ((com.fasterxml.jackson.databind.node.ObjectNode)answer).put("sentinel", OllamaStub.SENTINEL); return answer; };
            assertThat(runB(f, stub, "R1")).isZero();
            JsonNode binding = f.json("eval/f0/results/R1/B/run-binding.json");
            assertThat(binding.path("runId").asText()).isEqualTo("R1"); assertThat(binding.path("registeredCommit").asText()).isEqualTo(F0TestRoot.REGISTERED); assertThat(binding.path("preregCommit").asText()).isEqualTo(F0TestRoot.PREREG_COMMIT);
            assertThat(Instant.parse(binding.path("startedAt").asText())).isAfter(Instant.ofEpochSecond(F0TestRoot.COMMIT_EPOCH));
            assertThat(binding.path("runtimeManifest").path("modelDigest").asText()).isEqualTo(OllamaStub.GEN_DIGEST);
            assertThat(binding.path("harnessDigest").asText()).isEqualTo(HarnessDigest.compute(f.root));
            JsonNode result = f.json("eval/f0/results/R1/B/f0-results.json");
            int questions = 22;
            assertThat(result.path("complete").asBoolean()).isTrue(); assertThat(result.path("attempts")).hasSize(questions * 2);
            List<String> order = stub.inference().stream().map(r -> OllamaStub.question(r.body())).toList();
            assertThat(order.subList(0, questions)).containsExactlyElementsOf(order.subList(questions, questions * 2)).doesNotHaveDuplicates();
            for (int i = 0; i < result.path("attempts").size(); i++) assertThat(result.path("attempts").get(i).path("round").asInt()).isEqualTo(i < questions ? 1 : 2);
            assertThat(f.allOutput("eval/f0/results/R1")).doesNotContain(OllamaStub.SENTINEL).doesNotContain("PHOTO_001").doesNotContain("CONNECTION_TIMEOUT");
            GoldenSet golden = GoldenSet.load(f.root.resolve("eval/golden/tc-f0-v1.jsonl"), RegistryFixture.load(f.root.resolve("eval/golden/tc-registry-fixture-v1.json")));
            List<JsonNode> attempts = new ArrayList<>(); result.path("attempts").forEach(attempts::add);
            MetricsAggregator.Metrics metrics = MetricsAggregator.aggregate(attempts, golden, 2);
            assertThat(metrics.taskType().correct()).isEqualTo(questions); assertThat(metrics.args().correct()).isEqualTo(questions); assertThat(metrics.unsupported().correct()).isEqualTo(questions);
        }
    }
    @Test void endpointErrorPreservesPartialResultsAndTimeoutStaysAsFailedSample() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root); stub.failChatAfter = 5;
            assertThat(runB(f, stub, "R2")).isEqualTo(5); assertThat(f.lastError).isEqualTo("ENDPOINT_HTTP_ERROR");
            JsonNode partial = f.json("eval/f0/results/R2/B/f0-results.json");
            assertThat(partial.path("complete").asBoolean()).isFalse(); assertThat(partial.path("abortReason").asText()).isEqualTo("ENDPOINT_HTTP_ERROR"); assertThat(partial.path("attempts")).hasSize(5);
            assertThat(f.allOutput("eval/f0/results/R2")).doesNotContain(OllamaStub.SENTINEL);
        }
        try (OllamaStub stub = new OllamaStub()) {
            var correct = StubAnswers.correct(f.root); int[] calls = {0};
            stub.chat = body -> { if (++calls[0] == 2) { try { Thread.sleep(2600); } catch (InterruptedException e) { throw new IllegalStateException(e); } } return correct.apply(body); };
            assertThat(runB(f, stub, "R3")).isZero();
            JsonNode result = f.json("eval/f0/results/R3/B/f0-results.json");
            JsonNode timedOut = result.path("attempts").get(1);
            assertThat(timedOut.path("status").asText()).isEqualTo("TIMEOUT"); assertThat(timedOut.path("latencyMs").asLong()).isGreaterThanOrEqualTo(1900); assertThat(timedOut.path("predicted").isNull()).isTrue();
            assertThat(result.path("complete").asBoolean()).isTrue(); assertThat(result.path("attempts")).hasSize(44);
        }
    }
    @Test void argumentsMustBePresentAndMatchPreregisteredInputs() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            assertThat(f.exec("run", "--path", "B", "--endpoint", stub.endpoint())).isEqualTo(2); assertThat(f.lastError).isEqualTo("ARGUMENT_MISSING");
            assertThat(run(f, stub, "R4", "A", "eval/f0/prompts/classify-v1.txt", "2")).isEqualTo(2); assertThat(f.lastError).isEqualTo("ARGUMENT_NOT_REGISTERED");
            assertThat(run(f, stub, "R4", "B", "eval/f0/prompts/classify-v1.txt", "3")).isEqualTo(2); assertThat(f.lastError).isEqualTo("ARGUMENT_NOT_REGISTERED");
            assertThat(f.exec("run", "--path", "B", "--endpoint", stub.endpoint(), "--golden", "eval/golden/tc-f0-v1.jsonl", "--fixture", "eval/golden/tc-registry-fixture-v1.json",
                "--prompt", "eval/f0/prompts/classify-v1.txt", "--repeats", "2", "--run-id", "R4", "--out", "eval/f0/results/OTHER/B")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("RUN_ID_MISMATCH");
            assertThat(f.exec("run", "--path", "B", "--endpoint", "http://10.0.0.5:11434", "--golden", "eval/golden/tc-f0-v1.jsonl", "--fixture", "eval/golden/tc-registry-fixture-v1.json",
                "--prompt", "eval/f0/prompts/classify-v1.txt", "--repeats", "2", "--run-id", "R4", "--out", "eval/f0/results/R4/B")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("ENDPOINT_INVALID");
            assertThat(stub.requests).isEmpty();
            assertThat(runB(f, stub, "R5")).isEqualTo(0);
            assertThat(run(f, stub, "R5", "B", "eval/f0/prompts/classify-v1.txt", "2")).isEqualTo(2); assertThat(f.lastError).isEqualTo("RUN_BINDING_EXISTS");
        }
    }
}
