package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * v3 opt-in 응답 형식 JSON_SCHEMA_PLAN_ORDERED_V1 의 송신 바이트·해시 정의·등록 값(LLM_STUB — 실측 아님).
 * 요청 본문은 키 정렬 canonical JSON 이라 JSON_SCHEMA_V1 의 계획 스키마 properties 는 eqp, from, qRef, steps, t, to 순서로 송신됩니다.
 * 새 값은 계획(A) 스키마의 properties 만 선언 순서 t, eqp, from, to, qRef, steps 로 보내고, 스키마 내용·분류(B·경계) 스키마·나머지 본문 바이트는 그대로여야 합니다.
 * 기존 값(NONE·JSON_SCHEMA_V1)의 요청 바이트와 responseFormatSha256 은 변경 전 식(전체 키 정렬 canonical)과 바이트까지 같아야 합니다.
 */
class F0PlanSchemaOrderTest {
    static final String RUN = F0VersionedInputsTest.RUN, RESULTS = "eval/f0/results/" + RUN;
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    static final List<String> PLAN_ORDER = List.of("t", "eqp", "from", "to", "qRef", "steps"), SORTED_PLAN = List.of("eqp", "from", "qRef", "steps", "t", "to");
    @TempDir Path directory;

    static List<String> keys(JsonNode node) { List<String> keys = new ArrayList<>(); node.fieldNames().forEachRemaining(keys::add); return keys; }
    static JsonNode properties(JsonNode format) { return format.path("json_schema").path("schema").path("properties"); }
    /** 시험 쪽 독립 구성: 전체 정렬 canonical 문자열에서 properties 객체 하나만 선언 순서 렌더링으로 바꿉니다(하네스 직렬화 코드 미호출). */
    static String expectedOrdered(JsonNode root, JsonNode properties) {
        String sorted = Json.canonicalString(root), target = "\"properties\":" + Json.canonicalString(properties);
        assertThat(sorted.indexOf(target)).isNotNegative().isEqualTo(sorted.lastIndexOf(target));
        List<String> fields = new ArrayList<>();
        for (String key : PLAN_ORDER) fields.add(Json.canonicalString(TextNode.valueOf(key)) + ":" + Json.canonicalString(properties.get(key)));
        return sorted.replace(target, "\"properties\":{" + String.join(",", fields) + "}");
    }
    /** 실제 송신 본문 바이트에서 response_format 값 바이트만 잘라냅니다(정렬 키상 response_format 뒤에는 stream·temperature 만 옵니다). */
    static byte[] responseFormatBytes(byte[] raw) {
        String body = new String(raw, StandardCharsets.UTF_8), key = "\"response_format\":", tail = ",\"stream\":false,\"temperature\":0}";
        assertThat(body).endsWith(tail);
        return body.substring(body.lastIndexOf(key) + key.length(), body.length() - tail.length()).getBytes(StandardCharsets.UTF_8);
    }
    /** 실제 등록 입력의 질문 전체: v2 골든 22·회귀 30·경계 9. */
    static List<String> registeredQuestions() {
        RegistryFixture fixture = RegistryFixture.load(ROOT.resolve("eval/golden/tc-registry-fixture-v2.json"));
        List<String> questions = new ArrayList<>();
        for (String golden : List.of("eval/golden/tc-f0-v2.jsonl", "eval/f0/v3/golden-regression-v3.jsonl")) GoldenSet.load(ROOT.resolve(golden), fixture).questions().forEach(q -> questions.add(q.question()));
        BoundaryCases.load(ROOT.resolve("eval/f0/v2/output-boundary-cases.jsonl")).forEach(c -> questions.add(c.path("question").asText()));
        assertThat(questions).hasSize(22 + 30 + 9);
        return questions;
    }

