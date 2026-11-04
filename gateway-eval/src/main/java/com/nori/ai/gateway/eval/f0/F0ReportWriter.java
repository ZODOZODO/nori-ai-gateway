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
    static final String DIAGNOSTIC_REPORT = "diagnostic-report.md";
    private static final List<String> NON_RAW = List.of("report.md", "spec-14.5-proposal.md", DIAGNOSTIC_REPORT);
    private final MeasurementSession.Context context;
    private final LeafCatalog catalog = LeafCatalog.load(null);
    public F0ReportWriter(MeasurementSession.Context context) { this.context = context; }

    record Loaded(Prereg.State prereg, JsonNode binding, F0Judgement.Results results, String rawResultsSha256, GoldenSet golden, RegistryFixture fixture, String casesSha256, List<JsonNode> registeredCases) {}

    public String report(String runId, Path results, Path prereg, Path out) {
        requireGateEligible(prereg);
        Loaded loaded = load(runId, results, prereg);
        SpecBinding spec = new SpecGuard(context.env()).check(null, null, baseline());
        if (!spec.specSha().equals(loaded.binding().path("specSha").asText())) throw new EvalException(3, "SPEC_CHANGED_SINCE_MEASUREMENT");
        Path reportFile = results.resolve("report.md");
        if (Files.exists(reportFile)) throw new EvalException(2, "REPORT_EXISTS");
        F0Judgement.Outcome outcome = F0Judgement.judge(loaded.results(), loaded.prereg().document(), loaded.golden(), loaded.fixture(), loaded.binding().path("runtimeManifest").path("tokenizerSha256").asText(), loaded.casesSha256(), loaded.registeredCases());
        writeParent(out, runId, loaded.binding(), spec);
        for (F0Judgement.Verdict v : outcome.verdicts().values()) store(out, leafRecord(spec, runId, runId, loaded, v, "F0_REPORT", "F0R", null));
        MeasurementSession.writeNew(reportFile, markdown(runId, loaded, outcome).getBytes(StandardCharsets.UTF_8));
        Path proposal = results.resolve("spec-14.5-proposal.md");
        if (!Files.exists(proposal)) MeasurementSession.writeNew(proposal, proposal(runId, loaded, outcome).getBytes(StandardCharsets.UTF_8));
        return "F0_REPORT_WRITTEN " + outcome.verdicts().size();
    }

    public String rejudge(String runId, Path results, Path prereg, Path out) {
        requireGateEligible(prereg);
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
        if (!HarnessDigest.compute(context.root(), loaded.prereg().document()).equals(loaded.binding().path("harnessDigest").asText())) throw new EvalException(3, "HARNESS_CHANGED");
        if (!loaded.prereg().document().parameterManifestSha256().equals(loaded.binding().path("parameterManifestSha256").asText())) throw new EvalException(3, "PARAMETERS_CHANGED");
        if (!currentRuntimeSha(loaded.prereg().document()).equals(loaded.binding().path("runtimeManifestSha256").asText())) throw new EvalException(3, "RUNTIME_CHANGED");
        List<JsonNode> originals = readRecords(out).stream().filter(n -> n.path("kind").asText().equals("LEAF") && n.path("runId").asText().equals(runId) && n.path("parentRunId").asText().equals(runId) && !n.has("rawRunId") && n.path("producer").asText().equals("F0_REPORT")).toList();
        if (originals.isEmpty()) throw new EvalException(2, "ORIGINAL_RECORD_MISSING");
        for (JsonNode original : originals) if (!original.path("rawResultsSha256").asText().equals(loaded.rawResultsSha256())) throw new EvalException(3, "RAW_RESULTS_CHANGED");
        F0Judgement.Outcome outcome = F0Judgement.judge(loaded.results(), loaded.prereg().document(), loaded.golden(), loaded.fixture(), loaded.binding().path("runtimeManifest").path("tokenizerSha256").asText(), loaded.casesSha256(), loaded.registeredCases());
        int written = 0; String rejudgeRun = runId + "-rejudge-" + spec.specSha().substring(0, 12);
        for (F0Judgement.Verdict v : outcome.verdicts().values()) if (affected.contains(v.leafId())) { store(out, leafRecord(spec, rejudgeRun, runId, loaded, v, "F0_REPORT", "F0R", measured)); written++; }
        return "F0_REJUDGE_WRITTEN " + written;
    }

    public String specRecordCheck(String runId, Path results, Path prereg, Path proposal, Path out) {
        requireGateEligible(prereg);
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
    /** 진단 등록(purpose=DIAGNOSTIC)은 F0 leaf 증거·게이트 입력을 만들 수 없습니다. */
    private static void requireGateEligible(Path prereg) {
        if (Prereg.parse(prereg).diagnostic()) throw new EvalException(2, "DIAGNOSTIC_NOT_GATE_ELIGIBLE");
    }
    /**
     * 진단 회차 보고서 &lt;results&gt;/diagnostic-report.md: 등록 집계 규칙의 경로 지표와 시도별 원문 없는 진단 분포만 씁니다.
     * leaf 증거·RUN PARENT·§14.5 제안서를 만들지 않으므로 F0 게이트 판정에 들어갈 수 없습니다.
     */
    public String diagnosticReport(String runId, Path results, Path preregPath) {
        Loaded loaded = load(runId, results, preregPath);
        if (!loaded.prereg().document().diagnostic()) throw new EvalException(2, "DIAGNOSTIC_REGISTRATION_REQUIRED");
        Path file = results.resolve(DIAGNOSTIC_REPORT);
        if (Files.exists(file)) throw new EvalException(2, "REPORT_EXISTS");
        Prereg.Document document = loaded.prereg().document();
        StringBuilder md = new StringBuilder("# F0 진단 보고서 — ").append(runId).append("\n\n");
        md.append("- **진단 전용(purpose=DIAGNOSTIC)**: F0 leaf 증거·게이트 입력·§14.5 제안서 0. 이 보고서는 AC 판정이 아니다.\n");
        md.append("- 측정 시작: ").append(loaded.binding().path("startedAt").asText()).append(", 측정 SPEC SHA256: `").append(loaded.binding().path("specSha").asText()).append("`\n");
        md.append("- 사전등록: `").append(Prereg.relativePath(context.root(), preregPath)).append("`, 등록 commit `").append(document.registeredCommit()).append("`, responseFormat `").append(document.responseFormat()).append("`, contextProjection `").append(document.contextProjection()).append("`\n");
        md.append("- 신원: harnessDigest `").append(loaded.binding().path("harnessDigest").asText()).append("`, parameterManifestSha256 `").append(loaded.binding().path("parameterManifestSha256").asText()).append("`\n");
        md.append("- 원시 결과 SHA256: `").append(loaded.rawResultsSha256()).append("` (원문 질문·응답 미저장)\n");
        md.append(budgetLine(document)).append('\n');
        md.append("- 실제 `usage.prompt_tokens` 대 사전 audit 계수(호출 수) — 작음/큼: ");
        for (String key : List.of("A", "B", "boundary")) md.append(key).append(' ').append(belowAudit(loaded.results().get(key))).append('/').append(aboveAudit(loaded.results().get(key))).append(key.equals("boundary") ? "" : ", ");
        md.append("\n\n## 경로 지표(등록 집계 규칙, 회차 ").append(document.repeats()).append(")\n\n| 경로 | taskType | 인자 | UNSUPPORTED | JSON 유효 | 1차 유효 | 재시도 | p95 ms | 출력 토큰 max |\n|---|---|---|---|---|---|---|---|---|\n");
        for (String path : List.of("A", "B")) {
            JsonNode node = F0Judgement.complete(loaded.results().get(path), document.repeats());
            if (node == null) { md.append("| ").append(path).append(" | 결과 없음 |||||||\n"); continue; }
            List<JsonNode> attempts = new ArrayList<>(); node.path("attempts").forEach(attempts::add);
            MetricsAggregator.Metrics m = MetricsAggregator.aggregate(attempts, loaded.golden(), document.repeats());
            md.append("| ").append(path).append(" | ").append(m.taskType().correct()).append('/').append(m.taskType().denominator()).append(" | ").append(m.args().correct()).append('/').append(m.args().denominator())
                .append(" | ").append(m.unsupported().correct()).append('/').append(m.unsupported().denominator()).append(" | ").append(m.validCount()).append('/').append(m.attemptDenominator())
                .append(" | ").append(m.firstValidCount()).append('/').append(m.attemptDenominator()).append(" | ").append(m.retriedCount()).append('/').append(m.attemptDenominator())
                .append(" | ").append(m.latencyP95Ms()).append(" | ").append(m.outputTokensMax()).append(" |\n");
        }
        JsonNode boundary = F0Judgement.complete(loaded.results().get("boundary"), document.repeats());
        if (boundary != null) {
            md.append("\n## 경계 생성(boundary-gen)\n\n| caseId | kind | pass/전체 |\n|---|---|---|\n");
            boundary.path("cases").forEach(c -> md.append("| ").append(c.path("caseId").asText()).append(" | ").append(c.path("kind").asText()).append(" | ").append(c.path("passRounds").asInt()).append('/').append(c.path("totalRounds").asInt()).append(" |\n"));
        }
        md.append("\n## 기대 t × 초회 진단 t (골든 문항, 전 회차)\n\n| 경로 | 기대 t | 초회 t 값 | 건수 |\n|---|---|---|---|\n");
        for (String path : List.of("A", "B")) {
            JsonNode node = loaded.results().get(path); if (node == null) continue;
            Map<String,Integer> confusion = new TreeMap<>();
            node.path("attempts").forEach(row -> {
                JsonNode first = row.path("attemptDiagnostics").path(0);
                if (first.isMissingNode()) return;
                confusion.merge(loaded.golden().byId(row.path("questionId").asText()).expected().t() + " | " + first.path("t").path("value").asText(), 1, Integer::sum);
            });
            confusion.forEach((key, count) -> md.append("| ").append(path).append(" | ").append(key).append(" | ").append(count).append(" |\n"));
        }
        md.append("\n## 시도별 진단 분포(고정 분류·불리언·개수만)\n\n| 출처 | 시도 | 항목=값 | 건수 |\n|---|---|---|---|\n");
        for (String source : List.of("A", "B", "boundary")) {
            JsonNode node = loaded.results().get(source); if (node == null) continue;
            List<JsonNode> rows = new ArrayList<>(); node.path("attempts").forEach(rows::add); node.path("cases").forEach(c -> c.path("attempts").forEach(rows::add));
            Map<String,Integer> counts = new TreeMap<>();
            for (JsonNode row : rows) {
                JsonNode diagnosed = row.path("attemptDiagnostics");
                for (int i = 0; i < diagnosed.size(); i++) {
                    String attempt = i == 0 ? "초회" : "재시도";
                    flatten("", diagnosed.get(i), key -> counts.merge(attempt + " | " + key, 1, Integer::sum));
                    counts.merge(attempt + " | qRef.kind@candidateCount=" + diagnosed.get(i).path("qRef").path("candidateCount").asInt() + "=" + diagnosed.get(i).path("qRef").path("kind").asText(), 1, Integer::sum);
                }
                row.path("errorCodes").forEach(code -> counts.merge("판정 | errorCode=" + code.asText(), 1, Integer::sum));
            }
            counts.forEach((key, count) -> md.append("| ").append(source).append(" | ").append(key).append(" | ").append(count).append(" |\n"));
        }
        if (document.v4()) md.append(thinkingSection(loaded.results()));
        if (document.v4() && document.localAnswerCount()) md.append(localCountSection(loaded.results()));
        MeasurementSession.writeNew(file, md.toString().getBytes(StandardCharsets.UTF_8));
        return "F0_DIAGNOSTIC_REPORT_WRITTEN";
    }
    /**
     * v4 진단 전용: 시도별 사고 출력 관측·답변 토큰·출력 키 순서의 분포(원문 없음 — 분리 필드 이름·표지 불리언·개수만). 사고 토큰 분포는 사고 상한 T 제안
     * (Plan P03 v4 ⑩)의 근거 입력이며, 분리 필드·reasoning_tokens 보고 수와 content 0 건수는 답변 토큰 계수 방법(SPEC §14.5 열린 항목 ⓒ) 확인 입력입니다.
     */
    private static String thinkingSection(F0Judgement.Results results) {
        StringBuilder md = new StringBuilder("\n## v4 사고 출력 관측(원문 없음, nearest-rank)\n\n| 출처 | 시도 | 시도 수 | 분리 필드=건수 | reasoning_tokens 보고 | content `<think>`/`</think>` | content 0 | 사고 토큰 p50/p95/max | 사고 코드포인트 max | 답변 토큰 p50/p95/max(계수 불가) | 출력 키 순서=건수 |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (String source : List.of("A", "B", "boundary")) {
            JsonNode node = results.get(source); if (node == null) continue;
            List<JsonNode> rows = new ArrayList<>(); node.path("attempts").forEach(rows::add); node.path("cases").forEach(c -> c.path("attempts").forEach(rows::add));
            for (int attempt = 0; attempt < 2; attempt++) {
                int count = 0, reported = 0, open = 0, close = 0, empty = 0, uncountable = 0; Integer maxThinkingCodepoints = null;
                Map<String,Integer> fields = new TreeMap<>(), orders = new TreeMap<>(); List<Integer> reasoning = new ArrayList<>(), answers = new ArrayList<>();
                for (JsonNode row : rows) {
                    JsonNode o = row.path("thinkingObservations").path(attempt);
                    if (o.isMissingNode()) continue;
                    count++;
                    fields.merge(o.path("separateFields").isEmpty() ? "(없음)" : String.join("+", Json.MAPPER.convertValue(o.path("separateFields"), String[].class)), 1, Integer::sum);
                    if (o.path("reasoningTokens").isInt()) { reported++; reasoning.add(o.path("reasoningTokens").intValue()); }
                    if (o.path("contentThinkOpen").asBoolean()) open++;
                    if (o.path("contentThinkClose").asBoolean()) close++;
                    if (o.path("contentCodepoints").asInt(-1) == 0) empty++;
                    if (o.path("thinkingCodepoints").isInt()) maxThinkingCodepoints = Math.max(maxThinkingCodepoints == null ? 0 : maxThinkingCodepoints, o.path("thinkingCodepoints").intValue());
                    JsonNode answer = row.path("answerTokens").path(attempt);
                    if (answer.isInt()) answers.add(answer.intValue()); else uncountable++;
                    JsonNode order = row.path("outputKeyOrders").path(attempt);
                    orders.merge(order.isArray() ? String.join(",", Json.MAPPER.convertValue(order, String[].class)) : "(판별 불가)", 1, Integer::sum);
                }
                if (count == 0) continue;
                md.append("| ").append(source).append(" | ").append(attempt == 0 ? "초회" : "재시도").append(" | ").append(count).append(" | ").append(joined(fields)).append(" | ").append(reported).append('/').append(count)
                    .append(" | ").append(open).append('/').append(close).append(" | ").append(empty).append(" | ").append(distribution(reasoning)).append(" | ").append(maxThinkingCodepoints == null ? "-" : maxThinkingCodepoints)
                    .append(" | ").append(distribution(answers)).append('(').append(uncountable).append(") | ").append(joined(orders)).append(" |\n");
            }
        }
        return md.toString();
    }
    /**
     * 답변 토큰 계수 방법 (b) 교차 확인: 시도별 completionMinusLocal(= 서버 completion_tokens − (로컬 사고 + 로컬 답변)) 분포. `</think>` 같은 특수 토큰 상수가 있으면
     * 0 이 아닌 같은 값으로 모입니다. 판정은 바꾸지 않으며 크게 어긋남 판정 기준값은 관측 뒤 정합니다(등록 §1.4) — 그래서 값=건수 전체를 함께 냅니다.
     */
    private static String localCountSection(F0Judgement.Results results) {
        StringBuilder md = new StringBuilder("\n## v4 답변 토큰 로컬 계수 교차 확인(방법 b, 원문 없음, nearest-rank)\n\n- 차이 = 서버 `completion_tokens` − (로컬 사고 토큰 + 로컬 답변 토큰), 같은 tokenizer pin. 판정 불변, 어긋남 기준값은 관측 뒤 확정.\n\n"
            + "| 출처 | 시도 | 시도 수 | 답변 계수 불가 | 사고 계수 없음 | 차이 계산 수 | 차이 min/p50/p95/max | 0 아닌 차이 | 차이 값=건수 |\n|---|---|---|---|---|---|---|---|---|\n");
        List<Integer> all = new ArrayList<>(); int allCount = 0, allAnswerNull = 0, allThinkingNull = 0;
        for (String source : List.of("A", "B", "boundary")) {
            JsonNode node = results.get(source); if (node == null) continue;
            List<JsonNode> rows = new ArrayList<>(); node.path("attempts").forEach(rows::add); node.path("cases").forEach(c -> c.path("attempts").forEach(rows::add));
            for (int attempt = 0; attempt < 2; attempt++) {
                int count = 0, answerNull = 0, thinkingNull = 0; List<Integer> differences = new ArrayList<>();
                for (JsonNode row : rows) {
                    JsonNode local = row.path("localTokenCounts").path(attempt);
                    if (local.isMissingNode()) continue;
                    count++;
                    if (!local.path("answerTokens").isInt()) answerNull++;
                    if (!local.path("thinkingTokens").isInt()) thinkingNull++;
                    if (local.path("completionMinusLocal").isInt()) differences.add(local.path("completionMinusLocal").intValue());
                }
                if (count == 0) continue;
                md.append(localCountRow(source, attempt == 0 ? "초회" : "재시도", count, answerNull, thinkingNull, differences));
                all.addAll(differences); allCount += count; allAnswerNull += answerNull; allThinkingNull += thinkingNull;
            }
        }
        if (allCount > 0) md.append(localCountRow("전체", "-", allCount, allAnswerNull, allThinkingNull, all));
        return md.toString();
    }
    private static String localCountRow(String source, String attempt, int count, int answerNull, int thinkingNull, List<Integer> differences) {
        Map<String,Integer> values = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        differences.forEach(d -> values.merge(String.valueOf(d), 1, Integer::sum));
        return "| " + source + " | " + attempt + " | " + count + " | " + answerNull + " | " + thinkingNull + " | " + differences.size() + " | "
            + (differences.isEmpty() ? "-" : Collections.min(differences) + "/" + distribution(differences)) + " | " + differences.stream().filter(d -> d != 0).count() + " | " + joined(values) + " |\n";
    }
    private static String distribution(List<Integer> values) {
        return values.isEmpty() ? "-" : MetricsAggregator.nearestRank(values, 50) + "/" + MetricsAggregator.nearestRank(values, 95) + "/" + Collections.max(values);
    }
    private static String joined(Map<String,Integer> counts) {
        StringJoiner text = new StringJoiner("; "); counts.forEach((key, count) -> text.add(key + "=" + count)); return text.toString();
    }
    /** 진단 객체의 말단(문자열·불리언·정수)만 "경로=값"으로 펼칩니다. 진단에는 고정 값만 있으므로 원문이 섞이지 않습니다. */
    private static void flatten(String prefix, JsonNode node, java.util.function.Consumer<String> sink) {
        if (node.isObject()) node.properties().forEach(e -> flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), sink));
        else sink.accept(prefix + "=" + node.asText());
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
            if (binding.has("preregPath") || binding.has("preregSha256") || prereg.document().versioned()) {
                if (!binding.path("preregPath").asText().equals(Prereg.relativePath(context.root(), preregPath)) || !binding.path("preregSha256").asText().equals(Json.sha(Json.bytes(preregPath)))) throw new EvalException(2, "RUN_BINDING_INCONSISTENT");
            }
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
        RegistryFixture fixture = RegistryFixture.load(context.root().resolve(prereg.document().input("fixture")));
        GoldenSet golden = GoldenSet.load(context.root().resolve(prereg.document().input("golden")), fixture);
        return new Loaded(prereg, identity, new F0Judgement.Results(files, paths), rawResultsSha256(results), golden, fixture, prereg.document().inputs().get(prereg.document().input("boundaryCases")), prereg.document().versioned() ? BoundaryCases.load(context.root().resolve(prereg.document().input("boundaryCases"))) : null);
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
    private String currentRuntimeSha(Prereg.Document document) {
        Path dir = context.root().resolve(document.runtimeCaptureDir());
        String stem = document.profile().templateStem();
        Path sha = dir.resolve(stem + ".template.sha256"), digest = dir.resolve("model-digest.txt"), template = dir.resolve(stem + ".template");
        if (!Files.isRegularFile(sha) || !Files.isRegularFile(digest) || !Files.isRegularFile(template)) throw new EvalException(3, "RUNTIME_UNVERIFIED");
        String templateSha = new String(Json.bytes(sha), StandardCharsets.UTF_8).strip();
        if (!Json.sha(Json.bytes(template)).equals(templateSha)) throw new EvalException(3, "RUNTIME_UNVERIFIED");
        return new MeasurementSession.Runtime(new String(Json.bytes(digest), StandardCharsets.UTF_8).strip(), templateSha, MeasurementSession.tokenizerSha(context.root(), document.profile())).sha256();
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
        md.append("- 원시 결과 SHA256: `").append(loaded.rawResultsSha256()).append("` (원문 질문·응답 미저장, SHA·정제 필드만)\n");
        md.append(budgetLine(loaded.prereg().document())).append('\n');
        if (loaded.prereg().document().versioned()) {
            md.append("- 호출 전 입력 확인 = 사전등록 공식 tokenizer 계수 audit(준비 정적 계수). 실제 응답 `usage.prompt_tokens` 가 사전 계수보다 작은 호출 수(잘림 의심, 판정 규칙 변경 없음): ");
            for (String key : List.of("A", "B", "boundary")) md.append(key).append(' ').append(belowAudit(loaded.results().get(key))).append(key.equals("boundary") ? "" : ", ");
            md.append('\n');
        }
        md.append('\n');
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
    static String budgetLine(Prereg.Document document) {
        Prereg.Budget b = document.budget();
        return "- 토큰 예산 profile: 입력 상한 분류 " + b.classifyInput() + "·계획 " + b.planInput() + ", 요청 max_tokens 분류 " + b.classifyOutput() + "·계획 " + b.planOutput()
            + ", 경계 출력 상한 " + b.outputLimit() + ", context " + document.contextTokens();
    }
    /** A/B 행 또는 boundary 회차의 promptTokens[i] < auditInputTokens[i] 개수. 결과가 없으면 "-". */
    static String belowAudit(JsonNode results) { return auditCount(results, -1); }
    /** promptTokens[i] > auditInputTokens[i] 개수: 서버가 출력 제약 스키마 등을 입력에 덧붙이면 사전 계수 예산 가정이 깨지므로 따로 셉니다. */
    static String aboveAudit(JsonNode results) { return auditCount(results, 1); }
    private static String auditCount(JsonNode results, int sign) {
        if (results == null) return "-";
        List<JsonNode> rows = new ArrayList<>();
        results.path("attempts").forEach(rows::add);
        results.path("cases").forEach(c -> c.path("attempts").forEach(rows::add));
        int count = 0;
        for (JsonNode row : rows) {
            JsonNode prompt = row.path("promptTokens"), audit = row.path("auditInputTokens");
            for (int i = 0; i < Math.min(prompt.size(), audit.size()); i++) if (audit.get(i).isInt() && prompt.get(i).isInt() && Integer.signum(prompt.get(i).asInt() - audit.get(i).asInt()) == sign) count++;
        }
        return String.valueOf(count);
    }
    private static String proposal(String runId, Loaded loaded, F0Judgement.Outcome outcome) {
        StringBuilder text = new StringBuilder("# SPEC §14.5 기록 제안 — F0 회차 ").append(runId).append("\n\nSPEC 소유자가 아래 표지 사이 블록을 §14.5 에 그대로 반영한다(이 파일은 SPEC 을 고치지 않음).\n\n");
        text.append(PROPOSAL_BEGIN).append("\n**F0 측정 기록(회차 ").append(runId).append(", 측정 시작 ").append(loaded.binding().path("startedAt").asText()).append(")**\n\n");
        outcome.paths().forEach((path, m) -> text.append("- 경로 ").append(path).append(": taskType ").append(m.taskType().correct()).append('/').append(m.taskType().denominator())
            .append(", 인자 ").append(m.args().correct()).append('/').append(m.args().denominator()).append(", UNSUPPORTED ").append(m.unsupported().correct()).append('/').append(m.unsupported().denominator())
            .append(", JSON 유효 ").append(m.validCount()).append('/').append(m.attemptDenominator()).append(", 재시도 ").append(m.retriedCount()).append('/').append(m.attemptDenominator())
            .append(", p50/p95 ").append(m.latencyP50Ms()).append('/').append(m.latencyP95Ms()).append(" ms, 출력 토큰 최대 ").append(m.outputTokensMax()).append('\n'));
        text.append(budgetLine(loaded.prereg().document())).append('\n');
        F0Judgement.Verdict cmp = outcome.verdicts().get("AC-37-CMP");
        text.append("- 잠정 후보: ").append(cmp == null ? "판정 입력 없음" : cmp.reasonCode()).append('\n');
        for (ObjectNode row : outcome.boundaryRows()) text.append("- 경계 ").append(row.path("caseId").asText()).append(": 공식 ").append(row.path("officialTokens").asInt()).append(" / GGUF ").append(row.path("ggufTokens").asInt())
            .append(" 토큰, 종료 여유 ").append(row.path("terminationMargin").asInt()).append(", 완결 생성 ").append(row.path("passRounds").asInt()).append('/').append(row.path("totalRounds").asInt()).append('\n');
        return text.append(PROPOSAL_END).append('\n').toString();
    }
}
