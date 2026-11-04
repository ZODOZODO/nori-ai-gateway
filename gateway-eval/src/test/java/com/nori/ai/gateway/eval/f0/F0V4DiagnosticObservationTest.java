package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * P03 v4 ④ 진단 분기의 관측 입력(Plan P03 v4 ⑥ 최소 입력): 시도별 사고·content 개수와 출력 키 순서, max_tokens 1 계수 호출의 종료 사유·완료 토큰·사고 관측,
 * v4 예산의 input-audit-render, v4 진단 보고서 절. 원문(사고문 sentinel)은 어디에도 남지 않습니다. LLM_STUB — 실측 아님.
 */
class F0V4DiagnosticObservationTest {
    static final String PREREG = F0V4RuntimeProfileTest.PREREG, RUN = F0V4RuntimeProfileTest.RUN, RESULTS = F0V4RuntimeProfileTest.RESULTS;
    static final String THINKING = OllamaStub.SENTINEL + " 사고문";
    @TempDir Path directory;

    @Test void outputKeyOrderKeepsOnlySchemaKeyNamesInEmittedOrder() {
        assertThat(F0Measurements.outputKeyOrder("{\"t\":\"INC\",\"eqp\":null,\"from\":null,\"to\":null,\"qRef\":null}")).containsExactly("t", "eqp", "from", "to", "qRef");
        assertThat(F0Measurements.outputKeyOrder("{\"steps\":[],\"qRef\":null,\"t\":\"GQ\"}")).containsExactly("steps", "qRef", "t");
        // 스키마 밖 키 이름은 모델 원문이므로 남기지 않습니다(판별 불가 = null). 객체가 아니거나 파싱 실패도 null 입니다.
        for (String content : List.of("{\"t\":\"INC\",\"" + OllamaStub.SENTINEL + "\":1}", "[]", "not json", "", "{\"t\":1,\"t\":2}")) assertThat(F0Measurements.outputKeyOrder(content)).as(content).isNull();
    }