    @Test void keepingOrderSerializerIsCanonicalEverywhereExceptTheNamedObject() {
        JsonNode node = Json.parse("{\"z\":{\"b\":1,\"a\":[{\"y\":2,\"x\":\"é\\t\"}]},\"m\":{\"keep\":{\"q\":1e23,\"c\":{\"k\":true,\"j\":null},\"a\":-0.0},\"other\":{\"d\":1,\"c\":2}},\"a\":\"\\\"\"}".getBytes(StandardCharsets.UTF_8));
        // 경로가 없으면 Json.canonical 과 같은 바이트입니다(기존 송신·해시 경로).
        assertThat(ResponseSchema.canonicalKeepingOrder(node, Set.of())).isEqualTo(Json.canonical(node));
        // 지정 객체 하나만 노드 순서이고, 그 안의 객체·형제·조상은 정렬, 문자열·숫자 규칙은 canonical 그대로입니다.
        assertThat(new String(ResponseSchema.canonicalKeepingOrder(node, Set.of(List.of("m", "keep"))), StandardCharsets.UTF_8))
            .isEqualTo("{\"a\":\"\\\"\",\"m\":{\"keep\":{\"q\":1e+23,\"c\":{\"j\":null,\"k\":true},\"a\":0},\"other\":{\"c\":2,\"d\":1}},\"z\":{\"a\":[{\"x\":\"é\\t\",\"y\":2}],\"b\":1}}");
        assertThat(new String(ResponseSchema.canonicalKeepingOrder(node, Set.of(List.of())), StandardCharsets.UTF_8))
            .isEqualTo("{\"z\":{\"a\":[{\"x\":\"é\\t\",\"y\":2}],\"b\":1},\"m\":{\"keep\":{\"a\":0,\"c\":{\"j\":null,\"k\":true},\"q\":1e+23},\"other\":{\"c\":2,\"d\":1}},\"a\":\"\\\"\"}");
        // 배열 안 객체는 키 경로로 지정되지 않으므로 정렬됩니다.
        assertThat(ResponseSchema.canonicalKeepingOrder(node, Set.of(List.of("z", "a")))).isEqualTo(Json.canonical(node));
    }

    @Test void policiesShareOneSchemaAndOnlyThePlanOrderedPolicyReordersPlanPropertiesOnTheWire() {
        int planned = 0;
        for (String question : registeredQuestions()) {
            QRefContext refs = QRefContext.extract(question);
            for (MessageAssembler.Kind kind : MessageAssembler.Kind.values()) {
                assertThat(ResponseSchema.forPolicy("NONE", kind, refs)).isNull();
                ObjectNode legacy = ResponseSchema.responseFormat(kind, refs);
                // 변경 전 송신·responseFormatSha256 정의 = 전체 키 정렬 canonical 바이트.
                byte[] legacyBytes = Json.canonical(legacy);
                ResponseSchema.Format v1 = ResponseSchema.forPolicy(ResponseSchema.POLICY, kind, refs), ordered = ResponseSchema.forPolicy(ResponseSchema.PLAN_ORDERED_POLICY, kind, refs);
                assertThat(v1.node()).isEqualTo(legacy); assertThat(v1.bytes()).isEqualTo(legacyBytes); assertThat(v1.sha256()).isEqualTo(Json.sha(legacyBytes));
                assertThat(keys(properties(Json.parse(legacyBytes)))).containsExactlyElementsOf(kind == MessageAssembler.Kind.PLAN ? SORTED_PLAN : List.of("eqp", "from", "qRef", "t", "to"));
                // 새 정책도 스키마 내용(키·타입·enum·required·additionalProperties·strict)은 같습니다.
                assertThat(ordered.node()).isEqualTo(legacy);
                if (kind == MessageAssembler.Kind.CLASSIFY) {
                    // 분류 스키마(B·경계)는 송신 바이트·해시가 V1 과 같습니다.
                    assertThat(ordered.bytes()).isEqualTo(legacyBytes); assertThat(ordered.sha256()).isEqualTo(v1.sha256());
                    continue;
                }
                planned++;
                assertThat(new String(ordered.bytes(), StandardCharsets.UTF_8)).isEqualTo(expectedOrdered(legacy, properties(legacy)));
                assertThat(keys(properties(Json.parse(ordered.bytes())))).containsExactlyElementsOf(PLAN_ORDER);
                assertThat(Json.parse(ordered.bytes())).isEqualTo(legacy);
                assertThat(ordered.sha256()).isEqualTo(Json.sha(ordered.bytes())).isNotEqualTo(v1.sha256());
            }
        }
        assertThat(planned).isEqualTo(22 + 30 + 9);
    }

