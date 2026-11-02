package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

/** v2 예산 profile(입력 3072·출력 B192/A256)이 PREREG 검사→요청 max_tokens→판정→보고까지 같은 값으로 쓰이고, 사전 계수 표 밖 호출은 송신 전에 막히는지 확인합니다. */
class F0InputBudgetTest {
    static final String RUN = F0VersionedInputsTest.RUN;
    @TempDir Path directory;

    static int run(F0TestRoot f, OllamaStub stub, Prereg.Document doc, String route) { return F0VersionedInputsTest.run(f, stub, doc, route); }
    static List<JsonNode> chatBodies(OllamaStub stub) { return stub.requests.stream().filter(r -> r.path().equals("/v1/chat/completions")).map(OllamaStub.Request::body).toList(); }

    @Test void preregRejectsLegacyMismatchedOrNonIntegerBudgetValues() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        assertThat(doc.budget()).isEqualTo(Prereg.Budget.V2);
        List<Consumer<ObjectNode>> mutations = List.of(
            p -> ((ObjectNode)p.path("maxTokens")).put("classify", 128), p -> ((ObjectNode)p.path("maxTokens")).put("plan", 160), p -> p.put("outputLimitTokens", 128),
            p -> ((ObjectNode)p.path("maxTokens")).put("classify", "192"), p -> p.remove("inputLimitTokens"),
            p -> ((ObjectNode)p.path("inputLimitTokens")).put("plan", 3200), p -> ((ObjectNode)p.path("inputLimitTokens")).put("classify", 1024), p -> p.put("contextTokens", 8192), p -> p.remove("contextTokens"));
        for (Consumer<ObjectNode> mutation : mutations) {
            ObjectNode block = (ObjectNode)doc.block().deepCopy(); mutation.accept((ObjectNode)block.path("parameters")); F0VersionedInputsTest.writePrereg(f, block);
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        // 정규 직렬화는 192.0 을 192 로 쓰므로 소수 표기는 문서 바이트로 직접 넣습니다.
        String canonical = Json.canonicalString(doc.block());
        for (String decimal : List.of("\"classify\":192.0", "\"classify\":1.92e2")) {
            String text = canonical.replace("\"classify\":192", decimal);
            assertThat(text).isNotEqualTo(canonical);
            f.write(F0VersionedInputsTest.PREREG, "# v2\n\n```json\n" + text + "\n```\n");
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        // 실제 v1 등록 문서는 바이트 그대로 v1 profile(128/160)로 읽힙니다.
        Prereg.Document legacy = Prereg.parse(Path.of(System.getProperty("aigw.rootDir")).resolve(Prereg.DEFAULT_PATH));
        assertThat(legacy.budget()).isEqualTo(Prereg.Budget.V1);
        assertThat(legacy.budget().output(MessageAssembler.Kind.CLASSIFY)).isEqualTo(128); assertThat(legacy.budget().output(MessageAssembler.Kind.PLAN)).isEqualTo(160);
    }

    @Test void requestMaxTokensJudgementAndReportUseTheRegisteredProfile() throws Exception {
        F0TestRoot f; Prereg.Document doc;
        try (OllamaStub stub = new OllamaStub()) {
            f = UsageProbeTest.rendererRegistered(directory, stub); doc = F0VersionedInputsTest.versioned(f);
            var correct = StubAnswers.correct(f.root);
            // 과거 128 상한을 넘지만 v2 분류 192 안인 사용량은 유효 출력이어야 합니다.
            stub.chat = body -> { JsonNode answer = correct.apply(body); ((ObjectNode)answer.path("usage")).put("completion_tokens", 150); return answer; };
            assertThat(run(f, stub, doc, "B")).isZero();
            assertThat(chatBodies(stub)).isNotEmpty().allSatisfy(body -> assertThat(body.path("max_tokens").asInt()).isEqualTo(192));
            stub.requests.clear();
            assertThat(run(f, stub, doc, "A")).isZero();
            assertThat(chatBodies(stub)).isNotEmpty().allSatisfy(body -> assertThat(body.path("max_tokens").asInt()).isEqualTo(256));
            stub.requests.clear();
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", "eval/f0/results/" + RUN + "/boundary")).isZero();
            assertThat(chatBodies(stub)).isNotEmpty().allSatisfy(body -> assertThat(body.path("max_tokens").asInt()).isEqualTo(192));
        }
        JsonNode b = f.json("eval/f0/results/" + RUN + "/B/f0-results.json"), boundary = f.json("eval/f0/results/" + RUN + "/boundary/boundary-results.json");
        assertThat(b.path("maxTokens").asInt()).isEqualTo(192); assertThat(b.path("inputLimitTokens").asInt()).isEqualTo(3072);
        assertThat(f.json("eval/f0/results/" + RUN + "/A/f0-results.json").path("maxTokens").asInt()).isEqualTo(256);
        b.path("attempts").forEach(row -> { assertThat(row.path("status").asText()).isEqualTo("OK"); assertThat(row.path("auditInputTokens").toString()).isEqualTo("[10]"); });
        assertThat(boundary.path("maxTokens").asInt()).isEqualTo(192);
        boundary.path("cases").forEach(c -> c.path("attempts").forEach(a -> assertThat(a.path("auditInputTokens").toString()).isEqualTo("[10]")));
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--out", "evidence/F0")).isZero();
        String report = Files.readString(f.root.resolve("eval/f0/results/" + RUN + "/report.md"), StandardCharsets.UTF_8);
        assertThat(report).contains("입력 상한 분류 3072·계획 3072, 요청 max_tokens 분류 192·계획 256, 경계 출력 상한 192, context 4096").contains("A 0, B 0, boundary 0");
        // 판정 상한도 같은 profile 값입니다(192 통과·193 재시도 요지).
        QRefContext none = QRefContext.extract("없음");
        String wire = "{\"eqp\":null,\"from\":null,\"qRef\":null,\"t\":\"GQ\",\"to\":null}";
        assertThat(ClassificationJudge.judge(new OllamaChatClient.ChatResult(wire, "stop", 1, 192), doc.budget().classifyOutput(), MessageAssembler.Kind.CLASSIFY, true, "+09:00", none).valid()).isTrue();
        assertThat(ClassificationJudge.judge(new OllamaChatClient.ChatResult(wire, "stop", 1, 193), doc.budget().classifyOutput(), MessageAssembler.Kind.CLASSIFY, true, "+09:00", none).errorCode()).isEqualTo("USAGE_EXCEEDS_LIMIT");
    }

    @Test void unauditedRetryOverBudgetInputOrProbeFailsClosedBeforeAnyEndpointCall() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        Map<String,java.util.function.Function<String,Integer>> scenarios = new LinkedHashMap<>();
        scenarios.put("INPUT_AUDIT_MISSING", id -> id.equals("B/G03/retry/STEPS_INVALID") ? null : 10);
        scenarios.put("INPUT_BUDGET_EXCEEDED", id -> id.equals("B/G03/first") ? 3073 : 10);
        for (var scenario : scenarios.entrySet()) try (OllamaStub stub = new OllamaStub()) {
            Prereg.Document doc = F0VersionedInputsTest.versioned(f, scenario.getValue());
            assertThat(run(f, stub, doc, "B")).isEqualTo(2); assertThat(f.lastError).isEqualTo(scenario.getKey());
            assertThat(stub.requests).isEmpty();
            assertThat(f.root.resolve("eval/f0/results/" + RUN + "/B/run-binding.json")).doesNotExist();
        }
        try (OllamaStub stub = new OllamaStub()) {
            Prereg.Document doc = F0VersionedInputsTest.versioned(f, id -> id.equals("boundary/NORMAL-01/retry/QREF_INVALID") ? null : 10);
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", "eval/f0/results/" + RUN + "/boundary")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING");
            doc = F0VersionedInputsTest.versioned(f, id -> id.startsWith("token/TG12") ? Prereg.CONTEXT_TOKENS : 10);
            assertThat(F0VersionedInputsTest.execute(f, "token-golden", "--endpoint", stub.endpoint(), "--messages", doc.input("tokenMessages"), "--out", "eval/f0/results/" + RUN + "/token-golden.jsonl")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("INPUT_BUDGET_EXCEEDED");
            assertThat(stub.requests).isEmpty();
        }
    }

