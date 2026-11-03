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
 * G08 원인 확정 시험(f0 probe-g08, PREREG-v3-probe-g08)의 입력·송신·기록 계약(endpoint 는 localhost stub 만, 실측 0).
 * ① 실제 레포 변형 입력이 후보 i 기준선과 정확히 요소 하나만 다름, ② E-ORD format 의 properties 선언 순서 보존 직렬화와 E0 정렬 format = chat 송신 스키마,
 * ③ logprob 위치 분류가 등록 후보 집합 밖 토큰 문자열을 남기지 않음, ④ stub 종단에서 8건 순서·요청 본문·원문 비기록·호출 전 거부.
 */
class F0ProbeG08Test {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    static final String PREREG = "eval/f0/PREREG-v3-probe-g08.md", MANIFEST = "eval/f0/v3/probe-g08/probe-g08-v1.json", AUDIT = "eval/f0/v3/input-audit-probe-g08.json";
    static final String BASELINE_OUTPUT = "{\"eqp\":null,\"from\":null,\"qRef\":null,\"steps\":[],\"t\":\"INC\",\"to\":null}";
    static final String RUN = "20261103T000000Z-probe-g08", RESULTS = "eval/f0/results/" + RUN;
    @TempDir Path directory;

    static G08Probe.Roles roles(Path root, Prereg.Document d) {
        return new G08Probe.Roles(root.resolve(d.input("golden")), root.resolve(d.input("fixture")), root.resolve(d.input("planPrompt")), d.contextProjection());
    }
    static G08Probe.Plan realPlan() { return G08Probe.load(ROOT, roles(ROOT, Prereg.parse(ROOT.resolve(PREREG))), ROOT.resolve(MANIFEST)); }
    static JsonNode user(List<Map<String,String>> messages) { return Json.parse(messages.get(1).get("content").getBytes(StandardCharsets.UTF_8)); }

