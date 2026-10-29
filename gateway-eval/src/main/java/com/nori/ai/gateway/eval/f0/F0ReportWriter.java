package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.EvidenceRecord;
import com.nori.ai.gateway.eval.report.LeafCatalog;
import com.nori.ai.gateway.eval.spec.SpecBaseline;
import com.nori.ai.gateway.eval.spec.SpecBinding;
import com.nori.ai.gateway.eval.spec.SpecGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * f0 report(측정 판정)·--rejudge(N4 재판정)·--spec-record-check(SPR). report.md 는 항상 &lt;results&gt;/report.md, leaf 증거는 &lt;out&gt;/&lt;leafId&gt;/&lt;sha&gt;.json.
 * 원시 결과·기존 레코드는 다시 쓰지 않는다. 부모 manifest(RUN PARENT)는 측정 run-binding 의 빌드 신원 그대로.
 */
public final class F0ReportWriter {
    public static final String PROPOSAL_BEGIN = "<!-- AIGW-F0-SPEC-RECORD-BEGIN -->", PROPOSAL_END = "<!-- AIGW-F0-SPEC-RECORD-END -->";
    private static final List<String> NON_RAW = List.of("report.md", "spec-14.5-proposal.md");
    private final MeasurementSession.Context context;
    private final LeafCatalog catalog = LeafCatalog.load(null);
    public F0ReportWriter(MeasurementSession.Context context) { this.context = context; }

    record Loaded(Prereg.State prereg, JsonNode binding, F0Judgement.Results results, String rawResultsSha256, GoldenSet golden, RegistryFixture fixture, String casesSha256) {}

    public String report(String runId, Path results, Path prereg, Path out) {
        Loaded loaded = load(runId, results, prereg);
        SpecBinding spec = new SpecGuard(context.env()).check(null, null, baseline());
        if (!spec.specSha().equals(loaded.binding().path("specSha").asText())) throw new EvalException(3, "SPEC_CHANGED_SINCE_MEASUREMENT");
        Path reportFile = results.resolve("report.md");
        if (Files.exists(reportFile)) throw new EvalException(2, "REPORT_EXISTS");
        F0Judgement.Outcome outcome = F0Judgement.judge(loaded.results(), loaded.prereg().document(), loaded.golden(), loaded.fixture(), loaded.binding().path("runtimeManifest").path("tokenizerSha256").asText(), loaded.casesSha256());
        writeParent(out, runId, loaded.binding(), spec);
        for (F0Judgement.Verdict v : outcome.verdicts().values()) store(out, leafRecord(spec, runId, runId, loaded, v, "F0_REPORT", "F0R", null));
        MeasurementSession.writeNew(reportFile, markdown(runId, loaded, outcome).getBytes(StandardCharsets.UTF_8));
        Path proposal = results.resolve("spec-14.5-proposal.md");
        if (!Files.exists(proposal)) MeasurementSession.writeNew(proposal, proposal(runId, loaded, outcome).getBytes(StandardCharsets.UTF_8));
        return "F0_REPORT_WRITTEN " + outcome.verdicts().size();
    }