    @Test void auditCountedWithAnotherTemplateOrTokenizerCannotAuthorizeInference() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        try (OllamaStub stub = new OllamaStub()) {
            stub.template = OllamaStub.DEFAULT_TEMPLATE + " ";
            assertThat(run(f, stub, doc, "B")).isEqualTo(2); assertThat(f.lastError).isEqualTo("INPUT_AUDIT_RUNTIME_MISMATCH");
            assertThat(chatBodies(stub)).isEmpty();
            assertThat(f.root.resolve("eval/f0/results/" + RUN + "/B/run-binding.json")).doesNotExist();
        }
        InputAudit audit = InputAudit.load(f.root.resolve(doc.input("inputAudit")));
        String template = Json.sha(OllamaStub.DEFAULT_TEMPLATE.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> audit.requireRuntime(new MeasurementSession.Runtime("x", template, "0".repeat(64)))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("INPUT_AUDIT_RUNTIME_MISMATCH"));
        audit.requireRuntime(new MeasurementSession.Runtime("x", template, MeasurementSession.tokenizerSha(f.root)));
    }

    @Test void perCallGuardStopsAnUnauditedRetryAfterTheFirstCallAndChecksContextSum() throws Exception {
        QRefContext refs = QRefContext.extract("\"x\"");
        ObjectNode user = Json.MAPPER.createObjectNode().put("question", "\"x\""); user.set("qRefCatalog", refs.catalog());
        List<Map<String,String>> first = List.of(Map.of("role", "user", "content", Json.canonicalString(user)));
        Path file = directory.resolve("audit.json");
        Json.write(file, Map.of("schema", InputAudit.SCHEMA, "tokenizerSha256", "a".repeat(64), "templateSha256", "b".repeat(64),
            "rows", List.of(Map.of("messagesSha256", InputAudit.messagesSha256(first), "inputTokens", 3072))));
        InputAudit audit = InputAudit.load(file);
        assertThat(audit.require(first, 3072, 256)).isEqualTo(3072);
        assertThatThrownBy(() -> audit.require(first, 3071, 192)).hasMessage("INPUT_BUDGET_EXCEEDED");
        assertThatThrownBy(() -> audit.require(first, 4095, 1025)).hasMessage("INPUT_BUDGET_EXCEEDED");
        AtomicInteger sent = new AtomicInteger();
        assertThatThrownBy(() -> ClassificationJudge.execute(m -> { audit.require(m, 3072, 192); sent.incrementAndGet(); return new OllamaChatClient.ChatResult("not json", "stop", 1, 1); },
            first, 192, MessageAssembler.Kind.CLASSIFY, true, "+09:00", refs)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("INPUT_AUDIT_MISSING"));
        assertThat(sent).hasValue(1);
    }

    @Test void everyJudgeErrorCodeIsInTheAuditedRetrySet() {
        QRefContext refs = QRefContext.extract("\"x\"");
        String ok = "{\"eqp\":null,\"from\":null,\"qRef\":\"Q1\",\"t\":\"INC\",\"to\":null}";
        Map<String,OllamaChatClient.ChatResult> classify = new LinkedHashMap<>();
        classify.put("length", new OllamaChatClient.ChatResult(ok, "length", 1, 1)); classify.put("other", new OllamaChatClient.ChatResult(ok, "tool_calls", 1, 1));
        classify.put("usage", new OllamaChatClient.ChatResult(ok, "stop", 1, 193)); classify.put("json", new OllamaChatClient.ChatResult("not json", "stop", 1, 1));
        classify.put("keys", new OllamaChatClient.ChatResult("{\"t\":\"INC\"}", "stop", 1, 1)); classify.put("enum", new OllamaChatClient.ChatResult(ok.replace("INC", "XX"), "stop", 1, 1));
        classify.put("qref", new OllamaChatClient.ChatResult(ok.replace("Q1", "Q9"), "stop", 1, 1)); classify.put("type", new OllamaChatClient.ChatResult(ok.replace("\"eqp\":null", "\"eqp\":1"), "stop", 1, 1));
        classify.put("format", new OllamaChatClient.ChatResult(ok.replace("\"from\":null", "\"from\":\"2026-02-30T00:00\""), "stop", 1, 1));
        classify.put("length-arg", new OllamaChatClient.ChatResult(ok.replace("\"eqp\":null", "\"eqp\":\"" + "E".repeat(65) + "\""), "stop", 1, 1));
        Set<String> codes = new TreeSet<>();
        classify.values().forEach(r -> codes.add(ClassificationJudge.judge(r, 192, MessageAssembler.Kind.CLASSIFY, true, "+09:00", refs).errorCode()));
        codes.add(ClassificationJudge.judge(new OllamaChatClient.ChatResult(ok.replace("}", ",\"steps\":[]}"), "stop", 1, 1), 256, MessageAssembler.Kind.PLAN, true, "+09:00", refs).errorCode());
        assertThat(codes).isEqualTo(new TreeSet<>(ClassificationJudge.RETRY_ERROR_CODES));
    }
}