    @Test void realVariantsDifferFromTheCandidateIBaselineInExactlyOneElement() throws Exception {
        G08Probe.Plan plan = realPlan(); Prereg.Document probe = Prereg.parse(ROOT.resolve(PREREG)), diagI = Prereg.parse(ROOT.resolve("eval/f0/PREREG-v3-diag-i.md"));
        assertThat(plan.experiments()).extracting(G08Probe.Experiment::id).containsExactlyElementsOf(G08Probe.IDS);
        // 기준선 = 후보 i 등록 audit 의 A/G08/first 와 같은 메시지, 기대 출력 = g·h·i 의 G08 출력(키 정렬·인자 null·steps []·t INC).
        String baselineSha = null;
        for (JsonNode row : Json.read(ROOT.resolve(diagI.input("inputAudit"))).path("rows")) if (row.path("id").asText().equals("A/G08/first")) baselineSha = row.path("messagesSha256").asText();
        assertThat(plan.baselineMessagesSha256()).isEqualTo(baselineSha).startsWith("c64d1f75");
        assertThat(plan.baselineOutputSha256()).isEqualTo(Json.sha(BASELINE_OUTPUT.getBytes(StandardCharsets.UTF_8))).startsWith("d648833f");
        RegistryFixture fixture = RegistryFixture.load(ROOT.resolve(diagI.input("fixture")));
        GoldenSet.Question g08 = GoldenSet.load(ROOT.resolve(diagI.input("golden")), fixture).byId("G08");
        List<Map<String,String>> baseline = MessageAssembler.load(ROOT.resolve(diagI.input("planPrompt")), fixture, diagI.contextProjection()).messages(g08.question(), g08.context(), QRefContext.extract(g08.question()));
        assertThat(InputAudit.messagesSha256(baseline)).isEqualTo(baselineSha);
        Map<String,String> questions = Map.of("E1", "WM 으로 보낸 메시지 가 왜 실패했어?", "E2", "WORK_ORDER_REPLY 가 왜 실패했어?", "E3", "WM 으로 보낸 WORK_ORDER_REPLY 가 실패한 원인 분석해줘", "N1", "WM 으로 보낸 WORK_ORDER_REPLY 가 왜 실패했니?");
        for (G08Probe.Experiment e : plan.experiments()) {
            switch (e.id()) {
                case "E0", "E-ORD" -> { assertThat(e.messages()).isEqualTo(baseline); assertThat(e.call()).isEqualTo(G08Probe.GENERATE_RAW); }
                case "E5", "E6" -> {
                    // 파일 변형은 system 만 다르고 user(질문·Context·qRefCatalog)는 같습니다.
                    assertThat(e.messages().get(1)).isEqualTo(baseline.get(1)); assertThat(e.messages().get(0)).isNotEqualTo(baseline.get(0));
                    List<String> before = baseline.get(0).get("content").lines().toList(), after = e.messages().get(0).get("content").lines().toList();
                    assertThat(after).hasSameSizeAs(before);
                    List<Integer> changed = new ArrayList<>(); for (int i = 0; i < before.size(); i++) if (!before.get(i).equals(after.get(i))) changed.add(i);
                    assertThat(changed).as(e.id()).hasSize(1);
                    String b = before.get(changed.getFirst()), a = after.get(changed.getFirst());
                    if (e.id().equals("E5")) assertThat(b).isEqualTo(a + " TC 자료로 대신 채우지 않는다.");
                    else assertThat(a).isEqualTo(b.replace("LOT·Workorder 진행 상태", "LOT 진행 상태")).startsWith("- 시스템 WM ");
                }
                default -> {
                    // 질문 변형은 user 의 question 값만 다르고 system·context·qRefCatalog 는 같습니다.
                    assertThat(e.messages().get(0)).isEqualTo(baseline.get(0));
                    ObjectNode before = (ObjectNode)user(baseline), after = (ObjectNode)user(e.messages());
                    assertThat(after.path("question").asText()).as(e.id()).isEqualTo(questions.get(e.id())).isNotEqualTo(g08.question());
                    before.remove("question"); after.remove("question"); assertThat(after).isEqualTo(before);
                }
            }
            assertThat(e.call()).isEqualTo(Set.of("E0", "E-ORD").contains(e.id()) ? G08Probe.GENERATE_RAW : G08Probe.CHAT);
        }
        // 변형 파일 바이트는 기준 파일에 한 곳만 바꾼 것입니다(줄 단위로도 정확히 1줄).
        assertThat(Files.readString(ROOT.resolve("eval/f0/v3/probe-g08/plan-v3-i-e5.txt"))).isEqualTo(Files.readString(ROOT.resolve(diagI.input("planPrompt"))).replace(" TC 자료로 대신 채우지 않는다.", ""));
        assertThat(Files.readString(ROOT.resolve("eval/f0/v3/probe-g08/tc-registry-fixture-v2-e6.json"))).isEqualTo(Files.readString(ROOT.resolve(diagI.input("fixture"))).replace("LOT·Workorder 진행 상태", "LOT 진행 상태"));
        assertThat(plan.inputFiles()).containsExactly("eval/f0/v3/probe-g08/plan-v3-i-e5.txt", "eval/f0/v3/probe-g08/tc-registry-fixture-v2-e6.json");
        // 사전 계수 표는 8건 송신 메시지와 행 순서·id·SHA 가 같고, 기준선 계수는 후보 i audit 와 같습니다.
        JsonNode rows = Json.read(ROOT.resolve(AUDIT)).path("rows"); List<InputAudit.Planned> planned = plan.planned(probe.budget());
        assertThat(rows).hasSize(8); assertThat(Json.read(ROOT.resolve(AUDIT)).path("overBudgetIds")).isEmpty();
        for (int i = 0; i < 8; i++) {
            assertThat(rows.get(i).path("id").asText()).isEqualTo(planned.get(i).id()).isEqualTo("probe/" + G08Probe.IDS.get(i));
            assertThat(rows.get(i).path("messagesSha256").asText()).isEqualTo(InputAudit.messagesSha256(planned.get(i).messages()));
        }
        assertThat(rows.get(0).path("inputTokens").asInt()).isEqualTo(1916);
        assertThatCode(() -> InputAudit.load(ROOT.resolve(AUDIT)).requireAll(planned)).doesNotThrowAnyException();
        // 등록은 진단 i 와 같은 파라미터·역할(audit 만 다름)이고, 입력은 진단 i 에서 audit 를 바꾸고 manifest·변형 2파일을 더한 것입니다.
        assertThat(probe.parameters()).isEqualTo(diagI.parameters());
        ObjectNode probeRoles = probe.block().path("inputRoles").deepCopy(), iRoles = diagI.block().path("inputRoles").deepCopy();
        assertThat(probeRoles.path("inputAudit").asText()).isEqualTo(AUDIT); probeRoles.remove("inputAudit"); iRoles.remove("inputAudit"); assertThat(probeRoles).isEqualTo(iRoles);
        Map<String,String> expectedInputs = new TreeMap<>(diagI.inputs()); expectedInputs.remove(diagI.input("inputAudit"));
        for (String file : List.of(AUDIT, MANIFEST, "eval/f0/v3/probe-g08/plan-v3-i-e5.txt", "eval/f0/v3/probe-g08/tc-registry-fixture-v2-e6.json")) expectedInputs.put(file, Json.sha(Files.readAllBytes(ROOT.resolve(file))));
        assertThat(new TreeMap<>(probe.inputs())).isEqualTo(expectedInputs);
        assertThat(probe.diagnostic()).isTrue(); assertThat(probe.repeats()).isEqualTo(1);
    }

