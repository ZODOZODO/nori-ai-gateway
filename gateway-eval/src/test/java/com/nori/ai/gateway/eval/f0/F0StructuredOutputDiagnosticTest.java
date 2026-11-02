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

/** v3 요청별 response_format·fallback 거부·원문 없는 시도별 진단·진단 등록의 게이트 차단(LLM_STUB — 실측 아님). */
class F0StructuredOutputDiagnosticTest {
    static final String RUN = F0VersionedInputsTest.RUN, RESULTS = "eval/f0/results/" + RUN;
    static final Set<String> LEGACY_BODY = Set.of("model", "messages", "temperature", "max_tokens", "stream");
    @TempDir Path directory;

    /** 시험 v2 등록을 v3 로 바꿉니다(입력 바이트 동일, 목적·출력 제약만 선언). */
    static Prereg.Document v3(F0TestRoot f, String purpose, String format) throws Exception {
        ObjectNode block = (ObjectNode)F0VersionedInputsTest.versioned(f).block().deepCopy();
        block.put("schema", "f0-prereg-v3"); block.put("purpose", purpose); ((ObjectNode)block.path("parameters")).put("responseFormat", format);
        // 정식 GATE 등록은 R=5 만 유효하므로 시험 등록도 실제 명령과 같은 회차 수를 씁니다(DIAGNOSTIC 은 기존 R=2 시험 그대로).
        if (purpose.equals("GATE")) ((ObjectNode)block.path("parameters")).put("repeats", Prereg.GATE_REPEATS);
        F0VersionedInputsTest.writePrereg(f, block);
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))));
        F0VersionedInputsTest.writePrereg(f, block);
        return Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG));
    }
    static List<OllamaStub.Request> chats(OllamaStub stub) { return stub.requests.stream().filter(r -> r.path().equals("/v1/chat/completions")).toList(); }
    static GoldenSet golden(F0TestRoot f, Prereg.Document doc) { return GoldenSet.load(f.root.resolve(doc.input("golden")), RegistryFixture.load(f.root.resolve(doc.input("fixture")))); }

    @Test void schemaModeSendsTheSameRequestBoundSchemaOnFirstAndRetryWithoutAnswersOrCatalogValues() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v3(f, "DIAGNOSTIC", ResponseSchema.POLICY);
        try (OllamaStub stub = new OllamaStub()) {
            var correct = StubAnswers.correct(f.root);
            // 초회는 무효 JSON, 재시도는 정답 → 모든 문항이 재시도 요청을 보냅니다.
            stub.chat = body -> body.path("messages").size() == 2 ? OllamaStub.chatResponse("{}", "stop", 300, 2) : correct.apply(body);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "A")).isZero();
            Map<String,JsonNode> firstFormat = new HashMap<>();
            for (OllamaStub.Request r : chats(stub)) {
                Set<String> keys = new HashSet<>(LEGACY_BODY); keys.add("response_format");
                assertThat(GoldenSet.keys(r.body())).isEqualTo(keys);
                String question = OllamaStub.question(r.body());
                boolean plan = r.body().path("max_tokens").asInt() == Prereg.Budget.V2.planOutput();
                JsonNode format = r.body().path("response_format"), schema = format.path("json_schema").path("schema");
                assertThat(format).isEqualTo(ResponseSchema.responseFormat(plan ? MessageAssembler.Kind.PLAN : MessageAssembler.Kind.CLASSIFY, QRefContext.extract(question)));
                JsonNode catalog = QRefContext.extract(question).catalog();
                if (catalog.isEmpty()) assertThat(schema.path("properties").path("qRef")).isEqualTo(Json.MAPPER.createObjectNode().put("type", "null"));
                else assertThat(schema.path("properties").path("qRef").path("enum")).isEqualTo(catalog);
                assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
                // 정답·리소스·capability·후보 원문을 스키마에 넣어 통과시키지 않습니다.
                assertThat(format.toString()).doesNotContain("PHOTO_001", "EQP_001", "tc.", "CONNECTION_TIMEOUT", "EqpBindingService", "2026-", "INCIDENT_ANALYSIS");
                JsonNode previous = firstFormat.putIfAbsent((plan ? "A/" : "B/") + question, format);
                if (previous != null) assertThat(format).as("재시도도 같은 요청 바인딩 스키마").isEqualTo(previous);
            }
            assertThat(chats(stub)).hasSize(2 * 2 * 2 * golden(f, doc).questions().size());
        }
        for (JsonNode row : f.json(RESULTS + "/B/f0-results.json").path("attempts")) {
            assertThat(row.path("retried").asBoolean()).isTrue(); assertThat(row.path("status").asText()).isEqualTo("OK");
            String question = golden(f, doc).byId(row.path("questionId").asText()).question();
            assertThat(row.path("responseFormatSha256").asText()).isEqualTo(ResponseSchema.sha256(ResponseSchema.responseFormat(MessageAssembler.Kind.CLASSIFY, QRefContext.extract(question))));
            assertThat(row.path("attemptDiagnostics")).hasSize(2);
            assertThat(row.path("attemptDiagnostics").get(0).path("keys").asText()).isEqualTo("MISSING");
        }
    }

    @Test void plainRegistrationsKeepTheLegacyBodyAndSchemaModeNeverFallsBackWhenTheServerRejects() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            assertThat(chats(stub)).isNotEmpty().allSatisfy(r -> assertThat(GoldenSet.keys(r.body())).isEqualTo(LEGACY_BODY));
        }
        Files.walk(f.root.resolve("eval/f0/results")).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        doc = v3(f, "DIAGNOSTIC", "NONE");
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            assertThat(chats(stub)).isNotEmpty().allSatisfy(r -> assertThat(GoldenSet.keys(r.body())).isEqualTo(LEGACY_BODY));
            assertThat(f.json(RESULTS + "/B/f0-results.json").path("attempts").get(0).has("responseFormatSha256")).isFalse();
        }
        Files.walk(f.root.resolve("eval/f0/results")).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        doc = v3(f, "DIAGNOSTIC", ResponseSchema.POLICY);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            stub.rejectChat = body -> body.has("response_format");
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isEqualTo(5);
            assertThat(f.lastError).isEqualTo("ENDPOINT_HTTP_ERROR");
            // 거부 뒤 JSON 모드·비제약 재송신·재시도 0: chat 요청은 거부된 1건뿐입니다.
            assertThat(chats(stub)).hasSize(1).allSatisfy(r -> assertThat(r.body().has("response_format")).isTrue());
        }
        JsonNode partial = f.json(RESULTS + "/B/f0-results.json");
        assertThat(partial.path("complete").asBoolean()).isFalse(); assertThat(partial.path("abortReason").asText()).isEqualTo("ENDPOINT_HTTP_ERROR");
        assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void diagnosticsContinueAfterTheFirstStrictErrorWithoutChangingVerdictsDenominatorsOrLeakingOutput() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v3(f, "DIAGNOSTIC", "NONE");
        GoldenSet golden = golden(f, doc);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = body -> {
                String question = OllamaStub.question(body);
                if (question.equals("여기서 뭘 물어볼 수 있어?")) return OllamaStub.chatResponse("not json " + OllamaStub.SENTINEL, "stop", 300, 5);
                JsonNode context = golden.questions().stream().filter(q -> q.question().equals(question)).findFirst().orElseThrow().context();
                ObjectNode answer = Json.MAPPER.createObjectNode(); answer.put("t", "GQ");
                // Context 값을 복사하는 오류와 원문 없는 값(sentinel)을 섞습니다. qRef 는 후보가 없어도 Q1 입니다.
                JsonNode selected = context.path("selectedResourceRefs").path(0).path("id");
                answer.put("eqp", selected.isMissingNode() ? OllamaStub.SENTINEL : selected.asText());
                if (context.path("timeWindow").isNull()) answer.putNull("from"); else answer.put("from", context.path("timeWindow").path("from").asText());
                answer.putNull("to"); answer.put("qRef", "Q1");
                return OllamaStub.chatResponse(Json.canonicalString(answer), "stop", 300, 30);
            };
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
        }
        JsonNode result = f.json(RESULTS + "/B/f0-results.json");
        List<JsonNode> rows = new ArrayList<>(); result.path("attempts").forEach(rows::add);
        assertThat(rows).hasSize(golden.questions().size() * 2);
        String ac38 = golden.questions().stream().filter(q -> q.category().equals("AC38_CROSS_SYSTEM_NAME")).findFirst().orElseThrow().id();
        for (JsonNode row : rows) {
            GoldenSet.Question q = golden.byId(row.path("questionId").asText());
            boolean candidates = !QRefContext.extract(q.question()).catalog().isEmpty();
            JsonNode first = row.path("attemptDiagnostics").path(0);
            assertThat(row.path("attemptDiagnostics").size()).isEqualTo(row.path("finishReasons").size());
            if (q.id().equals("G05")) {
                assertThat(first.path("json").asText()).isEqualTo("PARSE_FAILED"); assertThat(first.path("keys").asText()).isEqualTo("NOT_EVALUABLE");
                for (String field : List.of("eqp", "from", "to")) assertThat(first.path(field).path("match").asText()).isEqualTo("NOT_EVALUABLE");
                continue;
            }
            assertThat(first.path("qRef").path("kind").asText()).isEqualTo(candidates ? "ID_IN_RANGE" : "ID_OUT_OF_RANGE");
            if (candidates) continue;
            // 엄격 판정은 QREF_INVALID 로 무효(재시도 포함)이고, 진단은 그 뒤 필드도 독립 기록합니다.
            assertThat(row.path("status").asText()).isEqualTo("INVALID"); assertThat(row.path("predicted").isNull()).isTrue();
            assertThat(row.path("errorCodes")).extracting(JsonNode::asText).containsExactly("QREF_INVALID", "QREF_INVALID");
            assertThat(row.path("diagnostics").path("argumentMatches")).allSatisfy(v -> assertThat(v.asBoolean()).isFalse());
            assertThat(first.path("qRef").path("candidateCount").asInt()).isZero(); assertThat(first.path("qRef").path("accepted").asBoolean()).isFalse();
            assertThat(first.path("t").path("value").asText()).isEqualTo("GQ");
            assertThat(first.path("t").path("match").asText()).isEqualTo(q.expected().t().equals("GQ") ? "MATCH" : "MISMATCH");
            assertThat(first.path("to").path("match").asText()).isEqualTo(q.expected().to() == null ? "MATCH" : "MISMATCH");
            assertThat(first.path("q").path("match").asText()).isEqualTo("NOT_EVALUABLE");
            assertThat(first.path("keys").asText()).isEqualTo("EXACT"); assertThat(first.path("rawQKey").asBoolean()).isFalse();
            if (q.id().equals(ac38)) {
                assertThat(first.path("eqp").path("equalsContext").asBoolean()).isTrue(); assertThat(first.path("eqp").path("inQuestion").asBoolean()).isFalse();
                assertThat(first.path("eqp").path("registered").asBoolean()).isTrue(); assertThat(first.path("eqp").path("match").asText()).isEqualTo("MISMATCH");
                assertThat(first.path("from").path("equalsContext").asBoolean()).isTrue(); assertThat(first.path("from").path("formatValid").asBoolean()).isTrue();
            } else if (q.context().path("selectedResourceRefs").isEmpty()) {
                assertThat(first.path("eqp").path("registered").asBoolean()).isFalse(); assertThat(first.path("eqp").path("equalsContext").asBoolean()).isFalse();
            }
        }
        MetricsAggregator.Metrics metrics = MetricsAggregator.aggregate(rows, golden, 2);
        assertThat(metrics.args().denominator()).isEqualTo(golden.questions().size()); assertThat(metrics.attemptDenominator()).isEqualTo(golden.questions().size() * 2);
        long candidateQuestions = golden.questions().stream().filter(q -> !QRefContext.extract(q.question()).catalog().isEmpty()).count();
        assertThat(metrics.validCount()).isEqualTo((int)candidateQuestions * 2);
        assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL).doesNotContain("not json");
    }

    @Test void boundaryRowsKeepFixedErrorCodesAndDiagnoseFieldsAfterQRefInvalidWithoutRawText() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v3(f, "DIAGNOSTIC", "NONE");
        List<JsonNode> cases = BoundaryCases.load(f.root.resolve(doc.input("boundaryCases")));
        try (OllamaStub stub = new OllamaStub()) {
            var correct = StubAnswers.correct(f.root);
            // 후보가 없는 케이스에서만 qRef 를 범위 밖 ID 로 바꾸고 나머지 필드는 정답 그대로 둡니다.
            stub.chat = body -> {
                if (!QRefContext.extract(OllamaStub.question(body)).catalog().isEmpty()) return correct.apply(body);
                ObjectNode answer = (ObjectNode)Json.parse(correct.apply(body).path("choices").path(0).path("message").path("content").asText().getBytes(StandardCharsets.UTF_8));
                answer.put("qRef", "Q1");
                return OllamaStub.chatResponse(Json.canonicalString(answer), "stop", 300, 40);
            };
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/boundary")).isZero();
        }
        Map<String,JsonNode> byId = new HashMap<>(); cases.forEach(c -> byId.put(c.path("caseId").asText(), c));
        int invalidCases = 0;
        for (JsonNode row : f.json(RESULTS + "/boundary/boundary-results.json").path("cases")) {
            boolean candidates = !QRefContext.extract(byId.get(row.path("caseId").asText()).path("question").asText()).catalog().isEmpty();
            if (!candidates) invalidCases++;
            for (JsonNode a : row.path("attempts")) {
                assertThat(a.path("errorCodes").isArray()).isTrue();
                if (candidates) { assertThat(a.path("errorCodes")).isEmpty(); continue; }
                assertThat(a.path("status").asText()).isEqualTo("INVALID"); assertThat(a.path("argsExact").asBoolean()).isFalse();
                assertThat(a.path("errorCodes")).extracting(JsonNode::asText).containsExactly("QREF_INVALID", "QREF_INVALID");
                // 엄격 판정이 QREF 에서 멈춰도 진단은 뒤 필드를 독립 평가합니다.
                JsonNode first = a.path("attemptDiagnostics").path(0);
                assertThat(a.path("attemptDiagnostics")).hasSize(2);
                assertThat(first.path("qRef").path("kind").asText()).isEqualTo("ID_OUT_OF_RANGE");
                for (String field : List.of("t", "eqp", "from", "to")) assertThat(first.path(field).path("match").asText()).as(field).isEqualTo("MATCH");
            }
        }
        assertThat(invalidCases).isPositive();
        String stored = f.allOutput(RESULTS);
        for (JsonNode c : cases) {
            assertThat(stored).doesNotContain(c.path("question").asText());
            JsonNode eqp = c.path("expectedResolvedOutput").path("eqp");
            if (eqp.isTextual()) assertThat(stored).doesNotContain(eqp.asText());
        }
    }

    @Test void diagnosticRegistrationsCannotProduceGateEvidenceAndKeepRawResultsUnchanged() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v3(f, "DIAGNOSTIC", ResponseSchema.POLICY);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/boundary")).isZero();
        }
        String raw = F0ReportWriter.rawResultsSha256(f.root.resolve(RESULTS));
        for (String[] args : List.of(new String[]{"--out", "evidence/F0"}, new String[]{"--rejudge"}, new String[]{"--spec-record-check", "--proposal", RESULTS + "/x.md"})) {
            List<String> all = new ArrayList<>(List.of("report", "--run-id", RUN, "--results", RESULTS)); all.addAll(List.of(args));
            assertThat(F0VersionedInputsTest.execute(f, all.toArray(String[]::new))).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("DIAGNOSTIC_NOT_GATE_ELIGIBLE");
        }
        F0ReportWriter writer = new F0ReportWriter(f.context());
        Path prereg = f.root.resolve(F0VersionedInputsTest.PREREG), results = f.root.resolve(RESULTS), evidence = f.root.resolve("evidence/F0");
        assertThatThrownBy(() -> writer.report(RUN, results, prereg, evidence)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("DIAGNOSTIC_NOT_GATE_ELIGIBLE"));
        assertThatThrownBy(() -> writer.rejudge(RUN, results, prereg, evidence)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("DIAGNOSTIC_NOT_GATE_ELIGIBLE"));
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", RUN, "--results", RESULTS)).isZero();
        assertThat(f.root.resolve("evidence")).doesNotExist();
        assertThat(f.root.resolve(RESULTS + "/report.md")).doesNotExist(); assertThat(f.root.resolve(RESULTS + "/spec-14.5-proposal.md")).doesNotExist();
        String report = Files.readString(f.root.resolve(RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT), StandardCharsets.UTF_8);
        assertThat(report).contains("진단 전용", raw, ResponseSchema.POLICY, "| NORMAL |", "B 0/" + golden(f, doc).questions().size() * 2).doesNotContain(OllamaStub.SENTINEL, "CONNECTION_TIMEOUT", "EqpBindingService");
        assertThat(F0ReportWriter.rawResultsSha256(f.root.resolve(RESULTS))).isEqualTo(raw);
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", RUN, "--results", RESULTS)).isEqualTo(2); assertThat(f.lastError).isEqualTo("REPORT_EXISTS");
    }

    @Test void gateRegistrationsCannotUseTheDiagnosticReportAndRegistrationKeysAreVersionScoped() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = v3(f, "GATE", ResponseSchema.POLICY);
        assertThat(doc.diagnostic()).isFalse(); assertThat(doc.versioned()).isTrue(); assertThat(doc.budget()).isEqualTo(Prereg.Budget.V2); assertThat(doc.repeats()).isEqualTo(5);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            // 실제 명령의 --repeats 도 등록 R=5 와 같아야 하며 다르면 endpoint 호출 전에 거부합니다.
            assertThat(F0VersionedInputsTest.execute(f, "run", "--path", "B", "--endpoint", stub.endpoint(), "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/B")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("ARGUMENT_NOT_REGISTERED"); assertThat(chats(stub)).isEmpty();
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero();
            assertThat(f.json(RESULTS + "/B/f0-results.json").path("attempts")).hasSize(5 * golden(f, doc).questions().size());
        }
        assertThatThrownBy(() -> new F0ReportWriter(f.context()).diagnosticReport(RUN, f.root.resolve(RESULTS), f.root.resolve(F0VersionedInputsTest.PREREG)))
            .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("DIAGNOSTIC_REGISTRATION_REQUIRED"));
        ObjectNode valid = (ObjectNode)doc.block().deepCopy();
        List<java.util.function.Consumer<ObjectNode>> invalid = List.of(
            b -> b.remove("purpose"), b -> b.put("purpose", "MEASUREMENT"), b -> ((ObjectNode)b.path("parameters")).remove("responseFormat"),
            b -> ((ObjectNode)b.path("parameters")).put("responseFormat", "JSON_OBJECT"), b -> ((ObjectNode)b.path("parameters")).put("responseFormat", 1),
            b -> b.put("schema", "f0-prereg-v2"), b -> { b.put("schema", "f0-prereg-v2"); b.remove("purpose"); },
            // 정식 GATE 의 R1/R2(및 R5 아닌 값) 변조는 등록 단계에서 거부합니다.
            b -> ((ObjectNode)b.path("parameters")).put("repeats", 1), b -> ((ObjectNode)b.path("parameters")).put("repeats", 2), b -> ((ObjectNode)b.path("parameters")).put("repeats", 7));
        for (var change : invalid) {
            ObjectNode block = valid.deepCopy(); change.accept(block); F0VersionedInputsTest.writePrereg(f, block);
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        // DIAGNOSTIC 은 R>=1 기존 규칙 그대로(R1·R2 유효).
        for (int repeats : List.of(1, 2)) {
            ObjectNode block = valid.deepCopy(); block.put("purpose", "DIAGNOSTIC"); ((ObjectNode)block.path("parameters")).put("repeats", repeats); F0VersionedInputsTest.writePrereg(f, block);
            assertThat(Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG)).repeats()).isEqualTo(repeats);
        }
    }

    @Test void schemaShapeCarriesOnlyStructuralConstraintsAndDiagnosticsReportStepAndReferenceKinds() {
        QRefContext two = QRefContext.extract("\"alpha\" 와 \"beta\" 로그 원인 찾아줘"), none = QRefContext.extract("설비 상태 알려줘");
        ObjectNode plan = ResponseSchema.responseFormat(MessageAssembler.Kind.PLAN, two);
        JsonNode schema = plan.path("json_schema").path("schema");
        assertThat(plan.path("type").asText()).isEqualTo("json_schema"); assertThat(plan.path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(schema.path("required")).extracting(JsonNode::asText).containsExactly("t", "eqp", "from", "to", "qRef", "steps");
        assertThat(schema.path("properties").path("t").path("enum")).extracting(JsonNode::asText).containsExactlyElementsOf(RegistryFixture.TASK_TYPES);
        assertThat(schema.path("properties").path("qRef").path("enum")).extracting(JsonNode::asText).containsExactly("Q1", "Q2");
        assertThat(schema.path("properties").path("steps")).isEqualTo(Json.parse("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}".getBytes(StandardCharsets.UTF_8)));
        assertThat(plan.toString()).doesNotContain("alpha", "beta");
        assertThat(GoldenSet.keys(ResponseSchema.responseFormat(MessageAssembler.Kind.CLASSIFY, none).path("json_schema").path("schema").path("properties"))).containsExactlyInAnyOrder("t", "eqp", "from", "to", "qRef");

        RegistryFixture fixture = RegistryFixture.load(Path.of(System.getProperty("aigw.rootDir")).resolve("eval/golden/tc-registry-fixture-v2.json"));
        JsonNode expected = Json.parse("{\"t\":\"UNS\",\"eqp\":null,\"from\":null,\"to\":null,\"q\":\"alpha\"}".getBytes(StandardCharsets.UTF_8));
        JsonNode context = Json.parse("{\"timezone\":\"+09:00\",\"referenceTime\":\"2026-10-20T10:00:00+09:00\",\"timeWindow\":null,\"selectedResourceRefs\":[]}".getBytes(StandardCharsets.UTF_8));
        String output = "{\"t\":\"UNS\",\"eqp\":\"alpha\",\"from\":null,\"to\":null,\"qRef\":\"alpha\",\"q\":\"alpha\",\"steps\":[\"tc.model.checkout\",\"tc.unknown.tool\"]}";
        OllamaChatClient.ChatResult result = new OllamaChatClient.ChatResult(output, "stop", 10, 40);
        ClassificationJudge.Verdict before = ClassificationJudge.judge(result, 256, MessageAssembler.Kind.PLAN, true, "+09:00", two);
        ObjectNode d = AttemptDiagnostics.diagnose(result, 256, MessageAssembler.Kind.PLAN, two, expected, "\"alpha\" 와 \"beta\" 로그 원인 찾아줘", context, "+09:00", fixture);
        assertThat(ClassificationJudge.judge(result, 256, MessageAssembler.Kind.PLAN, true, "+09:00", two)).isEqualTo(before);
        assertThat(before.valid()).isFalse(); assertThat(before.errorCode()).isEqualTo("SCHEMA_KEYS_INVALID");
        assertThat(d.path("keys").asText()).isEqualTo("EXTRA"); assertThat(d.path("rawQKey").asBoolean()).isTrue();
        assertThat(d.path("qRef").path("kind").asText()).isEqualTo("TEXT_OTHER"); assertThat(d.path("qRef").path("textInQuestion").asBoolean()).isTrue();
        assertThat(d.path("eqp").path("equalsQuoteCandidate").asBoolean()).isTrue(); assertThat(d.path("eqp").path("inQuestion").asBoolean()).isTrue();
        assertThat(d.path("t").path("match").asText()).isEqualTo("MATCH"); assertThat(d.path("q").path("match").asText()).isEqualTo("NOT_EVALUABLE");
        assertThat(d.path("steps").path("shape").asText()).isEqualTo("OK"); assertThat(d.path("steps").path("toolConsistency").asText()).isEqualTo("NONEMPTY_FOR_NO_TOOL_TASK");
        assertThat(d.path("steps").path("unknownCount").asInt()).isEqualTo(1); assertThat(d.path("steps").path("inactiveCount").asInt()).isEqualTo(1);
        assertThat(d.toString()).doesNotContain("alpha", "beta", "tc.");
        ObjectNode emptyQuote = AttemptDiagnostics.diagnose(new OllamaChatClient.ChatResult("{\"t\":\"KQ\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":\"Q2\",\"steps\":[]}", "length", 10, 300), 256, MessageAssembler.Kind.PLAN, two, expected, "x", context, "+09:00", fixture);
        assertThat(emptyQuote.path("finish").asText()).isEqualTo("LENGTH"); assertThat(emptyQuote.path("usageWithinLimit").asBoolean()).isFalse();
        assertThat(emptyQuote.path("qRef").path("kind").asText()).isEqualTo("ID_IN_RANGE"); assertThat(emptyQuote.path("q").path("match").asText()).isEqualTo("MISMATCH");
        assertThat(emptyQuote.path("steps").path("toolConsistency").asText()).isEqualTo("EMPTY_FOR_TOOL_TASK");
    }
}
