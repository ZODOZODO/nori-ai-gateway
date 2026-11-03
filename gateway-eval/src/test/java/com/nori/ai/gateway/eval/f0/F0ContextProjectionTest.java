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
import static org.assertj.core.api.Assertions.*;

/**
 * v3 opt-in Context 투영(parameters.contextProjection=EXTRACTION_V1)의 실제 HTTP stub 요청·등록 검증·audit 결속(LLM_STUB — 실측 아님).
 * 투영은 LLM 입력 뷰만 줄이며 원 Context·질문·qRef 카탈로그·재시도 바인딩·판정은 그대로여야 하고, 키가 없는 기존 등록은 전체 Context 바이트 그대로여야 합니다.
 */
class F0ContextProjectionTest {
    static final String RUN = F0VersionedInputsTest.RUN, RESULTS = "eval/f0/results/" + RUN, AUDIT = F0VersionedInputsTest.AUDIT;
    @TempDir Path directory;

    /** 시험 v3 등록에 투영 키(null 이면 키 없음)를 넣고 auditProjection 으로 조립한 audit 표를 다시 등록합니다. */
    static Prereg.Document projected(F0TestRoot f, String purpose, String projection, String auditProjection) throws Exception {
        ObjectNode block = (ObjectNode)F0StructuredOutputDiagnosticTest.v3(f, purpose, ResponseSchema.POLICY).block().deepCopy();
        if (projection != null) ((ObjectNode)block.path("parameters")).put("contextProjection", projection);
        F0VersionedInputsTest.writeAudit(f, block.path("inputRoles"), id -> 10, auditProjection);
        for (JsonNode input : block.path("inputs")) if (input.path("path").asText().equals(AUDIT)) ((ObjectNode)input).put("sha256", Json.sha(Json.bytes(f.root.resolve(AUDIT))));
        F0VersionedInputsTest.writePrereg(f, block);
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))));
        F0VersionedInputsTest.writePrereg(f, block);
        return Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG));
    }
    static JsonNode user(OllamaStub.Request request) { return Json.parse(request.body().path("messages").get(1).path("content").asText().getBytes(StandardCharsets.UTF_8)); }
    static Set<String> auditShas(F0TestRoot f) { Set<String> shas = new HashSet<>(); f.json(AUDIT).path("rows").forEach(r -> shas.add(r.path("messagesSha256").asText())); return shas; }
    static List<Map<String,String>> messages(OllamaStub.Request request) {
        List<Map<String,String>> messages = new ArrayList<>();
        request.body().path("messages").forEach(m -> messages.add(Map.of("role", m.path("role").asText(), "content", m.path("content").asText())));
        return messages;
    }

    @Test void projectedRequestsCarryOnlyTheTimeBasisAndKeepQuestionCatalogRetryBindingAndOriginalContext() throws Exception {
        F0TestRoot f; Prereg.Document doc; GoldenSet golden; byte[] goldenBytes, casesBytes;
        try (OllamaStub stub = new OllamaStub()) {
            f = UsageProbeTest.rendererRegistered(directory, stub);
            doc = projected(f, "DIAGNOSTIC", MessageAssembler.EXTRACTION_V1, MessageAssembler.EXTRACTION_V1);
            assertThat(doc.contextProjection()).isEqualTo(MessageAssembler.EXTRACTION_V1);
            golden = F0StructuredOutputDiagnosticTest.golden(f, doc);
            goldenBytes = Json.bytes(f.root.resolve(doc.input("golden"))); casesBytes = Json.bytes(f.root.resolve(doc.input("boundaryCases")));
            // 초회는 무효 JSON, 재시도는 정답 → 모든 문항·케이스가 재시도 요청을 보냅니다.
            var correct = StubAnswers.correct(f.root);
            stub.chat = body -> body.path("messages").size() == 2 ? OllamaStub.chatResponse("{}", "stop", 300, 2) : correct.apply(body);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero(); assertThat(F0VersionedInputsTest.run(f, stub, doc, "A")).isZero();
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/boundary")).isZero();
            UsageProbeTest.cacheModel(stub, false);
            assertThat(F0VersionedInputsTest.execute(f, "usage-probe", "--endpoint", stub.endpoint(), "--messages", doc.input("usageMessages"), "--out", RESULTS + "/usage.json")).isZero();
            assertThat(F0VersionedInputsTest.execute(f, "token-golden", "--endpoint", stub.endpoint(), "--messages", doc.input("tokenMessages"), "--out", RESULTS + "/token-golden.jsonl")).isZero();
            Map<String,JsonNode> contexts = new HashMap<>();
            golden.questions().forEach(q -> contexts.put(q.question(), q.context()));
            BoundaryCases.load(f.root.resolve(doc.input("boundaryCases"))).forEach(c -> contexts.put(c.path("question").asText(), c.path("context")));
            Set<String> audited = auditShas(f); Map<String,List<Map<String,String>>> firstByQuestion = new HashMap<>();
            int assembled = 0, retried = 0;
            for (OllamaStub.Request r : F0StructuredOutputDiagnosticTest.chats(stub)) {
                List<Map<String,String>> sent = messages(r);
                // 호출 직전 audit 계수 표와 실제 전송 메시지가 같은 SHA 입니다.
                assertThat(audited).contains(InputAudit.messagesSha256(sent));
                if (sent.size() < 2 || !sent.get(1).get("content").startsWith("{")) continue;
                assembled++;
                JsonNode user = user(r); String question = user.path("question").asText();
                assertThat(GoldenSet.keys(user)).containsExactlyInAnyOrder("context", "question", "qRefCatalog");
                assertThat(GoldenSet.keys(user.path("context"))).containsExactlyInAnyOrder("timezone", "referenceTime");
                JsonNode original = contexts.get(question);
                assertThat(original).as("등록 질문 원문 그대로").isNotNull();
                assertThat(user.path("context").path("timezone")).isEqualTo(original.path("timezone")); assertThat(user.path("context").path("referenceTime")).isEqualTo(original.path("referenceTime"));
                assertThat(user.path("qRefCatalog")).isEqualTo(QRefContext.extract(question).catalog());
                assertThat(sent.get(1).get("content")).doesNotContain("timeWindow", "selectedResourceRefs");
                String key = r.body().path("max_tokens").asInt() + "/" + question;
                if (sent.size() == 2) firstByQuestion.putIfAbsent(key, sent);
                else {
                    retried++;
                    // 재시도는 같은 투영 초회 메시지 + 고정 오류 코드 한 줄입니다.
                    List<Map<String,String>> first = firstByQuestion.get(key);
                    assertThat(ClassificationJudge.RETRY_ERROR_CODES.stream().map(code -> MessageAssembler.retry(first, code)).toList()).contains(sent);
                }
            }
            assertThat(assembled).isPositive(); assertThat(retried).isPositive();
        }
        // 원 Context(선택 자원·시간 창)는 파일·메모리 모두 그대로입니다.
        assertThat(Json.bytes(f.root.resolve(doc.input("golden")))).isEqualTo(goldenBytes); assertThat(Json.bytes(f.root.resolve(doc.input("boundaryCases")))).isEqualTo(casesBytes);
        GoldenSet.Question ac38 = golden.questions().stream().filter(q -> q.category().equals("AC38_CROSS_SYSTEM_NAME")).findFirst().orElseThrow();
        assertThat(GoldenSet.ac38Context(ac38.context())).isTrue();
        assertThat(F0StructuredOutputDiagnosticTest.golden(f, doc).byId(ac38.id()).context()).isEqualTo(ac38.context());
        assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void legacyRegistrationWithoutTheKeySendsTheOriginalFullContextBytes() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = projected(f, "DIAGNOSTIC", null, MessageAssembler.FULL);
        assertThat(doc.contextProjection()).isEqualTo(MessageAssembler.FULL);
        Map<String,JsonNode> fileContexts = new HashMap<>();
        for (String line : f.text(doc.input("golden")).split("\n")) if (!line.isBlank()) { JsonNode row = Json.parse(line.getBytes(StandardCharsets.UTF_8)); fileContexts.put(row.path("question").asText(), row.path("context")); }
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            int withWindow = 0;
            for (OllamaStub.Request r : F0StructuredOutputDiagnosticTest.chats(stub)) {
                String question = OllamaStub.question(r.body());
                // 조립기와 독립적으로 파일의 Context 원본으로 만든 기존 user 메시지 바이트와 정확히 같습니다.
                ObjectNode expected = Json.MAPPER.createObjectNode(); expected.set("context", fileContexts.get(question)); expected.put("question", question); expected.set("qRefCatalog", QRefContext.extract(question).catalog());
                assertThat(r.body().path("messages").get(1).path("content").asText()).isEqualTo(Json.canonicalString(expected));
                assertThat(GoldenSet.keys(user(r).path("context"))).containsExactlyInAnyOrder("timezone", "referenceTime", "timeWindow", "selectedResourceRefs");
                if (!user(r).path("context").path("timeWindow").isNull()) withWindow++;
            }
            assertThat(withWindow).isPositive();
        }
    }

    @Test void projectionKeyIsAV3OptInWithOneValueAndGateKeepsR5() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = projected(f, "DIAGNOSTIC", MessageAssembler.EXTRACTION_V1, MessageAssembler.EXTRACTION_V1);
        ObjectNode valid = (ObjectNode)doc.block().deepCopy();
        List<java.util.function.Consumer<ObjectNode>> invalid = List.of(
            b -> p(b).put("contextProjection", "FULL"), b -> p(b).put("contextProjection", "extraction_v1"), b -> p(b).put("contextProjection", "EXTRACTION_V2"),
            b -> p(b).put("contextProjection", ""), b -> p(b).put("contextProjection", 1), b -> p(b).putNull("contextProjection"), b -> p(b).putObject("contextProjection"),
            // v1/v2 계약에는 같은 키를 둘 수 없습니다.
            b -> { b.put("schema", "f0-prereg-v2"); b.remove("purpose"); p(b).remove("responseFormat"); },
            // 정식 GATE 는 투영과 무관하게 R=5 만 유효합니다.
            b -> { b.put("purpose", "GATE"); p(b).put("repeats", 1); });
        for (var change : invalid) {
            ObjectNode block = valid.deepCopy(); change.accept(block); F0VersionedInputsTest.writePrereg(f, block);
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        ObjectNode v2 = valid.deepCopy(); v2.put("schema", "f0-prereg-v2"); v2.remove("purpose"); p(v2).remove("responseFormat"); p(v2).remove("contextProjection");
        F0VersionedInputsTest.writePrereg(f, v2); assertThat(Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG)).contextProjection()).isEqualTo(MessageAssembler.FULL);
        ObjectNode gate = valid.deepCopy(); gate.put("purpose", "GATE"); p(gate).put("repeats", Prereg.GATE_REPEATS);
        F0VersionedInputsTest.writePrereg(f, gate);
        Prereg.Document gateDoc = Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG));
        assertThat(gateDoc.repeats()).isEqualTo(5); assertThat(gateDoc.contextProjection()).isEqualTo(MessageAssembler.EXTRACTION_V1);
    }
    private static ObjectNode p(ObjectNode block) { return (ObjectNode)block.path("parameters"); }

    @Test void auditMadeWithAnotherProjectionOrACliOverrideIsRejectedBeforeAnyCall() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        for (String[] pair : List.of(new String[]{MessageAssembler.EXTRACTION_V1, MessageAssembler.FULL}, new String[]{null, MessageAssembler.EXTRACTION_V1})) {
            Prereg.Document doc = projected(f, "DIAGNOSTIC", pair[0], pair[1]);
            try (OllamaStub stub = new OllamaStub()) {
                stub.chat = StubAnswers.correct(f.root);
                for (String route : List.of("A", "B")) { assertThat(F0VersionedInputsTest.run(f, stub, doc, route)).isEqualTo(2); assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING"); }
                assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/boundary")).isEqualTo(2);
                assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING");
                assertThat(F0VersionedInputsTest.execute(f, "usage-probe", "--endpoint", stub.endpoint(), "--messages", doc.input("usageMessages"), "--out", RESULTS + "/usage.json")).isEqualTo(2);
                assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING");
                assertThat(stub.requests).isEmpty();
            }
        }
        // 측정 명령에는 투영 override 인자가 없습니다(등록 parameters 만 사용).
        Prereg.Document doc = projected(f, "DIAGNOSTIC", MessageAssembler.EXTRACTION_V1, MessageAssembler.EXTRACTION_V1);
        try (OllamaStub stub = new OllamaStub()) {
            for (String value : List.of(MessageAssembler.FULL, MessageAssembler.EXTRACTION_V1)) {
                assertThat(F0VersionedInputsTest.execute(f, "run", "--path", "B", "--endpoint", stub.endpoint(), "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/B", "--context-projection", value)).isEqualTo(2);
                assertThat(f.lastError).isEqualTo("INVALID_ARGUMENTS");
            }
            assertThat(stub.requests).isEmpty();
        }
        assertThat(f.root.resolve("eval/f0/results")).doesNotExist();
    }

    @Test void inputAuditRenderUsesTheSameAssemblyAsMeasurementAndAcceptsOnlyRegistrableProjections() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        String template = f.json(ChatRenderers.REGISTRY).path("renderers").get(0).path("templateSha256").asText();
        java.util.function.Function<String,Path> role = r -> f.root.resolve(doc.input(r));
        for (String projection : List.of(MessageAssembler.FULL, MessageAssembler.EXTRACTION_V1)) {
            String out = "render-" + projection + ".jsonl";
            List<String> args = new ArrayList<>(List.of("input-audit-render", "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--classify-prompt", doc.input("classifyPrompt"), "--plan-prompt", doc.input("planPrompt"),
                "--cases", doc.input("boundaryCases"), "--usage-messages", doc.input("usageMessages"), "--token-messages", doc.input("tokenMessages"), "--renderers", ChatRenderers.REGISTRY, "--template-sha256", template, "--out", out));
            if (!projection.equals(MessageAssembler.FULL)) args.addAll(List.of("--context-projection", projection));
            assertThat(f.exec(args.toArray(String[]::new))).isZero();
            RegistryFixture fixture = RegistryFixture.load(role.apply("fixture")); GoldenSet golden = GoldenSet.load(role.apply("golden"), fixture);
            MessageAssembler classify = MessageAssembler.load(role.apply("classifyPrompt"), fixture, projection), plan = MessageAssembler.load(role.apply("planPrompt"), fixture, projection);
            List<InputAudit.Planned> planned = new ArrayList<>(InputAudit.run(golden, plan, Prereg.Budget.V2, "A"));
            planned.addAll(InputAudit.run(golden, classify, Prereg.Budget.V2, "B")); planned.addAll(InputAudit.boundary(BoundaryCases.load(role.apply("boundaryCases")), classify, Prereg.Budget.V2));
            for (String use : List.of("usage", "token")) planned.addAll(InputAudit.probes(use, F0RunnerCli.messages(role.apply(use + "Messages"), role.apply("fixture"), role.apply("classifyPrompt"), role.apply("golden"), true, projection)));
            List<String> lines = Arrays.stream(f.text(out).split("\n")).filter(l -> !l.isBlank()).toList();
            assertThat(lines).hasSize(planned.size());
            int withWindowKey = 0;
            for (int i = 0; i < lines.size(); i++) {
                JsonNode row = Json.parse(lines.get(i).getBytes(StandardCharsets.UTF_8));
                assertThat(row.path("id").asText()).isEqualTo(planned.get(i).id()); assertThat(row.path("messagesSha256").asText()).isEqualTo(InputAudit.messagesSha256(planned.get(i).messages()));
                if (row.path("rendered").asText().contains("\"timeWindow\":")) withWindowKey++;
            }
            // 렌더 문자열의 Context JSON 키 기준입니다(프롬프트 본문의 낱말은 무관).
            if (projection.equals(MessageAssembler.FULL)) assertThat(withWindowKey).isPositive(); else assertThat(withWindowKey).isZero();
        }
        for (String bad : List.of(MessageAssembler.FULL, "NONE", "extraction_v1")) {
            assertThat(f.exec("input-audit-render", "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--classify-prompt", doc.input("classifyPrompt"), "--plan-prompt", doc.input("planPrompt"),
                "--cases", doc.input("boundaryCases"), "--usage-messages", doc.input("usageMessages"), "--token-messages", doc.input("tokenMessages"), "--renderers", ChatRenderers.REGISTRY, "--template-sha256", template, "--context-projection", bad, "--out", "render-bad.jsonl")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("ARGUMENT_INVALID");
        }
        assertThat(f.root.resolve("render-bad.jsonl")).doesNotExist();
    }

    @Test void assemblerProjectionCopiesOnlyTheTimeBasisWithoutMutatingAndFailsClosed() {
        RegistryFixture fixture = RegistryFixture.load(Path.of(System.getProperty("aigw.rootDir")).resolve("eval/golden/tc-registry-fixture-v2.json"));
        byte[] prompt = "시험 프롬프트\n{{REGISTRY}}\n".getBytes(StandardCharsets.UTF_8);
        JsonNode context = Json.parse("{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00.123456789+09:00\",\"timeWindow\":{\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-04T00:00\"},\"selectedResourceRefs\":[{\"type\":\"EQP\",\"id\":\"X\"}]}".getBytes(StandardCharsets.UTF_8));
        JsonNode before = context.deepCopy(); QRefContext refs = QRefContext.extract("\"a\" 로그");
        MessageAssembler full = new MessageAssembler(prompt, fixture), legacy = new MessageAssembler(prompt, fixture, MessageAssembler.FULL), projected = new MessageAssembler(prompt, fixture, MessageAssembler.EXTRACTION_V1);
        assertThat(legacy.messages("\"a\" 로그", context, refs)).isEqualTo(full.messages("\"a\" 로그", context, refs));
        List<Map<String,String>> messages = projected.messages("\"a\" 로그", context, refs);
        assertThat(messages.get(0)).isEqualTo(full.messages("\"a\" 로그", context, refs).get(0));
        assertThat(messages.get(1).get("content")).isEqualTo("{\"context\":{\"referenceTime\":\"2026-10-20T10:00:00.123456789+09:00\",\"timezone\":\"+09:00\"},\"qRefCatalog\":[\"Q1\"],\"question\":\"\\\"a\\\" 로그\"}");
        assertThat(context).isEqualTo(before);
        for (String json : List.of("{\"timezone\":\"+09:00\"}", "{\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timezone\":null}", "{\"referenceTime\":1,\"timezone\":\"+09:00\"}")) {
            JsonNode bad = Json.parse(json.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> projected.messages("x", bad, QRefContext.extract("x"))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("CONTEXT_PROJECTION_INVALID"));
        }
        for (String bad : List.of("NONE", "", "extraction_v1")) assertThatThrownBy(() -> new MessageAssembler(prompt, fixture, bad)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("CONTEXT_PROJECTION_INVALID"));
    }

    @Test void judgementAndDiagnosticsUseTheOriginalContextAndDiagnosticRegistrationStillCannotProduceGateEvidence() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = projected(f, "DIAGNOSTIC", MessageAssembler.EXTRACTION_V1, MessageAssembler.EXTRACTION_V1);
        GoldenSet golden = F0StructuredOutputDiagnosticTest.golden(f, doc);
        GoldenSet.Question ac38 = golden.questions().stream().filter(q -> q.category().equals("AC38_CROSS_SYSTEM_NAME")).findFirst().orElseThrow();
        try (OllamaStub stub = new OllamaStub()) {
            var correct = StubAnswers.correct(f.root);
            // AC38 문항만 원 Context 의 선택 자원·시간 창을 복사한 답(과거 실패 유형)을 돌려줍니다. 요청에는 그 값이 없습니다.
            stub.chat = body -> {
                if (!OllamaStub.question(body).equals(ac38.question())) return correct.apply(body);
                ObjectNode answer = Json.MAPPER.createObjectNode(); answer.put("t", "INC"); answer.put("eqp", ac38.context().path("selectedResourceRefs").get(0).path("id").asText());
                answer.put("from", ac38.context().path("timeWindow").path("from").asText()); answer.put("to", ac38.context().path("timeWindow").path("to").asText()); answer.putNull("qRef");
                return OllamaStub.chatResponse(Json.canonicalString(answer), "stop", 300, 30);
            };
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            List<OllamaStub.Request> ac38Requests = F0StructuredOutputDiagnosticTest.chats(stub).stream().filter(r -> OllamaStub.question(r.body()).equals(ac38.question())).toList();
            assertThat(ac38Requests).isNotEmpty().allSatisfy(r -> assertThat(r.body().path("messages").get(1).path("content").asText()).doesNotContain("timeWindow", "selectedResourceRefs"));
        }
        int ac38Rows = 0;
        for (JsonNode row : f.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            if (!row.path("questionId").asText().equals(ac38.id())) continue;
            ac38Rows++;
            // 판정 oracle(기대 null)과 진단(원 Context 대조)은 투영 전 전체 Context 기준 그대로입니다.
            assertThat(row.path("diagnostics").path("argumentMatches").path("eqp").asBoolean()).isFalse(); assertThat(row.path("diagnostics").path("argumentMatches").path("from").asBoolean()).isFalse();
            JsonNode first = row.path("attemptDiagnostics").get(0);
            assertThat(first.path("eqp").path("equalsContext").asBoolean()).isTrue(); assertThat(first.path("from").path("equalsContext").asBoolean()).isTrue(); assertThat(first.path("to").path("equalsContext").asBoolean()).isTrue();
        }
        assertThat(ac38Rows).isEqualTo(doc.repeats());
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", RUN, "--results", RESULTS, "--out", "evidence/F0")).isEqualTo(2);
        assertThat(f.lastError).isEqualTo("DIAGNOSTIC_NOT_GATE_ELIGIBLE");
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", RUN, "--results", RESULTS)).isZero();
        assertThat(f.root.resolve("evidence")).doesNotExist();
        assertThat(f.text(RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT)).contains("contextProjection `EXTRACTION_V1`", ResponseSchema.POLICY);
        assertThat(f.json(RESULTS + "/B/run-binding.json").path("parameterManifest").path("contextProjection").asText()).isEqualTo(MessageAssembler.EXTRACTION_V1);
    }
}
