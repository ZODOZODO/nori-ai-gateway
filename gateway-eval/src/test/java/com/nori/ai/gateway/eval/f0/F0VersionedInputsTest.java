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

class F0VersionedInputsTest {
    static final String PREREG = "eval/f0/PREREG-v2.md", RUN = "20261101T000000Z";
    @TempDir Path directory;

    static final String AUDIT = "eval/f0/v2/input-audit-test.json";
    /** 역할마다 다른 경로를 등록해 하드코딩된 v1 경로로 우회하는 회귀를 잡습니다. */
    static Prereg.Document versioned(F0TestRoot f) throws Exception { return versioned(f, id -> 10); }
    /** counts 가 null 을 돌려준 위치 id 는 audit 표에서 뺍니다(누락 시험용). */
    static Prereg.Document versioned(F0TestRoot f, java.util.function.Function<String,Integer> counts) throws Exception {
        ObjectNode block = (ObjectNode)Prereg.parse(f.root.resolve(Prereg.DEFAULT_PATH)).block().deepCopy();
        block.put("schema", "f0-prereg-v2"); block.put("runtimeCaptureDir", "eval/f0/chat-template/v2");
        ObjectNode parameters = (ObjectNode)block.path("parameters"); parameters.put("judgeRulesVersion", "f0-judge-v2");
        Prereg.Budget v2 = Prereg.Budget.V2;
        parameters.putObject("maxTokens").put("classify", v2.classifyOutput()).put("plan", v2.planOutput()); parameters.put("outputLimitTokens", v2.outputLimit());
        parameters.putObject("inputLimitTokens").put("classify", v2.classifyInput()).put("plan", v2.planInput()); parameters.put("contextTokens", Prereg.CONTEXT_TOKENS);
        var inputs = block.putArray("inputs"); ObjectNode roles = block.putObject("inputRoles");
        for (var role : Prereg.LEGACY_ROLES.entrySet()) {
            String target = role.getValue() + "-v2";
            String text = f.text(role.getValue());
            if (role.getKey().equals("boundaryCases")) {
                StringBuilder converted = new StringBuilder();
                for (JsonNode original : BoundaryCases.load(f.root.resolve(role.getValue()))) {
                    ObjectNode row = original.deepCopy(); JsonNode resolved = row.path("expectedOutput");
                    ObjectNode wire = QRefContext.extract(row.path("question").asText()).wireExpected(resolved);
                    row.set("expectedResolvedOutput", resolved); row.set("expectedOutput", wire); row.put("expectedJson", Json.canonicalString(wire));
                    converted.append(Json.canonicalString(row)).append('\n');
                }
                text = converted.toString();
            }
            f.write(target, text); roles.put(role.getKey(), target);
            inputs.addObject().put("path", target).put("sha256", Json.sha(Json.bytes(f.root.resolve(target))));
        }
        writeAudit(f, roles, counts); roles.put("inputAudit", AUDIT);
        inputs.addObject().put("path", AUDIT).put("sha256", Json.sha(Json.bytes(f.root.resolve(AUDIT))));
        for (String file : Prereg.SUPPORT_INPUTS) {
            if (!Files.exists(f.root.resolve(file))) f.copy(file);
            inputs.addObject().put("path", file).put("sha256", Json.sha(Json.bytes(f.root.resolve(file))));
        }
        writePrereg(f, block);
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(PREREG))));
        writePrereg(f, block);
        return Prereg.parse(f.root.resolve(PREREG));
    }
    /** count_input_audit_v2.py 산출과 같은 모양의 표를 실제 Java 조립 목록으로 만듭니다(계수 값은 시험 지정). */
    static void writeAudit(F0TestRoot f, JsonNode roles, java.util.function.Function<String,Integer> counts) throws Exception { writeAudit(f, roles, counts, MessageAssembler.FULL); }
    /** projection 은 audit 를 만들 때 쓴 Context 투영(FULL = 기존 전체 Context 조립)입니다. */
    static void writeAudit(F0TestRoot f, JsonNode roles, java.util.function.Function<String,Integer> counts, String projection) throws Exception {
        java.util.function.Function<String,Path> role = name -> f.root.resolve(roles.path(name).asText());
        RegistryFixture fixture = RegistryFixture.load(role.apply("fixture")); GoldenSet golden = GoldenSet.load(role.apply("golden"), fixture);
        MessageAssembler classify = MessageAssembler.load(role.apply("classifyPrompt"), fixture, projection), plan = MessageAssembler.load(role.apply("planPrompt"), fixture, projection);
        List<InputAudit.Planned> planned = new ArrayList<>(InputAudit.run(golden, plan, Prereg.Budget.V2, "A"));
        planned.addAll(InputAudit.run(golden, classify, Prereg.Budget.V2, "B")); planned.addAll(InputAudit.boundary(BoundaryCases.load(role.apply("boundaryCases")), classify, Prereg.Budget.V2));
        for (String use : List.of("usage", "token")) planned.addAll(InputAudit.probes(use, F0RunnerCli.messages(role.apply(use + "Messages"), role.apply("fixture"), role.apply("classifyPrompt"), role.apply("golden"), true, projection)));
        ObjectNode audit = Json.MAPPER.createObjectNode().put("schema", InputAudit.SCHEMA).put("tokenizerSha256", MeasurementSession.tokenizerSha(f.root))
            .put("templateSha256", Json.sha(OllamaStub.DEFAULT_TEMPLATE.getBytes(StandardCharsets.UTF_8)));
        var rows = audit.putArray("rows");
        for (InputAudit.Planned call : planned) { Integer count = counts.apply(call.id()); if (count != null) rows.addObject().put("id", call.id()).put("messagesSha256", InputAudit.messagesSha256(call.messages())).put("inputTokens", count); }
        f.write(AUDIT, Json.canonicalString(audit));
    }
    static void writePrereg(F0TestRoot f, JsonNode block) throws Exception { f.write(PREREG, "# v2\n\n```json\n" + Json.canonicalString(block) + "\n```\n"); }
    static int execute(F0TestRoot f, String... args) {
        List<String> all = new ArrayList<>(List.of(args)); all.addAll(List.of("--prereg", PREREG));
        return f.exec(all.toArray(String[]::new));
    }
    static int run(F0TestRoot f, OllamaStub stub, Prereg.Document doc, String route) {
        return execute(f, "run", "--path", route, "--endpoint", stub.endpoint(), "--golden", doc.input("golden"), "--fixture", doc.input("fixture"), "--prompt", doc.input(route.equals("A") ? "planPrompt" : "classifyPrompt"), "--repeats", String.valueOf(doc.repeats()), "--run-id", RUN, "--out", "eval/f0/results/" + RUN + "/" + route);
    }

    @Test void everyMeasurementAndReportUsesSelectedRegistrationAndAssemblyRoles() throws Exception {
        F0TestRoot f; Prereg.Document doc;
        try (OllamaStub stub = new OllamaStub()) {
            f = UsageProbeTest.rendererRegistered(directory, stub); doc = versioned(f);
            String digest = HarnessDigest.compute(f.root, doc);
            f.write("eval/f0/PREREG-unused.md", "별도 사전등록");
            f.write("eval/f0/unrelated-v3-input.txt", "다음 버전 입력");
            assertThat(HarnessDigest.compute(f.root, doc)).isEqualTo(digest);
            assertThat(Prereg.localMismatches(f.root, doc)).isEmpty();
            stub.chat = StubAnswers.correct(f.root);
            // v1 원본이 없어도 내부 메시지 조립·렌더러·보고서까지 선택한 등록만 읽어야 합니다.
            for (String file : Prereg.LEGACY_ROLES.values()) Files.delete(f.root.resolve(file));
            assertThat(execute(f, "preflight", "--endpoint", stub.endpoint(), "--out", "eval/f0/results/" + RUN + "/preflight.json")).isZero();
            assertThat(run(f, stub, doc, "A")).isZero(); assertThat(run(f, stub, doc, "B")).isZero();
            assertThat(execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", "eval/f0/results/" + RUN + "/boundary")).isZero();
            assertThat(execute(f, "gguf-count", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--out", "eval/f0/results/" + RUN + "/gguf")).isZero();
            assertThat(execute(f, "chat-template", "--endpoint", stub.endpoint(), "--model", "qwen3:4b-instruct", "--out", "eval/f0/chat-template/v2")).isZero();
            UsageProbeTest.cacheModel(stub, false);
            assertThat(execute(f, "usage-probe", "--endpoint", stub.endpoint(), "--messages", doc.input("usageMessages"), "--out", "eval/f0/results/" + RUN + "/usage.json")).isZero();
            assertThat(execute(f, "token-golden", "--endpoint", stub.endpoint(), "--messages", doc.input("tokenMessages"), "--out", "eval/f0/results/" + RUN + "/token-golden.jsonl")).isZero();
        }
        JsonNode binding = f.json("eval/f0/results/" + RUN + "/B/run-binding.json");
        assertThat(binding.path("preregPath").asText()).isEqualTo(PREREG);
        assertThat(binding.path("preregSha256").asText()).isEqualTo(Json.sha(Json.bytes(f.root.resolve(PREREG))));
        assertThat(execute(f, "report", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--out", "evidence/F0")).isZero();
        assertThat(f.root.resolve("eval/f0/results/" + RUN + "/report.md")).exists();
        assertThat(f.allOutput("eval/f0/results/" + RUN)).doesNotContain(OllamaStub.SENTINEL).doesNotContain("WORK_ORDER_REPLY");
        Map<String,String> rawBefore = F0RejudgeTest.snapshot(f.root.resolve("eval/f0/results"));
        Map<String,String> evidenceBefore = F0RejudgeTest.snapshot(f.root.resolve("evidence"));
        // v1 캡처가 현재 런타임과 달라도 v2 재판정은 등록한 v2 캡처만 대조해야 합니다.
        f.write("eval/f0/chat-template/qwen3-4b-instruct.template", "과거 v1 템플릿");
        f.write("eval/f0/chat-template/qwen3-4b-instruct.template.sha256", "과거 v1 해시");
        f.write("eval/f0/chat-template/model-digest.txt", "과거 v1 모델");
        Map<String,String> legacyBefore = new TreeMap<>();
        for (String file : List.of("qwen3-4b-instruct.template", "qwen3-4b-instruct.template.sha256", "model-digest.txt")) legacyBefore.put(file, Json.sha(Json.bytes(f.root.resolve("eval/f0/chat-template/" + file))));
        String measuredSpec = F0RejudgeTest.reviseSpec(f, false, null);
        assertThat(execute(f, "report", "--rejudge", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--out", "evidence/F0")).isZero();
        assertThat(F0RejudgeTest.records(f).stream().filter(r -> r.has("rawRunId"))).isNotEmpty().allSatisfy(r -> assertThat(r.path("measurementSpecSha").asText()).isEqualTo(measuredSpec));
        assertThat(execute(f, "report", "--spec-record-check", "--proposal", "eval/f0/results/" + RUN + "/spec-14.5-proposal.md", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--out", "evidence/F0")).isZero();
        assertThat(F0RejudgeTest.records(f).stream().filter(r -> r.path("producer").asText().equals("SPEC_RECORD"))).hasSize(4).allSatisfy(r -> assertThat(r.path("result").asText()).isEqualTo("PASS"));
        assertThat(F0RejudgeTest.snapshot(f.root.resolve("eval/f0/results"))).isEqualTo(rawBefore);
        Map<String,String> evidenceAfter = F0RejudgeTest.snapshot(f.root.resolve("evidence"));
        evidenceBefore.forEach((file, sha) -> assertThat(evidenceAfter).containsEntry(file, sha));
        legacyBefore.forEach((file, sha) -> assertThat(Json.sha(Json.bytes(f.root.resolve("eval/f0/chat-template/" + file)))).isEqualTo(sha));
        Path selectedTemplate = f.root.resolve(doc.runtimeCaptureDir()).resolve("qwen3-4b-instruct.template");
        Files.writeString(selectedTemplate, "변조된 v2 템플릿", StandardCharsets.UTF_8);
        assertThat(execute(f, "report", "--rejudge", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--out", "evidence/F0")).isEqualTo(3);
        assertThat(f.lastError).isEqualTo("RUNTIME_UNVERIFIED");
        Files.writeString(selectedTemplate.resolveSibling("qwen3-4b-instruct.template.sha256"), Json.sha(Json.bytes(selectedTemplate)), StandardCharsets.UTF_8);
        assertThat(execute(f, "report", "--rejudge", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--out", "evidence/F0")).isEqualTo(3);
        assertThat(f.lastError).isEqualTo("RUNTIME_CHANGED");
        assertThat(F0RejudgeTest.snapshot(f.root.resolve("evidence"))).isEqualTo(evidenceAfter);
    }

    @Test void wrongVersionPathOrChangedShaCannotReachEndpointAndAllRegisteredInputsAreHashed() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = versioned(f);
        try (OllamaStub stub = new OllamaStub()) {
            assertThat(execute(f, "run", "--path", "B", "--endpoint", stub.endpoint(), "--golden", Prereg.LEGACY_ROLES.get("golden"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", RUN, "--out", "eval/f0/results/" + RUN + "/B")).isEqualTo(2);
            assertThat(f.lastError).isEqualTo("ARGUMENT_NOT_REGISTERED");
            for (String file : doc.inputs().keySet()) {
                byte[] before = Json.bytes(f.root.resolve(file)); Files.write(f.root.resolve(file), F0RejudgeTest.concat(before, "\n"));
                assertThat(Prereg.localMismatches(f.root, doc)).contains(file, "harnessDigest");
                assertThat(run(f, stub, doc, "B")).isEqualTo(2); assertThat(f.lastError).isEqualTo("PREREG_INPUT_CHANGED");
                Files.write(f.root.resolve(file), before);
            }
            assertThat(stub.requests).isEmpty();
        }
        ObjectNode bad = (ObjectNode)doc.block().deepCopy(); ((ObjectNode)bad.path("inputRoles")).put("golden", "eval/golden/not-registered.jsonl"); writePrereg(f, bad);
        assertThatThrownBy(() -> Prereg.parse(f.root.resolve(PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
    }

    @Test void selectedRegistrationCannotJudgeAnotherRegistrationsBinding() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = versioned(f);
        try (OllamaStub stub = new OllamaStub()) { stub.chat = StubAnswers.correct(f.root); assertThat(run(f, stub, doc, "B")).isZero(); }
        ObjectNode binding = (ObjectNode)f.json("eval/f0/results/" + RUN + "/B/run-binding.json"); binding.put("preregPath", Prereg.DEFAULT_PATH);
        Json.write(f.root.resolve("eval/f0/results/" + RUN + "/B/run-binding.json"), binding);
        assertThat(execute(f, "report", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--out", "evidence/F0")).isEqualTo(2);
        assertThat(f.lastError).isEqualTo("RUN_BINDING_INCONSISTENT"); assertThat(f.root.resolve("evidence")).doesNotExist();
    }

    @Test void captureDirectoryMustBeExplicitSafeAndMatchTheSelectedRegistrationBeforeAnyCall() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = versioned(f);
        try (OllamaStub stub = new OllamaStub()) {
            for (String target : List.of("eval/f0/chat-template", "eval/f0/chat-template/v3", "eval/f0/results/" + RUN + "/template")) {
                assertThat(execute(f, "chat-template", "--endpoint", stub.endpoint(), "--model", "qwen3:4b-instruct", "--out", target)).isEqualTo(2);
                assertThat(f.lastError).isEqualTo("ARGUMENT_NOT_REGISTERED");
            }
            assertThat(stub.requests).isEmpty();
            assertThat(f.root.resolve(doc.runtimeCaptureDir())).doesNotExist();
        }
        for (String invalid : List.of("", "eval/f0/chat-template", "eval/f0/chat-template/../v1", "eval/f0/chat-template/v2/nested", "eval/f0/results/v2", "C:/external/v2")) {
            ObjectNode block = (ObjectNode)doc.block().deepCopy(); block.put("runtimeCaptureDir", invalid); writePrereg(f, block);
            assertThatThrownBy(() -> Prereg.parse(f.root.resolve(PREREG))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INVALID"));
        }
        ObjectNode missing = (ObjectNode)doc.block().deepCopy(); missing.remove("runtimeCaptureDir"); writePrereg(f, missing);
        assertThatThrownBy(() -> Prereg.parse(f.root.resolve(PREREG))).isInstanceOf(EvalException.class);
    }

    @Test void oldReportsAndEvidenceStayReadableAndUnchangedWhenHarnessChanges() throws Exception {
        F0TestRoot f = F0RejudgeTest.measuredAndReported(directory);
        Map<String,String> raw = F0RejudgeTest.snapshot(f.root.resolve("eval/f0/results")), evidence = F0RejudgeTest.snapshot(f.root.resolve("evidence"));
        f.write("gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0/Changed.java", "class Changed {}\n");
        assertThat(F0RejudgeTest.report(f)).isEqualTo(2); assertThat(f.lastError).isEqualTo("PREREG_INPUT_CHANGED");
        assertThat(F0RejudgeTest.report(f, "--rejudge")).isEqualTo(3); assertThat(f.lastError).isEqualTo("HARNESS_CHANGED");
        assertThat(Files.readString(f.root.resolve("eval/f0/results/" + RUN + "/report.md"), StandardCharsets.UTF_8)).contains("F0");
        assertThat(F0RejudgeTest.snapshot(f.root.resolve("eval/f0/results"))).isEqualTo(raw);
        assertThat(F0RejudgeTest.snapshot(f.root.resolve("evidence"))).isEqualTo(evidence);
    }

    @Test void diagnosticFieldsContainOnlyBooleansAndFixedKindsIncludingInvalidAndTimeout() {
        ObjectNode expected = Json.MAPPER.createObjectNode(); expected.put("t", "INC");
        Map<String,String> values = new HashMap<>();
        for (String field : GoldenSet.ARG_FIELDS) { expected.put(field, "PRIVATE_EXPECTED_" + field); values.put(field, "PRIVATE_ACTUAL_" + field); }
        var verdict = new ClassificationJudge.Verdict(true, null, "UNS", values, List.of());
        var outcome = new ClassificationJudge.Outcome("OK", false, true, List.of(), List.of(), List.of(), List.of(), List.of(), 0, verdict);
        JsonNode diagnostics = F0Measurements.diagnostics(outcome, expected);
        assertThat(GoldenSet.keys(diagnostics)).containsExactlyInAnyOrder("argumentMatches", "errorKinds");
        diagnostics.path("argumentMatches").forEach(value -> assertThat(value.isBoolean() && !value.asBoolean()).isTrue());
        assertThat(diagnostics.path("errorKinds").toString()).contains("TASK_TYPE_MISMATCH", "ARGUMENT_MISMATCH");
        assertThat(diagnostics.toString()).doesNotContain("PRIVATE_");
        for (String status : List.of("INVALID", "TIMEOUT")) {
            var failed = new ClassificationJudge.Outcome(status, false, false, List.of(), List.of(), List.of(), List.of(), List.of(), 0, null);
            assertThat(F0Measurements.diagnostics(failed, expected).path("errorKinds").get(0).asText()).isEqualTo(status.equals("TIMEOUT") ? "TIMEOUT" : "INVALID_OUTPUT");
        }
    }
}
