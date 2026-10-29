package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class UsageProbeTest {
    @TempDir Path directory;

    /** 시험 stub template 의 SHA 를 렌더러 등록 파일에 넣고 사전등록을 다시 맞춥니다(실제 Qwen template 등록 아님). */
    static F0TestRoot rendererRegistered(Path directory, OllamaStub stub) throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        f.write(ChatRenderers.REGISTRY, "{\"renderers\":[{\"templateSha256\":\"" + Json.sha(stub.template.getBytes(StandardCharsets.UTF_8)) + "\",\"renderer\":\"CHATML_NO_SYSTEM_DEFAULT\",\"reviewRef\":\"test-fixture-only\"}]}");
        f.prereg(F0TestRoot.REGISTERED);
        return f;
    }
    /** cache 모사: 직전 요청과 같은 접두부(시스템 메시지·raw 프롬프트 앞부분)를 공유하면 reduce=true 일 때 축소 값을 보고합니다. flush 는 cache 를 바꿉니다. */
    static void cacheModel(OllamaStub stub, boolean reduce) {
        String[] last = {""};
        stub.chat = body -> {
            String system = body.path("messages").get(0).path("content").asText(), user = body.path("messages").get(body.path("messages").size() - 1).path("content").asText();
            int full = 100 + user.length() % 7; boolean hit = last[0].equals(system); last[0] = system;
            return OllamaStub.chatResponse("{}", "length", reduce && hit ? 3 : full, 1);
        };
        stub.generate = body -> {
            String prompt = body.path("prompt").asText();
            if (prompt.startsWith("flush-")) { last[0] = prompt; return OllamaStub.generateResponse(4); }
            String prefix = prompt.substring(0, Math.min(prompt.length(), 200)); boolean hit = last[0].equals(prefix); last[0] = prefix;
            return OllamaStub.generateResponse(reduce && hit ? 2 : 90 + prompt.length() % 5);
        };
    }
    int probe(F0TestRoot f, OllamaStub stub, String run) {
        f.env.put("AIGW_PARENT_RUN_ID", run);
        return f.exec("usage-probe", "--endpoint", stub.endpoint(), "--messages", "eval/f0/usage-probe-input-v1.jsonl", "--out", "eval/f0/results/" + run + "/usage.json");
    }
    @Test void oracleDetectsReducedAndUnreducedCacheHitsWithFlushOrderAndAllowedFieldsOnly() throws Exception {
        for (boolean reduce : List.of(true, false)) {
            try (OllamaStub stub = new OllamaStub()) {
                F0TestRoot f = rendererRegistered(directory.resolve(reduce ? "reduce" : "plain"), stub);
                cacheModel(stub, reduce);
                assertThat(probe(f, stub, "U" + reduce)).isZero();
                JsonNode usage = f.json("eval/f0/results/U" + reduce + "/usage.json");
                assertThat(GoldenSet.keys(usage)).isSubsetOf(Set.of("runId", "repeats", "complete", "abortReason", "calls", "equalOnCacheHit"));
                assertThat(usage.path("calls")).hasSize(4);
                usage.path("calls").forEach(c -> assertThat(GoldenSet.keys(c)).isEqualTo(Set.of("id", "phase", "promptTokens", "promptEvalCount")));
                assertThat(usage.path("calls")).extracting(c -> c.path("id").asText() + "/" + c.path("phase").asText()).containsExactly("U1/cold", "U1/warm", "U2/shared", "U2/cold");
                assertThat(usage.path("equalOnCacheHit").path("usage").asBoolean()).isEqualTo(!reduce);
                assertThat(usage.path("equalOnCacheHit").path("evalCount").asBoolean()).isEqualTo(!reduce);
                List<String> sequence = stub.inference().stream().map(r -> r.path().equals("/api/generate") ? (r.body().path("prompt").asText().startsWith("flush-") ? "FLUSH" : "GEN") : "CHAT").toList();
                assertThat(sequence).containsExactly("FLUSH", "CHAT", "CHAT", "CHAT", "FLUSH", "CHAT", "FLUSH", "GEN", "GEN", "GEN", "FLUSH", "GEN");
                stub.inference().stream().filter(r -> r.path().equals("/v1/chat/completions")).forEach(r -> assertThat(r.body().path("max_tokens").asInt()).isEqualTo(1));
                String rendered = stub.inference().stream().filter(r -> r.path().equals("/api/generate") && !r.body().path("prompt").asText().startsWith("flush-")).findFirst().orElseThrow().body().path("prompt").asText();
                assertThat(rendered).startsWith("<|im_start|>system\n").endsWith("<|im_start|>assistant\n");
                assertThat(f.allOutput("eval/f0/results/U" + reduce)).doesNotContain(OllamaStub.SENTINEL).doesNotContain("PHOTO_001");
                assertThat(f.root.resolve("eval/f0/results/U" + reduce + "/usage.run-binding.json")).exists();
            }
        }
    }
    @Test void unverifiedTemplateRendererIsRefusedBeforeAnyInferenceOrBinding() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            assertThat(probe(f, stub, "U9")).isEqualTo(2); assertThat(f.lastError).isEqualTo("TEMPLATE_RENDERER_UNVERIFIED");
            assertThat(stub.inference()).isEmpty();
            assertThat(f.root.resolve("eval/f0/results/U9/usage.run-binding.json")).doesNotExist();
        }
        try (OllamaStub stub = new OllamaStub()) {
            F0TestRoot g = rendererRegistered(directory.resolve("other"), stub);
            stub.template = stub.template + " ";
            assertThat(probe(g, stub, "U8")).isEqualTo(2); assertThat(g.lastError).isEqualTo("TEMPLATE_RENDERER_UNVERIFIED");
            assertThat(stub.inference()).isEmpty();
        }
    }
    @Test void usageInputMustShareSystemPrefixAndDifferOnlyInUserMessage() {
        Map<String,List<Map<String,String>>> same = Map.of("U1", List.of(Map.of("role", "system", "content", "s"), Map.of("role", "user", "content", "a")), "U2", List.of(Map.of("role", "system", "content", "s"), Map.of("role", "user", "content", "a")));
        Map<String,List<Map<String,String>>> differentPrefix = Map.of("U1", List.of(Map.of("role", "system", "content", "s"), Map.of("role", "user", "content", "a")), "U2", List.of(Map.of("role", "system", "content", "t"), Map.of("role", "user", "content", "b")));
        for (var inputs : List.of(same, differentPrefix)) assertThatThrownBy(() -> F0RunnerCli.validateUsage(inputs)).hasMessage("USAGE_INPUT_INVALID");
    }
}
