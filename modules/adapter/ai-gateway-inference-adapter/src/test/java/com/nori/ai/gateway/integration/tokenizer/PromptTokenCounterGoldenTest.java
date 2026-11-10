package com.nori.ai.gateway.integration.tokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.budget.ChatMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * chat template 정확 golden 게이트: P03 v4 진단(20261104T044320Z-diag-v2-t0-b, Dev report §12.32)의 token-golden 13행과
 * usage-probe 4호출 모두에서 Gateway 계수 == Ollama usage.prompt_tokens 여야 한다. 하나라도 다르면 실패다.
 */
class PromptTokenCounterGoldenTest {
    static final String DIR = "tokenizer/golden/";
    /** 원본(main checkout eval/f0/results/20261104T044320Z-diag-v2-t0-b·eval/f0/chat-template/v4-diag-v2-t0-b)과 같은 바이트의 SHA-256. */
    static final Map<String, String> COPY_SHA256 = Map.of(
        "token-golden.jsonl", "d330681fa4a6f8d11374c4d685489660ecde4c7827e38efb90e6414356cf9057",
        "token-golden.run-binding.json", "6fed1c3774bea9dd9933ece1be5ef9299ae902d2fead5b4320be7bce8f85cd73",
        "usage.json", "60867b5fc65f1dcb4fcff5c8368ee40b9030a145ba32161b19e8ca2bd1796d53",
        "usage.run-binding.json", "c7b6f1ad72a4343024b37ce2f87a4108651266b57ff10666f0f96c1991ac07be",
        "qwen3-4b.template", "2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9",
        "qwen3-4b.template.sha256", "76b20be53b5874851d416f910ef32aff33273738ce7c89f47a1bb0a5cad6b60a",
        "input-audit-v4-v2.json", "65f54494df5e34a2a17c0f3ca981f8d20630a1140aa8e87fec1379427e978c5b",
        // 등록 입력(token-golden-input-v2·usage-probe-input-v2, 조립 행은 classify-v2·golden v2·fixture v2)을 F0 하네스로 펼친 파생 사본
        "p03-v4-prompt-messages.jsonl", "f2c9fe833e37b2ec06139bfd5adeed08f7172044e3b2e064fcfde5105b132438");
    static final String MODEL_DIGEST = "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7";

    private static HfTokenizerCounter tokenizer;
    private static PromptTokenCounter counter;
    private static final QwenChatTemplateRenderer renderer = new QwenChatTemplateRenderer();

    @BeforeAll static void load() { tokenizer = new HfTokenizerCounter(); counter = new PromptTokenCounter(renderer, tokenizer); }
    @AfterAll static void close() { tokenizer.close(); }

    /** id → 펼친 메시지. 각 행의 메시지 해시가 파생 사본 기록값과 같아야 한다. */
    static Map<String, JsonNode> expandedMessages(String group) {
        Map<String, JsonNode> rows = new HashMap<>();
        for (JsonNode row : GoldenResources.jsonLines(DIR + "p03-v4-prompt-messages.jsonl")) {
            assertThat(GoldenResources.messagesSha256(row.path("messages"))).as(row.path("id").asText()).isEqualTo(row.path("messagesSha256").asText());
            if (row.path("group").asText().equals(group)) rows.put(row.path("id").asText(), row.path("messages"));
        }
        return rows;
    }

