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
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;

/**
 * v4 답변 토큰 계수 방법 (b)(2026-11-04 사용자 결정): 등록 opt-in(answerTokenMethod, 계수기 바이트 등록 결속), 측정 세션의 계수기 프로세스(stdin 한 줄 송신·READY 의
 * tokenizer SHA 확인·추론 호출 전 실패), 답변 상한 판정(로컬 답변 계수 > 상한 = 실패·1회 재시도), 시도별 교차 확인 차이 기록과 진단 보고서 절, 원문 비기록.
 * 계수기는 같은 줄 규약의 시험 프로세스(FakeAnswerCounter, 코드포인트 계수)이며 공식 tokenizer 계수 정확성은 eval/py/tests/test_count_answer_tokens_v4.py 가
 * 실제 pin 으로 확인합니다. LLM_STUB — 실측 아님.
 */
class F0V4AnswerTokenCountTest {
    static final String PREREG = F0V4RuntimeProfileTest.PREREG, RUN = F0V4RuntimeProfileTest.RUN, RESULTS = F0V4RuntimeProfileTest.RESULTS;
    /** 사고문(별도 reasoning 필드): sentinel·한글·보조 평면 문자(🙂)를 넣어 비 ASCII 이스케이프 송신과 원문 비기록을 함께 봅니다. */
    static final String THINKING = OllamaStub.SENTINEL + " 사고문 🙂";
    /** 서버 completion_tokens = 사고 + 답변 + 이 상수(`</think>` 같은 특수 토큰 재현) — 교차 확인 차이의 기대값. */
    static final int SPECIAL = 1;
    @TempDir Path directory;

