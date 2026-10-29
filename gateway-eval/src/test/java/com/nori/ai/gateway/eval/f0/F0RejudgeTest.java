package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.*;
import com.nori.ai.gateway.eval.spec.SpecBaseline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class F0RejudgeTest {
    static final String RUN = "20261101T000000Z";
    static final List<String> AC37 = List.of("AC-37-CMP", "AC-37-OUT-NORMAL", "AC-37-OUT-MAX", "AC-37-OUT-ESCAPE");
    @TempDir Path directory;

    /** stub 로 전체 측정(B·A·경계·GGUF·template)과 공식 계수 파일을 만든 뒤 원 판정(report)까지 수행합니다. */
    static F0TestRoot measuredAndReported(Path directory) throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = StubAnswers.correct(f.root);
            stub.generate = body -> { String prompt = body.path("prompt").asText(); return OllamaStub.generateResponse(prompt.equals(F0Measurements.CALIBRATION[0][1]) ? 57 : prompt.equals(F0Measurements.CALIBRATION[1][1]) ? 53 : prompt.startsWith("flush-") ? 4 : 30); };
            assertThat(F0RunTest.runB(f, stub, RUN)).isZero();
            assertThat(F0RunTest.run(f, stub, RUN, "A", "eval/f0/prompts/plan-v1.txt", "2")).isZero();
            assertThat(f.exec("boundary-gen", "--endpoint", stub.endpoint(), "--cases", "eval/f0/output-boundary-cases.jsonl", "--fixture", "eval/golden/tc-registry-fixture-v1.json",
                "--prompt", "eval/f0/prompts/classify-v1.txt", "--repeats", "2", "--run-id", RUN, "--out", "eval/f0/results/" + RUN + "/boundary")).isZero();
            assertThat(f.exec("gguf-count", "--endpoint", stub.endpoint(), "--cases", "eval/f0/output-boundary-cases.jsonl", "--out", "eval/f0/results/" + RUN + "/gguf")).isZero();
            assertThat(f.exec("chat-template", "--endpoint", stub.endpoint(), "--model", "qwen3:4b-instruct", "--out", "eval/f0/chat-template")).isZero();
        }
        Map<String,Object> official = new TreeMap<>();
        official.put("tool", "count_tokens.py"); official.put("tokenizerSha256", MeasurementSession.tokenizerSha(f.root)); official.put("tokenizersVersion", "fixture");
        official.put("casesSha256", Json.sha(Files.readAllBytes(f.root.resolve("eval/f0/output-boundary-cases.jsonl"))));
        official.put("rows", List.of(Map.of("caseId", "NORMAL-01", "kind", "NORMAL", "officialTokens", 30)));
        official.put("calibration", List.of(Map.of("id", "SPEC-14.5-OLD-EXAMPLE", "officialTokens", 57, "expectedTokens", 57), Map.of("id", "SPEC-14.5-OLD-EXAMPLE-NO-Q", "officialTokens", 53, "expectedTokens", 53)));
        Json.write(f.root.resolve("eval/f0/results/" + RUN + "/official-count.json"), official);
        assertThat(report(f)).isZero();
        return f;
    }
    static int report(F0TestRoot f, String... extra) {
        List<String> args = new ArrayList<>(List.of("report", "--run-id", RUN, "--results", "eval/f0/results/" + RUN, "--prereg", "eval/f0/PREREG.md", "--out", "evidence/F0")); args.addAll(List.of(extra));
        return f.exec(args.toArray(String[]::new));
    }
    static List<JsonNode> records(F0TestRoot f) { return new EvidenceRepositoryFs(f.root).readAll("F0"); }
    static Map<String,String> snapshot(Path base) throws Exception {
        Map<String,String> files = new TreeMap<>();
        try (var walk = Files.walk(base)) { for (Path p : walk.filter(Files::isRegularFile).toList()) files.put(base.relativize(p).toString(), Json.sha(Files.readAllBytes(p))); }
        return files;
    }
    static byte[] concat(byte[] bytes, String suffix) { byte[] tail = suffix.getBytes(StandardCharsets.UTF_8), all = Arrays.copyOf(bytes, bytes.length + tail.length); System.arraycopy(tail, 0, all, bytes.length, tail.length); return all; }
    static GateEvaluator.GateReport gate(F0TestRoot f) { return new GateEvaluator().evaluate("F0", RUN, records(f), LeafCatalog.load(null), SpecBaseline.parseChain(f.root.resolve("docs/gates/spec-baseline.md"))); }
    static Map<String,String> leaves(GateEvaluator.GateReport report) { Map<String,String> map = new TreeMap<>(); report.leaves().forEach(l -> map.put(l.leafId(), l.result())); return map; }
    /** SPEC 소유자가 제안서 블록을 §14.5 에 반영한 새 SPEC(S1)과 사슬 항목(seq 2)을 만듭니다. */
    static String reviseSpec(F0TestRoot f, boolean measurementAffecting, String blockMutation) throws Exception {
        String s0 = Json.sha(Files.readAllBytes(f.spec));
        String block = F0ReportWriter.proposalBlock(f.root.resolve("eval/f0/results/" + RUN + "/spec-14.5-proposal.md"));
        if (blockMutation != null) block = block.replace("회차", blockMutation);
        Files.writeString(f.spec, Files.readString(f.spec, StandardCharsets.UTF_8).replace("본문 S0\n\n", "본문 S0\n\n" + block + "\n\n"), StandardCharsets.UTF_8);
        String s1 = Json.sha(Files.readAllBytes(f.spec));
        f.baseline(List.of(f.entry(1, s0, null, "ALL", true), f.entry(2, s1, s0, AC37, measurementAffecting)));
        return s0;
    }

    @Test void reportWritesBoundLeafRecordsParentManifestAndReportWithoutRawText() throws Exception {
        F0TestRoot f = measuredAndReported(directory);
        Map<String,JsonNode> byLeaf = new TreeMap<>(); JsonNode parent = null;
        for (JsonNode r : records(f)) { if (r.path("kind").asText().equals("LEAF")) byLeaf.put(r.path("leafId").asText(), r); else if (r.path("status").asText().equals("PARENT")) parent = r; }
        assertThat(byLeaf.keySet()).containsExactly("AC-37-CMP", "AC-37-OUT-NORMAL", "AC-38-F0");
        byLeaf.values().forEach(r -> { assertThat(r.path("result").asText()).isEqualTo("PASS"); assertThat(r.path("producer").asText()).isEqualTo("F0_REPORT"); assertThat(r.path("componentId").asText()).isEqualTo("F0R"); assertThat(r.path("parentRunId").asText()).isEqualTo(RUN); });
        assertThat(byLeaf.get("AC-37-CMP").path("reasonCode").asText()).isEqualTo("KEEP_B");
        assertThat(byLeaf.get("AC-37-CMP").path("metrics").path("A").path("taskType").path("denominator").asInt()).isEqualTo(22);
        assertThat(byLeaf.get("AC-37-OUT-NORMAL").path("metrics").path("terminationMargin").asInt()).isEqualTo(98);
        JsonNode binding = f.json("eval/f0/results/" + RUN + "/B/run-binding.json");
        assertThat(parent).isNotNull(); assertThat(parent.path("buildIdentity")).isEqualTo(Json.MAPPER.valueToTree(F0ReportWriter.identity(binding)));
        assertThat(byLeaf.get("AC-38-F0").path("rawResultsSha256").asText()).isEqualTo(F0ReportWriter.rawResultsSha256(f.root.resolve("eval/f0/results/" + RUN)));
        assertThat(f.root.resolve("eval/f0/results/" + RUN + "/report.md")).exists();
        assertThat(f.allOutput("eval/f0/results/" + RUN) + f.allOutput("evidence")).doesNotContain(OllamaStub.SENTINEL).doesNotContain("WORK_ORDER_REPLY").doesNotContain("PHOTO_001 설비");
        Map<String,String> gate = leaves(gate(f));
        assertThat(gate).containsEntry("AC-38-F0", "PASS").containsEntry("AC-37-CMP", "NOT_RUN").containsEntry("AC-37-OUT-MAX", "NOT_RUN");
        assertThat(report(f)).isEqualTo(2); assertThat(f.lastError).isEqualTo("REPORT_EXISTS");
    }
    @Test void rejudgeAddsNewRecordsOnlyForAffectedLeavesAndKeepsOriginalsBytesThenSpecRecordCompletesGate() throws Exception {
        F0TestRoot f = measuredAndReported(directory);
        Map<String,String> evidenceBefore = snapshot(f.root.resolve("evidence")), rawBefore = snapshot(f.root.resolve("eval/f0/results/" + RUN));
        String s0 = reviseSpec(f, false, null), s1 = Json.sha(Files.readAllBytes(f.spec));
        assertThat(report(f, "--rejudge")).isZero();
        Map<String,String> evidenceAfter = snapshot(f.root.resolve("evidence"));
        evidenceBefore.forEach((file, sha) -> assertThat(evidenceAfter).containsEntry(file, sha));
        assertThat(snapshot(f.root.resolve("eval/f0/results/" + RUN))).isEqualTo(rawBefore);
        List<JsonNode> rejudged = records(f).stream().filter(r -> r.has("rawRunId")).toList();
        assertThat(rejudged).extracting(r -> r.path("leafId").asText()).containsExactlyInAnyOrder("AC-37-CMP", "AC-37-OUT-NORMAL");
        for (JsonNode r : rejudged) {
            assertThat(r.path("specSha").asText()).isEqualTo(s1); assertThat(r.path("measurementSpecSha").asText()).isEqualTo(s0); assertThat(r.path("rawRunId").asText()).isEqualTo(RUN);
            assertThat(r.path("rawResultsSha256").asText()).isEqualTo(F0ReportWriter.rawResultsSha256(f.root.resolve("eval/f0/results/" + RUN)));
        }
        assertThat(report(f, "--spec-record-check", "--proposal", "eval/f0/results/" + RUN + "/spec-14.5-proposal.md")).isZero();
        assertThat(records(f).stream().filter(r -> r.path("producer").asText().equals("SPEC_RECORD")).map(r -> r.path("result").asText())).hasSize(4).containsOnly("PASS");
        Map<String,String> gate = leaves(gate(f));
        assertThat(gate).containsEntry("AC-37-CMP", "PASS").containsEntry("AC-37-OUT-NORMAL", "PASS").containsEntry("AC-38-F0", "PASS").containsEntry("AC-37-OUT-MAX", "NOT_RUN").containsEntry("AC-37-OUT-ESCAPE", "NOT_RUN");
        assertThat(gate(f).result()).isEqualTo("NOT_RUN");
    }
    @Test void harnessChangeMeasurementAffectingSameSpecRawTamperAndRebindingAreRefused() throws Exception {
        F0TestRoot f = measuredAndReported(directory);
        assertThat(report(f, "--rejudge")).isEqualTo(2); assertThat(f.lastError).isEqualTo("REJUDGE_NOT_REQUIRED");
        reviseSpec(f, true, null);
        assertThat(report(f, "--rejudge")).isEqualTo(3); assertThat(f.lastError).isEqualTo("REMEASURE_REQUIRED");
        String s1 = Json.sha(Files.readAllBytes(f.spec)), s0 = SpecBaseline.parseChain(f.root.resolve("docs/gates/spec-baseline.md")).entries().get(0).path("sha256").asText();
        f.baseline(List.of(f.entry(1, s0, null, "ALL", true), f.entry(2, s1, s0, AC37, false)));
        Path note = f.root.resolve("eval/f0/GGUF-COUNT.md"); byte[] original = Files.readAllBytes(note);
        Files.write(note, concat(original, " "));
        assertThat(report(f, "--rejudge")).isEqualTo(3); assertThat(f.lastError).isEqualTo("HARNESS_CHANGED");
        Files.write(note, original);
        Path raw = f.root.resolve("eval/f0/results/" + RUN + "/B/f0-results.json"); byte[] rawBytes = Files.readAllBytes(raw);
        Files.write(raw, concat(rawBytes, " "));
        assertThat(report(f, "--rejudge")).isEqualTo(3); assertThat(f.lastError).isEqualTo("RAW_RESULTS_CHANGED");
        Files.write(raw, rawBytes);
        byte[] binding = Files.readAllBytes(f.root.resolve("eval/f0/results/" + RUN + "/B/run-binding.json"));
        try (OllamaStub stub = new OllamaStub()) { stub.chat = StubAnswers.correct(f.root); assertThat(F0RunTest.runB(f, stub, RUN)).isEqualTo(2); assertThat(f.lastError).isEqualTo("RUN_BINDING_EXISTS"); assertThat(stub.inference()).isEmpty(); }
        assertThat(Files.readAllBytes(f.root.resolve("eval/f0/results/" + RUN + "/B/run-binding.json"))).isEqualTo(binding);
        assertThat(records(f).stream().filter(r -> r.has("rawRunId"))).isEmpty();
    }
    @Test void specRecordCheckFailsWhenSpecBlockDiffersByOneByte() throws Exception {
        F0TestRoot f = measuredAndReported(directory);
        reviseSpec(f, false, "회챠");
        assertThat(report(f, "--spec-record-check", "--proposal", "eval/f0/results/" + RUN + "/spec-14.5-proposal.md")).isZero();
        assertThat(records(f).stream().filter(r -> r.path("producer").asText().equals("SPEC_RECORD")).map(r -> r.path("result").asText())).hasSize(4).containsOnly("FAIL");
        assertThat(leaves(gate(f))).containsEntry("AC-37-CMP", "FAIL");
        Files.writeString(f.root.resolve("eval/f0/results/" + RUN + "/bad-proposal.md"), "표지 없음");
        assertThat(report(f, "--spec-record-check", "--proposal", "eval/f0/results/" + RUN + "/bad-proposal.md")).isEqualTo(2); assertThat(f.lastError).isEqualTo("PROPOSAL_INVALID");
    }
    @Test void resultsMeasuredBeforePreregCommitProduceNoRecords() throws Exception {
        F0TestRoot f = new F0TestRoot(directory);
        try (OllamaStub stub = new OllamaStub()) { stub.chat = StubAnswers.correct(f.root); assertThat(F0RunTest.runB(f, stub, RUN)).isZero(); }
        f.git.epoch = 4102444800L; // PREREG commit 이 측정보다 뒤(2100-01-01)
        assertThat(report(f)).isEqualTo(2); assertThat(f.lastError).isEqualTo("RUN_BINDING_MISSING");
        assertThat(f.root.resolve("evidence")).doesNotExist();
        f.git.epoch = F0TestRoot.COMMIT_EPOCH;
        assertThat(report(f)).isZero();
        assertThat(records(f).stream().filter(r -> r.path("kind").asText().equals("LEAF"))).as("A·경계 결과가 없으면 해당 leaf 레코드 없음").isEmpty();
    }
}
