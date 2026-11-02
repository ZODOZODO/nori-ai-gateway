package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.f0.F0RunnerCli;
import com.nori.ai.gateway.eval.f0.GitReader;
import com.nori.ai.gateway.eval.f0.MeasurementSession;
import com.nori.ai.gateway.eval.f3.EmbeddingProfile;
import com.nori.ai.gateway.eval.io.Json;
import java.time.Clock;
import com.nori.ai.gateway.eval.report.*;
import com.nori.ai.gateway.eval.spec.*;
import java.io.PrintStream;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class EvalCli {
    private final Map<String,String> env;
    private final PrintStream out;
    private final PrintStream err;
    private final Path root;
    public EvalCli(Map<String,String> env, PrintStream out, PrintStream err, Path root) { this.env = Map.copyOf(env); this.out = out; this.err = err; this.root = root.toAbsolutePath().normalize(); }
    public static void main(String[] args) { System.exit(new EvalCli(System.getenv(), System.out, System.err, Path.of(".")).execute(args)); }
    public int execute(String... arguments) {
        try { dispatch(arguments); return 0; }
        catch (EvalException e) { err.println(e.getMessage()); return e.exitCode(); }
        catch (RuntimeException e) { err.println("EVAL_INPUT_INVALID"); return 2; }
    }
    private void dispatch(String[] arguments) {
        if (arguments.length == 0) throw new EvalException(2, "UNKNOWN_COMMAND");
        // F0 하네스·F3 profile 은 자체 플래그를 쓰므로 공통 인자 해석 전에 분기합니다.
        if (arguments[0].equals("f0")) { new F0RunnerCli(new MeasurementSession.Context(root, env, GitReader.process(), Clock.systemUTC()), out).execute(arguments); return; }
        if (arguments[0].equals("f3")) { EmbeddingProfile.execute(arguments, root, out); return; }
        String command = arguments[0]; boolean nested = Set.of("spec-input", "test-evidence", "record", "perf", "f0", "f3").contains(command);
        String operation = nested && arguments.length > 1 ? arguments[1] : "";
        if (nested && operation.startsWith("--")) throw new EvalException(2, "UNKNOWN_COMMAND");
        Args args = new Args(arguments, nested ? 2 : 1, Set.of("--require-executed", "--require-docker"));
        Path baseline = path(args.value("--baseline", "docs/gates/spec-baseline.md"));
        switch (command) {
            case "spec-guard" -> {
                args.only("--spec", "--baseline"); SpecBinding binding = new SpecGuard(env).check(spec(args), null, baseline);
                out.println(Json.canonicalString(Json.MAPPER.valueToTree(Map.of("specSha", binding.specSha(), "specShaSource", binding.specShaSource(), "specInputOrigin", binding.specInputOrigin()))));
            }
            case "spec-input" -> {
                args.only("--spec", "--baseline", "--out-dir", "--work-dir", "--api-url", "--project-id", "--sha", "--auth-header", "--token-env", "--token-file");
                SpecInputPackage packages = new SpecInputPackage(env);
                switch (operation) {
                    case "pack" -> { Path packed = packages.pack(spec(args), baseline, path(args.required("--out-dir"))); out.println("SPEC_PACKAGE_PACKED " + packed.getParent().getFileName()); }
                    case "publish" -> out.println(Json.canonicalString(Json.MAPPER.valueToTree(packages.publish(spec(args), baseline, path(args.required("--work-dir")), args.required("--api-url"), args.required("--project-id"), args.required("--auth-header"), args.optional("--token-env"), tokenFile(args)))));
                    case "fetch" -> { Path file = packages.fetch(args.required("--sha"), baseline, path(args.required("--out-dir")), args.required("--api-url"), args.required("--project-id"), args.required("--auth-header"), args.optional("--token-env"), tokenFile(args)); out.println("SPEC_PACKAGE_FETCHED " + file.getParent().getFileName()); }
                    default -> throw new EvalException(2, "UNKNOWN_COMMAND");
                }
            }
            case "leaf-extract" -> {
                args.only("--otr-dir", "--task", "--module", "--baseline", "--spec", "--out", "--current-fingerprint", "--require-executed", "--catalog");
                new OtrLeafExtractor().extract(path(args.required("--otr-dir")), args.required("--task"), args.required("--module"), baseline, spec(args), path(args.required("--out")), args.required("--current-fingerprint"), args.flag("--require-executed"), catalog(args), env);
            }
            case "test-evidence" -> {
                if (!operation.equals("verify")) throw new EvalException(2, "UNKNOWN_COMMAND");
                args.only("--tasks", "--selector", "--required", "--require-docker", "--spec", "--baseline");
                new SpecGuard(env).check(spec(args), null, baseline);
                List<String> tasks = new ArrayList<>(); Map<String,List<String>> classes = new LinkedHashMap<>();
                if (args.optional("--tasks") != null) tasks.addAll(List.of(args.required("--tasks").split(",")));
                else {
                    String selector = args.required("--selector");
                    for (String line : lines(path(args.required("--required")))) {
                        if (line.isBlank() || line.startsWith("#")) continue;
                        String[] parts = line.strip().split("[ |]+", 2); String module = parts[0].startsWith(":") ? parts[0] : ":" + parts[0];
                        String task = module + ":" + selector; tasks.add(task);
                        if (parts.length == 2) classes.put(task, List.of(parts[1].split(",")));
                    }
                }
                new TestEvidenceVerifier().verify(root, tasks, args.flag("--require-docker"), classes); out.println("TEST_EVIDENCE_VERIFIED");
            }
            case "ingest", "gate", "record", "perf" -> {
                args.only("--stage", "--parent-run-id", "--input", "--source", "--spec", "--baseline", "--leaf", "--run-id", "--field", "--samples", "--criteria", "--out", "--catalog");
                SpecBinding binding = new SpecGuard(env).check(spec(args), null, baseline); String stage = args.required("--stage"), parent = args.value("--parent-run-id", env.get("AIGW_PARENT_RUN_ID"));
                if (!stage.matches("F[0-4]") || parent == null || !parent.matches("[A-Za-z0-9_.:-]{1,128}")) throw new EvalException(2, "PARENT_RUN_ID_MISSING");
                LeafCatalog catalog = catalog(args); EvidenceRepositoryFs repository = new EvidenceRepositoryFs(root);
                if (command.equals("ingest")) new EvidenceIngestor(repository, catalog).ingest(path(args.required("--input")), args.value("--source", "measurement"), stage, parent, binding);
                else if (command.equals("gate")) {
                    var report = new GateEvaluator().evaluate(stage, parent, repository.readAll(stage), catalog, SpecBaseline.parseChain(baseline));
                    if (args.optional("--out") == null) new GateReportWriter().write(root.resolve("evidence/" + stage + "/GATE-REPORT.md"), report);
                    else new GateReportWriter().writeNew(derivedGateReport(path(args.required("--out"))), report);
                    out.println(report.result());
                } else if (command.equals("record")) new ManualRecorder(catalog, repository).record(operation, stage, args.required("--leaf"), args.required("--run-id"), parent, args.all("--field"), binding);
                else {
                    if (!operation.equals("report")) throw new EvalException(2, "UNKNOWN_COMMAND");
                    JsonNode manifest = repository.readAll(stage).stream().filter(n -> n.path("status").asText().equals("PARENT") && n.path("parentRunId").asText().equals(parent)).findFirst().orElseThrow(() -> new EvalException(2, "PARENT_MANIFEST_MISSING"));
                    new PerfReporter().report(stage, args.required("--leaf"), parent, args.all("--samples").stream().map(this::path).toList(), path(args.required("--criteria")), path(args.required("--out")), root, binding, catalog, manifest);
                }
            }
            case "ci-evidence" -> throw new EvalException(2, "COMMAND_NOT_IMPLEMENTED_IN_P01");
            default -> throw new EvalException(2, "UNKNOWN_COMMAND");
        }
    }
    /**
     * gate --out: 회차별 파생 보고서는 레포 안·evidence 밖이고, eval/f0/results 아래면 gate-reports 디렉터리만 허용합니다.
     * 측정 회차 디렉터리에 쓰면 그 회차 원시 결과 SHA(F0ReportWriter.rawResultsSha256) 집계가 바뀌기 때문입니다.
     */
    private Path derivedGateReport(Path output) {
        Path results = root.resolve("eval/f0/results"), reports = results.resolve("gate-reports");
        if (!output.startsWith(root) || output.equals(root) || output.startsWith(root.resolve("evidence")) || (output.startsWith(results) && !output.getParent().equals(reports))
            || !output.getFileName().toString().endsWith(".md")) throw new EvalException(2, "GATE_REPORT_PATH_INVALID");
        return output;
    }
    private LeafCatalog catalog(Args args) { return LeafCatalog.load(args.optional("--catalog") == null ? null : path(args.optional("--catalog"))); }
    private String tokenFile(Args args) { return args.optional("--token-file") == null ? null : path(args.optional("--token-file")).toString(); }
    private String spec(Args args) { String explicit = args.optional("--spec"); return explicit == null ? null : path(explicit).toString(); }
    private Path path(String text) { return root.resolve(text).normalize(); }
    private static List<String> lines(Path path) { try { return Files.readAllLines(path); } catch (IOException e) { throw new EvalException(2, "INPUT_MISSING"); } }
}
