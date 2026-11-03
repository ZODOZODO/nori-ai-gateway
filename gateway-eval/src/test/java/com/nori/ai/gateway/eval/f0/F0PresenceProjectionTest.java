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
 * v3 opt-in Context 투영 PRESENCE_V1 의 조립·등록·audit 결속(LLM_STUB — 실측 아님).
 * PRESENCE_V1 은 선택 자원 ID·시간 창 값을 지우고 "선택돼 있다"는 신호(자원 type·timeWindow=true)만 남깁니다.
 * 값이 없는 Context(timeWindow=null·selectedResourceRefs=[])는 FULL 과 바이트가 같아야 하며, 기존 FULL·EXTRACTION_V1 조립은 바뀌지 않아야 합니다.
 */
class F0PresenceProjectionTest {
    static final String RUN = F0VersionedInputsTest.RUN, RESULTS = "eval/f0/results/" + RUN;
    @TempDir Path directory;

    /** 시험 쪽에서 독립적으로 다시 적은 PRESENCE_V1 규칙(조립기 코드를 호출하지 않습니다). */
    static ObjectNode presence(JsonNode context) {
        ObjectNode p = Json.MAPPER.createObjectNode();
        p.set("timezone", context.get("timezone")); p.set("referenceTime", context.get("referenceTime"));
        if (context.get("timeWindow").isNull()) p.putNull("timeWindow"); else p.put("timeWindow", true);
        ArrayNode refs = p.putArray("selectedResourceRefs");
        context.get("selectedResourceRefs").forEach(r -> refs.addObject().put("type", r.get("type").asText()));
        return p;
    }
    static boolean emptyContext(JsonNode context) { return context.path("timeWindow").isNull() && context.path("selectedResourceRefs").isEmpty(); }

