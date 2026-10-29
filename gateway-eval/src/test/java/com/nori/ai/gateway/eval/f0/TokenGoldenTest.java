package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class TokenGoldenTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    @TempDir Path directory;

    int golden(F0TestRoot f, OllamaStub stub, String run) {
        f.env.put("AIGW_PARENT_RUN_ID", run);
        return f.exec("token-golden", "--endpoint", stub.endpoint(), "--messages", "eval/f0/token-golden-input-v1.jsonl", "--out", "eval/f0/results/" + run + "/token-golden.jsonl");
    }
    @Test void everyRowIsFlushedColdAndBoundToModelAndTemplateWithAllowedFieldsOnly() throws Exception {
        for (boolean reduce : List.of(true, false)) {
            F0TestRoot f = new F0TestRoot(directory.resolve(reduce ? "reduce" : "plain"));
            try (OllamaStub stub = new OllamaStub()) {
                UsageProbeTest.cacheModel(stub, reduce);
                assertThat(golden(f, stub, "T1")).isZero();
                List<JsonNode> rows = new ArrayList<>();
                for (String line : Files.readAllLines(f.root.resolve("eval/f0/results/T1/token-golden.jsonl"), StandardCharsets.UTF_8)) rows.add(Json.parse(line.getBytes(StandardCharsets.UTF_8)));
                assertThat(rows).hasSize(13);
                String templateSha = Json.sha(stub.template.getBytes(StandardCharsets.UTF_8));
                for (JsonNode row : rows) {
                    assertThat(GoldenSet.keys(row)).isEqualTo(Set.of("id", "messagesSha256", "promptTokens", "cacheExcluded", "modelDigest", "templateSha256"));
                    assertThat(row.path("cacheExcluded").asBoolean()).isTrue(); assertThat(row.path("modelDigest").asText()).isEqualTo(OllamaStub.GEN_DIGEST); assertThat(row.path("templateSha256").asText()).isEqualTo(templateSha);
                    assertThat(row.path("promptTokens").asInt()).as("flush 직후 cold 값").isGreaterThanOrEqualTo(100);
                }
                List<String> sequence = stub.inference().stream().map(r -> r.path().equals("/api/generate") ? "FLUSH" : "CHAT").toList();
                for (int i = 0; i < sequence.size(); i += 2) assertThat(sequence.subList(i, i + 2)).containsExactly("FLUSH", "CHAT");
                assertThat(f.allOutput("eval/f0/results/T1")).doesNotContain(OllamaStub.SENTINEL).doesNotContain("포토 설비").doesNotContain("PHOTO_001");
                assertThat(f.root.resolve("eval/f0/results/T1/token-golden.jsonl.partial")).doesNotExist();
                // 분류 프롬프트 실제 조립본 행은 classify-v1 + Registry fixture 조립 결과의 SHA 와 같습니다.
                RegistryFixture fixture = RegistryFixture.load(f.root.resolve("eval/golden/tc-registry-fixture-v1.json"));
                GoldenSet.Question q = GoldenSet.load(f.root.resolve("eval/golden/tc-f0-v1.jsonl"), fixture).byId("G01");
                var assembled = MessageAssembler.load(f.root.resolve("eval/f0/prompts/classify-v1.txt"), fixture).messages(q.question(), q.context());
                assertThat(rows.stream().filter(r -> r.path("id").asText().equals("TG08-CLASSIFY-ASSEMBLED-G01")).findFirst().orElseThrow().path("messagesSha256").asText())
                    .isEqualTo(Json.sha(Json.canonical(Json.MAPPER.valueToTree(assembled))));
            }
        }
    }
    @Test void digestMismatchExitsSixBeforeInferenceAndEndpointErrorKeepsOnlyPartial() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.tags = OllamaStub.tagModels("7".repeat(64));
            assertThat(golden(f, stub, "T2")).isEqualTo(6); assertThat(stub.inference()).isEmpty();
        }
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = body -> OllamaStub.chatResponse("{}", "length", 100, 1); stub.failChatAfter = 3;
            assertThat(golden(f, stub, "T3")).isEqualTo(5);
            assertThat(f.root.resolve("eval/f0/results/T3/token-golden.jsonl")).doesNotExist();
            assertThat(Files.readAllLines(f.root.resolve("eval/f0/results/T3/token-golden.jsonl.partial"))).hasSize(3);
        }
    }
    @Test void repositoryInputCoversRequiredKinds() throws Exception {
        List<String> ids = new ArrayList<>(); String text = Files.readString(ROOT.resolve("eval/f0/token-golden-input-v1.jsonl"), StandardCharsets.UTF_8);
        for (String line : text.split("\n")) if (!line.isBlank()) ids.add(Json.parse(line.getBytes(StandardCharsets.UTF_8)).path("id").asText());
        assertThat(ids).hasSizeGreaterThanOrEqualTo(12).contains("TG01-KO", "TG02-EN", "TG03-DIGITS", "TG04-SPECIAL", "TG05-CONTROL", "TG06-SYSTEM-USER", "TG07-MULTI-TURN", "TG08-CLASSIFY-ASSEMBLED-G01", "TG12-NEAR-COMPOSER-LIMIT");
        assertThat(text).contains("\\u0001");
    }
}
