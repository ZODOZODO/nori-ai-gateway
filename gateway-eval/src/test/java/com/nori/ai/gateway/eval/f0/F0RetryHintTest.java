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
import static org.assertj.core.api.Assertions.*;

/**
 * v3 opt-in 재시도 힌트 STEPS_DETAIL_V1(LLM_STUB — 실측 아님).
 * 판정(valid·errorCode·결과 행 errorCodes)은 그대로 두고, 계획(A) 재시도 메시지에 덧붙이는 고정 코드만 STEPS_INVALID 대신 판정이 같은 검사에서 찾은
 * 세부 원인(ITEM_FORMAT_INVALID·EMPTY_FOR_TOOL_TASK·NONEMPTY_FOR_NO_TOOL_TASK)으로 바꿉니다. 분류(B·경계) 재시도와 키가 없는 기존 등록의 재시도 바이트는 그대로여야 하고,
 * 사전 입력 audit 은 측정이 보낼 수 있는 재시도 메시지를 같은 코드 집합으로 모두 계수해야 합니다.
 */
class F0RetryHintTest {
    static final String RUN = F0VersionedInputsTest.RUN, RESULTS = "eval/f0/results/" + RUN;
    static final String NONE = ClassificationJudge.RETRY_HINT_NONE, DETAIL = ClassificationJudge.STEPS_DETAIL_V1;
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    @TempDir Path directory;

