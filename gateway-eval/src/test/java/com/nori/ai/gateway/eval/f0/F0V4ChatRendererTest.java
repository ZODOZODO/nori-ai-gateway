package com.nori.ai.gateway.eval.f0;

import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * P03 v4 ③ 렌더러 등록(eval/f0/v4/chat-renderers-v4.json, 근거 docs/gates/f0/template-review-v4.md)과 Thinking-2507 tokenizer pin 을 레포 실제 바이트로 검사합니다.
 * 기대 문자열은 저장 Go template 을 text/template 로 실행한 결과(검토 §5 Go 엔진 대조)와 같은 값입니다.
 */
class F0V4ChatRendererTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    static final String V4_REGISTRY = "eval/f0/v4/chat-renderers-v4.json";
    static final String V4_TEMPLATE_SHA = "2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9", V1_TEMPLATE_SHA = "40c21f34cf67d8c760ef72f8ad3ae5afad514299d4b06e91dd9a8d705af7b541";
    static final String RETRY = "직전 출력이 무효다(오류: USAGE_EXCEEDS_LIMIT). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다.";

    static List<Map<String,String>> messages(String... roleContent) {
        List<Map<String,String>> list = new ArrayList<>();
        for (int i = 0; i < roleContent.length; i += 2) list.add(Map.of("role", roleContent[i], "content", roleContent[i + 1]));
        return list;
    }
    static String error(Runnable call) { Throwable t = catchThrowable(call::run); assertThat(t).isInstanceOf(EvalException.class); return t.getMessage(); }

    @Test void v4RegistrySelectsGoTemplateRendererThatPadsSystemMergesUsersAndOpensThink() {
        ChatRenderers.Renderer renderer = ChatRenderers.forTemplate(ROOT.resolve(V4_REGISTRY), V4_TEMPLATE_SHA);
        assertThat(renderer.render(messages("system", "S", "user", "U")))
            .isEqualTo("<|im_start|>system\nS\n\n<|im_end|>\n<|im_start|>user\nU<|im_end|>\n<|im_start|>assistant\n<think>\n");
        assertThat(renderer.render(messages("system", "줄1\n줄2\n", "user", "{\"q\":\"설비 A\n상태\"}\n", "user", RETRY)))
            .isEqualTo("<|im_start|>system\n줄1\n줄2\n\n\n<|im_end|>\n<|im_start|>user\n{\"q\":\"설비 A\n상태\"}\n\n\n" + RETRY + "<|im_end|>\n<|im_start|>assistant\n<think>\n");
        assertThat(renderer.render(messages("user", "U only"))).isEqualTo("<|im_start|>user\nU only<|im_end|>\n<|im_start|>assistant\n<think>\n");
        assertThat(renderer.render(messages("system", "", "user", "U"))).isEqualTo("<|im_start|>user\nU<|im_end|>\n<|im_start|>assistant\n<think>\n");
        // 실제 재시도 메시지 조립(MessageAssembler.retry)도 user 두 개를 한 블록으로 합칩니다.
        List<Map<String,String>> retried = MessageAssembler.retry(messages("system", "S", "user", "U"), "USAGE_EXCEEDS_LIMIT");
        assertThat(renderer.render(retried)).isEqualTo("<|im_start|>system\nS\n\n<|im_end|>\n<|im_start|>user\nU\n\n" + RETRY + "<|im_end|>\n<|im_start|>assistant\n<think>\n");
    }

    /**
     * 마지막 user 앞 assistant 대화(token-golden TG07 다중 턴 포함). 기대 SHA 는 저장 Go template 을 Ollama v0.40.13 thinking/parser.go(filterThinkTags) 와
     * collate 와 같은 값 구성으로 text/template 실행한 결과(검토 §7 Go 엔진 대조, golang:1.24-alpine `--network none`)입니다.
     */
    @Test void v4RendererMatchesGoTemplateForAssistantTurnsBeforeTheLastUser() throws Exception {
        ChatRenderers.Renderer renderer = ChatRenderers.forTemplate(ROOT.resolve(V4_REGISTRY), V4_TEMPLATE_SHA);
        Map<String,List<Map<String,String>>> cases = new LinkedHashMap<>();
        for (var row : F0RunnerCli.messages(ROOT.resolve("eval/f0/v2/token-golden-input-v2.jsonl"), ROOT.resolve("eval/golden/tc-registry-fixture-v2.json"), ROOT.resolve("eval/f0/v2/prompts/classify-v2.txt"), ROOT.resolve("eval/golden/tc-f0-v2.jsonl"), true))
            if (row.getKey().equals("TG07-MULTI-TURN")) cases.put("d36326fe5af46d12622f8f0669ed2ec6991fa037844aacc45c11bf2a92256b05", row.getValue());
        cases.put("601fb337ac82b9097c35db933b63dba10010094845e784491ebf80fbbda79ef4", messages("system", "S", "user", "U", "assistant", "A", "user", "U2", "user", "R"));
        cases.put("5e592fcc7ada9672ed75d57c2fbd858dec7d10f8315c8e0ad97a0d97e8c7ed52", messages("user", "U", "assistant", "A1", "assistant", "A2\n", "user", "U2"));
        cases.put("2a211244ab8c5c1f67a78e8714eb8803fc1e288bc3399899a645dc3b74aa1844", messages("user", "U", "assistant", "A </think> 중간", "user", "U2"));
        cases.put("467f6e2e54c7e0d154ec4ee15c66a24397b684f6bd93006e0baa727055c945e7", messages("system", "", "user", "U", "assistant", "A", "user", "U2"));
        cases.put("85250dda15499c1479108145e778090544a0a51a2a130afb35d5735a1f4f42cc", messages("system", "S", "user", "U"));
        cases.put("f0e4226e918edac65e609e9cae90aa49b16193eb86ff7f758701781e0333051d", messages("user", "U only"));
        assertThat(cases).hasSize(7);
        cases.forEach((sha, input) -> assertThat(Json.sha(renderer.render(input).getBytes(StandardCharsets.UTF_8))).as(sha).isEqualTo(sha));
        assertThat(renderer.render(messages("system", "S", "user", "U", "assistant", "A", "user", "U2")))
            .isEqualTo("<|im_start|>system\nS\n\n<|im_end|>\n<|im_start|>user\nU<|im_end|>\n<|im_start|>assistant\nA<|im_end|>\n<|im_start|>user\nU2<|im_end|>\n<|im_start|>assistant\n<think>\n");
    }

    @Test void v4RendererRejectsInputsOutsideReviewedScope() {
        ChatRenderers.Renderer renderer = ChatRenderers.forTemplate(ROOT.resolve(V4_REGISTRY), V4_TEMPLATE_SHA);
        // 마지막이 assistant(이어 쓰기)이거나, filterThinkTags 가 내용을 바꿀 수 있는 assistant 내용(빈 값·공백 시작·'<' 시작)은 렌더하지 않습니다.
        assertThat(error(() -> renderer.render(messages("system", "S", "user", "U", "assistant", "A")))).isEqualTo("RENDERER_INPUT_UNSUPPORTED");
        for (String content : List.of("", " A", "\nA", " A", "<think>x</think>A", "<", "<th"))
            assertThat(error(() -> renderer.render(messages("user", "U", "assistant", content, "user", "U2")))).as(content).isEqualTo("RENDERER_INPUT_UNSUPPORTED");
        assertThat(error(() -> renderer.render(messages("system", "S")))).isEqualTo("RENDERER_INPUT_UNSUPPORTED");
        assertThat(error(() -> renderer.render(messages("user", "U", "system", "S")))).isEqualTo("RENDERER_INPUT_UNSUPPORTED");
        assertThat(error(() -> renderer.render(messages("system", "S", "user", "U", "tool", "T")))).isEqualTo("RENDERER_INPUT_UNSUPPORTED");
        assertThat(error(() -> renderer.render(List.of()))).isEqualTo("RENDERER_INPUT_UNSUPPORTED");
    }

    @Test void v1RegistryAndChatMlStayUnchangedAndDoNotAcceptV4Template() throws Exception {
        Path v1 = ROOT.resolve(ChatRenderers.REGISTRY);
        assertThat(Json.sha(Files.readAllBytes(v1))).isEqualTo("ba743ec75bbee567aa9c600d519b449d4192001374d0329adcd3fb5f9c5fac7e");
        assertThat(error(() -> ChatRenderers.forTemplate(v1, V4_TEMPLATE_SHA))).isEqualTo("TEMPLATE_RENDERER_UNVERIFIED");
        assertThat(error(() -> ChatRenderers.forTemplate(ROOT.resolve(V4_REGISTRY), V1_TEMPLATE_SHA))).isEqualTo("TEMPLATE_RENDERER_UNVERIFIED");
        assertThat(ChatRenderers.forTemplate(v1, V1_TEMPLATE_SHA).render(messages("system", "S", "user", "U", "user", "R")))
            .isEqualTo("<|im_start|>system\nS<|im_end|>\n<|im_start|>user\nU<|im_end|>\n<|im_start|>user\nR<|im_end|>\n<|im_start|>assistant\n");
    }

    @Test void thinkingTokenizerPinMatchesBytesAndEqualsInstructPin() throws Exception {
        Prereg.RuntimeProfile v4 = Prereg.RuntimeProfile.INFER_V4, jetson = Prereg.RuntimeProfile.JETSON;
        byte[] thinking = Files.readAllBytes(ROOT.resolve(v4.tokenizerFile()));
        assertThat(MeasurementSession.tokenizerSha(ROOT, v4)).isEqualTo(Json.sha(thinking)).isEqualTo("aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4");
        assertThat(Files.readString(ROOT.resolve(v4.tokenizerPin()), StandardCharsets.UTF_8)).isEqualTo("aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4  qwen3-4b-thinking-2507.tokenizer.json\n");
        assertThat(thinking).isEqualTo(Files.readAllBytes(ROOT.resolve(jetson.tokenizerFile())));
        assertThat(MeasurementSession.tokenizerSha(ROOT, jetson)).isEqualTo(MeasurementSession.tokenizerSha(ROOT, v4));
    }
}