    @Test void existingPoliciesKeepTheLegacyRequestBytesAndTheNewPolicyMovesOnlyPlanProperties() throws Exception {
        Map<String,List<OllamaStub.Request>> sent = new LinkedHashMap<>(); Map<String,F0TestRoot> roots = new LinkedHashMap<>(); Map<String,Prereg.Document> docs = new LinkedHashMap<>();
        for (String policy : List.of("NONE", ResponseSchema.POLICY, ResponseSchema.PLAN_ORDERED_POLICY)) {
            try (OllamaStub stub = new OllamaStub()) {
                F0TestRoot f = UsageProbeTest.rendererRegistered(directory.resolve(policy), stub);
                Prereg.Document doc = F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", policy);
                var correct = StubAnswers.correct(f.root);
                // 초회는 무효 JSON, 재시도는 정답 → 모든 문항·케이스가 재시도 요청을 보냅니다.
                stub.chat = body -> body.path("messages").size() == 2 ? OllamaStub.chatResponse("{}", "stop", 300, 2) : correct.apply(body);
                assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).isZero(); assertThat(F0VersionedInputsTest.run(f, stub, doc, "A")).isZero();
                assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", RESULTS + "/boundary")).isZero();
                UsageProbeTest.cacheModel(stub, false);
                assertThat(F0VersionedInputsTest.execute(f, "usage-probe", "--endpoint", stub.endpoint(), "--messages", doc.input("usageMessages"), "--out", RESULTS + "/usage.json")).isZero();
                assertThat(F0VersionedInputsTest.execute(f, "token-golden", "--endpoint", stub.endpoint(), "--messages", doc.input("tokenMessages"), "--out", RESULTS + "/token-golden.jsonl")).isZero();
                sent.put(policy, F0StructuredOutputDiagnosticTest.chats(stub)); roots.put(policy, f); docs.put(policy, doc);
            }
        }
        List<OllamaStub.Request> none = sent.get("NONE"), v1 = sent.get(ResponseSchema.POLICY), ordered = sent.get(ResponseSchema.PLAN_ORDERED_POLICY);
        assertThat(v1).hasSameSizeAs(none).hasSameSizeAs(ordered);
        Map<String,byte[]> planWire = new HashMap<>();
        int plan = 0, other = 0;
        for (int i = 0; i < v1.size(); i++) {
            OllamaStub.Request n = none.get(i), a = v1.get(i), b = ordered.get(i);
            // 기존 정책(NONE = 진단 a, JSON_SCHEMA_V1 = b~g)은 모든 요청이 변경 전 식(전체 키 정렬 canonical)과 같은 바이트입니다.
            assertThat(n.raw()).isEqualTo(Json.canonical(n.body())); assertThat(a.raw()).isEqualTo(Json.canonical(a.body()));
            assertThat(n.body().has("response_format")).isFalse();
            ObjectNode withoutFormat = a.body().deepCopy(); withoutFormat.remove("response_format");
            assertThat(withoutFormat).isEqualTo(n.body());
            if (a.body().path("max_tokens").asInt() != Prereg.Budget.V2.planOutput()) {
                // B·경계·usage·token 요청은 새 정책에서도 V1 과 바이트가 같습니다.
                other++;
                assertThat(b.raw()).isEqualTo(a.raw());
                continue;
            }
            plan++;
            JsonNode properties = properties(b.body().path("response_format"));
            assertThat(b.body()).isEqualTo(a.body());
            assertThat(new String(b.raw(), StandardCharsets.UTF_8)).isEqualTo(expectedOrdered(b.body(), properties));
            assertThat(keys(properties)).containsExactlyElementsOf(PLAN_ORDER); assertThat(keys(properties(a.body().path("response_format")))).containsExactlyElementsOf(SORTED_PLAN);
            // 초회·재시도 모두 같은 요청 바인딩 형식이며, 송신된 response_format 바이트 = Format.bytes() 입니다.
            String question = OllamaStub.question(b.body());
            byte[] wire = responseFormatBytes(b.raw());
            assertThat(wire).isEqualTo(ResponseSchema.forPolicy(ResponseSchema.PLAN_ORDERED_POLICY, MessageAssembler.Kind.PLAN, QRefContext.extract(question)).bytes());
            byte[] previous = planWire.putIfAbsent(question, wire);
            if (previous != null) assertThat(wire).isEqualTo(previous);
        }
        GoldenSet golden = F0StructuredOutputDiagnosticTest.golden(roots.get(ResponseSchema.POLICY), docs.get(ResponseSchema.POLICY));
        assertThat(plan).isEqualTo(2 * 2 * golden.questions().size()); assertThat(other).isPositive();
        // 결과 행 responseFormatSha256: 기존 정의(정렬 canonical)는 그대로이고, 새 정책의 계획 행은 실제 송신 바이트의 SHA 입니다.
        for (String route : List.of("B", "A")) {
            JsonNode rowsV1 = roots.get(ResponseSchema.POLICY).json(RESULTS + "/" + route + "/f0-results.json").path("attempts"), rowsOrdered = roots.get(ResponseSchema.PLAN_ORDERED_POLICY).json(RESULTS + "/" + route + "/f0-results.json").path("attempts");
            assertThat(rowsOrdered.size()).isEqualTo(rowsV1.size()).isPositive();
            assertThat(roots.get("NONE").json(RESULTS + "/" + route + "/f0-results.json").path("attempts").get(0).has("responseFormatSha256")).isFalse();
            MessageAssembler.Kind kind = route.equals("A") ? MessageAssembler.Kind.PLAN : MessageAssembler.Kind.CLASSIFY;
            for (int i = 0; i < rowsV1.size(); i++) {
                String question = golden.byId(rowsV1.get(i).path("questionId").asText()).question();
                String legacySha = Json.sha(Json.canonical(ResponseSchema.responseFormat(kind, QRefContext.extract(question))));
                assertThat(rowsV1.get(i).path("responseFormatSha256").asText()).isEqualTo(legacySha);
                assertThat(rowsOrdered.get(i).path("questionId").asText()).isEqualTo(rowsV1.get(i).path("questionId").asText());
                if (kind == MessageAssembler.Kind.CLASSIFY) assertThat(rowsOrdered.get(i).path("responseFormatSha256").asText()).isEqualTo(legacySha);
                else assertThat(rowsOrdered.get(i).path("responseFormatSha256").asText()).isEqualTo(Json.sha(planWire.get(question))).isNotEqualTo(legacySha);
            }
        }
        JsonNode casesV1 = roots.get(ResponseSchema.POLICY).json(RESULTS + "/boundary/boundary-results.json").path("cases"), casesOrdered = roots.get(ResponseSchema.PLAN_ORDERED_POLICY).json(RESULTS + "/boundary/boundary-results.json").path("cases");
        assertThat(casesOrdered.size()).isEqualTo(casesV1.size()).isPositive();
        for (int i = 0; i < casesV1.size(); i++) for (int k = 0; k < casesV1.get(i).path("attempts").size(); k++)
            assertThat(casesOrdered.get(i).path("attempts").get(k).path("responseFormatSha256").asText()).isEqualTo(casesV1.get(i).path("attempts").get(k).path("responseFormatSha256").asText()).isNotEmpty();
        F0TestRoot f = roots.get(ResponseSchema.PLAN_ORDERED_POLICY);
        assertThat(f.json(RESULTS + "/A/run-binding.json").path("parameterManifest").path("responseFormat").asText()).isEqualTo(ResponseSchema.PLAN_ORDERED_POLICY);
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", RUN, "--results", RESULTS)).isZero();
        assertThat(f.text(RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT)).contains("responseFormat `" + ResponseSchema.PLAN_ORDERED_POLICY + "`");
        assertThat(f.allOutput(RESULTS)).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void planOrderedIsAV3OptInResponseFormatValue() throws Exception {
        assertThat(Prereg.RESPONSE_FORMATS).containsExactlyInAnyOrder("NONE", ResponseSchema.POLICY, ResponseSchema.PLAN_ORDERED_POLICY);
        F0TestRoot f = new F0TestRoot(directory);
        Prereg.Document doc = F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", ResponseSchema.PLAN_ORDERED_POLICY);
        assertThat(doc.responseFormat()).isEqualTo(ResponseSchema.PLAN_ORDERED_POLICY);
        ObjectNode valid = (ObjectNode)doc.block().deepCopy();
        for (var change : List.<java.util.function.Consumer<ObjectNode>>of(
                b -> p(b).put("responseFormat", "json_schema_plan_ordered_v1"), b -> p(b).put("responseFormat", "JSON_SCHEMA_PLAN_ORDERED_V2"), b -> p(b).put("responseFormat", "JSON_SCHEMA_V2"),
                b -> { b.put("schema", "f0-prereg-v2"); b.remove("purpose"); },
                b -> { b.put("purpose", "GATE"); p(b).put("repeats", 1); })) {
            ObjectNode block = valid.deepCopy(); change.accept(block); F0VersionedInputsTest.writePrereg(f, block);
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        ObjectNode gate = valid.deepCopy(); gate.put("purpose", "GATE"); p(gate).put("repeats", Prereg.GATE_REPEATS); F0VersionedInputsTest.writePrereg(f, gate);
        assertThat(Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG)).responseFormat()).isEqualTo(ResponseSchema.PLAN_ORDERED_POLICY);
    }
    private static ObjectNode p(ObjectNode block) { return (ObjectNode)block.path("parameters"); }
}
