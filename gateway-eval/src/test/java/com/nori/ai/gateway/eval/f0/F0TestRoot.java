package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;

/** 실제 레포의 사전등록 대상 파일을 임시 루트로 복사하고 SPEC·기준선·fake git·PREREG 를 갖춘 F0 시험 환경. */
final class F0TestRoot {
    static final String REGISTERED = "a".repeat(40), PREREG_COMMIT = "b".repeat(40);
    static final long COMMIT_EPOCH = 1767225600L; // 2026-01-01T00:00:00Z
    final Path root, spec, actual = Path.of(System.getProperty("aigw.rootDir"));
    final FakeGit git = new FakeGit();
    final Map<String,String> env = new HashMap<>();
    String lastError;
    final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

    F0TestRoot(Path root) throws IOException {
        this.root = root;
        for (String file : List.of("eval/golden/tc-f0-v1.jsonl", "eval/golden/tc-registry-fixture-v1.json", "eval/f0/prompts/classify-v1.txt", "eval/f0/prompts/plan-v1.txt", "eval/f0/arg-limits-v1.json",
            "eval/f0/usage-probe-input-v1.jsonl", "eval/f0/token-golden-input-v1.jsonl", "eval/f0/chat-renderers-v1.json", "eval/f0/GGUF-COUNT.md")) copy(file);
        write("eval/f0/output-boundary-cases.jsonl", cases(BoundaryCases.generate(ArgLimits.load(root.resolve("eval/f0/arg-limits-v1.json")), RegistryFixture.load(root.resolve("eval/golden/tc-registry-fixture-v1.json")))));
        write("eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json", "{\"fixture\":\"시험용 바이트 — 공식 tokenizer 아님\"}");
        write(MeasurementSession.TOKENIZER_PIN, Json.sha(Files.readAllBytes(root.resolve(MeasurementSession.TOKENIZER_FILE))) + "  qwen3-4b-instruct-2507.tokenizer.json\n");
        spec = root.resolve("spec.md"); Files.writeString(spec, "# SPEC\n\n### 14.5 추론 호출 예산\n\n본문 S0\n\n### 14.6 다음\n", StandardCharsets.UTF_8);
        baseline(List.of(entry(1, Json.sha(Files.readAllBytes(spec)), null, "ALL", true)));
        env.put("AIGW_SPEC_FILE", spec.toString()); env.put("AIGW_PARENT_RUN_ID", "20261101T000000Z");
        prereg(REGISTERED);
    }
    static String cases(BoundaryCases.Result result) { StringBuilder text = new StringBuilder(); result.rows().forEach(r -> text.append(Json.canonicalString(r)).append('\n')); return text.toString(); }
    void copy(String file) throws IOException { Path target = root.resolve(file); Files.createDirectories(target.getParent()); Files.copy(actual.resolve(file), target, StandardCopyOption.REPLACE_EXISTING); }
    void write(String file, String text) throws IOException { Path target = root.resolve(file); Files.createDirectories(target.getParent()); Files.writeString(target, text, StandardCharsets.UTF_8); }
    Map<String,Object> entry(int seq, String sha, String previous, Object leaves, boolean affecting) {
        Map<String,Object> entry = new LinkedHashMap<>(); entry.put("seq", seq); entry.put("sha256", sha); entry.put("prevSha256", previous); entry.put("affectedLeaves", leaves); entry.put("measurementAffecting", affecting);
        entry.put("approvedAt", "2026-10-29"); entry.put("approvalRef", "fixture-approval"); entry.put("changedSections", List.of("fixture")); return entry;
    }
    void baseline(List<Map<String,Object>> entries) throws IOException { write("docs/gates/spec-baseline.md", "# 기준선\n\n```json\n" + Json.canonicalString(Json.MAPPER.valueToTree(entries)) + "\n```\n"); }
    /** 현재 임시 루트 바이트로 PREREG 를 다시 씁니다(registered=null 이면 미커밋 상태 표기). */
    void prereg(String registered) throws IOException {
        ObjectNode block = Json.MAPPER.createObjectNode(); block.put("schema", "f0-prereg-v1");
        if (registered == null) block.putNull("registeredCommit"); else block.put("registeredCommit", registered);
        var inputs = block.putArray("inputs");
        for (String file : Prereg.REQUIRED_INPUTS) inputs.addObject().put("path", file).put("sha256", Json.sha(Files.readAllBytes(root.resolve(file))));
        block.put("harnessDigest", HarnessDigest.compute(root));
        ObjectNode p = block.putObject("parameters"); p.put("generationModel", "qwen3:4b-instruct"); p.put("embeddingModel", "bge-m3"); p.put("repeats", 2); p.put("temperature", 0);
        p.putObject("maxTokens").put("classify", 128).put("plan", 160); p.put("requestTimeoutMs", 2000); p.put("outputLimitTokens", 128); p.put("terminationMarginTokens", 1);
        p.putObject("promptSha256").put("classify", Json.sha(Files.readAllBytes(root.resolve("eval/f0/prompts/classify-v1.txt")))).put("plan", Json.sha(Files.readAllBytes(root.resolve("eval/f0/prompts/plan-v1.txt"))));
        p.putObject("latencyBudgetMsByT").put("UNS", 15000).put("LQ", 30000).put("KQ", 30000).put("GQ", 30000).put("INC", 90000);
        write(Prereg.DEFAULT_PATH, "# PREREG 시험 fixture\n\n```json\n" + Json.canonicalString(block) + "\n```\n");
    }
    MeasurementSession.Context context() { return new MeasurementSession.Context(root, env, git, Clock.systemUTC()); }
    /** EvalCli 와 같은 예외→exit 코드 변환(원문 없이 고정 코드만). */
    int exec(String... args) {
        String[] full = new String[args.length + 1]; full[0] = "f0"; System.arraycopy(args, 0, full, 1, args.length);
        try { new F0RunnerCli(context(), new PrintStream(stdout, true, StandardCharsets.UTF_8)).execute(full); lastError = null; return 0; }
        catch (EvalException e) { lastError = e.getMessage(); return e.exitCode(); }
    }
    JsonNode json(String relative) { return Json.read(root.resolve(relative)); }
    String text(String relative) throws IOException { return Files.readString(root.resolve(relative), StandardCharsets.UTF_8); }
    /** 결과 디렉터리 전체 바이트(원문 sentinel 비노출 검사용). */
    String allOutput(String relative) throws IOException {
        StringBuilder text = new StringBuilder(stdout.toString(StandardCharsets.UTF_8));
        Path base = root.resolve(relative); if (!Files.exists(base)) return text.toString();
        try (var walk = Files.walk(base)) { for (Path p : walk.filter(Files::isRegularFile).toList()) text.append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)); }
        return text.toString();
    }

    /** 읽기 전용 git 응답을 흉내 냅니다(commit·checkout 같은 쓰기 명령은 GitReader 계약상 호출 자체가 없음). */
    final class FakeGit implements GitReader {
        boolean committed = true, ordered = true, inside = true; long epoch = COMMIT_EPOCH; String status = ""; final Map<String,byte[]> blobOverride = new HashMap<>(); final List<List<String>> calls = new ArrayList<>();
        @Override public Result run(Path repositoryRoot, List<String> args) {
            calls.add(List.copyOf(args));
            if (!GitReader.READ_ONLY.contains(args.getFirst())) throw new AssertionError("쓰기 git 명령 호출");
            return switch (args.getFirst()) {
                case "rev-parse" -> inside ? ok("true\n") : new Result(128, new byte[0]);
                case "status" -> ok(status);
                case "log" -> ok(committed ? PREREG_COMMIT + " " + epoch + "\n" : "");
                case "merge-base" -> new Result(ordered ? 0 : 1, new byte[0]);
                case "cat-file" -> {
                    String path = args.get(2).substring(args.get(2).indexOf(':') + 1);
                    if (blobOverride.containsKey(path)) yield new Result(0, blobOverride.get(path));
                    Path file = root.resolve(path);
                    try { yield Files.isRegularFile(file) ? new Result(0, Files.readAllBytes(file)) : new Result(128, new byte[0]); } catch (IOException e) { throw new UncheckedIOException(e); }
                }
                case "ls-tree" -> ok(String.join("\0", HarnessDigest.files(root).keySet()) + "\0");
                default -> new Result(1, new byte[0]);
            };
        }
        private Result ok(String text) { return new Result(0, text.getBytes(StandardCharsets.UTF_8)); }
    }
}