    public String rejudge(String runId, Path results, Path prereg, Path out) {
        // 하네스(사전등록 입력 포함) 변경은 사전등록 검사 실패보다 먼저 재측정 필요(exit 3)로 알립니다.
        if (!Prereg.localMismatches(context.root(), Prereg.parse(prereg)).isEmpty()) throw new EvalException(3, "HARNESS_CHANGED");
        Loaded loaded = load(runId, results, prereg);
        SpecBinding spec = new SpecGuard(context.env()).check(null, null, baseline());
        SpecBaseline chain = SpecBaseline.parseChain(baseline());
        String measured = loaded.binding().path("specSha").asText();
        int index = -1; for (int i = 0; i < chain.entries().size(); i++) if (chain.entries().get(i).path("sha256").asText().equals(measured)) index = i;
        if (index < 0) throw new EvalException(3, "REMEASURE_REQUIRED");
        if (measured.equals(spec.specSha())) throw new EvalException(2, "REJUDGE_NOT_REQUIRED");
        Set<String> affected = new TreeSet<>();
        for (int i = index + 1; i < chain.entries().size(); i++) {
            JsonNode entry = chain.entries().get(i);
            if (entry.path("measurementAffecting").asBoolean()) throw new EvalException(3, "REMEASURE_REQUIRED");
            if (entry.path("affectedLeaves").asText().equals("ALL")) affected.addAll(F0Judgement.LEAVES); else entry.path("affectedLeaves").forEach(n -> affected.add(n.asText()));
        }
        affected.retainAll(F0Judgement.LEAVES);
        if (affected.isEmpty()) throw new EvalException(2, "REJUDGE_NOT_REQUIRED");
        if (!HarnessDigest.compute(context.root()).equals(loaded.binding().path("harnessDigest").asText())) throw new EvalException(3, "HARNESS_CHANGED");
        if (!loaded.prereg().document().parameterManifestSha256().equals(loaded.binding().path("parameterManifestSha256").asText())) throw new EvalException(3, "PARAMETERS_CHANGED");
        if (!currentRuntimeSha().equals(loaded.binding().path("runtimeManifestSha256").asText())) throw new EvalException(3, "RUNTIME_CHANGED");
        List<JsonNode> originals = readRecords(out).stream().filter(n -> n.path("kind").asText().equals("LEAF") && n.path("runId").asText().equals(runId) && n.path("parentRunId").asText().equals(runId) && !n.has("rawRunId") && n.path("producer").asText().equals("F0_REPORT")).toList();
        if (originals.isEmpty()) throw new EvalException(2, "ORIGINAL_RECORD_MISSING");
        for (JsonNode original : originals) if (!original.path("rawResultsSha256").asText().equals(loaded.rawResultsSha256())) throw new EvalException(3, "RAW_RESULTS_CHANGED");
        F0Judgement.Outcome outcome = F0Judgement.judge(loaded.results(), loaded.prereg().document(), loaded.golden(), loaded.fixture(), loaded.binding().path("runtimeManifest").path("tokenizerSha256").asText(), loaded.casesSha256());
        int written = 0; String rejudgeRun = runId + "-rejudge-" + spec.specSha().substring(0, 12);
        for (F0Judgement.Verdict v : outcome.verdicts().values()) if (affected.contains(v.leafId())) { store(out, leafRecord(spec, rejudgeRun, runId, loaded, v, "F0_REPORT", "F0R", measured)); written++; }
        return "F0_REJUDGE_WRITTEN " + written;
    }

    public String specRecordCheck(String runId, Path results, Path prereg, Path proposal, Path out) {
        Loaded loaded = load(runId, results, prereg);
        SpecGuard.CheckedSpec checked = new SpecGuard(context.env()).readAndCheck(null, null, baseline());
        String block = proposalBlock(proposal);
        String section = section(new String(checked.bytes(), StandardCharsets.UTF_8));
        boolean present = section != null && section.contains(block);
        for (String leaf : F0Judgement.AC37_LEAVES) {
            ObjectNode metrics = Json.MAPPER.createObjectNode(); metrics.put("proposalBlockSha256", Json.sha(block.getBytes(StandardCharsets.UTF_8)));
            store(out, leafRecord(checked.binding(), runId + "-spec-record-" + checked.binding().specSha().substring(0, 12), runId, loaded,
                new F0Judgement.Verdict(leaf, present ? "PASS" : "FAIL", present ? "SPEC_RECORD_PRESENT" : "SPEC_RECORD_ABSENT", metrics), "SPEC_RECORD", "SPR", null));
        }
        return present ? "SPEC_RECORD_PRESENT" : "SPEC_RECORD_ABSENT";
    }
    static String proposalBlock(Path proposal) {
        String text = new String(Json.bytes(proposal), StandardCharsets.UTF_8);
        int start = text.indexOf(PROPOSAL_BEGIN), end = text.indexOf(PROPOSAL_END);
        if (start < 0 || end < start || text.indexOf(PROPOSAL_BEGIN, start + 1) >= 0) throw new EvalException(2, "PROPOSAL_INVALID");
        return text.substring(start, end + PROPOSAL_END.length());
    }
    /** SPEC §14.5 구간 = "### 14.5" 줄부터 다음 "## "/"### " 제목 직전까지(바이트 그대로). */
    static String section(String spec) {
        int start = spec.indexOf("\n### 14.5 ");
        if (start < 0) return null;
        int next = spec.length();
        for (String marker : List.of("\n### ", "\n## ")) { int i = spec.indexOf(marker, start + 1); if (i >= 0) next = Math.min(next, i); }
        return spec.substring(start + 1, next);
    }