    @Test void goldenCopiesAreByteIdenticalAndBoundToThePinnedTokenizerTemplateAndModel() {
        COPY_SHA256.forEach((name, sha) -> assertThat(GoldenResources.sha256(DIR + name)).as(name).isEqualTo(sha));
        assertThat(QwenChatTemplateRenderer.TEMPLATE_SHA256).isEqualTo(COPY_SHA256.get("qwen3-4b.template"));
        assertThat(new String(GoldenResources.bytes(DIR + "qwen3-4b.template.sha256")).trim()).startsWith(QwenChatTemplateRenderer.TEMPLATE_SHA256);
        for (String binding : List.of("token-golden.run-binding.json", "usage.run-binding.json")) {
            JsonNode runtime = GoldenResources.json(DIR + binding).path("runtimeManifest");
            assertThat(runtime.path("tokenizerSha256").asText()).isEqualTo(HfTokenizerCounter.TOKENIZER_SHA256);
            assertThat(runtime.path("templateSha256").asText()).isEqualTo(QwenChatTemplateRenderer.TEMPLATE_SHA256);
            assertThat(runtime.path("modelDigest").asText()).isEqualTo(MODEL_DIGEST);
            assertThat(GoldenResources.json(DIR + binding).path("parameterManifest").path("contextTokens").asInt()).isEqualTo(8_192);
        }
        assertThat(GoldenResources.json(DIR + "usage.json").path("equalOnCacheHit").path("usage").asBoolean()).isTrue();
    }

    @Test void everyTokenGoldenRowCountsExactlyTheServerPromptTokens() {
        Map<String, JsonNode> messages = expandedMessages("token");
        List<JsonNode> golden = GoldenResources.jsonLines(DIR + "token-golden.jsonl");
        assertThat(golden).hasSize(13);
        assertThat(messages.keySet()).containsExactlyInAnyOrderElementsOf(golden.stream().map(g -> g.path("id").asText()).toList());
        for (JsonNode row : golden) {
            String id = row.path("id").asText();
            JsonNode input = messages.get(id);
            assertThat(GoldenResources.messagesSha256(input)).as("%s 입력 결속", id).isEqualTo(row.path("messagesSha256").asText());
            assertThat(row.path("templateSha256").asText()).isEqualTo(QwenChatTemplateRenderer.TEMPLATE_SHA256);
            assertThat(row.path("modelDigest").asText()).isEqualTo(MODEL_DIGEST);
            assertThat(counter.count(GoldenResources.messages(input))).as("%s 계수 == usage.prompt_tokens", id).isEqualTo(row.path("promptTokens").asInt());
        }
    }

    @Test void everyUsageProbeCallCountsExactlyTheServerPromptTokensAndEvalCount() {
        Map<String, JsonNode> messages = expandedMessages("usage");
        Map<String, String> auditSha = new HashMap<>();
        for (JsonNode row : GoldenResources.json(DIR + "input-audit-v4-v2.json").path("rows")) auditSha.put(row.path("id").asText(), row.path("messagesSha256").asText());
        JsonNode calls = GoldenResources.json(DIR + "usage.json").path("calls");
        assertThat(calls).hasSize(4);
        for (JsonNode call : calls) {
            String id = call.path("id").asText();
            assertThat(GoldenResources.messagesSha256(messages.get(id))).as("%s 입력 결속", id).isEqualTo(auditSha.get("usage/" + id));
            int count = counter.count(GoldenResources.messages(messages.get(id)));
            assertThat(count).as("%s/%s usage.prompt_tokens", id, call.path("phase").asText()).isEqualTo(call.path("promptTokens").asInt());
            assertThat(count).as("%s/%s prompt_eval_count", id, call.path("phase").asText()).isEqualTo(call.path("promptEvalCount").asInt());
        }
    }

    @Test void goldenDetectsATemplateSerializationThatDiffersOnlyInTheThinkPrefix() {
        // 생성 접두부 "<think>\n" 만 뺀 렌더는 모든 golden 행에서 서버 계수와 달라야 한다(게이트가 실제로 차이를 잡는지 확인).
        Map<String, JsonNode> messages = expandedMessages("token");
        for (JsonNode row : GoldenResources.jsonLines(DIR + "token-golden.jsonl")) {
            List<ChatMessage> input = GoldenResources.messages(messages.get(row.path("id").asText()));
            String rendered = renderer.render(input);
            String withoutThink = rendered.substring(0, rendered.length() - "<think>\n".length());
            assertThat(tokenizer.countText(withoutThink)).as(row.path("id").asText()).isNotEqualTo(row.path("promptTokens").asInt());
        }
    }
}
