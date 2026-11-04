package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

/** v4(2026-11-04 교체 추론 서버) 등록 분기: 런타임 profile·등록 digest preflight·등록 temperature·사고 출력 관측(원문 비저장). LLM_STUB — 실측 아님. */
class F0V4RuntimeProfileTest {
    static final String PREREG = "eval/f0/PREREG-v4-test.md", RUN = F0VersionedInputsTest.RUN, RESULTS = "eval/f0/results/" + RUN;
    static final Prereg.RuntimeProfile V4 = Prereg.RuntimeProfile.INFER_V4;
    @TempDir Path directory;

    /** 시험 v2 등록 입력을 그대로 쓰고 tokenizer 쌍·audit tokenizer SHA·런타임 parameters 만 v4 로 바꿉니다. edit 는 parameters 를 고친 뒤 harnessDigest 를 계산합니다. */
    static Prereg.Document v4(F0TestRoot f, Consumer<ObjectNode> edit) throws Exception {
        ObjectNode block = (ObjectNode)F0VersionedInputsTest.versioned(f).block().deepCopy();
        f.write(V4.tokenizerFile(), "{\"fixture\":\"시험용 Thinking-2507 바이트 — 공식 tokenizer 아님\"}");
        f.write(V4.tokenizerPin(), Json.sha(Files.readAllBytes(f.root.resolve(V4.tokenizerFile()))) + "  qwen3-4b-thinking-2507.tokenizer.json\n");
        ObjectNode audit = (ObjectNode)f.json(F0VersionedInputsTest.AUDIT); audit.put("tokenizerSha256", MeasurementSession.tokenizerSha(f.root, V4));
        f.write(F0VersionedInputsTest.AUDIT, Json.canonicalString(audit));
        ArrayNode inputs = Json.MAPPER.createArrayNode();
        for (JsonNode input : block.path("inputs")) {
            String file = input.path("path").asText();
            if (file.equals(MeasurementSession.TOKENIZER_FILE) || file.equals(MeasurementSession.TOKENIZER_PIN)) continue;
            inputs.addObject().put("path", file).put("sha256", Json.sha(Json.bytes(f.root.resolve(file))));
        }
        f.copy(V4.inputAuditCounter());
        for (String file : List.of(V4.tokenizerFile(), V4.tokenizerPin(), V4.inputAuditCounter())) inputs.addObject().put("path", file).put("sha256", Json.sha(Json.bytes(f.root.resolve(file))));
        block.set("inputs", inputs);
        block.put("schema", "f0-prereg-v4"); block.put("purpose", "DIAGNOSTIC");
        ObjectNode p = (ObjectNode)block.path("parameters");
        p.put("responseFormat", "NONE"); p.put("generationModel", V4.generationModel()); p.put("generationModelDigest", OllamaStub.GEN_DIGEST); p.put("contextTokens", V4.contextTokens());
        p.putObject("maxTokens").put("classify", 5120).put("plan", 5120); p.put("outputLimitTokens", 192); p.putObject("answerLimitTokens").put("classify", 192).put("plan", 256);
        edit.accept(p);
        write(f, block);
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(PREREG))));
        write(f, block);
        return Prereg.parse(f.root.resolve(PREREG));
    }
    static void write(F0TestRoot f, JsonNode block) throws Exception { f.write(PREREG, "# v4\n\n```json\n" + Json.canonicalString(block) + "\n```\n"); }
    static Prereg.Document parseBlock(JsonNode block) { return Prereg.parseBytes(("# 시험\n\n```json\n" + Json.canonicalString(block) + "\n```\n").getBytes(StandardCharsets.UTF_8)); }
    static String error(Runnable call) { Throwable t = catchThrowable(call::run); assertThat(t).isInstanceOf(EvalException.class); return t.getMessage(); }
    /** 새 서버 /api/ps·/api/tags 모양(qwen3:4b + bge-m3). */
    static JsonNode ps(int context, String digest) {
        ObjectNode root = Json.MAPPER.createObjectNode(); var models = root.putArray("models");
        models.addObject().put("name", "qwen3:4b").put("digest", digest).put("context_length", context).put("expires_at", OllamaStub.SENTINEL);
        models.addObject().put("name", "bge-m3:latest").put("digest", OllamaStub.EMB_DIGEST).put("context_length", 8192);
        return root;
    }
    static JsonNode tags(String digest) {
        ObjectNode root = Json.MAPPER.createObjectNode(); var models = root.putArray("models");
        models.addObject().put("name", "qwen3:4b").put("digest", digest); models.addObject().put("name", "bge-m3:latest").put("digest", OllamaStub.EMB_DIGEST);
        return root;
    }
    static void serve(OllamaStub stub, int context, String digest) { stub.ps = ps(context, digest); stub.tags = tags(digest); }
    /** 정답 응답에 사고문 sentinel 별도 필드와 reasoning_tokens 77 을 더합니다(원문 비저장 시험). completion_tokens 는 사고를 포함하도록(OpenAI 호환 정의) 77 을 더합니다. */
    static java.util.function.Function<JsonNode,JsonNode> withThinking(java.util.function.Function<JsonNode,JsonNode> answer) {
        return body -> {
            ObjectNode response = (ObjectNode)answer.apply(body).deepCopy();
            return withUsage(response, 77, response.path("usage").path("completion_tokens").asInt());
        };
    }
    /** 사고 reasoning 토큰과 답변 토큰을 따로 정한 응답: completion_tokens = reasoning + answer, 사고문은 별도 필드(sentinel). */
    static ObjectNode withUsage(ObjectNode response, int reasoning, int answer) {
        ((ObjectNode)response.path("choices").get(0).path("message")).put("reasoning", OllamaStub.SENTINEL + " 사고문");
        ObjectNode usage = (ObjectNode)response.path("usage"); usage.put("completion_tokens", reasoning + answer);
        usage.putObject("completion_tokens_details").put("reasoning_tokens", reasoning);
        return response;
    }
    static OllamaChatClient.ChatResult result(String content, int completion, OllamaChatClient.ThinkingObservation thinking) { return new OllamaChatClient.ChatResult(content, "stop", 300, completion, thinking); }
    static final String VALID_CLASSIFY = "{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null}";
    static int runB(F0TestRoot f, OllamaStub stub, Prereg.Document doc) {
        return f.exec("run", "--prereg", PREREG, "--path", "B", "--endpoint", stub.endpoint(), "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"),
            "--repeats", String.valueOf(doc.repeats()), "--run-id", RUN, "--out", RESULTS + "/B");
    }

    @Test void jetsonRegistrationsKeepTheirRuntimeAndV4UsesTheReplacementServerProfile() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        Prereg.Document v2 = F0VersionedInputsTest.versioned(f);
        assertThat(v2.profile()).isEqualTo(Prereg.RuntimeProfile.JETSON); assertThat(v2.contextTokens()).isEqualTo(4096); assertThat(v2.generationModelDigest()).isNull();
        assertThat(v2.budget()).isEqualTo(Prereg.Budget.V2); assertThat(Prereg.SUPPORT_INPUTS).contains("eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json");
        Prereg.Document doc = v4(f, p -> {});
        assertThat(doc.v4()).isTrue(); assertThat(doc.versioned()).isTrue(); assertThat(doc.diagnostic()).isTrue();
        assertThat(doc.profile()).isEqualTo(V4); assertThat(doc.contextTokens()).isEqualTo(8192); assertThat(doc.generationModel()).isEqualTo("qwen3:4b");
        assertThat(doc.generationModelDigest()).isEqualTo(OllamaStub.GEN_DIGEST);
        // 진단 규칙: 요청 max_tokens = 8192 − 입력 상한, 경계 판정 상한 = 답변(wire) 192.
        assertThat(doc.budget()).isEqualTo(new Prereg.Budget(3072, 3072, 5120, 5120, 192));
    }

    @Test void v4RegistrationRulesRejectJetsonValuesGateAndUnregisteredTemperatures() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        JsonNode valid = v4(f, p -> {}).block();
        assertThat(parseBlock(valid).v4()).isTrue();
        Map<String,Consumer<ObjectNode>> invalid = new LinkedHashMap<>();
        invalid.put("GATE 목적(사고 상한 미확정)", b -> b.put("purpose", "GATE"));
        invalid.put("Jetson 모델", b -> ((ObjectNode)b.path("parameters")).put("generationModel", F0RunnerCli.GENERATION_MODEL));
        invalid.put("context 4096", b -> ((ObjectNode)b.path("parameters")).put("contextTokens", 4096));
        invalid.put("digest 없음", b -> ((ObjectNode)b.path("parameters")).remove("generationModelDigest"));
        invalid.put("digest 앞자리만", b -> ((ObjectNode)b.path("parameters")).put("generationModelDigest", "359d7dd4bcda"));
        invalid.put("temperature 0.7", b -> ((ObjectNode)b.path("parameters")).put("temperature", 0.7));
        invalid.put("요청 max_tokens 192", b -> ((ObjectNode)b.path("parameters")).putObject("maxTokens").put("classify", 192).put("plan", 256));
        invalid.put("답변 상한 없음", b -> ((ObjectNode)b.path("parameters")).remove("answerLimitTokens"));
        invalid.put("Jetson tokenizer 만 등록", b -> {
            ArrayNode inputs = Json.MAPPER.createArrayNode();
            for (JsonNode input : b.path("inputs")) if (!input.path("path").asText().contains("thinking")) inputs.add(input);
            inputs.addObject().put("path", MeasurementSession.TOKENIZER_FILE).put("sha256", "0".repeat(64)); inputs.addObject().put("path", MeasurementSession.TOKENIZER_PIN).put("sha256", "0".repeat(64));
            b.set("inputs", inputs);
        });
        for (var entry : invalid.entrySet()) {
            ObjectNode block = valid.deepCopy(); entry.getValue().accept(block);
            assertThat(error(() -> parseBlock(block))).as(entry.getKey()).isEqualTo("PREREG_INVALID");
        }
        ObjectNode warm = valid.deepCopy(); ((ObjectNode)warm.path("parameters")).put("temperature", 0.6);
        assertThat(parseBlock(warm).temperature().asDouble()).isEqualTo(0.6);
        // v1~v3 은 새 v4 키를 받지 않고 temperature 0 만 허용합니다(기존 등록 의미 불변).
        ObjectNode v3 = (ObjectNode)F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", "NONE").block().deepCopy();
        ObjectNode withDigest = v3.deepCopy(); ((ObjectNode)withDigest.path("parameters")).put("generationModelDigest", OllamaStub.GEN_DIGEST);
        ObjectNode withAnswer = v3.deepCopy(); ((ObjectNode)withAnswer.path("parameters")).putObject("answerLimitTokens").put("classify", 192).put("plan", 256);
        ObjectNode v3Warm = v3.deepCopy(); ((ObjectNode)v3Warm.path("parameters")).put("temperature", 0.6);
        for (ObjectNode block : List.of(withDigest, withAnswer, v3Warm)) assertThat(error(() -> parseBlock(block))).isEqualTo("PREREG_INVALID");
    }

    @Test void v4RunSendsRegisteredModelBudgetAndTemperatureAndRecordsThinkingWithoutRawText() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v4(f, p -> p.put("temperature", 0.6));
        try (OllamaStub stub = new OllamaStub()) {
            serve(stub, 8192, OllamaStub.GEN_DIGEST); stub.chat = withThinking(StubAnswers.correct(f.root));
            assertThat(runB(f, stub, doc)).as(f.lastError).isZero();
            List<OllamaStub.Request> chats = F0StructuredOutputDiagnosticTest.chats(stub);
            assertThat(chats).isNotEmpty().allSatisfy(r -> {
                assertThat(GoldenSet.keys(r.body())).isEqualTo(F0StructuredOutputDiagnosticTest.LEGACY_BODY);
                assertThat(r.body().path("model").asText()).isEqualTo("qwen3:4b");
                assertThat(r.body().path("max_tokens").asInt()).isEqualTo(5120);
                assertThat(r.body().path("temperature").asDouble()).isEqualTo(0.6);
            });
        }
        JsonNode results = f.json(RESULTS + "/B/f0-results.json");
        assertThat(results.path("complete").asBoolean()).isTrue(); assertThat(results.path("maxTokens").asInt()).isEqualTo(5120);
        for (JsonNode row : results.path("attempts")) {
            assertThat(row.path("status").asText()).isEqualTo("OK");
            JsonNode observations = row.path("thinkingObservations");
            assertThat(observations).hasSize(row.path("completionTokens").size());
            JsonNode first = observations.get(0);
            assertThat(GoldenSet.keys(first)).isEqualTo(Set.of("separateFields", "reasoningTokens", "contentThinkOpen", "contentThinkClose", "thinkingCodepoints", "contentCodepoints"));
            assertThat(first.path("separateFields")).containsExactly(Json.MAPPER.getNodeFactory().textNode("reasoning"));
            assertThat(first.path("reasoningTokens").asInt()).isEqualTo(77);
            assertThat(first.path("contentThinkOpen").asBoolean()).isFalse(); assertThat(first.path("contentThinkClose").asBoolean()).isFalse();
        }
        JsonNode binding = f.json(RESULTS + "/B/run-binding.json");
        assertThat(binding.path("runtimeManifest").path("tokenizerSha256").asText()).isEqualTo(MeasurementSession.tokenizerSha(f.root, V4));
        assertThat(binding.path("runtimeManifest").path("modelDigest").asText()).isEqualTo(OllamaStub.GEN_DIGEST);
        // 사고문 원문(sentinel)은 결과·binding·stdout 어디에도 남지 않습니다.
        assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void thinkingMixedIntoContentIsAnInvalidAnswerAndIsCountedOnlyAsMarkers() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v4(f, p -> {});
        try (OllamaStub stub = new OllamaStub()) {
            serve(stub, 8192, OllamaStub.GEN_DIGEST);
            var correct = StubAnswers.correct(f.root);
            stub.chat = body -> {
                ObjectNode response = (ObjectNode)correct.apply(body).deepCopy(); ObjectNode message = (ObjectNode)response.path("choices").get(0).path("message");
                message.put("content", OllamaStub.SENTINEL + "</think>" + message.path("content").asText());
                return response;
            };
            assertThat(runB(f, stub, doc)).isZero();
            assertThat(F0StructuredOutputDiagnosticTest.chats(stub)).allSatisfy(r -> assertThat(r.body().path("temperature").asInt(-1)).isZero());
        }
        for (JsonNode row : f.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            assertThat(row.path("status").asText()).isNotEqualTo("OK");
            assertThat(row.path("thinkingObservations")).allSatisfy(o -> {
                assertThat(o.path("separateFields")).isEmpty(); assertThat(o.path("reasoningTokens").isNull()).isTrue();
                assertThat(o.path("contentThinkOpen").asBoolean()).isFalse(); assertThat(o.path("contentThinkClose").asBoolean()).isTrue();
            });
        }
        assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void preflightRequiresRegisteredDigestAnd8192BeforeAnyInference() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v4(f, p -> {});
        try (OllamaStub stub = new OllamaStub()) {
            // 태그와 상주 digest 는 서로 같지만 등록값과 다름(다른 소비자의 pull 로 같은 태그가 바뀐 경우).
            serve(stub, 8192, "3".repeat(64));
            assertThat(runB(f, stub, doc)).isEqualTo(6); assertThat(f.lastError).isEqualTo("MODEL_DIGEST_MISMATCH");
            serve(stub, 4096, OllamaStub.GEN_DIGEST);
            assertThat(runB(f, stub, doc)).isEqualTo(4); assertThat(f.lastError).isEqualTo("CONTEXT_LENGTH_MISMATCH");
            assertThat(stub.inference()).isEmpty();
            assertThat(f.root.resolve(RESULTS + "/B/run-binding.json")).doesNotExist();
            serve(stub, 8192, "3".repeat(64));
            assertThat(f.exec("preflight", "--prereg", PREREG, "--endpoint", stub.endpoint(), "--out", RESULTS + "/preflight.json")).isEqualTo(4);
            assertThat(f.json(RESULTS + "/preflight.json").path("reason").asText()).isEqualTo("MODEL_DIGEST_MISMATCH");
            assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL);
        }
        // v1~v3 경로는 등록 digest 없이 기존 검사 그대로입니다(4096·태그=상주).
        try (OllamaStub stub = new OllamaStub()) {
            OllamaChatClient client = new OllamaChatClient(stub.endpoint(), Duration.ofSeconds(5));
            assertThat(Preflight.run(client, F0RunnerCli.GENERATION_MODEL, F0RunnerCli.EMBEDDING_MODEL).summary().ok()).isTrue();
        }
    }

    @Test void chatTemplateUsesTheV4ModelAndProbeG08StaysV3Only() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v4(f, p -> p.put("repeats", 1));
        try (OllamaStub stub = new OllamaStub()) {
            serve(stub, 8192, OllamaStub.GEN_DIGEST);
            assertThat(f.exec("chat-template", "--prereg", PREREG, "--endpoint", stub.endpoint(), "--model", F0RunnerCli.GENERATION_MODEL, "--out", doc.runtimeCaptureDir())).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("ARGUMENT_INVALID");
            assertThat(f.exec("chat-template", "--prereg", PREREG, "--endpoint", stub.endpoint(), "--model", "qwen3:4b", "--out", doc.runtimeCaptureDir())).as(f.lastError).isZero();
            assertThat(f.root.resolve(doc.runtimeCaptureDir() + "/qwen3-4b.template")).hasContent(OllamaStub.DEFAULT_TEMPLATE);
            assertThat(f.root.resolve(doc.runtimeCaptureDir() + "/qwen3-4b-instruct.template")).doesNotExist();
            assertThat(f.exec("probe-g08", "--prereg", PREREG, "--endpoint", stub.endpoint(), "--probe", "eval/f0/v3/probe-g08/probe-g08-v1.json", "--run-id", RUN, "--out", RESULTS + "/probe")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("PREREG_INVALID");
            assertThat(stub.inference()).isEmpty();
        }
    }

    @Test void answerTokensOverTheAnswerLimitOrUncountableAreFailuresWhileLegacyJudgeIsUnchanged() {
        QRefContext refs = QRefContext.extract("PHOTO_001 장애 원인");
        MessageAssembler.Kind classify = MessageAssembler.Kind.CLASSIFY;
        var separated = new OllamaChatClient.ThinkingObservation(List.of("reasoning"), 900, false, false, 2400, 60);
        // 사고 900 은 답변 상한에 넣지 않습니다: 답변 192 = 상한 안, 193 = 초과(SPEC §14.5 "답변 상한 초과 = 실패").
        assertThat(ClassificationJudge.judge(result(VALID_CLASSIFY, 900 + 192, separated), 5120, classify, true, "Asia/Seoul", refs, 192).valid()).isTrue();
        assertThat(ClassificationJudge.judge(result(VALID_CLASSIFY, 900 + 193, separated), 5120, classify, true, "Asia/Seoul", refs, 192).errorCode()).isEqualTo(ClassificationJudge.ANSWER_LIMIT_EXCEEDED);
        assertThat(ClassificationJudge.answerTokens(result(VALID_CLASSIFY, 900 + 193, separated))).isEqualTo(193);
        // 계수 불가(fail-closed): 사고 토큰 수 보고 없음 · content 에 사고 표지 · 사고 토큰 > completion · 관측 없음.
        List<OllamaChatClient.ChatResult> uncountable = List.of(
            result(VALID_CLASSIFY, 40, new OllamaChatClient.ThinkingObservation(List.of("reasoning"), null, false, false, 100, 60)),
            result(VALID_CLASSIFY, 40, new OllamaChatClient.ThinkingObservation(List.of(), 10, false, true, null, 70)),
            result(VALID_CLASSIFY, 40, new OllamaChatClient.ThinkingObservation(List.of("reasoning"), 41, false, false, 100, 60)),
            result(VALID_CLASSIFY, 40, null));
        for (OllamaChatClient.ChatResult r : uncountable) {
            assertThat(ClassificationJudge.answerTokens(r)).isNull();
            assertThat(ClassificationJudge.judge(r, 5120, classify, true, "Asia/Seoul", refs, 192).errorCode()).isEqualTo(ClassificationJudge.ANSWER_TOKENS_UNCOUNTABLE);
            // v1~v3(답변 상한 없음)은 같은 응답을 기존 규칙대로 유효로 판정합니다.
            assertThat(ClassificationJudge.judge(r, 5120, classify, true, "Asia/Seoul", refs).valid()).isTrue();
        }
        // 길이·종료 검사는 답변 상한보다 먼저입니다(사고 도중 상한 도달 = 기존 FINISH_LENGTH).
        OllamaChatClient.ChatResult cut = new OllamaChatClient.ChatResult("", "length", 300, 5120, separated);
        assertThat(ClassificationJudge.judge(cut, 5120, classify, true, "Asia/Seoul", refs, 192).errorCode()).isEqualTo("FINISH_LENGTH");
        assertThat(ClassificationJudge.RETRY_ERROR_CODES).doesNotContain(ClassificationJudge.ANSWER_LIMIT_EXCEEDED, ClassificationJudge.ANSWER_TOKENS_UNCOUNTABLE);
    }

    @Test void v4RunRetriesAnswerLimitFailuresOnceWithTheAuditedRetryMessageAndRecordsCallWindows() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v4(f, p -> {});
        int questions;
        try (OllamaStub stub = new OllamaStub()) {
            serve(stub, 8192, OllamaStub.GEN_DIGEST); var correct = StubAnswers.correct(f.root);
            stub.chat = body -> withUsage((ObjectNode)correct.apply(body).deepCopy(), 900, 193);
            // 재시도 메시지가 사전 audit 에 없으면 INPUT_AUDIT_MISSING(exit 2)이므로 exit 0 자체가 audit 호환 확인입니다.
            assertThat(runB(f, stub, doc)).as(f.lastError).isZero();
            List<OllamaStub.Request> chats = F0StructuredOutputDiagnosticTest.chats(stub);
            questions = f.json(RESULTS + "/B/f0-results.json").path("attempts").size();
            assertThat(chats).hasSize(2 * questions);
            List<String> raw = chats.stream().map(r -> new String(r.raw(), StandardCharsets.UTF_8)).toList();
            assertThat(raw.stream().filter(s -> s.contains("오류: USAGE_EXCEEDS_LIMIT"))).hasSize(questions);
            assertThat(raw).noneMatch(s -> s.contains("ANSWER_"));
        }
        java.time.Instant before = java.time.Instant.now();
        for (JsonNode row : f.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            assertThat(row.path("status").asText()).isEqualTo("INVALID");
            assertThat(row.path("errorCodes")).extracting(JsonNode::asText).containsExactly(ClassificationJudge.ANSWER_LIMIT_EXCEEDED, ClassificationJudge.ANSWER_LIMIT_EXCEEDED);
            assertThat(row.path("answerTokens")).extracting(JsonNode::asInt).containsExactly(193, 193);
            assertThat(row.path("callWindows")).hasSize(2).allSatisfy(w -> {
                java.time.Instant sent = java.time.Instant.parse(w.path("sentAt").asText()), received = java.time.Instant.parse(w.path("receivedAt").asText());
                assertThat(w.path("sentAt").asText()).endsWith("Z"); assertThat(sent).isBeforeOrEqualTo(received).isBeforeOrEqualTo(before);
            });
        }
        assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
        // 답변 192(상한 안)이면 같은 사고 길이에서도 유효합니다.
        F0TestRoot g = new F0TestRoot(directory.resolve("within")); Prereg.Document within = v4(g, p -> {});
        try (OllamaStub stub = new OllamaStub()) {
            serve(stub, 8192, OllamaStub.GEN_DIGEST); var correct = StubAnswers.correct(g.root);
            stub.chat = body -> withUsage((ObjectNode)correct.apply(body).deepCopy(), 900, 192);
            assertThat(runB(g, stub, within)).as(g.lastError).isZero();
            assertThat(F0StructuredOutputDiagnosticTest.chats(stub)).hasSize(questions);
        }
        for (JsonNode row : g.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            assertThat(row.path("status").asText()).isEqualTo("OK"); assertThat(row.path("answerTokens")).extracting(JsonNode::asInt).containsExactly(192);
        }
    }

    @Test void sharedServerOverlapExcludesOnlyOverlappingLatencySamples() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v4(f, p -> {});
        try (OllamaStub stub = new OllamaStub()) {
            serve(stub, 8192, OllamaStub.GEN_DIGEST); stub.chat = withThinking(StubAnswers.correct(f.root));
            assertThat(runB(f, stub, doc)).as(f.lastError).isZero();
        }
        int rows = f.json(RESULTS + "/B/f0-results.json").path("attempts").size();
        f.write("share-none.json", "{\"schema\":\"f0-share-intervals-v1\",\"intervals\":[]}");
        f.write("share-all.json", "{\"schema\":\"f0-share-intervals-v1\",\"intervals\":[{\"start\":\"2000-01-01T00:00:00Z\",\"end\":\"2100-01-01T00:00:00Z\"}]}");
        assertThat(f.exec("share-exclude", "--prereg", PREREG, "--results", RESULTS, "--intervals", "share-none.json")).as(f.lastError).isZero();
        assertThat(f.exec("share-exclude", "--prereg", PREREG, "--results", RESULTS, "--intervals", "share-all.json")).as(f.lastError).isZero();
        List<String> lines = f.stdout.toString(StandardCharsets.UTF_8).lines().filter(l -> l.startsWith("{\"B\"")).toList();
        assertThat(lines).hasSize(2);
        JsonNode none = Json.parse(lines.get(0).getBytes(StandardCharsets.UTF_8)).path("B"), all = Json.parse(lines.get(1).getBytes(StandardCharsets.UTF_8)).path("B");
        assertThat(none.path("samples").asInt()).isEqualTo(rows); assertThat(none.path("excluded").asInt()).isZero(); assertThat(none.path("keptSamples").asInt()).isEqualTo(rows);
        assertThat(none.path("latencyP95Ms").isIntegralNumber()).isTrue(); assertThat(none.path("latencyP95MsByExpectedT")).isNotEmpty();
        assertThat(all.path("excluded").asInt()).isEqualTo(rows); assertThat(all.path("excludedRatio").asDouble()).isEqualTo(1.0);
        assertThat(all.path("keptSamples").asInt()).isZero(); assertThat(all.path("latencyP95Ms").isNull()).isTrue(); assertThat(all.path("latencyP95MsByExpectedT")).isEmpty();
        // 원시 결과는 지우거나 바꾸지 않습니다(채점 입력 그대로).
        assertThat(f.json(RESULTS + "/B/f0-results.json").path("attempts")).hasSize(rows);
        // 닫힌 구간 경계: 수신 시각과 같은 시각에 시작한 외부 요청은 겹침, 1ms 뒤는 겹치지 않음.
        JsonNode row = Json.parse("{\"latencyMs\":5,\"callWindows\":[{\"sentAt\":\"2026-11-04T00:00:00Z\",\"receivedAt\":\"2026-11-04T00:00:05Z\"}]}".getBytes(StandardCharsets.UTF_8));
        java.time.Instant end = java.time.Instant.parse("2026-11-04T00:00:05Z");
        assertThat(SharedServerOverlap.overlapped(row, List.of(new SharedServerOverlap.Interval(end, end.plusSeconds(1))))).isTrue();
        assertThat(SharedServerOverlap.overlapped(row, List.of(new SharedServerOverlap.Interval(end.plusMillis(1), end.plusSeconds(1))))).isFalse();
        // 구간 파일·결과 형식 오류와 v1~v3 등록은 집계하지 않고 거부합니다.
        f.write("share-bad.json", "{\"schema\":\"f0-share-intervals-v1\",\"intervals\":[{\"start\":\"2026-11-04T09:00:00+09:00\",\"end\":\"2026-11-04T10:00:00Z\"}]}");
        assertThat(f.exec("share-exclude", "--prereg", PREREG, "--results", RESULTS, "--intervals", "share-bad.json")).isEqualTo(2); assertThat(f.lastError).isEqualTo("SHARE_INTERVALS_INVALID");
        f.write("share-reversed.json", "{\"schema\":\"f0-share-intervals-v1\",\"intervals\":[{\"start\":\"2026-11-04T10:00:00Z\",\"end\":\"2026-11-04T09:00:00Z\"}]}");
        assertThat(f.exec("share-exclude", "--prereg", PREREG, "--results", RESULTS, "--intervals", "share-reversed.json")).isEqualTo(2); assertThat(f.lastError).isEqualTo("SHARE_INTERVALS_INVALID");
        assertThat(error(() -> SharedServerOverlap.overlapped(Json.parse("{\"latencyMs\":5}".getBytes(StandardCharsets.UTF_8)), List.of()))).isEqualTo("CALL_WINDOWS_MISSING");
        assertThat(f.exec("share-exclude", "--prereg", Prereg.DEFAULT_PATH, "--results", RESULTS, "--intervals", "share-none.json")).isEqualTo(2); assertThat(f.lastError).isEqualTo("PREREG_INVALID");
    }

    @Test void observationNeverExposesThinkingTextAndLegacyCallsKeepIntegerZeroTemperature() throws Exception {
        try (OllamaStub stub = new OllamaStub()) {
            serve(stub, 8192, OllamaStub.GEN_DIGEST);
            stub.chat = withThinking(body -> OllamaStub.chatResponse("<think>" + OllamaStub.SENTINEL + "</think>{}", "length", 10, 5120));
            OllamaChatClient client = new OllamaChatClient(stub.endpoint(), Duration.ofSeconds(5));
            Preflight.Verified verified = Preflight.run(client, "qwen3:4b", "bge-m3", 8192, OllamaStub.GEN_DIGEST).verified();
            List<Map<String,String>> messages = List.of(Map.of("role", "user", "content", "x"));
            OllamaChatClient.ChatResult result = client.chat(verified, "qwen3:4b", messages, 5120, null, Json.MAPPER.getNodeFactory().numberNode(0.6));
            String thinkingText = OllamaStub.SENTINEL + " 사고문", content = "<think>" + OllamaStub.SENTINEL + "</think>{}";
            assertThat(result.thinking()).isEqualTo(new OllamaChatClient.ThinkingObservation(List.of("reasoning"), 77, true, true, thinkingText.codePointCount(0, thinkingText.length()), content.codePointCount(0, content.length())));
            assertThat(result.toString()).doesNotContain(OllamaStub.SENTINEL); assertThat(result.thinking().toString()).doesNotContain(OllamaStub.SENTINEL);
            client.chat(verified, "qwen3:4b", messages, 192, null);
            List<OllamaStub.Request> chats = F0StructuredOutputDiagnosticTest.chats(stub);
            assertThat(new String(chats.get(0).raw(), StandardCharsets.UTF_8)).contains("\"temperature\":0.6}");
            assertThat(new String(chats.get(1).raw(), StandardCharsets.UTF_8)).contains("\"temperature\":0}");
        }
    }
}