    private Loaded load(String runId, Path results, Path preregPath) {
        if (!runId.matches("[A-Za-z0-9_.:-]{1,64}") || !Files.isDirectory(results)) throw new EvalException(2, "RESULTS_MISSING");
        Prereg.State prereg = Prereg.verify(context.root(), preregPath, context.git());
        Map<String,JsonNode> files = new LinkedHashMap<>(); Map<String,Path> paths = new LinkedHashMap<>(); JsonNode identity = null;
        Map<String,String[]> layout = new LinkedHashMap<>();
        layout.put("A", new String[]{"A/f0-results.json", "A/run-binding.json"}); layout.put("B", new String[]{"B/f0-results.json", "B/run-binding.json"});
        layout.put("boundary", new String[]{"boundary/boundary-results.json", "boundary/run-binding.json"}); layout.put("gguf", new String[]{"gguf/gguf-count.json", "gguf/run-binding.json"});
        layout.put("usage", new String[]{"usage.json", "usage.run-binding.json"});
        for (var entry : layout.entrySet()) {
            Path file = results.resolve(entry.getValue()[0]), bindingFile = results.resolve(entry.getValue()[1]);
            if (!Files.isRegularFile(file) || !Files.isRegularFile(bindingFile)) continue;
            JsonNode binding = Json.read(bindingFile);
            // PREREG 커밋 이후·같은 회차·같은 신원으로 시작한 측정만 판정 입력입니다.
            if (!binding.path("runId").asText().equals(runId) || !binding.path("preregCommit").asText().equals(prereg.preregCommit()) || !binding.path("registeredCommit").asText().equals(prereg.document().registeredCommit())
                || !Instant.parse(binding.path("startedAt").asText()).isAfter(prereg.preregCommitTime())) continue;
            if (identity == null) identity = binding;
            else for (String key : List.of("specSha", "harnessDigest", "runtimeManifestSha256", "parameterManifestSha256")) if (!identity.path(key).equals(binding.path(key))) throw new EvalException(2, "RUN_BINDING_INCONSISTENT");
            if (!binding.path("parameterManifestSha256").asText().equals(prereg.document().parameterManifestSha256()) || !binding.path("harnessDigest").asText().equals(prereg.document().harnessDigest())) throw new EvalException(2, "RUN_BINDING_INCONSISTENT");
            files.put(entry.getKey(), Json.read(file)); paths.put(entry.getKey(), file);
        }
        if (identity == null) throw new EvalException(2, "RUN_BINDING_MISSING");
        Path official = results.resolve("official-count.json");
        try {
            if (Files.isRegularFile(official) && Files.getLastModifiedTime(official).toInstant().isAfter(prereg.preregCommitTime())) { files.put("official", Json.read(official)); paths.put("official", official); }
        } catch (IOException e) { throw new EvalException(5, "RESULTS_READ_FAILED"); }
        RegistryFixture fixture = RegistryFixture.load(context.root().resolve("eval/golden/tc-registry-fixture-v1.json"));
        GoldenSet golden = GoldenSet.load(context.root().resolve("eval/golden/tc-f0-v1.jsonl"), fixture);
        return new Loaded(prereg, identity, new F0Judgement.Results(files, paths), rawResultsSha256(results), golden, fixture, prereg.document().inputs().get("eval/f0/output-boundary-cases.jsonl"));
    }
    /** 원시 결과 SHA = 결과 디렉터리의 report.md·제안서를 뺀 모든 파일의 정렬 경로 + 바이트 SHA 목록의 SHA-256. */
    static String rawResultsSha256(Path results) {
        SortedMap<String,String> files = new TreeMap<>();
        try (var walk = Files.walk(results)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = results.relativize(file).toString().replace('\\', '/');
                if (!NON_RAW.contains(relative)) files.put(relative, Json.sha(Json.bytes(file)));
            }
        } catch (IOException e) { throw new EvalException(5, "RESULTS_READ_FAILED"); }
        return HarnessDigest.digest(files);
    }
    /** 재판정 시점 런타임 = 캡처된 chat template·모델 digest 파일과 tokenizer pin(endpoint 호출 0). */
    private String currentRuntimeSha() {
        Path dir = context.root().resolve("eval/f0/chat-template");
        Path sha = dir.resolve("qwen3-4b-instruct.template.sha256"), digest = dir.resolve("model-digest.txt"), template = dir.resolve("qwen3-4b-instruct.template");
        if (!Files.isRegularFile(sha) || !Files.isRegularFile(digest) || !Files.isRegularFile(template)) throw new EvalException(3, "RUNTIME_UNVERIFIED");
        String templateSha = new String(Json.bytes(sha), StandardCharsets.UTF_8).strip();
        if (!Json.sha(Json.bytes(template)).equals(templateSha)) throw new EvalException(3, "RUNTIME_UNVERIFIED");
        return new MeasurementSession.Runtime(new String(Json.bytes(digest), StandardCharsets.UTF_8).strip(), templateSha, MeasurementSession.tokenizerSha(context.root())).sha256();
    }
    private ObjectNode leafRecord(SpecBinding spec, String recordRun, String parent, Loaded loaded, F0Judgement.Verdict v, String producer, String component, String measurementSpecSha) {
        Map<String,Object> fields = new LinkedHashMap<>();
        fields.put("kind", "LEAF"); fields.put("leafId", v.leafId()); fields.put("stage", "F0"); fields.put("producer", producer); fields.put("componentId", component);
        fields.put("runId", recordRun); fields.put("parentRunId", parent); fields.put("labels", List.of("LLM_LIVE")); fields.put("environment", "verify");
        fields.put("result", v.result()); fields.put("reasonCode", v.reasonCode()); fields.put("metrics", v.metrics()); fields.put("buildIdentity", identity(loaded.binding()));
        fields.put("rawResultsSha256", loaded.rawResultsSha256()); fields.put("recordedAt", context.clock().instant().toString());
        if (measurementSpecSha != null) { fields.put("measurementSpecSha", measurementSpecSha); fields.put("rawRunId", parent); }
        ObjectNode record = EvidenceRecord.create(spec, fields);
        EvidenceRecord.validate(record, catalog);
        return record;
    }
    static Map<String,String> identity(JsonNode binding) {
        return new TreeMap<>(Map.of("harnessDigest", binding.path("harnessDigest").asText(), "runtimeManifestSha256", binding.path("runtimeManifestSha256").asText(), "parameterManifestSha256", binding.path("parameterManifestSha256").asText()));
    }
    private void writeParent(Path out, String runId, JsonNode binding, SpecBinding spec) {
        boolean exists = readRecords(out).stream().anyMatch(n -> n.path("kind").asText().equals("RUN") && n.path("status").asText().equals("PARENT") && n.path("runId").asText().equals(runId));
        if (exists) return;
        ObjectNode parent = EvidenceRecord.create(spec, Map.of("kind", "RUN", "status", "PARENT", "stage", "F0", "runId", runId, "parentRunId", runId, "producer", "F0_REPORT",
            "buildIdentity", identity(binding), "recordedAt", context.clock().instant().toString()));
        EvidenceRecord.validate(parent, catalog); store(out, parent);
    }
    private static List<JsonNode> readRecords(Path out) {
        if (!Files.isDirectory(out)) return List.of();
        try (var walk = Files.walk(out)) { return walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().map(Json::read).toList(); }
        catch (IOException e) { throw new EvalException(5, "EVIDENCE_READ_FAILED"); }
    }
    /** 내용 주소 저장(이미 있으면 바이트가 같아야 함 — 덮어쓰기 0). */
    static Path store(Path out, JsonNode record) {
        String group = record.path("kind").asText().equals("LEAF") ? record.path("leafId").asText() : "runs";
        Path file = out.resolve(group).resolve(record.path("contentSha256").asText() + ".json").normalize();
        if (!file.startsWith(out.normalize()) || !group.matches("[A-Za-z0-9_-]+")) throw new EvalException(2, "EVIDENCE_PATH_INVALID");
        byte[] bytes = Json.canonical(record);
        if (Files.exists(file)) { if (!Arrays.equals(Json.bytes(file), bytes)) throw new EvalException(5, "EVIDENCE_IMMUTABLE"); return file; }
        MeasurementSession.writeNew(file, bytes); return file;
    }
    private Path baseline() { return context.root().resolve("docs/gates/spec-baseline.md"); }

    private String markdown(String runId, Loaded loaded, F0Judgement.Outcome outcome) {
        StringBuilder md = new StringBuilder("# F0 측정 보고서 — ").append(runId).append("\n\n");
        md.append("- 작성 시각(UTC): ").append(context.clock().instant()).append('\n');
        md.append("- 측정 시작: ").append(loaded.binding().path("startedAt").asText()).append(", 측정 SPEC SHA256: `").append(loaded.binding().path("specSha").asText()).append("`\n");
        md.append("- 사전등록: 등록 commit `").append(loaded.prereg().document().registeredCommit()).append("`, PREREG commit `").append(loaded.prereg().preregCommit()).append("`\n");
        md.append("- 신원: harnessDigest `").append(loaded.binding().path("harnessDigest").asText()).append("`, runtimeManifestSha256 `").append(loaded.binding().path("runtimeManifestSha256").asText())
            .append("`, parameterManifestSha256 `").append(loaded.binding().path("parameterManifestSha256").asText()).append("`\n");
        md.append("- 원시 결과 SHA256: `").append(loaded.rawResultsSha256()).append("` (원문 질문·응답 미저장, SHA·정제 필드만)\n\n");
        md.append("## 경로별 결과 (정확도 = 문항당 회차 1 표본, 분모 = 문항 수 / 지연·유효율·재시도 = 전 회차, nearest-rank)\n\n");
        md.append("| 경로 | taskType | 인자 | UNSUPPORTED | UNS 오탐 | JSON 유효 | 1차 유효 | 재시도 | timeout | p50 ms | p95 ms | 출력 토큰 p50/p95/max | 회차 간 불일치 문항 |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        outcome.paths().forEach((path, m) -> md.append("| ").append(path).append(" | ").append(m.taskType().correct()).append('/').append(m.taskType().denominator()).append(" | ").append(m.args().correct()).append('/').append(m.args().denominator())
            .append(" | ").append(m.unsupported().correct()).append('/').append(m.unsupported().denominator()).append(" | ").append(m.unsupportedFalsePositives()).append(" | ").append(m.validCount()).append('/').append(m.attemptDenominator())
            .append(" | ").append(m.firstValidCount()).append('/').append(m.attemptDenominator()).append(" | ").append(m.retriedCount()).append('/').append(m.attemptDenominator()).append(" | ").append(m.timeoutCount())
            .append(" | ").append(m.latencyP50Ms()).append(" | ").append(m.latencyP95Ms()).append(" | ").append(m.outputTokensP50()).append('/').append(m.outputTokensP95()).append('/').append(m.outputTokensMax()).append(" | ").append(m.roundDisagreementCount()).append(" |\n"));
        F0Judgement.Verdict cmp = outcome.verdicts().get("AC-37-CMP");
        md.append("\n- 잠정 후보 판정: ").append(cmp == null ? "판정 입력 없음" : cmp.reasonCode()).append(" (최종 승격 기록 0 — 최종 승격은 F4 AC-24-PERF)\n\n");
        md.append("## 출력 경계 (기대 출력 토큰 수 = 공식/GGUF 계수, 생성 증거 = boundary-gen 회차별 oracle)\n\n| caseId | kind | 공식 | GGUF | 종료 여유 | pass/전체 회차 |\n|---|---|---|---|---|---|\n");
        for (ObjectNode row : outcome.boundaryRows()) md.append("| ").append(row.path("caseId").asText()).append(" | ").append(row.path("kind").asText()).append(" | ").append(row.path("officialTokens").asInt()).append(" | ").append(row.path("ggufTokens").asInt())
            .append(" | ").append(row.path("terminationMargin").asInt()).append(" | ").append(row.path("passRounds").asInt()).append('/').append(row.path("totalRounds").asInt()).append(" |\n");
        md.append("\n## G-USAGE\n\n- equalOnCacheHit: ").append(outcome.usage() == null ? "측정 결과 없음" : Json.canonicalString(outcome.usage())).append("\n\n## F0R 판정\n\n| leaf | 결과 | 사유 |\n|---|---|---|\n");
        outcome.verdicts().values().forEach(v -> md.append("| ").append(v.leafId()).append(" | ").append(v.result()).append(" | ").append(v.reasonCode()).append(" |\n"));
        md.append("\n- 판정 입력 없음(레코드 미생성): ").append(outcome.notes().isEmpty() ? "없음" : String.join(", ", outcome.notes())).append('\n');
        md.append("- AC-37 하위는 F0R AND SPR 두 component 가 모두 PASS 여야 PASS 다(SPEC §14.5 기록 반영 뒤 --spec-record-check).\n");
        return md.toString();
    }
    private static String proposal(String runId, Loaded loaded, F0Judgement.Outcome outcome) {
        StringBuilder text = new StringBuilder("# SPEC §14.5 기록 제안 — F0 회차 ").append(runId).append("\n\nSPEC 소유자가 아래 표지 사이 블록을 §14.5 에 그대로 반영한다(이 파일은 SPEC 을 고치지 않음).\n\n");
        text.append(PROPOSAL_BEGIN).append("\n**F0 측정 기록(회차 ").append(runId).append(", 측정 시작 ").append(loaded.binding().path("startedAt").asText()).append(")**\n\n");
        outcome.paths().forEach((path, m) -> text.append("- 경로 ").append(path).append(": taskType ").append(m.taskType().correct()).append('/').append(m.taskType().denominator())
            .append(", 인자 ").append(m.args().correct()).append('/').append(m.args().denominator()).append(", UNSUPPORTED ").append(m.unsupported().correct()).append('/').append(m.unsupported().denominator())
            .append(", JSON 유효 ").append(m.validCount()).append('/').append(m.attemptDenominator()).append(", 재시도 ").append(m.retriedCount()).append('/').append(m.attemptDenominator())
            .append(", p50/p95 ").append(m.latencyP50Ms()).append('/').append(m.latencyP95Ms()).append(" ms, 출력 토큰 최대 ").append(m.outputTokensMax()).append('\n'));
        F0Judgement.Verdict cmp = outcome.verdicts().get("AC-37-CMP");
        text.append("- 잠정 후보: ").append(cmp == null ? "판정 입력 없음" : cmp.reasonCode()).append('\n');
        for (ObjectNode row : outcome.boundaryRows()) text.append("- 경계 ").append(row.path("caseId").asText()).append(": 공식 ").append(row.path("officialTokens").asInt()).append(" / GGUF ").append(row.path("ggufTokens").asInt())
            .append(" 토큰, 종료 여유 ").append(row.path("terminationMargin").asInt()).append(", 완결 생성 ").append(row.path("passRounds").asInt()).append('/').append(row.path("totalRounds").asInt()).append('\n');
        return text.append(PROPOSAL_END).append('\n').toString();
    }
}
