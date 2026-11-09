package com.nori.ai.gateway.integration.tokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * 같은 입력에서 {@link HfTokenizerCounter} 계수가 P03 공식 계수(Python HF tokenizers + 같은 pin)·GGUF 계수와 같은지 확인한다.
 * ① v2 qRef 회차(20261102T003840Z) 출력 경계 9케이스 + SPEC §14.5 보정 2문자열의 official-count.json·gguf-count.json.
 *    이 GGUF 값은 당시 qwen3:4b-instruct(Jetson) 모델의 tokenizer 다(Thinking-2507 pin 과 tokenizer.json 바이트 동일, Dev report §12.29).
 * ② Thinking-2507 v4 입력 audit(공식 계수, 렌더 문자열)의 token·usage 행. Thinking GGUF 쪽은 서버 prompt_tokens 로
 *    {@link PromptTokenCounterGoldenTest} 가 같은 행을 확인한다.
 */
class OfficialGgufCountParityTest {
    static final String DIR = "tokenizer/parity/";
    static final Map<String, String> COPY_SHA256 = Map.of(
        "official-count.json", "eb0e04856f54f608023db6bf29f20a3c07aa067ed43d5edbdfad7a6c966533c9",
        "gguf-count.json", "892dc6e3c68b85fcc46700851945d87beae22b826e1791d75dfb125001e3a519",
        "gguf-count.run-binding.json", "2aba9aae43f972ae55a11baf21a4b7fe209c5d70fe80bb480ba2f3e6d3a776ff",
        "output-boundary-cases.jsonl", "b5a31c952a124be1f809997dcf64194d9ca994799b578639637f2b8a97464962");
    /** count_tokens_qref_v2.py CALIBRATION 과 같은 문자열(SPEC §14.5 역사 예시). */
    static final Map<String, String> CALIBRATION = Map.of(
        "SPEC-14.5-OLD-EXAMPLE", "{\"t\":\"INC\",\"eqp\":\"PHOTO_001\",\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-03T23:59\",\"q\":null}",
        "SPEC-14.5-OLD-EXAMPLE-NO-Q", "{\"t\":\"INC\",\"eqp\":\"PHOTO_001\",\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-03T23:59\"}");

    private static HfTokenizerCounter tokenizer;
    @BeforeAll static void load() { tokenizer = new HfTokenizerCounter(); }
    @AfterAll static void close() { tokenizer.close(); }

    @Test void copiesAreByteIdenticalAndBoundToTheSamePinAndCases() {
        COPY_SHA256.forEach((name, sha) -> assertThat(GoldenResources.sha256(DIR + name)).as(name).isEqualTo(sha));
        JsonNode official = GoldenResources.json(DIR + "official-count.json");
        assertThat(official.path("tokenizerSha256").asText()).isEqualTo(HfTokenizerCounter.TOKENIZER_SHA256);
        assertThat(official.path("casesSha256").asText()).isEqualTo(COPY_SHA256.get("output-boundary-cases.jsonl"));
        JsonNode binding = GoldenResources.json(DIR + "gguf-count.run-binding.json");
        assertThat(binding.path("runtimeManifest").path("tokenizerSha256").asText()).isEqualTo(HfTokenizerCounter.TOKENIZER_SHA256);
        assertThat(binding.path("parameterManifest").path("generationModel").asText()).isEqualTo("qwen3:4b-instruct");
        assertThat(GoldenResources.json(DIR + "gguf-count.json").path("complete").asBoolean()).isTrue();
    }

    @Test void boundaryWireJsonAndCalibrationCountsEqualOfficialAndGgufCounts() {
        Map<String, String> texts = new HashMap<>(CALIBRATION);
        for (JsonNode row : GoldenResources.jsonLines(DIR + "output-boundary-cases.jsonl")) texts.put(row.path("caseId").asText(), row.path("expectedJson").asText());
        JsonNode official = GoldenResources.json(DIR + "official-count.json"), gguf = GoldenResources.json(DIR + "gguf-count.json");
        int compared = 0;
        for (String section : List.of("rows", "calibration")) {
            assertThat(official.path(section).size()).isEqualTo(gguf.path(section).size());
            for (int i = 0; i < official.path(section).size(); i++) {
                JsonNode o = official.path(section).get(i), g = gguf.path(section).get(i);
                String id = o.has("caseId") ? o.path("caseId").asText() : o.path("id").asText();
                assertThat(g.has("caseId") ? g.path("caseId").asText() : g.path("id").asText()).isEqualTo(id);
                int count = tokenizer.countText(texts.get(id));
                assertThat(count).as("%s 공식 계수", id).isEqualTo(o.path("officialTokens").asInt());
                assertThat(count).as("%s GGUF 계수", id).isEqualTo(g.path("ggufTokens").asInt());
                if (o.has("expectedTokens")) assertThat(count).as("%s SPEC 기록값", id).isEqualTo(o.path("expectedTokens").asInt());
                compared++;
            }
        }
        assertThat(compared).isEqualTo(11);
    }

    @Test void thinkingRenderedInputsEqualTheOfficialAuditCounts() {
        Map<String, Integer> audit = new HashMap<>();
        JsonNode file = GoldenResources.json(PromptTokenCounterGoldenTest.DIR + "input-audit-v4-v2.json");
        assertThat(file.path("renderer").asText()).isEqualTo("OLLAMA_GO_QWEN3_THINKING_V1");
        for (JsonNode row : file.path("rows")) audit.put(row.path("messagesSha256").asText(), row.path("inputTokens").asInt());
        var counter = new PromptTokenCounter(new QwenChatTemplateRenderer(), tokenizer);
        int compared = 0;
        for (String group : List.of("token", "usage")) {
            for (var entry : PromptTokenCounterGoldenTest.expandedMessages(group).entrySet()) {
                Integer official = audit.get(GoldenResources.messagesSha256(entry.getValue()));
                assertThat(official).as("%s/%s audit 행", group, entry.getKey()).isNotNull();
                assertThat(counter.count(GoldenResources.messages(entry.getValue()))).as("%s/%s", group, entry.getKey()).isEqualTo(official);
                compared++;
            }
        }
        assertThat(compared).isEqualTo(15);
    }
}