    static int codepoints(String text) { return text.codePointCount(0, text.length()); }
    /** 방법 (b) 등록: 시험 v4 등록에 계수기 입력과 answerTokenMethod 만 더하고 harnessDigest 를 다시 계산합니다. */
    static Prereg.Document methodB(F0TestRoot f, Consumer<ObjectNode> edit) throws Exception {
        ObjectNode block = (ObjectNode)F0V4RuntimeProfileTest.v4(f, edit).block().deepCopy();
        f.copy(AnswerTokenCounter.SCRIPT);
        ((ArrayNode)block.path("inputs")).addObject().put("path", AnswerTokenCounter.SCRIPT).put("sha256", Json.sha(Json.bytes(f.root.resolve(AnswerTokenCounter.SCRIPT))));
        ((ObjectNode)block.path("parameters")).put("answerTokenMethod", ClassificationJudge.ANSWER_METHOD_LOCAL_PIN);
        F0V4RuntimeProfileTest.write(f, block);
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(PREREG))));
        F0V4RuntimeProfileTest.write(f, block);
        return Prereg.parse(f.root.resolve(PREREG));
    }
    /** 정답 응답에 사고문 필드를 넣고 completion_tokens = 사고 코드포인트 + 답변 코드포인트 + SPECIAL 로 둡니다(reasoning_tokens 미보고 — Ollama 0.40.13 실측과 같음). */
    static Function<JsonNode,JsonNode> answering(Function<JsonNode,JsonNode> answer, Function<String,String> content) {
        return body -> {
            ObjectNode response = (ObjectNode)answer.apply(body).deepCopy();
            ObjectNode message = (ObjectNode)response.path("choices").get(0).path("message");
            String text = content.apply(message.path("content").asText()); message.put("content", text); message.put("reasoning", THINKING);
            ((ObjectNode)response.path("usage")).put("completion_tokens", codepoints(THINKING) + codepoints(text) + SPECIAL);
            return response;
        };
    }
    static String error(Runnable call) { Throwable t = catchThrowable(call::run); assertThat(t).isInstanceOf(EvalException.class); return t.getMessage(); }

    @Test void methodBUsesTheLocalAnswerCountWhileRegistrationsWithoutTheKeyKeepMethodA() {
        QRefContext refs = QRefContext.extract("PHOTO_001 장애 원인");
        MessageAssembler.Kind classify = MessageAssembler.Kind.CLASSIFY;
        String valid = F0V4RuntimeProfileTest.VALID_CLASSIFY;
        // reasoning_tokens 미보고(실측 형태)여도 로컬 답변 계수로 판정합니다: 192 = 상한 안, 193 = 초과.
        var observed = new OllamaChatClient.ThinkingObservation(List.of("reasoning"), null, false, false, 2400, codepoints(valid));
        Function<Integer,OllamaChatClient.ChatResult> local = answer -> new OllamaChatClient.ChatResult(valid, "stop", 300, 1000, observed, new OllamaChatClient.LocalTokenCount(answer, 800, 1000 - 800 - (answer == null ? 0 : answer)));
        assertThat(ClassificationJudge.judge(local.apply(192), 5120, classify, true, "Asia/Seoul", refs, 192).valid()).isTrue();
        assertThat(ClassificationJudge.judge(local.apply(193), 5120, classify, true, "Asia/Seoul", refs, 192).errorCode()).isEqualTo(ClassificationJudge.ANSWER_LIMIT_EXCEEDED);
        assertThat(ClassificationJudge.answerTokens(local.apply(193))).isEqualTo(193);
        // 로컬 계수 불가·content 사고 표지 = 계수 불가 실패(fail-closed).
        assertThat(ClassificationJudge.judge(local.apply(null), 5120, classify, true, "Asia/Seoul", refs, 192).errorCode()).isEqualTo(ClassificationJudge.ANSWER_TOKENS_UNCOUNTABLE);
        var marked = new OllamaChatClient.ThinkingObservation(List.of("reasoning"), null, false, true, 2400, 60);
        OllamaChatClient.ChatResult mixed = new OllamaChatClient.ChatResult(valid, "stop", 300, 1000, marked, new OllamaChatClient.LocalTokenCount(10, 800, 190));
        assertThat(ClassificationJudge.answerTokens(mixed)).isNull();
        // 로컬 계수가 없는 응답(방법 a 등록)은 기존대로 completion − reasoning_tokens 이고, 미보고면 계수 불가입니다.
        var reported = new OllamaChatClient.ThinkingObservation(List.of("reasoning"), 900, false, false, 2400, 60);
        assertThat(ClassificationJudge.answerTokens(new OllamaChatClient.ChatResult(valid, "stop", 300, 900 + 193, reported))).isEqualTo(193);
        assertThat(ClassificationJudge.answerTokens(new OllamaChatClient.ChatResult(valid, "stop", 300, 1000, observed))).isNull();
        // 교차 확인 차이 = completion − (사고 + 답변), 한쪽이라도 셀 수 없으면 null.
        assertThat(OllamaChatClient.LocalTokenCount.of(new AnswerTokenCounter.Counts(60, 900), 961)).isEqualTo(new OllamaChatClient.LocalTokenCount(60, 900, 1));
        assertThat(OllamaChatClient.LocalTokenCount.of(new AnswerTokenCounter.Counts(60, null), 961).completionMinusLocal()).isNull();
    }

    @Test void answerTokenMethodIsAV4OptInThatMustRegisterTheCounterBytes() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        Prereg.Document legacy = F0V4RuntimeProfileTest.v4(f, p -> {});
        assertThat(legacy.answerTokenMethod()).isEqualTo(ClassificationJudge.ANSWER_METHOD_USAGE_REASONING); assertThat(legacy.localAnswerCount()).isFalse();
        Prereg.Document doc = methodB(f, p -> {});
        assertThat(doc.answerTokenMethod()).isEqualTo(ClassificationJudge.ANSWER_METHOD_LOCAL_PIN); assertThat(doc.localAnswerCount()).isTrue();
        assertThat(doc.inputs()).containsKey(AnswerTokenCounter.SCRIPT);
        JsonNode valid = doc.block();
        Map<String,Consumer<ObjectNode>> invalid = new LinkedHashMap<>();
        invalid.put("계수기 미등록", b -> {
            ArrayNode inputs = Json.MAPPER.createArrayNode();
            for (JsonNode input : b.path("inputs")) if (!input.path("path").asText().equals(AnswerTokenCounter.SCRIPT)) inputs.add(input);
            b.set("inputs", inputs);
        });
        invalid.put("방법 (a) 값을 등록 값으로", b -> ((ObjectNode)b.path("parameters")).put("answerTokenMethod", ClassificationJudge.ANSWER_METHOD_USAGE_REASONING));
        invalid.put("미정의 값", b -> ((ObjectNode)b.path("parameters")).put("answerTokenMethod", "local_tokenizer_pin_v1"));
        invalid.put("비문자열", b -> ((ObjectNode)b.path("parameters")).put("answerTokenMethod", 1));
        for (var entry : invalid.entrySet()) {
            ObjectNode block = valid.deepCopy(); entry.getValue().accept(block);
            assertThat(error(() -> F0V4RuntimeProfileTest.parseBlock(block))).as(entry.getKey()).isEqualTo("PREREG_INVALID");
        }
        // v1~v3 은 새 키를 받지 않습니다(기존 등록 의미 불변).
        ObjectNode v3 = (ObjectNode)F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", "NONE").block().deepCopy();
        ((ObjectNode)v3.path("parameters")).put("answerTokenMethod", ClassificationJudge.ANSWER_METHOD_LOCAL_PIN);
        ((ArrayNode)v3.path("inputs")).addObject().put("path", AnswerTokenCounter.SCRIPT).put("sha256", "0".repeat(64));
        assertThat(error(() -> F0V4RuntimeProfileTest.parseBlock(v3))).isEqualTo("PREREG_INVALID");
    }

    @Test void counterProcessSpeaksOneStdinLinePerAttemptAndChecksTheTokenizerSha() {
        String sha = "a".repeat(64);
        AnswerTokenCounter counter = AnswerTokenCounter.start(FakeAnswerCounter.command(sha, -1), sha);
        try {
            // 비 ASCII·보조 평면·줄바꿈·따옴표도 이스케이프된 한 줄로 가며(시험 프로세스는 비 ASCII 바이트를 받으면 exit 9) 같은 문자열로 셉니다.
            String answer = "{\"q\":\"설비\\n\\\"x\\\"\"}\n", thinking = THINKING + "\r\n끝";
            assertThat(counter.count(answer, thinking)).isEqualTo(new AnswerTokenCounter.Counts(codepoints(answer), codepoints(thinking)));
            assertThat(counter.count("", null)).isEqualTo(new AnswerTokenCounter.Counts(0, null));
        } finally { counter.close(); }
        assertThat(error(() -> AnswerTokenCounter.start(FakeAnswerCounter.command("b".repeat(64), -1), sha))).isEqualTo("ANSWER_COUNTER_TOKENIZER_MISMATCH");
        assertThat(error(() -> AnswerTokenCounter.start(FakeAnswerCounter.command("NONE", -1), sha))).isEqualTo("ANSWER_COUNTER_UNAVAILABLE_EXIT_4");
        assertThat(error(() -> AnswerTokenCounter.start(List.of(directory.resolve("no-such-python.exe").toString()), sha))).isEqualTo("ANSWER_COUNTER_UNAVAILABLE");
        assertThat(error(() -> AnswerTokenCounter.python(directory, Map.of(), Prereg.RuntimeProfile.INFER_V4, sha))).isEqualTo("AIGW_PYTHON_MISSING");
        // 계수기가 응답 없이 끝나면 측정 중 오류(exit 5)이며 메시지에 원문이 없습니다.
        AnswerTokenCounter dying = AnswerTokenCounter.start(FakeAnswerCounter.command(sha, 0), sha);
        Throwable failed = catchThrowable(() -> dying.count(OllamaStub.SENTINEL, THINKING));
        assertThat(failed).isInstanceOfSatisfying(EvalException.class, e -> { assertThat(e.exitCode()).isEqualTo(5); assertThat(e.getMessage()).isEqualTo("ANSWER_COUNTER_FAILED"); });
        dying.close();
    }

    @Test void methodBRunRecordsLocalCountsAndTheCrossCheckDifferenceAndReportsItsDistributionWithoutRawText() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = methodB(f, p -> {});
        List<List<String>> launched = new ArrayList<>(); f.answerCounter = FakeAnswerCounter.launcher(-1, launched);
        Set<Path> before;
        try (var walk = Files.walk(f.root)) { before = new HashSet<>(walk.toList()); }
        List<OllamaStub.Request> methodBChats;
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST); stub.chat = answering(StubAnswers.correct(f.root), c -> c);
            assertThat(F0V4RuntimeProfileTest.runB(f, stub, doc)).as(f.lastError).isZero();
            methodBChats = F0StructuredOutputDiagnosticTest.chats(stub);
        }
        assertThat(launched).hasSize(1).allSatisfy(command -> assertThat(String.join(" ", command)).doesNotContain(OllamaStub.SENTINEL));
        JsonNode attempts = f.json(RESULTS + "/B/f0-results.json").path("attempts");
        assertThat(attempts).isNotEmpty();
        for (JsonNode row : attempts) {
            assertThat(row.path("status").asText()).isEqualTo("OK");
            JsonNode local = row.path("localTokenCounts").get(0); int answer = row.path("thinkingObservations").get(0).path("contentCodepoints").asInt();
            assertThat(row.path("localTokenCounts")).hasSize(1);
            assertThat(local.path("answerTokens").asInt()).isEqualTo(answer); assertThat(row.path("answerTokens").get(0).asInt()).isEqualTo(answer);
            assertThat(local.path("thinkingTokens").asInt()).isEqualTo(codepoints(THINKING)); assertThat(local.path("completionMinusLocal").asInt()).isEqualTo(SPECIAL);
            assertThat(row.path("thinkingObservations").get(0).path("reasoningTokens").isNull()).isTrue();
        }
        // 계수기는 결과 디렉터리 밖에 아무 파일도 만들지 않고, 결과·출력 어디에도 원문이 없습니다.
        try (var walk = Files.walk(f.root)) { assertThat(walk.filter(p -> !before.contains(p) && !f.root.relativize(p).toString().replace('\\', '/').startsWith("eval/f0/results"))).isEmpty(); }
        assertThat(f.exec("report", "--prereg", PREREG, "--run-id", RUN, "--results", RESULTS)).as(f.lastError).isZero();
        String report = f.text(RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT); int rows = attempts.size();
        assertThat(report).contains("## v4 답변 토큰 로컬 계수 교차 확인", "| B | 초회 | " + rows + " | 0 | 0 | " + rows + " | 1/1/1/1 | " + rows + " | 1=" + rows + " |",
            "| 전체 | - | " + rows + " | 0 | 0 | " + rows + " | 1/1/1/1 | " + rows + " | 1=" + rows + " |");
        assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
        // 요청 body 는 계수 방법과 무관합니다: 같은 응답을 받는 방법 (a) 등록의 초회 송신 바이트와 같습니다(방법 a 는 계수 불가로 문항마다 재시도가 더 나갑니다).
        F0TestRoot g = new F0TestRoot(directory.resolve("method-a")); Prereg.Document legacy = F0V4RuntimeProfileTest.v4(g, p -> {});
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST); stub.chat = answering(StubAnswers.correct(g.root), c -> c);
            assertThat(F0V4RuntimeProfileTest.runB(g, stub, legacy)).as(g.lastError).isZero();
            List<String> legacyFirst = F0StructuredOutputDiagnosticTest.chats(stub).stream().map(r -> new String(r.raw(), StandardCharsets.UTF_8)).filter(s -> !s.contains("오류: USAGE_EXCEEDS_LIMIT")).toList();
            assertThat(legacyFirst).isEqualTo(methodBChats.stream().map(r -> new String(r.raw(), StandardCharsets.UTF_8)).toList());
        }
        // 방법 (a) 등록 결과에는 새 열이 없고 보고서에도 교차 확인 절이 없습니다(reasoning_tokens 미보고 → 전부 계수 불가, 기존 동작).
        for (JsonNode row : g.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            assertThat(GoldenSet.keys(row)).doesNotContain("localTokenCounts"); assertThat(row.path("errorCodes").get(0).asText()).isEqualTo(ClassificationJudge.ANSWER_TOKENS_UNCOUNTABLE);
        }
        assertThat(g.exec("report", "--prereg", PREREG, "--run-id", RUN, "--results", RESULTS)).as(g.lastError).isZero();
        assertThat(g.text(RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT)).contains("## v4 사고 출력 관측").doesNotContain("로컬 계수 교차 확인");
    }

    @Test void localAnswerCountOverTheLimitFailsAndIsRetriedOnceWhileThinkingIsNotCounted() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = methodB(f, p -> {});
        f.answerCounter = FakeAnswerCounter.launcher(-1, new ArrayList<>());
        String pad = " ".repeat(200);
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST); var correct = StubAnswers.correct(f.root);
            // 초회 답변은 공백 200 을 붙여 192 를 넘기고(JSON 은 유효), 재시도(오류 요지 USAGE_EXCEEDS_LIMIT)는 그대로 둡니다. 사고문 길이는 상한에 넣지 않습니다.
            stub.chat = body -> answering(correct, c -> body.toString().contains("오류: USAGE_EXCEEDS_LIMIT") ? c : c + pad).apply(body);
            assertThat(F0V4RuntimeProfileTest.runB(f, stub, doc)).as(f.lastError).isZero();
        }
        for (JsonNode row : f.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            assertThat(row.path("status").asText()).isEqualTo("OK"); assertThat(row.path("retried").asBoolean()).isTrue();
            assertThat(row.path("errorCodes")).extracting(JsonNode::asText).containsExactly(ClassificationJudge.ANSWER_LIMIT_EXCEEDED);
            assertThat(row.path("answerTokens").get(0).asInt()).isGreaterThan(192); assertThat(row.path("answerTokens").get(1).asInt()).isLessThanOrEqualTo(192);
            assertThat(row.path("localTokenCounts")).hasSize(2).allSatisfy(l -> assertThat(l.path("completionMinusLocal").asInt()).isEqualTo(SPECIAL));
        }
        // 두 시도 모두 초과 = 실패(재시도 1회뿐).
        F0TestRoot g = new F0TestRoot(directory.resolve("both")); Prereg.Document both = methodB(g, p -> {});
        g.answerCounter = FakeAnswerCounter.launcher(-1, new ArrayList<>());
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST); stub.chat = answering(StubAnswers.correct(g.root), c -> c + pad);
            assertThat(F0V4RuntimeProfileTest.runB(g, stub, both)).as(g.lastError).isZero();
            assertThat(F0StructuredOutputDiagnosticTest.chats(stub)).hasSize(2 * g.json(RESULTS + "/B/f0-results.json").path("attempts").size());
        }
        for (JsonNode row : g.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            assertThat(row.path("status").asText()).isEqualTo("INVALID");
            assertThat(row.path("errorCodes")).extracting(JsonNode::asText).containsExactly(ClassificationJudge.ANSWER_LIMIT_EXCEEDED, ClassificationJudge.ANSWER_LIMIT_EXCEEDED);
        }
    }

    @Test void counterFailuresStopBeforeAnyRequestOrPreserveThePartialResultWithoutRawText() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = methodB(f, p -> {});
        // 계수기 준비 실패(tokenizer SHA 불일치·Python 미지정)는 endpoint 호출 0 으로 exit 2 입니다.
        f.answerCounter = (root, env, profile, sha) -> AnswerTokenCounter.start(FakeAnswerCounter.command("c".repeat(64), -1), sha);
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST);
            assertThat(F0V4RuntimeProfileTest.runB(f, stub, doc)).isEqualTo(2); assertThat(f.lastError).isEqualTo("ANSWER_COUNTER_TOKENIZER_MISMATCH");
            f.answerCounter = AnswerTokenCounter::python;
            assertThat(F0V4RuntimeProfileTest.runB(f, stub, doc)).isEqualTo(2); assertThat(f.lastError).isEqualTo("AIGW_PYTHON_MISSING");
            assertThat(stub.requests).isEmpty();
        }
        // 측정 중 계수기 사망 = exit 5, 지금까지 결과 보존(complete=false), 원문 없음.
        f.answerCounter = FakeAnswerCounter.launcher(2, new ArrayList<>());
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST); stub.chat = answering(StubAnswers.correct(f.root), c -> c);
            assertThat(F0V4RuntimeProfileTest.runB(f, stub, doc)).isEqualTo(5); assertThat(f.lastError).isEqualTo("ANSWER_COUNTER_FAILED");
        }
        JsonNode result = f.json(RESULTS + "/B/f0-results.json");
        assertThat(result.path("complete").asBoolean()).isFalse(); assertThat(result.path("abortReason").asText()).isEqualTo("ANSWER_COUNTER_FAILED");
        assertThat(result.path("attempts")).hasSize(2);
        assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
    }
}
