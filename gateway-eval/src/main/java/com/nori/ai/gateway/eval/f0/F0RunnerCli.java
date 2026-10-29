package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.Args;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/**
 * F0 하네스 진입점(EvalCli 의 "f0" 하위 명령). 모든 인자는 필수이며 누락 시 exit 2.
 * exit: 0 완료, 2 입력·사전등록·결속 오류, 3 재판정 불가, 4 preflight 실패, 5 측정 중 endpoint 오류(부분 결과 보존), 6 모델 digest 불일치.
 */
public final class F0RunnerCli {
    public static final String GENERATION_MODEL = "qwen3:4b-instruct", EMBEDDING_MODEL = "bge-m3";
    private static final Map<String,String> PROMPT_BY_PATH = Map.of("A", "eval/f0/prompts/plan-v1.txt", "B", "eval/f0/prompts/classify-v1.txt");
    private final MeasurementSession.Context context;
    private final PrintStream out;

    public F0RunnerCli(MeasurementSession.Context context, PrintStream out) { this.context = context; this.out = out; }

    public void execute(String[] arguments) {
        String operation = arguments.length > 1 ? arguments[1] : "";
        Args args = new Args(arguments, 2, Set.of("--rejudge", "--spec-record-check"));
        switch (operation) {
            case "cases" -> {
                args.only("--limits", "--fixture", "--out");
                ArgLimits limits = ArgLimits.load(path(args, "--limits"));
                RegistryFixture fixture = RegistryFixture.load(path(args, "--fixture"));
                BoundaryCases.Result result = BoundaryCases.generate(limits, fixture);
                BoundaryCases.write(path(args, "--out"), result);
                if (!result.unresolved().isEmpty()) throw new EvalException(2, "ARG_LIMITS_UNRESOLVED");
                out.println("F0_CASES_WRITTEN " + result.rows().size());
            }
            case "preflight" -> {
                args.only("--endpoint", "--out");
                Path file = path(args, "--out");
                if (Files.exists(file)) throw new EvalException(2, "OUTPUT_EXISTS");
                Preflight.Result result = Preflight.run(new OllamaChatClient(args.required("--endpoint"), Duration.ofSeconds(30)), GENERATION_MODEL, EMBEDDING_MODEL);
                MeasurementSession.writeNew(file, Json.canonical(Json.MAPPER.valueToTree(result.summary())));
                if (!result.summary().ok()) throw new EvalException(4, result.summary().reason());
                out.println("F0_PREFLIGHT_OK " + result.summary().residentCount());
            }
            case "run" -> {
                args.only("--path", "--endpoint", "--golden", "--fixture", "--prompt", "--repeats", "--run-id", "--out");
                String route = args.required("--path"), runId = runId(args.required("--run-id")); int repeats = repeats(args);
                if (!PROMPT_BY_PATH.containsKey(route)) throw new EvalException(2, "ARGUMENT_INVALID");
                registered(args, "--golden", "eval/golden/tc-f0-v1.jsonl"); registered(args, "--fixture", "eval/golden/tc-registry-fixture-v1.json"); registered(args, "--prompt", PROMPT_BY_PATH.get(route));
                Path dir = results(path(args, "--out"), runId);
                RegistryFixture fixture = RegistryFixture.load(path(args, "--fixture")); GoldenSet golden = GoldenSet.load(path(args, "--golden"), fixture); MessageAssembler assembler = MessageAssembler.load(path(args, "--prompt"), fixture);
                checkRepeats(repeats);
                MeasurementSession session = MeasurementSession.start(context, "f0 run --path " + route, args.required("--endpoint"), runId, dir.resolve("run-binding.json"), null);
                F0Measurements.run(session, route, golden, fixture, assembler, repeats, runId, dir);
                out.println("F0_RUN_COMPLETED " + route);
            }
            case "boundary-gen" -> {
                args.only("--endpoint", "--cases", "--fixture", "--prompt", "--repeats", "--run-id", "--out");
                String runId = runId(args.required("--run-id")); int repeats = repeats(args);
                registered(args, "--cases", "eval/f0/output-boundary-cases.jsonl"); registered(args, "--fixture", "eval/golden/tc-registry-fixture-v1.json"); registered(args, "--prompt", PROMPT_BY_PATH.get("B"));
                Path dir = results(path(args, "--out"), runId);
                RegistryFixture fixture = RegistryFixture.load(path(args, "--fixture")); List<JsonNode> cases = BoundaryCases.load(path(args, "--cases")); MessageAssembler assembler = MessageAssembler.load(path(args, "--prompt"), fixture);
                checkRepeats(repeats);
                MeasurementSession session = MeasurementSession.start(context, "f0 boundary-gen", args.required("--endpoint"), runId, dir.resolve("run-binding.json"), null);
                F0Measurements.boundaryGen(session, cases, assembler, repeats, runId, dir);
                out.println("F0_BOUNDARY_COMPLETED " + cases.size());
            }
            case "gguf-count" -> {
                args.only("--endpoint", "--cases", "--out");
                registered(args, "--cases", "eval/f0/output-boundary-cases.jsonl");
                String runId = envRunId(); Path dir = results(path(args, "--out"), runId);
                List<JsonNode> cases = BoundaryCases.load(path(args, "--cases"));
                MeasurementSession session = MeasurementSession.start(context, "f0 gguf-count", args.required("--endpoint"), runId, dir.resolve("run-binding.json"), null);
                F0Measurements.ggufCount(session, cases, runId, dir);
                out.println("F0_GGUF_COUNT_COMPLETED " + cases.size());
            }
            case "chat-template" -> {
                args.only("--endpoint", "--model", "--out");
                if (!args.required("--model").equals(GENERATION_MODEL)) throw new EvalException(2, "ARGUMENT_INVALID");
                String runId = envRunId(); Path dir = path(args, "--out");
                MeasurementSession session = MeasurementSession.start(context, "f0 chat-template", args.required("--endpoint"), runId, dir.resolve("run-binding.json"), null);
                F0Measurements.chatTemplate(session, dir, "qwen3-4b-instruct");
                out.println("F0_CHAT_TEMPLATE_CAPTURED");
            }
            case "usage-probe" -> {
                args.only("--endpoint", "--messages", "--out");
                registered(args, "--messages", "eval/f0/usage-probe-input-v1.jsonl");
                String runId = envRunId(); Path file = results(path(args, "--out"), runId);
                Map<String,List<Map<String,String>>> inputs = new LinkedHashMap<>(); for (var e : messages(path(args, "--messages"))) inputs.put(e.getKey(), e.getValue());
                validateUsage(inputs);
                ChatRenderers.Renderer[] renderer = new ChatRenderers.Renderer[1];
                // 렌더러 검증 실패는 run-binding 작성·추론 호출 전에 거부합니다(chat template 추정 0).
                MeasurementSession session = MeasurementSession.start(context, "f0 usage-probe", args.required("--endpoint"), runId, sibling(file, ".run-binding.json"),
                    runtime -> renderer[0] = ChatRenderers.forTemplate(context.root().resolve(ChatRenderers.REGISTRY), runtime.templateSha256()));
                F0Measurements.usageProbe(session, inputs, renderer[0], runId, file);
                out.println("F0_USAGE_PROBE_COMPLETED");
            }
            case "token-golden" -> {
                args.only("--endpoint", "--messages", "--out");
                registered(args, "--messages", "eval/f0/token-golden-input-v1.jsonl");
                String runId = envRunId(); Path file = results(path(args, "--out"), runId);
                if (Files.exists(file)) throw new EvalException(2, "OUTPUT_EXISTS");
                List<Map.Entry<String,List<Map<String,String>>>> inputs = messages(path(args, "--messages"));
                if (inputs.size() < 12) throw new EvalException(2, "TOKEN_GOLDEN_INPUT_INVALID");
                MeasurementSession session = MeasurementSession.start(context, "f0 token-golden", args.required("--endpoint"), runId, sibling(file, ".run-binding.json"), null);
                F0Measurements.tokenGolden(session, inputs, file);
                out.println("F0_TOKEN_GOLDEN_COMPLETED " + inputs.size());
            }
            case "report" -> {
                args.only("--run-id", "--results", "--prereg", "--out", "--rejudge", "--spec-record-check", "--proposal");
                String runId = runId(args.required("--run-id")); Path results = path(args, "--results"), prereg = path(args, "--prereg"), target = path(args, "--out");
                registered(args, "--prereg", Prereg.DEFAULT_PATH);
                F0ReportWriter writer = new F0ReportWriter(context);
                if (args.flag("--rejudge") && args.flag("--spec-record-check")) throw new EvalException(2, "ARGUMENT_INVALID");
                if (args.flag("--spec-record-check")) out.println(writer.specRecordCheck(runId, results, prereg, path(args, "--proposal"), target));
                else if (args.optional("--proposal") != null) throw new EvalException(2, "ARGUMENT_INVALID");
                else if (args.flag("--rejudge")) out.println(writer.rejudge(runId, results, prereg, target));
                else out.println(writer.report(runId, results, prereg, target));
            }
            case "prereg-check" -> {
                args.only("--prereg");
                registered(args, "--prereg", Prereg.DEFAULT_PATH);
                Prereg.Document document = Prereg.parse(path(args, "--prereg"));
                Map<String,Object> summary = new TreeMap<>();
                Map<String,String> current = new TreeMap<>(); document.inputs().keySet().forEach(f -> { Path p = context.root().resolve(f); current.put(f, Files.isRegularFile(p) ? Json.sha(Json.bytes(p)) : "MISSING"); });
                summary.put("currentInputs", current); summary.put("currentHarnessDigest", HarnessDigest.compute(context.root())); summary.put("localMismatches", Prereg.localMismatches(context.root(), document));
                summary.put("parameterManifestSha256", document.parameterManifestSha256());
                out.println(Json.canonicalString(Json.MAPPER.valueToTree(summary)));
                Prereg.verify(context.root(), path(args, "--prereg"), context.git());
                out.println("PREREG_VERIFIED");
            }
            default -> throw new EvalException(2, "UNKNOWN_COMMAND");
        }
    }
    private Path path(Args args, String key) { return context.root().resolve(args.required(key)).normalize(); }
    // 측정 입력은 사전등록한 파일만 허용합니다(다른 경로의 같은 이름 파일로 우회 0).
    private void registered(Args args, String key, String expected) { if (!path(args, key).equals(context.root().resolve(expected).normalize())) throw new EvalException(2, "ARGUMENT_NOT_REGISTERED"); }
    private static int repeats(Args args) {
        try { int value = Integer.parseInt(args.required("--repeats")); if (value < 1 || value > 100) throw new NumberFormatException(); return value; }
        catch (NumberFormatException e) { throw new EvalException(2, "ARGUMENT_INVALID"); }
    }
    // 회차 수는 사전등록 값과 같아야 하며 endpoint 호출·run-binding 작성 전에 거부합니다.
    private void checkRepeats(int repeats) { if (Prereg.parse(context.root().resolve(Prereg.DEFAULT_PATH)).repeats() != repeats) throw new EvalException(2, "ARGUMENT_NOT_REGISTERED"); }
    private static String runId(String value) { if (!value.matches("[A-Za-z0-9_.:-]{1,64}")) throw new EvalException(2, "RUN_ID_INVALID"); return value; }
    private String envRunId() { String value = context.env().get("AIGW_PARENT_RUN_ID"); if (value == null || value.isBlank()) throw new EvalException(2, "RUN_ID_MISSING"); return runId(value); }
    /** eval/f0/results 아래 출력은 반드시 같은 runId 디렉터리 안이어야 합니다(회차 혼합 0). */
    private Path results(Path target, String runId) {
        Path base = context.root().resolve("eval/f0/results").normalize();
        if (target.startsWith(base) && (target.getNameCount() <= base.getNameCount() || !target.getName(base.getNameCount()).toString().equals(runId))) throw new EvalException(2, "RUN_ID_MISMATCH");
        return target;
    }
    private static Path sibling(Path file, String suffix) {
        String name = file.getFileName().toString(); int dot = name.lastIndexOf('.');
        return file.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + suffix);
    }
    /** 사전등록 메시지 입력: {id, messages} 또는 {id, assemble:{goldenId}}(classify-v1 + Registry fixture 실제 조립). */
    List<Map.Entry<String,List<Map<String,String>>>> messages(Path file) {
        List<Map.Entry<String,List<Map<String,String>>>> rows = new ArrayList<>(); Set<String> ids = new HashSet<>();
        MessageAssembler assembler = null; GoldenSet golden = null;
        for (String line : new String(Json.bytes(file), StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank()) continue;
            JsonNode row = Json.parse(line.getBytes(StandardCharsets.UTF_8));
            String id = row.path("id").asText("");
            if (!id.matches("[A-Za-z0-9_-]{1,32}") || !ids.add(id) || row.has("messages") == row.has("assemble") || GoldenSet.keys(row).size() != 2) throw new EvalException(2, "MESSAGES_INPUT_INVALID");
            List<Map<String,String>> messages = new ArrayList<>();
            if (row.has("assemble")) {
                if (assembler == null) {
                    RegistryFixture fixture = RegistryFixture.load(context.root().resolve("eval/golden/tc-registry-fixture-v1.json"));
                    assembler = MessageAssembler.load(context.root().resolve(PROMPT_BY_PATH.get("B")), fixture); golden = GoldenSet.load(context.root().resolve("eval/golden/tc-f0-v1.jsonl"), fixture);
                }
                GoldenSet.Question q = golden.byId(row.path("assemble").path("goldenId").asText());
                messages.addAll(assembler.messages(q.question(), q.context()));
            } else for (JsonNode m : row.path("messages")) {
                if (!GoldenSet.keys(m).equals(Set.of("role", "content")) || !Set.of("system", "user", "assistant").contains(m.path("role").asText()) || !m.path("content").isTextual()) throw new EvalException(2, "MESSAGES_INPUT_INVALID");
                messages.add(Map.of("role", m.path("role").asText(), "content", m.path("content").asText()));
            }
            if (messages.isEmpty() || !messages.getLast().get("role").equals("user")) throw new EvalException(2, "MESSAGES_INPUT_INVALID");
            rows.add(Map.entry(id, List.copyOf(messages)));
        }
        return rows;
    }
    /** U1·U2 는 시스템 접두부를 공유하고 사용자 메시지만 다릅니다. */
    static void validateUsage(Map<String,List<Map<String,String>>> inputs) {
        if (!inputs.keySet().equals(Set.of("U1", "U2"))) throw new EvalException(2, "USAGE_INPUT_INVALID");
        List<Map<String,String>> u1 = inputs.get("U1"), u2 = inputs.get("U2");
        if (u1.size() != u2.size() || !u1.getFirst().get("role").equals("system") || !u1.subList(0, u1.size() - 1).equals(u2.subList(0, u2.size() - 1)) || u1.getLast().equals(u2.getLast())) throw new EvalException(2, "USAGE_INPUT_INVALID");
    }
}