    @Test void assemblerMasksValuesKeepsPresenceEqualsFullWhenEmptyAndFailsClosed() {
        RegistryFixture fixture = RegistryFixture.load(Path.of(System.getProperty("aigw.rootDir")).resolve("eval/golden/tc-registry-fixture-v2.json"));
        byte[] prompt = "시험 프롬프트\n{{REGISTRY}}\n".getBytes(StandardCharsets.UTF_8);
        JsonNode selected = Json.parse("{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-04T09:00:00+09:00\",\"timeWindow\":{\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-04T00:00\"},\"selectedResourceRefs\":[{\"type\":\"EQP\",\"id\":\"PHOTO_001\"}]}".getBytes(StandardCharsets.UTF_8));
        JsonNode empty = Json.parse("{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null,\"selectedResourceRefs\":[]}".getBytes(StandardCharsets.UTF_8));
        JsonNode before = selected.deepCopy(); QRefContext refs = QRefContext.extract("\"a\" 로그");
        MessageAssembler full = new MessageAssembler(prompt, fixture), presence = new MessageAssembler(prompt, fixture, MessageAssembler.PRESENCE_V1);
        assertThat(presence.contextProjection()).isEqualTo(MessageAssembler.PRESENCE_V1);
        List<Map<String,String>> masked = presence.messages("\"a\" 로그", selected, refs);
        assertThat(masked.get(0)).isEqualTo(full.messages("\"a\" 로그", selected, refs).get(0));
        // 값(자원 ID·창 경계 시각)은 없고 존재 신호만 남습니다. 키 집합은 원 Context 와 같습니다.
        assertThat(masked.get(1).get("content")).isEqualTo("{\"context\":{\"referenceTime\":\"2026-10-04T09:00:00+09:00\",\"selectedResourceRefs\":[{\"type\":\"EQP\"}],\"timeWindow\":true,\"timezone\":\"+09:00\"},\"qRefCatalog\":[\"Q1\"],\"question\":\"\\\"a\\\" 로그\"}");
        assertThat(masked.get(1).get("content")).doesNotContain("PHOTO_001", "2026-10-03T00:00", "2026-10-04T00:00\"");
        assertThat(selected).isEqualTo(before);
        // 값이 없는 Context 는 FULL 과 같은 바이트입니다(2인자 질문 메시지도 같음).
        assertThat(presence.messages("\"a\" 로그", empty, refs)).isEqualTo(full.messages("\"a\" 로그", empty, refs));
        assertThat(presence.messages("질문", empty)).isEqualTo(full.messages("질문", empty));
        List<String> invalid = List.of(
            "{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null}",
            "{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null,\"selectedResourceRefs\":[],\"extra\":\"PHOTO_001\"}",
            "{\"timezone\":null,\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null,\"selectedResourceRefs\":[]}",
            "{\"timezone\":\"+09:00\",\"referenceTime\":1,\"timeWindow\":null,\"selectedResourceRefs\":[]}",
            "{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":\"2026-10-03T00:00\",\"selectedResourceRefs\":[]}",
            "{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null,\"selectedResourceRefs\":{}}",
            "{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null,\"selectedResourceRefs\":[{\"id\":\"PHOTO_001\"}]}",
            "{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null,\"selectedResourceRefs\":[\"EQP\"]}");
        for (String json : invalid) {
            JsonNode bad = Json.parse(json.getBytes(StandardCharsets.UTF_8));
            assertThatThrownBy(() -> presence.messages("x", bad, QRefContext.extract("x"))).as(json).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("CONTEXT_PROJECTION_INVALID"));
        }
        for (String bad : List.of("presence_v1", "PRESENCE", "PRESENCE_V2")) assertThatThrownBy(() -> new MessageAssembler(prompt, fixture, bad)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("CONTEXT_PROJECTION_INVALID"));
    }

    @Test void existingFullAndExtractionAssembliesAreUnchangedForTheRealRegisteredInputs() throws Exception {
        Path root = Path.of(System.getProperty("aigw.rootDir"));
        RegistryFixture fixture = RegistryFixture.load(root.resolve("eval/golden/tc-registry-fixture-v2.json"));
        GoldenSet golden = GoldenSet.load(root.resolve("eval/f0/v3/golden-regression-v3.jsonl"), fixture);
        MessageAssembler full = MessageAssembler.load(root.resolve("eval/f0/v3/prompts/classify-v3.txt"), fixture), extraction = MessageAssembler.load(root.resolve("eval/f0/v3/prompts/classify-v3-d.txt"), fixture, MessageAssembler.EXTRACTION_V1);
        int masked = 0;
        for (GoldenSet.Question q : golden.questions()) {
            QRefContext refs = QRefContext.extract(q.question());
            // FULL = 파일 Context 원본, EXTRACTION_V1 = 두 필드만. 조립기와 독립적으로 만든 기대 user 메시지와 바이트가 같습니다.
            ObjectNode fullUser = Json.MAPPER.createObjectNode(); fullUser.set("context", q.context()); fullUser.put("question", q.question()); fullUser.set("qRefCatalog", refs.catalog());
            ObjectNode extractionContext = Json.MAPPER.createObjectNode(); extractionContext.set("timezone", q.context().get("timezone")); extractionContext.set("referenceTime", q.context().get("referenceTime"));
            ObjectNode extractionUser = fullUser.deepCopy(); extractionUser.set("context", extractionContext);
            assertThat(full.messages(q.question(), q.context(), refs).get(1).get("content")).isEqualTo(Json.canonicalString(fullUser));
            assertThat(extraction.messages(q.question(), q.context(), refs).get(1).get("content")).isEqualTo(Json.canonicalString(extractionUser));
            // 같은 프롬프트에서 PRESENCE_V1 은 값 없는 Context 면 FULL 과 같고, 값이 있으면 다릅니다(값 복사 경로 0).
            MessageAssembler presence = MessageAssembler.load(root.resolve("eval/f0/v3/prompts/classify-v3.txt"), fixture, MessageAssembler.PRESENCE_V1);
            List<Map<String,String>> p = presence.messages(q.question(), q.context(), refs), f = full.messages(q.question(), q.context(), refs);
            if (emptyContext(q.context())) assertThat(p).as(q.id()).isEqualTo(f);
            else {
                masked++;
                assertThat(p).as(q.id()).isNotEqualTo(f);
                JsonNode user = Json.parse(p.get(1).get("content").getBytes(StandardCharsets.UTF_8));
                assertThat(user.path("context")).as(q.id()).isEqualTo(presence(q.context()));
                for (JsonNode ref : q.context().path("selectedResourceRefs")) if (!q.question().contains(ref.path("id").asText())) assertThat(p.get(1).get("content")).as(q.id()).doesNotContain(ref.path("id").asText());
                if (!q.context().path("timeWindow").isNull()) assertThat(p.get(1).get("content")).as(q.id()).doesNotContain(q.context().path("timeWindow").path("from").asText(), q.context().path("timeWindow").path("to").asText());
            }
        }
        // 실제 회귀 골든 30 중 값 있는 Context 는 G08·G20·R03 세 문항입니다.
        assertThat(masked).isEqualTo(3);
        // 경계 9케이스는 모두 값 없는 Context 라 PRESENCE_V1 조립이 FULL 과 바이트가 같습니다.
        MessageAssembler fullCases = MessageAssembler.load(root.resolve("eval/f0/v3/prompts/classify-v3.txt"), fixture), presenceCases = MessageAssembler.load(root.resolve("eval/f0/v3/prompts/classify-v3.txt"), fixture, MessageAssembler.PRESENCE_V1);
        List<JsonNode> cases = BoundaryCases.load(root.resolve("eval/f0/v2/output-boundary-cases.jsonl"));
        assertThat(cases).hasSize(9);
        for (JsonNode c : cases) {
            QRefContext refs = QRefContext.extract(c.path("question").asText());
            assertThat(presenceCases.messages(c.path("question").asText(), c.path("context"), refs)).as(c.path("caseId").asText()).isEqualTo(fullCases.messages(c.path("question").asText(), c.path("context"), refs));
        }
    }

    @Test void presenceIsAV3OptInRegistrationValueAlongsideExtraction() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        Prereg.Document doc = F0ContextProjectionTest.projected(f, "DIAGNOSTIC", MessageAssembler.PRESENCE_V1, MessageAssembler.PRESENCE_V1);
        assertThat(doc.contextProjection()).isEqualTo(MessageAssembler.PRESENCE_V1);
        assertThat(Prereg.CONTEXT_PROJECTIONS).containsExactlyInAnyOrder(MessageAssembler.EXTRACTION_V1, MessageAssembler.PRESENCE_V1);
        ObjectNode valid = (ObjectNode)doc.block().deepCopy();
        for (var change : List.<java.util.function.Consumer<ObjectNode>>of(
                b -> p(b).put("contextProjection", "presence_v1"), b -> p(b).put("contextProjection", "PRESENCE_V2"), b -> p(b).put("contextProjection", MessageAssembler.FULL),
                b -> { b.put("schema", "f0-prereg-v2"); b.remove("purpose"); p(b).remove("responseFormat"); },
                b -> { b.put("purpose", "GATE"); p(b).put("repeats", 1); })) {
            ObjectNode block = valid.deepCopy(); change.accept(block); F0VersionedInputsTest.writePrereg(f, block);
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        ObjectNode gate = valid.deepCopy(); gate.put("purpose", "GATE"); p(gate).put("repeats", Prereg.GATE_REPEATS); F0VersionedInputsTest.writePrereg(f, gate);
        assertThat(Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG)).contextProjection()).isEqualTo(MessageAssembler.PRESENCE_V1);
    }
    private static ObjectNode p(ObjectNode block) { return (ObjectNode)block.path("parameters"); }

    @Test void presenceRequestsMaskValuesAndRetryAndPreCountUseTheSameMessages() throws Exception {
        F0TestRoot f; Prereg.Document doc; GoldenSet golden; byte[] goldenBytes;
        try (OllamaStub stub = new OllamaStub()) {
            f = UsageProbeTest.rendererRegistered(directory, stub);
            doc = F0ContextProjectionTest.projected(f, "DIAGNOSTIC", MessageAssembler.PRESENCE_V1, MessageAssembler.PRESENCE_V1);
            golden = F0StructuredOutputDiagnosticTest.golden(f, doc); goldenBytes = Json.bytes(f.root.resolve(doc.input("golden")));
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
            Set<String> audited = F0ContextProjectionTest.auditShas(f); Map<String,List<Map<String,String>>> firstByQuestion = new HashMap<>();
            int assembled = 0, retried = 0, masked = 0;
            for (OllamaStub.Request r : F0StructuredOutputDiagnosticTest.chats(stub)) {
                List<Map<String,String>> sent = F0ContextProjectionTest.messages(r);
                assertThat(audited).contains(InputAudit.messagesSha256(sent));
                if (sent.size() < 2 || !sent.get(1).get("content").startsWith("{")) continue;
                assembled++;
                JsonNode user = F0ContextProjectionTest.user(r); String question = user.path("question").asText();
                JsonNode original = contexts.get(question);
                assertThat(original).as("등록 질문 원문 그대로").isNotNull();
                assertThat(user.path("context")).isEqualTo(presence(original));
                assertThat(user.path("qRefCatalog")).isEqualTo(QRefContext.extract(question).catalog());
                if (emptyContext(original)) {
                    // 값 없는 Context 는 FULL 조립과 바이트가 같습니다(조립기 독립 구성).
                    ObjectNode expected = Json.MAPPER.createObjectNode(); expected.set("context", original); expected.put("question", question); expected.set("qRefCatalog", QRefContext.extract(question).catalog());
                    assertThat(sent.get(1).get("content")).isEqualTo(Json.canonicalString(expected));
                } else {
                    masked++;
                    assertThat(sent.get(1).get("content")).doesNotContain("\"id\"", "\"from\"", "\"to\"");
                }
                String key = r.body().path("max_tokens").asInt() + "/" + question;
                if (sent.size() == 2) firstByQuestion.putIfAbsent(key, sent);
                else {
                    retried++;
                    List<Map<String,String>> first = firstByQuestion.get(key);
                    assertThat(ClassificationJudge.RETRY_ERROR_CODES.stream().map(code -> MessageAssembler.retry(first, code)).toList()).contains(sent);
                }
            }
            assertThat(assembled).isPositive(); assertThat(retried).isPositive(); assertThat(masked).isPositive();
        }
        assertThat(Json.bytes(f.root.resolve(doc.input("golden")))).isEqualTo(goldenBytes);
        assertThat(f.json(RESULTS + "/B/run-binding.json").path("parameterManifest").path("contextProjection").asText()).isEqualTo(MessageAssembler.PRESENCE_V1);
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", RUN, "--results", RESULTS)).isZero();
        assertThat(f.text(RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT)).contains("contextProjection `PRESENCE_V1`");
        assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void auditMadeWithAnotherProjectionIsRejectedBeforeAnyCall() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        // PRESENCE_V1 등록 + EXTRACTION_V1 audit: 모든 조립 메시지가 달라 A/B·경계·usage 모두 호출 전 거부.
        Prereg.Document doc = F0ContextProjectionTest.projected(f, "DIAGNOSTIC", MessageAssembler.PRESENCE_V1, MessageAssembler.EXTRACTION_V1);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            for (String route : List.of("A", "B")) { assertThat(F0VersionedInputsTest.run(f, stub, doc, route)).isEqualTo(2); assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING"); }
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/boundary")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING");
            assertThat(F0VersionedInputsTest.execute(f, "usage-probe", "--endpoint", stub.endpoint(), "--messages", doc.input("usageMessages"), "--out", RESULTS + "/usage.json")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING");
            assertThat(stub.requests).isEmpty();
        }
        // PRESENCE_V1 등록 + FULL audit: 값 있는 Context 문항(AC38 등)의 메시지가 표에 없어 A/B 가 호출 전 거부됩니다.
        doc = F0ContextProjectionTest.projected(f, "DIAGNOSTIC", MessageAssembler.PRESENCE_V1, MessageAssembler.FULL);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            for (String route : List.of("A", "B")) { assertThat(F0VersionedInputsTest.run(f, stub, doc, route)).isEqualTo(2); assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING"); }
            assertThat(stub.requests).isEmpty();
        }
        // 기존 등록(키 없음 = FULL) + PRESENCE_V1 audit 도 같은 이유로 거부됩니다.
        doc = F0ContextProjectionTest.projected(f, "DIAGNOSTIC", null, MessageAssembler.PRESENCE_V1);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            for (String route : List.of("A", "B")) { assertThat(F0VersionedInputsTest.run(f, stub, doc, route)).isEqualTo(2); assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING"); }
            // 측정 명령에는 투영 override 인자가 없습니다.
            assertThat(F0VersionedInputsTest.execute(f, "run", "--path", "B", "--endpoint", stub.endpoint(), "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/B", "--context-projection", MessageAssembler.PRESENCE_V1)).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("INVALID_ARGUMENTS");
            assertThat(stub.requests).isEmpty();
        }
        assertThat(f.root.resolve("eval/f0/results")).doesNotExist();
    }

    @Test void inputAuditRenderWithPresenceMatchesTheMeasurementAssembly() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        String template = f.json(ChatRenderers.REGISTRY).path("renderers").get(0).path("templateSha256").asText();
        java.util.function.Function<String,Path> role = r -> f.root.resolve(doc.input(r));
        assertThat(f.exec("input-audit-render", "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--classify-prompt", doc.input("classifyPrompt"), "--plan-prompt", doc.input("planPrompt"),
            "--cases", doc.input("boundaryCases"), "--usage-messages", doc.input("usageMessages"), "--token-messages", doc.input("tokenMessages"), "--renderers", ChatRenderers.REGISTRY, "--template-sha256", template,
            "--context-projection", MessageAssembler.PRESENCE_V1, "--out", "render-presence.jsonl")).isZero();
        RegistryFixture fixture = RegistryFixture.load(role.apply("fixture")); GoldenSet golden = GoldenSet.load(role.apply("golden"), fixture);
        MessageAssembler classify = MessageAssembler.load(role.apply("classifyPrompt"), fixture, MessageAssembler.PRESENCE_V1), plan = MessageAssembler.load(role.apply("planPrompt"), fixture, MessageAssembler.PRESENCE_V1);
        List<InputAudit.Planned> planned = new ArrayList<>(InputAudit.run(golden, plan, Prereg.Budget.V2, "A"));
        planned.addAll(InputAudit.run(golden, classify, Prereg.Budget.V2, "B")); planned.addAll(InputAudit.boundary(BoundaryCases.load(role.apply("boundaryCases")), classify, Prereg.Budget.V2));
        for (String use : List.of("usage", "token")) planned.addAll(InputAudit.probes(use, F0RunnerCli.messages(role.apply(use + "Messages"), role.apply("fixture"), role.apply("classifyPrompt"), role.apply("golden"), true, MessageAssembler.PRESENCE_V1)));
        List<String> lines = Arrays.stream(f.text("render-presence.jsonl").split("\n")).filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(planned.size());
        int masked = 0;
        for (int i = 0; i < lines.size(); i++) {
            JsonNode row = Json.parse(lines.get(i).getBytes(StandardCharsets.UTF_8));
            assertThat(row.path("id").asText()).isEqualTo(planned.get(i).id()); assertThat(row.path("messagesSha256").asText()).isEqualTo(InputAudit.messagesSha256(planned.get(i).messages()));
            if (row.path("rendered").asText().contains("\"timeWindow\":true")) masked++;
        }
        assertThat(masked).isPositive();
    }
}
