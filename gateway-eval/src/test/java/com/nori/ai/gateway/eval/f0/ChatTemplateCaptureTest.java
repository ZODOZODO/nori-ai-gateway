package com.nori.ai.gateway.eval.f0;

import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ChatTemplateCaptureTest {
    @TempDir Path directory;

    @Test void storesOnlyTemplateFieldShaAndDigestAndRefusesRecapture() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            assertThat(f.exec("chat-template", "--endpoint", stub.endpoint(), "--model", "qwen3:4b-instruct", "--out", "eval/f0/chat-template")).isZero();
            Path dir = f.root.resolve("eval/f0/chat-template");
            try (var list = Files.list(dir)) { assertThat(list.map(p -> p.getFileName().toString()).toList()).containsExactlyInAnyOrder("qwen3-4b-instruct.template", "qwen3-4b-instruct.template.sha256", "model-digest.txt", "run-binding.json"); }
            byte[] template = Files.readAllBytes(dir.resolve("qwen3-4b-instruct.template"));
            assertThat(new String(template, StandardCharsets.UTF_8)).isEqualTo(stub.template);
            assertThat(Files.readString(dir.resolve("qwen3-4b-instruct.template.sha256")).strip()).isEqualTo(Json.sha(template));
            assertThat(Files.readString(dir.resolve("model-digest.txt")).strip()).isEqualTo(OllamaStub.GEN_DIGEST);
            assertThat(f.json("eval/f0/chat-template/run-binding.json").path("runtimeManifest").path("templateSha256").asText()).isEqualTo(Json.sha(template));
            assertThat(f.allOutput("eval/f0/chat-template")).doesNotContain(OllamaStub.SENTINEL);
            assertThat(stub.inference()).isEmpty();
            assertThat(f.exec("chat-template", "--endpoint", stub.endpoint(), "--model", "qwen3:4b-instruct", "--out", "eval/f0/chat-template")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("RUN_BINDING_EXISTS");
            assertThat(Files.readAllBytes(dir.resolve("qwen3-4b-instruct.template"))).isEqualTo(template);
        }
    }
    @Test void digestMismatchBetweenTagsAndPsExitsSixWithoutFiles() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.tags = OllamaStub.tagModels("8".repeat(64));
            assertThat(f.exec("chat-template", "--endpoint", stub.endpoint(), "--model", "qwen3:4b-instruct", "--out", "eval/f0/chat-template")).isEqualTo(6);
            assertThat(f.lastError).isEqualTo("MODEL_DIGEST_MISMATCH");
            assertThat(f.root.resolve("eval/f0/chat-template")).doesNotExist();
            assertThat(f.exec("chat-template", "--endpoint", stub.endpoint(), "--model", "bge-m3", "--out", "eval/f0/chat-template")).isEqualTo(2);
        }
    }
}