    @Test void loaderRejectsAmbiguousHandEditedOrMismatchedInputsBeforeAnyRequest() throws Exception {
        assertThat(G08Probe.replaceOnce("a-b-c", "-b", "")).isEqualTo("a-c");
        for (String find : List.of("", "x", "-")) assertThatThrownBy(() -> G08Probe.replaceOnce("a-b-c", find, "y")).as(find)
            .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PROBE_INPUT_INVALID"));
        Prereg.Document probe = Prereg.parse(ROOT.resolve(PREREG));
        for (String file : List.of(probe.input("golden"), probe.input("fixture"), probe.input("planPrompt"), MANIFEST, "eval/f0/v3/probe-g08/plan-v3-i-e5.txt", "eval/f0/v3/probe-g08/tc-registry-fixture-v2-e6.json")) {
            Path target = directory.resolve(file); Files.createDirectories(target.getParent()); Files.copy(ROOT.resolve(file), target);
        }
        assertThatCode(() -> G08Probe.load(directory, roles(directory, probe), directory.resolve(MANIFEST))).doesNotThrowAnyException();
        String manifest = Files.readString(directory.resolve(MANIFEST));
        Map<String,String> broken = new LinkedHashMap<>();
        broken.put("PROBE_BASELINE_MISMATCH", manifest.replace("c64d1f75", "c64d1f76"));
        broken.put("PROBE_INPUT_INVALID", manifest.replace("\"id\": \"E5\"", "\"id\": \"E4\""));
        broken.put("PROBE_INPUT_INVALID ", manifest.replace("\"find\": \"실패했어?\"", "\"find\": \"_\""));
        for (var entry : broken.entrySet()) {
            Files.writeString(directory.resolve(MANIFEST), entry.getValue());
            assertThatThrownBy(() -> G08Probe.load(directory, roles(directory, probe), directory.resolve(MANIFEST))).as(entry.getKey())
                .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo(entry.getKey().strip()));
        }
        Files.writeString(directory.resolve(MANIFEST), manifest);
        // 변형 파일에 edit 밖 변경이 하나라도 있으면 거부합니다.
        Path e5 = directory.resolve("eval/f0/v3/probe-g08/plan-v3-i-e5.txt");
        Files.writeString(e5, Files.readString(e5) + "\n");
        assertThatThrownBy(() -> G08Probe.load(directory, roles(directory, probe), directory.resolve(MANIFEST)))
            .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PROBE_INPUT_INVALID"));
    }

    @Test void tFirstFormatKeepsTheDeclaredPropertyOrderAndSortedFormatIsTheChatWireSchema() {
        QRefContext refs = QRefContext.extract(GoldenSet.AC38_QUESTION);
        ObjectNode schema = (ObjectNode)ResponseSchema.responseFormat(MessageAssembler.Kind.PLAN, refs).path("json_schema").path("schema");
        byte[] sorted = ResponseSchema.canonicalKeepingOrder(schema, Set.of()), tFirst = ResponseSchema.canonicalKeepingOrder(schema, Set.of(List.of("properties")));
        assertThat(Json.parse(tFirst)).isEqualTo(Json.parse(sorted)); assertThat(tFirst).isNotEqualTo(sorted);
        assertThat(order(Json.parse(sorted).path("properties"))).containsExactly("eqp", "from", "qRef", "steps", "t", "to");
        assertThat(order(Json.parse(tFirst).path("properties"))).containsExactly("t", "eqp", "from", "to", "qRef", "steps");
        // properties 밖(최상위·각 속성 객체 안)은 정렬 그대로입니다.
        assertThat(order(Json.parse(tFirst))).containsExactly("additionalProperties", "properties", "required", "type");
        assertThat(new String(tFirst, StandardCharsets.UTF_8)).isEqualTo(new String(sorted, StandardCharsets.UTF_8).replaceFirst("\"properties\":\\{.*?\\},\"required\"", java.util.regex.Matcher.quoteReplacement(
            "\"properties\":" + propertiesInOrder(schema, List.of("t", "eqp", "from", "to", "qRef", "steps")) + ",\"required\"")));
        // E0 정렬 format = 진단 i chat 요청 response_format.json_schema.schema 의 송신 바이트(Ollama 재직렬화 뒤 서버가 받는 키 순서).
        JsonNode wire = Json.parse(ResponseSchema.forPolicy(ResponseSchema.POLICY, MessageAssembler.Kind.PLAN, refs).bytes()).path("json_schema").path("schema");
        assertThat(Json.canonical(wire)).isEqualTo(sorted);
    }
    static List<String> order(JsonNode node) { List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); return names; }
    static String propertiesInOrder(ObjectNode schema, List<String> names) {
        List<String> fields = new ArrayList<>(); for (String name : names) fields.add("\"" + name + "\":" + Json.canonicalString(schema.path("properties").path(name)));
        return "{" + String.join(",", fields) + "}";
    }

    static OllamaChatClient.TokenLogprob lp(String token, double logprob) { return new OllamaChatClient.TokenLogprob(token, logprob); }
    static OllamaChatClient.Position at(String token, OllamaChatClient.TokenLogprob... top) { return new OllamaChatClient.Position(lp(token, -0.01), List.of(top)); }
    /** 기준선 출력 바이트를 이어 붙이면 BASELINE_OUTPUT 이 되는 합성 토큰열. steps·t 위치에 후보를 둡니다. */
    static List<OllamaChatClient.Position> tokens(boolean mergedSteps) {
        List<OllamaChatClient.Position> p = new ArrayList<>();
        for (String t : List.of("{\"", "eqp", "\":", "null", ",\"", "from", "\":", "null", ",\"", "q", "Ref", "\":", "null", ",\"", "steps")) p.add(at(t));
        if (mergedSteps) p.add(at("\":[],\"", lp("\":[],\"", -0.2), lp("\":[\"", -1.9), lp("\":\"", -3.0), lp("\":", -4.0), lp(OllamaStub.SENTINEL, -6.0)));
        else { p.add(at("\":[")); p.add(at("],\"", lp("],\"", -0.1), lp("]", -2.5), lp("\"tc", -3.1), lp(" ", -5.0), lp(OllamaStub.SENTINEL, -7.0))); }
        p.add(at("t")); p.add(at("\":\""));
        p.add(at("INC", lp("INC", -0.3), lp("UNS", -1.4), lp("U", -2.0), lp("GQ", -4.0), lp(OllamaStub.SENTINEL, -8.0)));
        for (String t : List.of("\",\"", "to", "\":", "null", "}")) p.add(at(t));
        return p;
    }

    @Test void logprobPositionsKeepOnlyTheRegisteredCandidateStrings() {
        for (boolean merged : List.of(false, true)) {
            OllamaChatClient.GenerateResult g = new OllamaChatClient.GenerateResult(BASELINE_OUTPUT, "stop", 1916, 27, tokens(merged));
            ObjectNode summary = G08Probe.logprobSummary(g);
            assertThat(summary.path("status").asText()).isEqualTo("PRESENT"); assertThat(summary.path("tokensMatchContent").asBoolean()).isTrue();
            JsonNode steps = summary.path("stepsFirst"), t = summary.path("tValue");
            assertThat(steps.path("status").asText()).isEqualTo("FOUND"); assertThat(steps.path("chosenClass").asText()).isEqualTo("CLOSE_BRACKET");
            assertThat(steps.path("tokenIndex").asInt()).isEqualTo(merged ? 15 : 16);
            List<String> close = new ArrayList<>(), quote = new ArrayList<>();
            steps.path("classes").path("CLOSE_BRACKET").path("tokens").forEach(x -> close.add(x.path("token").asText()));
            steps.path("classes").path("QUOTE").path("tokens").forEach(x -> quote.add(x.path("token").asText()));
            if (merged) {
                // 앞 토큰이 '[' 까지 덮어도 '[' 까지 실제 텍스트와 같은 후보만 분류합니다(":" 는 '[' 전에 갈라짐, ":" 만은 아직 문자 없음).
                assertThat(close).containsExactly("\":[],\""); assertThat(quote).containsExactly("\":[\""); assertThat(steps.path("otherCount").asInt()).isEqualTo(3);
                assertThat(steps.path("classes").path("CLOSE_BRACKET").path("probability").asDouble()).isCloseTo(Math.exp(-0.2), within(1e-12));
            } else {
                // 공백 토큰은 첫 비공백 문자가 아직 없으므로 후보 집합 밖입니다.
                assertThat(close).containsExactly("],\"", "]"); assertThat(quote).containsExactly("\"tc"); assertThat(steps.path("otherCount").asInt()).isEqualTo(2);
                assertThat(steps.path("classes").path("CLOSE_BRACKET").path("probability").asDouble()).isCloseTo(Math.exp(-0.1) + Math.exp(-2.5), within(1e-12));
            }
            assertThat(t.path("status").asText()).isEqualTo("FOUND"); assertThat(t.path("chosenClass").asText()).isEqualTo("INC");
            List<String> inc = new ArrayList<>(), uns = new ArrayList<>();
            t.path("classes").path("INC").path("tokens").forEach(x -> inc.add(x.path("token").asText())); t.path("classes").path("UNS").path("tokens").forEach(x -> uns.add(x.path("token").asText()));
            assertThat(inc).containsExactly("INC"); assertThat(uns).containsExactly("UNS", "U"); assertThat(t.path("otherCount").asInt()).isEqualTo(2);
            assertThat(t.path("classes").path("UNS").path("probability").asDouble()).isCloseTo(Math.exp(-1.4) + Math.exp(-2.0), within(1e-12));
            // 후보 집합 밖 토큰 문자열과 생성 원문은 요약에 없습니다.
            String text = Json.canonicalString(summary);
            assertThat(text).doesNotContain(OllamaStub.SENTINEL).doesNotContain("GQ").doesNotContain("\"null\"").doesNotContain(BASELINE_OUTPUT);
        }
        assertThat(G08Probe.logprobSummary(new OllamaChatClient.GenerateResult(BASELINE_OUTPUT, "stop", 1, 1, null)).path("status").asText()).isEqualTo("ABSENT");
        assertThat(G08Probe.logprobSummary(new OllamaChatClient.GenerateResult("{}", "stop", 1, 1, List.of(at("{}")))).path("stepsFirst").path("status").asText()).isEqualTo("NOT_FOUND");
        assertThat(G08Probe.logsSearch("{\"steps\":[\"tc.logs.search\"]}")).isEqualTo("YES"); assertThat(G08Probe.logsSearch(BASELINE_OUTPUT)).isEqualTo("NO");
        assertThat(G08Probe.logsSearch("not json " + OllamaStub.SENTINEL)).isEqualTo("NOT_EVALUABLE");
    }

    /** 실제 등록 입력을 임시 루트로 복사하고 stub template·시험 tokenizer 기준 audit·등록 완료 PREREG(fake git)를 만듭니다. */
    static F0TestRoot registered(Path dir, OllamaStub stub, java.util.function.Predicate<String> keepAuditRow) throws Exception {
        F0TestRoot f = UsageProbeTest.rendererRegistered(dir, stub);
        Prereg.Document real = Prereg.parse(ROOT.resolve(PREREG));
        for (String file : real.inputs().keySet()) if (!Files.exists(f.root.resolve(file)) && !file.equals(AUDIT)) f.copy(file);
        G08Probe.Plan plan = G08Probe.load(f.root, roles(f.root, real), f.root.resolve(MANIFEST));
        ObjectNode audit = Json.MAPPER.createObjectNode().put("schema", InputAudit.SCHEMA).put("tokenizerSha256", MeasurementSession.tokenizerSha(f.root)).put("templateSha256", Json.sha(stub.template.getBytes(StandardCharsets.UTF_8)));
        ArrayNode rows = audit.putArray("rows");
        for (InputAudit.Planned call : plan.planned(real.budget())) if (keepAuditRow.test(call.id())) rows.addObject().put("id", call.id()).put("messagesSha256", InputAudit.messagesSha256(call.messages())).put("inputTokens", 1916);
        f.write(AUDIT, Json.canonicalString(audit));
        ObjectNode block = real.block().deepCopy(); block.put("registrationStatus", "REGISTERED"); block.put("registeredCommit", F0TestRoot.REGISTERED);
        for (JsonNode input : block.path("inputs")) ((ObjectNode)input).put("sha256", Json.sha(Files.readAllBytes(f.root.resolve(input.path("path").asText()))));
        f.write(PREREG, "# probe\n\n```json\n" + Json.canonicalString(block) + "\n```\n");
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(PREREG))));
        f.write(PREREG, "# probe\n\n```json\n" + Json.canonicalString(block) + "\n```\n");
        return f;
    }
    static int probe(F0TestRoot f, OllamaStub stub, String manifest) {
        return f.exec("probe-g08", "--prereg", PREREG, "--endpoint", stub.endpoint(), "--probe", manifest, "--run-id", RUN, "--out", RESULTS);
    }
    static JsonNode generateAnswer(JsonNode body) {
        ObjectNode root = Json.MAPPER.createObjectNode(); root.put("model", OllamaStub.SENTINEL); root.put("response", BASELINE_OUTPUT); root.put("done", true); root.put("done_reason", "stop");
        root.put("prompt_eval_count", 1916); root.put("eval_count", 27); root.putArray("context").add(1);
        ArrayNode logprobs = root.putArray("logprobs");
        for (OllamaChatClient.Position p : tokens(false)) {
            ObjectNode entry = logprobs.addObject(); entry.put("token", p.chosen().token()); entry.put("logprob", p.chosen().logprob()); entry.putArray("bytes").add(1);
            ArrayNode top = entry.putArray("top_logprobs"); for (OllamaChatClient.TokenLogprob c : p.top()) top.addObject().put("token", c.token()).put("logprob", c.logprob());
        }
        return root;
    }

    @Test void probeSendsTheEightRegisteredRequestsInOrderAndRecordsOnlySanitizedFields() throws Exception {
        try (OllamaStub stub = new OllamaStub()) {
            F0TestRoot f = registered(directory, stub, id -> true);
            G08Probe.Plan plan = G08Probe.load(f.root, roles(f.root, Prereg.parse(f.root.resolve(PREREG))), f.root.resolve(MANIFEST));
            stub.generate = F0ProbeG08Test::generateAnswer;
            // E1 은 Tool 계획, N1 은 원문 sentinel 이 든 인자, 나머지는 기준선과 같은 출력입니다.
            stub.chat = body -> {
                String question = OllamaStub.question(body);
                String content = question.contains("메시지") ? "{\"eqp\":null,\"from\":null,\"qRef\":null,\"steps\":[\"tc.logs.search\"],\"t\":\"INC\",\"to\":null}"
                    : question.endsWith("실패했니?") ? "{\"eqp\":\"" + OllamaStub.SENTINEL + "\",\"from\":null,\"qRef\":null,\"steps\":[],\"t\":\"INC\",\"to\":null}" : BASELINE_OUTPUT;
                return OllamaStub.chatResponse(content, "stop", 1916, 27);
            };
            assertThat(probe(f, stub, MANIFEST)).as(f.lastError).isZero();
            assertThat(f.stdout.toString(StandardCharsets.UTF_8)).contains("F0_PROBE_G08_COMPLETED 8");
            List<OllamaStub.Request> inference = stub.inference();
            assertThat(inference).extracting(OllamaStub.Request::path).containsExactly("/api/generate", "/api/generate", "/v1/chat/completions", "/v1/chat/completions", "/v1/chat/completions", "/v1/chat/completions", "/v1/chat/completions", "/v1/chat/completions");
            for (int i = 0; i < 8; i++) {
                OllamaStub.Request r = inference.get(i); G08Probe.Experiment e = plan.experiments().get(i);
                OllamaChatClientContractTest.noKeyAnywhere(r.body(), "num_ctx"); OllamaChatClientContractTest.noKeyAnywhere(r.body(), "keep_alive");
                if (i < 2) {
                    assertThat(GoldenSet.keys(r.body())).isEqualTo(Set.of("model", "prompt", "raw", "stream", "format", "options", "logprobs", "top_logprobs"));
                    assertThat(GoldenSet.keys(r.body().path("options"))).isEqualTo(Set.of("temperature", "num_predict"));
                    assertThat(r.body().path("options").path("temperature").asDouble()).isZero(); assertThat(r.body().path("options").path("num_predict").asInt()).isEqualTo(256);
                    assertThat(r.body().path("raw").asBoolean()).isTrue(); assertThat(r.body().path("stream").asBoolean()).isFalse();
                    assertThat(r.body().path("logprobs").asBoolean()).isTrue(); assertThat(r.body().path("top_logprobs").asInt()).isEqualTo(5);
                    assertThat(r.body().path("prompt").asText()).isEqualTo(ChatRenderers.chatMl(e.messages()));
                    // 송신 바이트에서 format.properties 순서: E0 정렬, E-ORD 선언 순서(t 먼저).
                    JsonNode raw = Json.MAPPER.readTree(r.raw());
                    assertThat(order(raw.path("format").path("properties"))).containsExactlyElementsOf(i == 0 ? List.of("eqp", "from", "qRef", "steps", "t", "to") : List.of("t", "eqp", "from", "to", "qRef", "steps"));
                    assertThat(order(raw.path("format"))).containsExactly("additionalProperties", "properties", "required", "type");
                } else {
                    assertThat(F0ContextProjectionTest.messages(r)).isEqualTo(e.messages());
                    assertThat(Json.canonical(r.body().path("response_format"))).isEqualTo(ResponseSchema.forPolicy(ResponseSchema.POLICY, MessageAssembler.Kind.PLAN, e.references()).bytes());
                    assertThat(r.body().path("max_tokens").asInt()).isEqualTo(256); assertThat(r.body().path("temperature").asDouble()).isZero();
                }
            }
            JsonNode result = f.json(RESULTS + "/" + G08Probe.RESULT_FILE), rows = result.path("experiments");
            assertThat(result.path("complete").asBoolean()).isTrue(); assertThat(result.path("baselineOutputSha256").asText()).startsWith("d648833f");
            assertThat(rows).hasSize(8);
            for (int i = 0; i < 8; i++) {
                JsonNode row = rows.get(i);
                assertThat(row.path("id").asText()).isEqualTo(G08Probe.IDS.get(i)); assertThat(row.path("status").asText()).isEqualTo("OK");
                assertThat(row.path("messagesSha256").asText()).isEqualTo(InputAudit.messagesSha256(plan.experiments().get(i).messages()));
                assertThat(row.path("auditInputTokens").asInt()).isEqualTo(1916); assertThat(row.path("promptTokens").asInt()).isEqualTo(1916); assertThat(row.path("completionTokens").asInt()).isEqualTo(27);
                assertThat(row.path("t").asText()).isEqualTo("INC"); assertThat(row.has("latencyMs")).isTrue(); assertThat(row.path("formatSha256").asText()).matches("[0-9a-f]{64}");
                String id = row.path("id").asText();
                assertThat(row.path("toolConsistency").asText()).isEqualTo(id.equals("E1") ? "CONSISTENT" : "EMPTY_FOR_TOOL_TASK");
                assertThat(row.path("stepsIncludeLogsSearch").asText()).isEqualTo(id.equals("E1") ? "YES" : "NO");
                assertThat(row.path("sameAsBaselineOutput").asBoolean()).isEqualTo(!Set.of("E1", "N1").contains(id));
                assertThat(row.path("firstAttemptVerdict").path("valid").asBoolean()).isEqualTo(id.equals("E1"));
                if (!id.equals("E1")) assertThat(row.path("firstAttemptVerdict").path("stepsDetail").asText()).isEqualTo("EMPTY_FOR_TOOL_TASK");
                assertThat(row.has("logprobs")).isEqualTo(i < 2);
            }
            assertThat(rows.get(0).path("logprobs").path("stepsFirst").path("chosenClass").asText()).isEqualTo("CLOSE_BRACKET");
            assertThat(rows.get(1).path("logprobs").path("tValue").path("chosenClass").asText()).isEqualTo("INC");
            assertThat(rows.get(0).path("formatSha256").asText()).isNotEqualTo(rows.get(1).path("formatSha256").asText());
            assertThat(result.path("baselineReproduction").path("E0").asBoolean()).isTrue(); assertThat(result.path("baselineReproduction").path("N1").asBoolean()).isFalse();
            assertThat(f.json(RESULTS + "/run-binding.json").path("command").asText()).isEqualTo("f0 probe-g08");
            // 응답 원문·stub 응답의 다른 필드·후보 집합 밖 토큰은 결과·표준 출력에 없습니다.
            assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL).doesNotContain(BASELINE_OUTPUT).doesNotContain("tc.logs.search\"]");
            // 같은 회차 디렉터리 재실행은 run-binding 이 있어 추론 호출 전에 거부됩니다.
            int before = stub.inference().size();
            assertThat(probe(f, stub, MANIFEST)).isEqualTo(2); assertThat(f.lastError).isEqualTo("RUN_BINDING_EXISTS"); assertThat(stub.inference()).hasSize(before);
        }
    }

    @Test void unregisteredManifestMissingAuditRowOrUndeclaredPurposeIsRejectedBeforeAnyInference() throws Exception {
        try (OllamaStub stub = new OllamaStub()) {
            F0TestRoot f = registered(directory.resolve("a"), stub, id -> !id.equals("probe/E6"));
            f.write("eval/f0/v3/probe-g08/copy.json", f.text(MANIFEST));
            assertThat(probe(f, stub, "eval/f0/v3/probe-g08/copy.json")).isEqualTo(2); assertThat(f.lastError).isEqualTo("ARGUMENT_NOT_REGISTERED");
            assertThat(probe(f, stub, MANIFEST)).isEqualTo(2); assertThat(f.lastError).isEqualTo("INPUT_AUDIT_MISSING");
            assertThat(stub.requests).isEmpty();
        }
        try (OllamaStub stub = new OllamaStub()) {
            F0TestRoot f = registered(directory.resolve("b"), stub, id -> true);
            String text = f.text(PREREG).replace("\"purpose\":\"DIAGNOSTIC\"", "\"purpose\":\"GATE\"");
            f.write(PREREG, text.replace("\"repeats\":1", "\"repeats\":5"));
            assertThat(probe(f, stub, MANIFEST)).isEqualTo(2); assertThat(f.lastError).isEqualTo("PREREG_INVALID");
            assertThat(stub.requests).isEmpty();
        }
    }
}