    @Test void v4RunRecordsThinkingAndContentCodepointsKeyOrderAndTheReportSummarizesThemWithoutRawText() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0V4RuntimeProfileTest.v4(f, p -> p.put("repeats", 1));
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST); stub.chat = F0V4RuntimeProfileTest.withThinking(StubAnswers.correct(f.root));
            assertThat(F0V4RuntimeProfileTest.runB(f, stub, doc)).as(f.lastError).isZero();
        }
        JsonNode attempts = f.json(RESULTS + "/B/f0-results.json").path("attempts");
        int rows = attempts.size();
        for (JsonNode row : attempts) {
            JsonNode first = row.path("thinkingObservations").get(0);
            assertThat(first.path("thinkingCodepoints").asInt()).isEqualTo(THINKING.codePointCount(0, THINKING.length()));
            assertThat(first.path("contentCodepoints").asInt()).isPositive();
            // StubAnswers 는 키 정렬 JSON 을 돌려줍니다(출력 순서 그대로 기록).
            assertThat(row.path("outputKeyOrders")).hasSize(1);
            assertThat(Json.MAPPER.convertValue(row.path("outputKeyOrders").get(0), String[].class)).containsExactly("eqp", "from", "qRef", "t", "to");
        }
        assertThat(f.exec("report", "--prereg", PREREG, "--run-id", RUN, "--results", RESULTS)).as(f.lastError).isZero();
        String report = f.text(RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT);
        assertThat(report).contains("## v4 사고 출력 관측", "| B | 초회 | " + rows + " | reasoning=" + rows + " | " + rows + "/" + rows + " | 0/0 | 0 | 77/77/77 | " + THINKING.codePointCount(0, THINKING.length()) + " | ",
            "(0) | eqp,from,qRef,t,to=" + rows + " |");
        assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
    }

    @Test void v3DiagnosticReportHasNoV4SectionAndV3RowsKeepTheirShape() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0StructuredOutputDiagnosticTest.v3(f, "DIAGNOSTIC", "NONE");
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            assertThat(F0VersionedInputsTest.run(f, stub, doc, "B")).as(f.lastError).isZero();
        }
        for (JsonNode row : f.json(F0StructuredOutputDiagnosticTest.RESULTS + "/B/f0-results.json").path("attempts"))
            assertThat(GoldenSet.keys(row)).doesNotContain("thinkingObservations", "answerTokens", "callWindows", "outputKeyOrders");
        assertThat(F0VersionedInputsTest.execute(f, "report", "--run-id", F0VersionedInputsTest.RUN, "--results", F0StructuredOutputDiagnosticTest.RESULTS)).as(f.lastError).isZero();
        assertThat(f.text(F0StructuredOutputDiagnosticTest.RESULTS + "/" + F0ReportWriter.DIAGNOSTIC_REPORT)).doesNotContain("v4 사고 출력 관측");
    }

    /** max_tokens 1 계수 호출은 사고 도중 상한 도달입니다: 응답 모양(종료 사유·완료 토큰·content 0·사고 분리 필드)을 v4 행에만 남깁니다. */
    @Test void v4CountingProbesRecordFinishCompletionAndThinkingWhileV2RowsKeepTheirShape() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        // usage-probe 의 generate raw 렌더러 선택용 시험 등록(stub template SHA — 실제 Qwen template 등록 아님, UsageProbeTest 와 같은 방식).
        f.write(ChatRenderers.REGISTRY, "{\"renderers\":[{\"templateSha256\":\"" + Json.sha(OllamaStub.DEFAULT_TEMPLATE.getBytes(StandardCharsets.UTF_8)) + "\",\"renderer\":\"CHATML_NO_SYSTEM_DEFAULT\",\"reviewRef\":\"test-fixture-only\"}]}");
        Prereg.Document doc = F0V4RuntimeProfileTest.v4(f, p -> {});
        try (OllamaStub stub = new OllamaStub()) {
            F0V4RuntimeProfileTest.serve(stub, 8192, OllamaStub.GEN_DIGEST);
            stub.chat = body -> F0V4RuntimeProfileTest.withUsage((ObjectNode)OllamaStub.chatResponse("", "length", 300, 0).deepCopy(), 1, 0);
            assertThat(f.exec("usage-probe", "--prereg", PREREG, "--endpoint", stub.endpoint(), "--messages", doc.input("usageMessages"), "--out", RESULTS + "/usage.json")).as(f.lastError).isZero();
            assertThat(f.exec("token-golden", "--prereg", PREREG, "--endpoint", stub.endpoint(), "--messages", doc.input("tokenMessages"), "--out", RESULTS + "/token-golden.jsonl")).as(f.lastError).isZero();
            assertThat(F0StructuredOutputDiagnosticTest.chats(stub)).allSatisfy(r -> assertThat(r.body().path("max_tokens").asInt()).isEqualTo(1));
        }
        List<JsonNode> rows = new ArrayList<>(); f.json(RESULTS + "/usage.json").path("calls").forEach(rows::add);
        assertThat(rows).hasSize(4);
        for (String line : f.text(RESULTS + "/token-golden.jsonl").split("\n")) if (!line.isBlank()) rows.add(Json.parse(line.getBytes(StandardCharsets.UTF_8)));
        assertThat(rows).hasSizeGreaterThan(4 + 11);
        for (JsonNode row : rows) {
            assertThat(row.path("finishReason").asText()).isEqualTo("length"); assertThat(row.path("completionTokens").asInt()).isEqualTo(1);
            JsonNode thinking = row.path("thinking");
            assertThat(thinking.path("separateFields")).containsExactly(Json.MAPPER.getNodeFactory().textNode("reasoning"));
            assertThat(thinking.path("reasoningTokens").asInt()).isEqualTo(1); assertThat(thinking.path("contentCodepoints").asInt()).isZero();
            // 시험 audit 의 계수(10)와 서버 응답 prompt_tokens(300)를 따로 남깁니다(동등성 확인 입력).
            assertThat(row.path("auditInputTokens").asInt()).isEqualTo(10); assertThat(row.path("promptTokens").asInt()).isEqualTo(300);
        }
        assertThat(f.allOutput("eval/f0/results")).doesNotContain(OllamaStub.SENTINEL);
        // v2 등록의 같은 명령 결과 행에는 새 열이 없습니다.
        F0TestRoot g = new F0TestRoot(directory.resolve("v2"));
        g.write(ChatRenderers.REGISTRY, f.text(ChatRenderers.REGISTRY));
        Prereg.Document v2 = F0VersionedInputsTest.versioned(g);
        try (OllamaStub stub = new OllamaStub()) {
            assertThat(F0VersionedInputsTest.execute(g, "usage-probe", "--endpoint", stub.endpoint(), "--messages", v2.input("usageMessages"), "--out", F0StructuredOutputDiagnosticTest.RESULTS + "/usage.json")).as(g.lastError).isZero();
            assertThat(F0VersionedInputsTest.execute(g, "token-golden", "--endpoint", stub.endpoint(), "--messages", v2.input("tokenMessages"), "--out", F0StructuredOutputDiagnosticTest.RESULTS + "/token-golden.jsonl")).as(g.lastError).isZero();
        }
        g.json(F0StructuredOutputDiagnosticTest.RESULTS + "/usage.json").path("calls").forEach(row -> assertThat(GoldenSet.keys(row)).isEqualTo(Set.of("id", "phase", "promptTokens", "promptEvalCount")));
        for (String line : g.text(F0StructuredOutputDiagnosticTest.RESULTS + "/token-golden.jsonl").split("\n")) if (!line.isBlank())
            assertThat(GoldenSet.keys(Json.parse(line.getBytes(StandardCharsets.UTF_8)))).isEqualTo(Set.of("id", "messagesSha256", "promptTokens", "cacheExcluded", "modelDigest", "templateSha256"));
    }

    @Test void inputAuditRenderV4ProfileUsesTheDiagnosticBudgetAnd8192AndKeepsTheSameCallList() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        String template = f.json(ChatRenderers.REGISTRY).path("renderers").get(0).path("templateSha256").asText();
        List<String> base = List.of("input-audit-render", "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--classify-prompt", doc.input("classifyPrompt"), "--plan-prompt", doc.input("planPrompt"),
            "--cases", doc.input("boundaryCases"), "--usage-messages", doc.input("usageMessages"), "--token-messages", doc.input("tokenMessages"), "--renderers", ChatRenderers.REGISTRY, "--template-sha256", template);
        List<String> v2 = new ArrayList<>(base); v2.addAll(List.of("--out", "render-v2.jsonl"));
        List<String> v4 = new ArrayList<>(base); v4.addAll(List.of("--runtime-profile", "INFER_V4", "--out", "render-v4.jsonl"));
        assertThat(f.exec(v2.toArray(String[]::new))).as(f.lastError).isZero(); assertThat(f.exec(v4.toArray(String[]::new))).as(f.lastError).isZero();
        List<JsonNode> before = lines(f, "render-v2.jsonl"), after = lines(f, "render-v4.jsonl");
        assertThat(after).hasSameSizeAs(before).isNotEmpty();
        for (int i = 0; i < before.size(); i++) {
            JsonNode a = before.get(i), b = after.get(i); String id = a.path("id").asText();
            // 호출 목록·메시지·렌더 문자열은 같고 예산 열만 profile 값입니다.
            assertThat(b.path("id").asText()).isEqualTo(id); assertThat(b.path("messagesSha256").asText()).isEqualTo(a.path("messagesSha256").asText()); assertThat(b.path("rendered").asText()).isEqualTo(a.path("rendered").asText());
            boolean probe = id.startsWith("usage/") || id.startsWith("token/");
            assertThat(a.path("maxTokens").asInt()).as(id).isEqualTo(probe ? 1 : id.startsWith("A/") ? 256 : 192); assertThat(a.path("inputLimit").asInt()).as(id).isEqualTo(probe ? 4095 : 3072);
            assertThat(b.path("maxTokens").asInt()).as(id).isEqualTo(probe ? 1 : 5120); assertThat(b.path("inputLimit").asInt()).as(id).isEqualTo(probe ? 8191 : 3072);
        }
        for (String bad : List.of("JETSON", "infer_v4", "")) {
            List<String> args = new ArrayList<>(base); args.addAll(List.of("--runtime-profile", bad, "--out", "render-bad.jsonl"));
            assertThat(f.exec(args.toArray(String[]::new))).as(bad).isEqualTo(2); assertThat(f.lastError).isEqualTo("ARGUMENT_INVALID");
        }
        assertThat(f.root.resolve("render-bad.jsonl")).doesNotExist();
    }
    private static List<JsonNode> lines(F0TestRoot f, String file) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : f.text(file).split("\n")) if (!line.isBlank()) rows.add(Json.parse(line.getBytes(StandardCharsets.UTF_8)));
        return rows;
    }
}