    static OllamaChatClient.ChatResult result(String content) { return new OllamaChatClient.ChatResult(content, "stop", 40, 30); }
    static String plan(String t, String steps) { return "{\"eqp\":null,\"from\":null,\"qRef\":null,\"steps\":" + steps + ",\"t\":\"" + t + "\",\"to\":null}"; }
    /** 시험용 계획 무효 출력: 기대 t 를 유지하고 steps 만 틀립니다(GQ·UNS = Tool 있음, KQ = 형식 위반 항목, LQ·INC = 빈 배열). */
    static String expectedDetail(String t) { return Set.of("GQ", "UNS").contains(t) ? "NONEMPTY_FOR_NO_TOOL_TASK" : t.equals("KQ") ? "ITEM_FORMAT_INVALID" : "EMPTY_FOR_TOOL_TASK"; }
    static JsonNode invalidSteps(JsonNode correctResponse) {
        ObjectNode answer = (ObjectNode)Json.parse(correctResponse.path("choices").get(0).path("message").path("content").asText().getBytes(StandardCharsets.UTF_8));
        String t = answer.path("t").asText(); ArrayNode steps = answer.putArray("steps");
        if (Set.of("GQ", "UNS").contains(t)) steps.add("tc.logs.search"); else if (t.equals("KQ")) steps.add("Bad");
        return OllamaStub.chatResponse(Json.canonicalString(answer), "stop", 300, 30);
    }
    /** 기존 v3 시험 등록(JSON_SCHEMA_V1)에 retryHint 를 더하고, audit 는 같은 힌트의 Java 조립 목록으로 다시 만듭니다(withAudit=false 면 기존 audit 유지). */
    static Prereg.Document hinted(F0TestRoot f, boolean withAudit) throws Exception {
        ObjectNode block = (ObjectNode)F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", ResponseSchema.POLICY).block().deepCopy();
        ((ObjectNode)block.path("parameters")).put("retryHint", DETAIL);
        if (withAudit) {
            F0VersionedInputsTest.writeAudit(f, block.path("inputRoles"), id -> 10, MessageAssembler.FULL, DETAIL);
            for (JsonNode input : block.path("inputs")) if (input.path("path").asText().equals(F0VersionedInputsTest.AUDIT)) ((ObjectNode)input).put("sha256", Json.sha(Json.bytes(f.root.resolve(F0VersionedInputsTest.AUDIT))));
        }
        F0VersionedInputsTest.writePrereg(f, block);
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))));
        F0VersionedInputsTest.writePrereg(f, block);
        return Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG));
    }
    static Map<String,String> auditRows(F0TestRoot f) {
        Map<String,String> rows = new HashMap<>(); f.json(F0VersionedInputsTest.AUDIT).path("rows").forEach(r -> rows.put(r.path("id").asText(), r.path("messagesSha256").asText()));
        return rows;
    }

    @Test void stepsInvalidCarriesTheJudgeOwnDetailWithoutChangingValidityOrErrorCode() {
        RegistryFixture fixture = RegistryFixture.load(ROOT.resolve("eval/golden/tc-registry-fixture-v2.json"));
        QRefContext refs = QRefContext.extract("없음");
        ObjectNode context = Json.MAPPER.createObjectNode(); context.putArray("selectedResourceRefs"); context.putNull("timeWindow");
        Map<String,String[]> cases = new LinkedHashMap<>();
        // 출력 → {판정 errorCode(null = 유효), 세부 코드(null = 없음)}
        cases.put(plan("INC", "[\"tc.logs.search\"]"), new String[]{null, null});
        cases.put(plan("UNS", "[]"), new String[]{null, null});
        cases.put(plan("INC", "[]"), new String[]{"STEPS_INVALID", "EMPTY_FOR_TOOL_TASK"});
        cases.put(plan("LQ", "[]"), new String[]{"STEPS_INVALID", "EMPTY_FOR_TOOL_TASK"});
        cases.put(plan("GQ", "[\"tc.logs.search\"]"), new String[]{"STEPS_INVALID", "NONEMPTY_FOR_NO_TOOL_TASK"});
        cases.put(plan("INC", "[\"Bad\"]"), new String[]{"STEPS_INVALID", "ITEM_FORMAT_INVALID"});
        cases.put(plan("INC", "[\"tc.logs.search\",\"Bad\"]"), new String[]{"STEPS_INVALID", "ITEM_FORMAT_INVALID"});
        // 배열 아님·10개 초과·문자열 아닌 항목은 세부 코드 없이 기존 STEPS_INVALID 그대로입니다.
        cases.put(plan("INC", "\"tc.logs.search\""), new String[]{"STEPS_INVALID", null});
        cases.put(plan("INC", "[" + String.join(",", Collections.nCopies(11, "\"tc.logs.search\"")) + "]"), new String[]{"STEPS_INVALID", null});
        cases.put(plan("INC", "[1]"), new String[]{"STEPS_INVALID", null});
        cases.put(plan("INC", "[\"tc.logs.search\",1]"), new String[]{"STEPS_INVALID", null});
        cases.put(plan("INC", "[]").replace("\"qRef\":null,", ""), new String[]{"SCHEMA_KEYS_INVALID", null});
        for (var c : cases.entrySet()) {
            ClassificationJudge.Verdict verdict = ClassificationJudge.judge(result(c.getKey()), 256, MessageAssembler.Kind.PLAN, true, "+09:00", refs);
            assertThat(verdict.valid()).as(c.getKey()).isEqualTo(c.getValue()[0] == null);
            assertThat(verdict.errorCode()).as(c.getKey()).isEqualTo(c.getValue()[0]); assertThat(verdict.stepsDetail()).as(c.getKey()).isEqualTo(c.getValue()[1]);
            if (c.getValue()[1] == null) continue;
            // 세부 코드는 독립 진단(attemptDiagnostics.steps)의 같은 이름 분류와 일치합니다.
            ObjectNode expected = Json.MAPPER.createObjectNode().put("t", "INC"); for (String field : GoldenSet.ARG_FIELDS) expected.putNull(field);
            JsonNode steps = AttemptDiagnostics.diagnose(result(c.getKey()), 256, MessageAssembler.Kind.PLAN, refs, expected, "없음", context, "+09:00", fixture).path("steps");
            assertThat(c.getValue()[1].equals("ITEM_FORMAT_INVALID") ? steps.path("shape").asText() : steps.path("toolConsistency").asText()).as(c.getKey()).isEqualTo(c.getValue()[1]);
        }
        // 분류 판정에는 steps 가 없으므로 세부 코드도 없습니다.
        assertThat(ClassificationJudge.judge(result("not json"), 192, MessageAssembler.Kind.CLASSIFY, true, "+09:00", refs).stepsDetail()).isNull();
    }

    @Test void retryCodeSetAddsTheThreeDetailCodesOnlyForPlanUnderTheOptIn() {
        assertThat(ClassificationJudge.STEPS_DETAIL_CODES).containsExactly("ITEM_FORMAT_INVALID", "EMPTY_FOR_TOOL_TASK", "NONEMPTY_FOR_NO_TOOL_TASK");
        assertThat(ClassificationJudge.RETRY_ERROR_CODES).doesNotContainAnyElementsOf(ClassificationJudge.STEPS_DETAIL_CODES);
        assertThat(ClassificationJudge.retryCodes(MessageAssembler.Kind.PLAN, NONE)).isEqualTo(ClassificationJudge.RETRY_ERROR_CODES);
        assertThat(ClassificationJudge.retryCodes(MessageAssembler.Kind.CLASSIFY, NONE)).isEqualTo(ClassificationJudge.RETRY_ERROR_CODES);
        assertThat(ClassificationJudge.retryCodes(MessageAssembler.Kind.CLASSIFY, DETAIL)).isEqualTo(ClassificationJudge.RETRY_ERROR_CODES);
        List<String> expected = new ArrayList<>(ClassificationJudge.RETRY_ERROR_CODES); expected.addAll(ClassificationJudge.STEPS_DETAIL_CODES);
        assertThat(ClassificationJudge.retryCodes(MessageAssembler.Kind.PLAN, DETAIL)).containsExactlyElementsOf(expected);
        for (String bad : List.of("", "none", "steps_detail_v1", "STEPS_DETAIL_V2")) {
            assertThatThrownBy(() -> ClassificationJudge.retryCodes(MessageAssembler.Kind.PLAN, bad)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("RETRY_HINT_INVALID"));
            assertThatThrownBy(() -> ClassificationJudge.execute(m -> result("{}"), List.of(Map.of("role", "user", "content", "{}")), 256, MessageAssembler.Kind.PLAN, false, null, null, bad))
                .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("RETRY_HINT_INVALID"));
        }
    }

    @Test void executeSendsTheDetailCodeOnlyUnderTheOptInAndKeepsTheJudgeOutcome() {
        List<Map<String,String>> original = List.of(Map.of("role", "system", "content", "s"), Map.of("role", "user", "content", "{}"));
        String unversioned = "{\"eqp\":null,\"from\":null,\"q\":null,\"steps\":%s,\"t\":\"%s\",\"to\":null}";
        Map<String,String[]> cases = new LinkedHashMap<>();
        // 출력 → {종류, 기존 재시도 코드, opt-in 재시도 코드}
        cases.put(unversioned.formatted("[]", "INC"), new String[]{"PLAN", "STEPS_INVALID", "EMPTY_FOR_TOOL_TASK"});
        cases.put(unversioned.formatted("[\"tc.logs.search\"]", "UNS"), new String[]{"PLAN", "STEPS_INVALID", "NONEMPTY_FOR_NO_TOOL_TASK"});
        cases.put(unversioned.formatted("[\"Bad\"]", "KQ"), new String[]{"PLAN", "STEPS_INVALID", "ITEM_FORMAT_INVALID"});
        cases.put(unversioned.formatted("[1]", "INC"), new String[]{"PLAN", "STEPS_INVALID", "STEPS_INVALID"});
        cases.put("{\"t\":\"INC\"}", new String[]{"PLAN", "SCHEMA_KEYS_INVALID", "SCHEMA_KEYS_INVALID"});
        cases.put("not json", new String[]{"CLASSIFY", "JSON_PARSE_FAILED", "JSON_PARSE_FAILED"});
        for (var c : cases.entrySet()) {
            MessageAssembler.Kind kind = MessageAssembler.Kind.valueOf(c.getValue()[0]); int maxTokens = kind == MessageAssembler.Kind.PLAN ? 256 : 192;
            Map<String,ClassificationJudge.Outcome> outcomes = new LinkedHashMap<>();
            for (String hint : List.of(NONE, DETAIL)) {
                List<List<Map<String,String>>> sent = new ArrayList<>();
                ClassificationJudge.Outcome outcome = ClassificationJudge.execute(m -> { sent.add(m); return result(c.getKey()); }, original, maxTokens, kind, false, null, null, hint);
                assertThat(sent).hasSize(2); assertThat(sent.get(0)).isEqualTo(original);
                String code = hint.equals(NONE) ? c.getValue()[1] : c.getValue()[2];
                assertThat(sent.get(1)).as(c.getKey() + " " + hint).isEqualTo(MessageAssembler.retry(original, code));
                assertThat(sent.get(1).getLast().get("content")).isEqualTo("직전 출력이 무효다(오류: " + code + "). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다.");
                outcomes.put(hint, outcome);
            }
            // 판정 결과(상태·재시도·결과 행에 남는 errorCodes)는 힌트와 무관하게 같습니다.
            ClassificationJudge.Outcome legacy = outcomes.get(NONE), detail = outcomes.get(DETAIL);
            assertThat(detail.status()).isEqualTo(legacy.status()).isEqualTo("INVALID"); assertThat(detail.retried()).isEqualTo(legacy.retried()).isTrue();
            assertThat(detail.firstValid()).isEqualTo(legacy.firstValid()).isFalse(); assertThat(detail.outputSha256()).isEqualTo(legacy.outputSha256());
            assertThat(detail.errorCodes()).isEqualTo(legacy.errorCodes()).containsExactly(c.getValue()[1], c.getValue()[1]);
        }
        // 7인자 호출(기존 경로)은 힌트 NONE 과 같습니다.
        List<List<Map<String,String>>> sent = new ArrayList<>();
        ClassificationJudge.execute(m -> { sent.add(m); return result(unversioned.formatted("[]", "INC")); }, original, 256, MessageAssembler.Kind.PLAN, false, null, null);
        assertThat(sent.get(1)).isEqualTo(MessageAssembler.retry(original, "STEPS_INVALID"));
        // 재시도가 유효하면 재시도 출력으로 진행합니다.
        Iterator<OllamaChatClient.ChatResult> answers = List.of(result(unversioned.formatted("[]", "INC")), result(unversioned.formatted("[\"tc.logs.search\"]", "INC"))).iterator();
        ClassificationJudge.Outcome ok = ClassificationJudge.execute(m -> answers.next(), original, 256, MessageAssembler.Kind.PLAN, false, null, null, DETAIL);
        assertThat(ok.status()).isEqualTo("OK"); assertThat(ok.verdict().steps()).containsExactly("tc.logs.search"); assertThat(ok.errorCodes()).containsExactly("STEPS_INVALID");
    }

    @Test void everyRetryCodeTheOptInCanSendIsInItsAuditedRetrySet() {
        QRefContext refs = QRefContext.extract("\"x\"");
        String ok = "{\"eqp\":null,\"from\":null,\"qRef\":\"Q1\",\"steps\":[\"tc.logs.search\"],\"t\":\"INC\",\"to\":null}";
        List<OllamaChatClient.ChatResult> outputs = new ArrayList<>(List.of(new OllamaChatClient.ChatResult(ok, "length", 1, 1), new OllamaChatClient.ChatResult(ok, "tool_calls", 1, 1),
            new OllamaChatClient.ChatResult(ok, "stop", 1, 257), result("not json"), result("{\"t\":\"INC\"}"), result(ok.replace("\"INC\"", "\"XX\"")), result(ok.replace("Q1", "Q9")),
            result(ok.replace("\"eqp\":null", "\"eqp\":1")), result(ok.replace("\"from\":null", "\"from\":\"2026-02-30T00:00\"")), result(ok.replace("\"eqp\":null", "\"eqp\":\"" + "E".repeat(65) + "\""))));
        for (String steps : List.of("[1]", "[]", "[\"Bad\"]")) outputs.add(result(ok.replace("[\"tc.logs.search\"]", steps)));
        outputs.add(result(ok.replace("\"INC\"", "\"GQ\"")));
        Set<String> sentCodes = new TreeSet<>();
        for (OllamaChatClient.ChatResult output : outputs) {
            ObjectNode user = Json.MAPPER.createObjectNode().put("question", "\"x\""); user.set("qRefCatalog", refs.catalog());
            List<Map<String,String>> first = List.of(Map.of("role", "user", "content", Json.canonicalString(user)));
            List<List<Map<String,String>>> sent = new ArrayList<>();
            ClassificationJudge.execute(m -> { sent.add(m); return output; }, first, 256, MessageAssembler.Kind.PLAN, true, "+09:00", refs, DETAIL);
            String content = sent.get(1).getLast().get("content");
            sentCodes.add(content.substring(content.indexOf("오류: ") + 4, content.indexOf(")")));
        }
        assertThat(sentCodes).isEqualTo(new TreeSet<>(ClassificationJudge.retryCodes(MessageAssembler.Kind.PLAN, DETAIL)));
    }

    @Test void retryHintIsAV3OptInParameter() throws Exception {
        assertThat(Prereg.RETRY_HINTS).containsExactly(DETAIL);
        F0TestRoot f = new F0TestRoot(directory);
        Prereg.Document legacy = F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", ResponseSchema.POLICY);
        assertThat(legacy.parameters().has("retryHint")).isFalse(); assertThat(legacy.retryHint()).isEqualTo(NONE);
        ObjectNode valid = (ObjectNode)legacy.block().deepCopy(); p(valid).put("retryHint", DETAIL);
        F0VersionedInputsTest.writePrereg(f, valid);
        assertThat(Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG)).retryHint()).isEqualTo(DETAIL);
        for (var change : List.<java.util.function.Consumer<ObjectNode>>of(
                b -> p(b).put("retryHint", NONE), b -> p(b).put("retryHint", "steps_detail_v1"), b -> p(b).put("retryHint", "STEPS_DETAIL_V2"), b -> p(b).put("retryHint", 1), b -> p(b).putNull("retryHint"),
                b -> { b.put("schema", "f0-prereg-v2"); b.remove("purpose"); p(b).remove("responseFormat"); })) {
            ObjectNode block = valid.deepCopy(); change.accept(block); F0VersionedInputsTest.writePrereg(f, block);
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        ObjectNode gate = valid.deepCopy(); gate.put("purpose", "GATE"); p(gate).put("repeats", Prereg.GATE_REPEATS); F0VersionedInputsTest.writePrereg(f, gate);
        assertThat(Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG)).retryHint()).isEqualTo(DETAIL);
    }
    private static ObjectNode p(ObjectNode block) { return (ObjectNode)block.path("parameters"); }

    @Test void optInRegistrationWithoutDetailRetryRowsIsRejectedBeforeAnyPlanRequest() throws Exception {
        try (OllamaStub stub = new OllamaStub()) {
            F0TestRoot f = UsageProbeTest.rendererRegistered(directory, stub);
            Prereg.Document doc = hinted(f, false);
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "A")).isEqualTo(2); assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING");
            assertThat(stub.requests).isEmpty();
            assertThat(f.root.resolve(RESULTS + "/A/run-binding.json")).doesNotExist();
            // 분류(B) 재시도 코드 집합은 힌트와 무관하므로 기존 audit 로 그대로 측정됩니다.
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
        }
    }

    @Test void optInChangesOnlyPlanRetryMessagesAndTheAuditCoversExactlyTheSentRetries() throws Exception {
        Map<String,List<OllamaStub.Request>> sent = new LinkedHashMap<>(); Map<String,F0TestRoot> roots = new LinkedHashMap<>(); Map<String,Prereg.Document> docs = new LinkedHashMap<>();
        for (String hint : List.of(NONE, DETAIL)) {
            try (OllamaStub stub = new OllamaStub()) {
                F0TestRoot f = UsageProbeTest.rendererRegistered(directory.resolve(hint), stub);
                Prereg.Document doc = hint.equals(NONE) ? F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", ResponseSchema.POLICY) : hinted(f, true);
                assertThat(doc.retryHint()).isEqualTo(hint);
                var correct = StubAnswers.correct(f.root);
                // 분류는 초회 무효 → 재시도 정답, 계획은 두 시도 모두 steps 만 틀린 같은 출력(기대 t 유지)입니다.
                stub.chat = body -> body.path("max_tokens").asInt() == Prereg.Budget.V2.planOutput() ? invalidSteps(correct.apply(body))
                    : body.path("messages").size() == 2 ? OllamaStub.chatResponse("{}", "stop", 300, 2) : correct.apply(body);
                assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero(); assertThat(F0VersionedInputsTest.run(f, stub, doc, "A")).isZero();
                assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/boundary")).isZero();
                sent.put(hint, F0StructuredOutputDiagnosticTest.chats(stub)); roots.put(hint, f); docs.put(hint, doc);
            }
        }
        GoldenSet golden = F0StructuredOutputDiagnosticTest.golden(roots.get(DETAIL), docs.get(DETAIL));
        Map<String,GoldenSet.Question> byQuestion = new HashMap<>(); golden.questions().forEach(q -> byQuestion.put(q.question(), q));
        Map<String,String> legacyAudit = auditRows(roots.get(NONE)), detailAudit = auditRows(roots.get(DETAIL));
        List<OllamaStub.Request> legacy = sent.get(NONE), detail = sent.get(DETAIL);
        assertThat(detail).hasSameSizeAs(legacy);
        int other = 0, planFirst = 0, planRetry = 0; Set<String> details = new TreeSet<>();
        for (int i = 0; i < legacy.size(); i++) {
            OllamaStub.Request a = legacy.get(i), b = detail.get(i);
            boolean plan = a.body().path("max_tokens").asInt() == Prereg.Budget.V2.planOutput();
            if (!plan || a.body().path("messages").size() == 2) {
                // B·경계 요청(초회·재시도)과 계획 초회 요청은 바이트까지 같습니다.
                assertThat(b.raw()).isEqualTo(a.raw());
                if (plan) planFirst++; else other++;
                continue;
            }
            planRetry++;
            List<Map<String,String>> legacyMessages = F0ContextProjectionTest.messages(a), detailMessages = F0ContextProjectionTest.messages(b), first = legacyMessages.subList(0, 2);
            GoldenSet.Question q = byQuestion.get(OllamaStub.question(a.body())); String code = expectedDetail(q.expected().t());
            assertThat(legacyMessages).isEqualTo(MessageAssembler.retry(first, "STEPS_INVALID"));
            assertThat(detailMessages).isEqualTo(MessageAssembler.retry(first, code));
            ObjectNode legacyRest = a.body().deepCopy(), detailRest = b.body().deepCopy(); legacyRest.remove("messages"); detailRest.remove("messages");
            assertThat(detailRest).isEqualTo(legacyRest);
            // 실제 송신 재시도 메시지 = 사전 audit 의 같은 위치 id 행(사전 계수와 측정이 같은 메시지).
            assertThat(InputAudit.messagesSha256(detailMessages)).isEqualTo(detailAudit.get("A/" + q.id() + "/retry/" + code));
            assertThat(InputAudit.messagesSha256(legacyMessages)).isEqualTo(legacyAudit.get("A/" + q.id() + "/retry/STEPS_INVALID"));
            assertThat(legacyAudit).doesNotContainKey("A/" + q.id() + "/retry/" + code);
            details.add(code);
        }
        assertThat(planFirst).isEqualTo(planRetry).isEqualTo(2 * golden.questions().size()); assertThat(other).isPositive();
        assertThat(details).containsExactlyInAnyOrderElementsOf(ClassificationJudge.STEPS_DETAIL_CODES);
        // 결과 행(판정)은 같습니다: 계획은 두 시도 STEPS_INVALID 로 최종 INVALID.
        for (String route : List.of("B", "A")) {
            JsonNode rowsLegacy = roots.get(NONE).json(RESULTS + "/" + route + "/f0-results.json").path("attempts"), rowsDetail = roots.get(DETAIL).json(RESULTS + "/" + route + "/f0-results.json").path("attempts");
            assertThat(rowsDetail.size()).isEqualTo(rowsLegacy.size()).isPositive();
            for (int i = 0; i < rowsLegacy.size(); i++) {
                for (String field : List.of("questionId", "status", "retried", "firstValid", "errorCodes", "outputSha256", "predicted", "responseFormatSha256"))
                    assertThat(rowsDetail.get(i).path(field)).as(route + " " + i + " " + field).isEqualTo(rowsLegacy.get(i).path(field));
                if (route.equals("A")) assertThat(rowsDetail.get(i).path("errorCodes").toString()).isEqualTo("[\"STEPS_INVALID\",\"STEPS_INVALID\"]");
            }
        }
        assertThat(roots.get(DETAIL).json(RESULTS + "/A/run-binding.json").path("parameterManifest").path("retryHint").asText()).isEqualTo(DETAIL);
        assertThat(roots.get(NONE).json(RESULTS + "/A/run-binding.json").path("parameterManifest").has("retryHint")).isFalse();
        for (F0TestRoot f : roots.values()) assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void inputAuditRenderAppendsDetailRetriesOnlyToPlanRowsUnderTheOptIn() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        String template = f.json(ChatRenderers.REGISTRY).path("renderers").get(0).path("templateSha256").asText();
        java.util.function.Function<String,List<String>> render = hint -> {
            List<String> args = new ArrayList<>(List.of("input-audit-render", "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--classify-prompt", doc.input("classifyPrompt"), "--plan-prompt", doc.input("planPrompt"),
                "--cases", doc.input("boundaryCases"), "--usage-messages", doc.input("usageMessages"), "--token-messages", doc.input("tokenMessages"), "--renderers", ChatRenderers.REGISTRY, "--template-sha256", template, "--out", "render-" + hint + ".jsonl"));
            if (!hint.equals(NONE)) args.addAll(List.of("--retry-hint", hint));
            assertThat(f.exec(args.toArray(String[]::new))).isZero();
            try { return Arrays.stream(f.text("render-" + hint + ".jsonl").split("\n")).filter(l -> !l.isBlank()).toList(); } catch (java.io.IOException e) { throw new AssertionError(e); }
        };
        List<String> legacy = render.apply(NONE), detail = render.apply(DETAIL);
        GoldenSet golden = F0StructuredOutputDiagnosticTest.golden(f, doc);
        assertThat(detail).hasSize(legacy.size() + ClassificationJudge.STEPS_DETAIL_CODES.size() * golden.questions().size());
        // 힌트가 없으면 기존 조립 목록(고정 오류 코드 11종)과 같습니다.
        java.util.function.Function<String,Path> role = r -> f.root.resolve(doc.input(r));
        RegistryFixture fixture = RegistryFixture.load(role.apply("fixture"));
        MessageAssembler plan = MessageAssembler.load(role.apply("planPrompt"), fixture, MessageAssembler.FULL);
        List<InputAudit.Planned> legacyA = InputAudit.run(golden, plan, Prereg.Budget.V2, "A"), detailA = InputAudit.run(golden, plan, Prereg.Budget.V2, "A", DETAIL);
        assertThat(legacyA).hasSize(12 * golden.questions().size()); assertThat(detailA).hasSize(15 * golden.questions().size());
        for (int i = 0; i < legacyA.size(); i++) assertThat(Json.parse(legacy.get(i).getBytes(StandardCharsets.UTF_8)).path("id").asText()).isEqualTo(legacyA.get(i).id());
        for (int i = 0; i < detailA.size(); i++) {
            JsonNode row = Json.parse(detail.get(i).getBytes(StandardCharsets.UTF_8));
            assertThat(row.path("id").asText()).isEqualTo(detailA.get(i).id()); assertThat(row.path("messagesSha256").asText()).isEqualTo(InputAudit.messagesSha256(detailA.get(i).messages()));
        }
        // 힌트를 켜도 A 외 행(B·경계·usage·token)은 순서·id·SHA·렌더 문자열까지 같고, A 행은 기존 행 뒤에 문항마다 세부 코드 3행만 더해집니다.
        assertThat(detail.subList(detailA.size(), detail.size())).isEqualTo(legacy.subList(legacyA.size(), legacy.size()));
        Map<String,String> legacyRows = new LinkedHashMap<>(); for (String line : legacy.subList(0, legacyA.size())) legacyRows.put(Json.parse(line.getBytes(StandardCharsets.UTF_8)).path("id").asText(), line);
        int added = 0;
        for (String line : detail.subList(0, detailA.size())) {
            String id = Json.parse(line.getBytes(StandardCharsets.UTF_8)).path("id").asText();
            if (legacyRows.containsKey(id)) { assertThat(line).isEqualTo(legacyRows.get(id)); continue; }
            added++;
            assertThat(ClassificationJudge.STEPS_DETAIL_CODES).contains(id.substring(id.lastIndexOf('/') + 1)); assertThat(id).contains("/retry/");
        }
        assertThat(added).isEqualTo(3 * golden.questions().size());
        for (String bad : List.of(NONE, "steps_detail_v1", "STEPS_DETAIL_V2")) {
            assertThat(f.exec("input-audit-render", "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--classify-prompt", doc.input("classifyPrompt"), "--plan-prompt", doc.input("planPrompt"),
                "--cases", doc.input("boundaryCases"), "--usage-messages", doc.input("usageMessages"), "--token-messages", doc.input("tokenMessages"), "--renderers", ChatRenderers.REGISTRY, "--template-sha256", template, "--retry-hint", bad, "--out", "render-bad.jsonl")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("ARGUMENT_INVALID");
        }
        assertThat(f.root.resolve("render-bad.jsonl")).doesNotExist();
    }
}
