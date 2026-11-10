package com.nori.ai.gateway.eval.ci;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.EvidenceRecord;
import com.nori.ai.gateway.eval.report.LeafCatalog;
import com.nori.ai.gateway.eval.report.TestEvidenceVerifier;
import com.nori.ai.gateway.eval.spec.SpecBinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 통합 시험 증거 묶음(Plan §4.3 (b)·P15 M-10). pack 은 Docker 가능 환경의 운영자가 정제 요약만 묶고,
 * verify 는 CI 가 같은 commit·SPEC 입력·package 빌드 신원으로 다시 판정한다. 원시 XML·실패 메시지·SPEC 본문은 묶지 않는다.
 */
public final class CiEvidence {
    static final String FORMAT = "aigw-it-evidence-v1";
    static final String MANIFEST = "bundle.json";
    static final String SELECTOR = "integrationTest";
    private static final Set<String> MANIFEST_FIELDS = Set.of("formatVersion", "gitSha", "worktreeClean", "dockerServerVersion", "testcontainersVersion",
        "specSha", "specShaSource", "specInputOrigin", "files");

    /** 외부 명령(git·docker) 경계. 실패는 null 이 아니라 예외다. */
    @FunctionalInterface public interface Commands { String run(Path directory, List<String> command); }

    private final Path root;
    private final Commands commands;

    public CiEvidence(Path root, Commands commands) { this.root = root; this.commands = commands; }

    /** 필수 등록 파일 = 줄마다 {@code <모듈 경로> <IT FQCN>[,<FQCN>…]}. 모듈별 {@code integrationTest} task 와 등록 클래스를 돌려준다. */
    static Map<String,List<String>> required(Path file) {
        List<String> lines;
        try { lines = Files.readAllLines(file); } catch (IOException e) { throw new EvalException(2, "INPUT_MISSING"); }
        var tasks = new LinkedHashMap<String,List<String>>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.strip().split("[ |]+", 2);
            String module = parts[0].startsWith(":") ? parts[0] : ":" + parts[0];
            tasks.computeIfAbsent(module + ":" + SELECTOR, k -> new ArrayList<>()).addAll(parts.length == 2 ? List.of(parts[1].split(",")) : List.of());
        }
        if (tasks.isEmpty()) throw new EvalException(4, "NO_TESTS_EXECUTED");
        return tasks;
    }

    // 묶음 안 항목 이름(task 경로 기준 논리 이름). evidence 모드 job 에는 fingerprint 가 없으므로 실제 projectDir 이 아니라 task 경로로 정한다.
    static String summaryPath(String task) {
        int split = task.lastIndexOf(':');
        return task.substring(1, split).replace(':', '/') + "/build/aigw-evidence/" + task.substring(split + 1) + "/leaf-results.json";
    }

    public Path pack(Path out, Path requiredFile, SpecBinding binding) {
        Map<String,List<String>> required = required(requiredFile);
        // test-evidence verify --selector integrationTest --required … --require-docker 와 같은 판정을 먼저 통과해야 한다(N17).
        new TestEvidenceVerifier().verify(root, List.copyOf(required.keySet()), true, required);
        String head = commands.run(root, List.of("git", "rev-parse", "HEAD")).strip();
        if (!head.matches("[0-9a-f]{40}")) throw new EvalException(4, "GIT_IDENTITY_INVALID");
        boolean clean = commands.run(root, List.of("git", "status", "--porcelain")).isBlank();
        String docker = commands.run(root, List.of("docker", "version", "--format", "{{.Server.Version}}")).strip();
        if (docker.isEmpty()) throw new EvalException(4, "DOCKER_UNAVAILABLE");
        var entries = new TreeMap<String,byte[]>();
        var files = new TreeMap<String,String>();
        for (String task : required.keySet()) {
            String path = summaryPath(task);
            byte[] bytes = Json.bytes(TestEvidenceVerifier.summaryFile(root, task));
            JsonNode evidence = Json.parse(bytes);
            requireSpec(evidence.path("summary"), binding);
            for (JsonNode record : evidence.path("records")) requireSpec(record, binding);
            if (!head.equals(evidence.path("summary").path("buildIdentity").path("gitSha").asText(null))) throw new EvalException(4, "BUILD_IDENTITY_MISMATCH");
            entries.put(path, bytes); files.put(path, Json.sha(bytes));
        }
        ObjectNode manifest = Json.MAPPER.createObjectNode();
        manifest.put("formatVersion", FORMAT); manifest.put("gitSha", head); manifest.put("worktreeClean", clean);
        manifest.put("dockerServerVersion", docker); manifest.put("testcontainersVersion", testcontainersVersion());
        manifest.put("specSha", binding.specSha()); manifest.put("specShaSource", binding.specShaSource()); manifest.put("specInputOrigin", binding.specInputOrigin());
        manifest.set("files", Json.MAPPER.valueToTree(files));
        entries.put(MANIFEST, Json.canonical(manifest));
        TarGz.write(out, entries);
        return out;
    }

    public void verify(Path bundle, String sha, Path requiredFile, SpecBinding binding, BuildIdentityManifest identity) {
        if (sha == null || !sha.matches("[0-9a-f]{40}")) throw new EvalException(2, "INVALID_ARGUMENTS");
        Map<String,List<String>> required = required(requiredFile);
        var entries = TarGz.read(bundle);
        JsonNode manifest = manifest(entries.get(MANIFEST));
        var allowed = new HashSet<String>();
        for (String task : required.keySet()) allowed.add(summaryPath(task));
        var listed = new HashSet<String>(); manifest.path("files").fieldNames().forEachRemaining(listed::add);
        for (String name : entries.keySet()) {
            if (name.equals(MANIFEST)) continue;
            // 등록 task 의 정제 요약만 허용한다. 미등록 모듈 요약·원시 XML·로그·SPEC 등은 이름이 요약 경로 모양이어도 묶음 전체를 거부한다.
            if (!allowed.contains(name) || !listed.contains(name)) throw new EvalException(4, "CI_EVIDENCE_UNEXPECTED_ENTRY");
            if (!Json.sha(entries.get(name)).equals(manifest.path("files").path(name).asText())) throw new EvalException(4, "CI_EVIDENCE_CONTENT_MISMATCH");
        }
        if (!entries.keySet().containsAll(listed)) throw new EvalException(4, "CI_EVIDENCE_CONTENT_MISMATCH");
        if (!sha.equals(manifest.path("gitSha").asText())) throw new EvalException(4, "CI_EVIDENCE_SHA_MISMATCH");
        if (!manifest.path("worktreeClean").asBoolean()) throw new EvalException(4, "CI_EVIDENCE_DIRTY");
        requireSpec(manifest, binding);
        LeafCatalog catalog = LeafCatalog.load(null);
        for (var task : required.entrySet()) {
            byte[] bytes = entries.get(summaryPath(task.getKey()));
            if (bytes == null) throw new EvalException(4, "REQUIRED_TASK_SUMMARY_MISSING");
            JsonNode evidence = evidence(bytes), summary = evidence.path("summary");
            String module = task.getKey().substring(0, task.getKey().lastIndexOf(':'));
            if (!task.getKey().equals(summary.path("taskPath").asText(null)) || !module.equals(summary.path("module").asText(null)))
                throw new EvalException(4, "CI_EVIDENCE_TASK_MISMATCH");
            if (summary.path("executedTestCount").asInt() < 1) throw new EvalException(4, "NO_TESTS_EXECUTED");
            if (summary.path("skippedTestCount").asInt() != 0 || summary.path("skipped").asInt() != 0) throw new EvalException(4, "TESTS_SKIPPED");
            if (summary.path("failed").asInt() != 0 || summary.path("aborted").asInt() != 0) throw new EvalException(4, "TESTS_FAILED");
            var executed = new HashSet<String>(); summary.path("executedClasses").forEach(node -> executed.add(node.asText()));
            if (!executed.containsAll(task.getValue())) throw new EvalException(4, "REQUIRED_CLASS_NOT_EXECUTED");
            validate(summary, catalog, binding, identity, sha);
            for (JsonNode record : evidence.path("records")) {
                validate(record, catalog, binding, identity, sha);
                if ("FAIL".equals(record.path("result").asText())) throw new EvalException(4, "TESTS_FAILED");
            }
        }
    }

    /** manifest = pack 이 쓰는 고정 필드만, 정규 JSON 바이트(중복 키·추가 공백 0)여야 한다. */
    private static JsonNode manifest(byte[] bytes) {
        if (bytes == null) throw new EvalException(4, "CI_EVIDENCE_BUNDLE_INVALID");
        JsonNode manifest;
        try { manifest = Json.parse(bytes); } catch (RuntimeException e) { throw new EvalException(4, "CI_EVIDENCE_BUNDLE_INVALID"); }
        if (!manifest.isObject() || !Arrays.equals(Json.canonical(manifest), bytes)) throw new EvalException(4, "CI_EVIDENCE_BUNDLE_INVALID");
        var names = new HashSet<String>(); manifest.fieldNames().forEachRemaining(names::add);
        if (!names.equals(MANIFEST_FIELDS) || !FORMAT.equals(manifest.path("formatVersion").asText()) || !manifest.path("worktreeClean").isBoolean()
            || !manifest.path("files").isObject()) throw new EvalException(4, "CI_EVIDENCE_BUNDLE_INVALID");
        for (String text : List.of("gitSha", "dockerServerVersion", "testcontainersVersion", "specSha", "specShaSource", "specInputOrigin"))
            if (!manifest.path(text).isTextual() || manifest.path(text).asText().length() > 128) throw new EvalException(4, "CI_EVIDENCE_BUNDLE_INVALID");
        for (JsonNode file : manifest.path("files")) if (!file.isTextual() || !file.asText().matches("[0-9a-f]{64}")) throw new EvalException(4, "CI_EVIDENCE_BUNDLE_INVALID");
        return manifest;
    }

    /** 요약 파일 = {@code {summary, records}} 만. 레코드는 {@link EvidenceRecord#validate} 의 허용 필드만 가진다. */
    private static JsonNode evidence(byte[] bytes) {
        JsonNode evidence;
        try { evidence = Json.parse(bytes); } catch (RuntimeException e) { throw new EvalException(4, "CI_EVIDENCE_RECORD_INVALID"); }
        var names = new HashSet<String>(); evidence.fieldNames().forEachRemaining(names::add);
        if (!names.equals(Set.of("summary", "records")) || !evidence.path("summary").isObject() || !evidence.path("records").isArray())
            throw new EvalException(4, "CI_EVIDENCE_RECORD_INVALID");
        return evidence;
    }

    private static void validate(JsonNode record, LeafCatalog catalog, SpecBinding binding, BuildIdentityManifest identity, String sha) {
        requireSpec(record, binding);
        try { EvidenceRecord.validate(record, catalog); }
        catch (EvalException e) { throw new EvalException(4, e.getMessage().equals("EVIDENCE_HASH_MISMATCH") ? "CI_EVIDENCE_CONTENT_MISMATCH" : "CI_EVIDENCE_RECORD_INVALID"); }
        identity.requireTaskIdentity(record.path("buildIdentity"), sha);
    }

    private static void requireSpec(JsonNode node, SpecBinding binding) {
        if (!"FILE_HASHED".equals(node.path("specShaSource").asText())) throw new EvalException(3, "SPEC_SHA_SOURCE_INVALID");
        if (!binding.specSha().equals(node.path("specSha").asText())) throw new EvalException(3, "SPEC_BASELINE_MISMATCH");
    }

    private String testcontainersVersion() {
        try {
            for (String line : Files.readAllLines(root.resolve("gradle/libs.versions.toml"), StandardCharsets.UTF_8)) {
                var match = Pattern.compile("^testcontainers\\s*=\\s*\"([^\"]+)\"\\s*$").matcher(line);
                if (match.matches()) return match.group(1);
            }
        } catch (IOException e) { throw new EvalException(4, "TESTCONTAINERS_VERSION_UNKNOWN"); }
        throw new EvalException(4, "TESTCONTAINERS_VERSION_UNKNOWN");
    }

    /** 실제 프로세스 실행(git·docker 한 줄 응답용: 60초·64 KiB). */
    public static Commands processes() { return processes(Duration.ofSeconds(60), 64 * 1024); }

    /**
     * 시간 제한은 시작부터 하나의 마감 시각으로 프로세스 종료와 출력 읽기 완료 모두에 적용된다(출력은 별도 스레드가 상한까지만 읽는다).
     * 시간 초과·출력 초과·출력 읽기 오류·인터럽트·0 이 아닌 종료는 명령 이름만 담은 오류이며(출력 원문 0),
     * 돌아오기 전에 자식과 그 후손에 강제 종료를 보내고, 소유한 출력 스트림을 닫고, 읽기 스레드와 자식의 종료를 제한 시간 안에서 기다린다.
     */
    public static Commands processes(Duration timeout, int maxOutputBytes) {
        return (directory, command) -> {
            String failure = "COMMAND_FAILED:" + command.getFirst();
            long deadline = System.nanoTime() + timeout.toNanos();
            Process process;
            try { process = new ProcessBuilder(command).directory(directory.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
            catch (IOException e) { throw new EvalException(4, failure); }
            var output = new java.io.ByteArrayOutputStream();
            var overflow = new java.util.concurrent.atomic.AtomicBoolean();
            var readFailed = new java.util.concurrent.atomic.AtomicBoolean();
            java.io.InputStream stream = process.getInputStream();
            Thread reader = Thread.ofVirtual().start(() -> {
                try (stream) {
                    byte[] buffer = new byte[8192];
                    for (int count; (count = stream.read(buffer)) >= 0;) {
                        if (output.size() + count > maxOutputBytes) { overflow.set(true); process.destroyForcibly(); return; }
                        output.write(buffer, 0, count);
                    }
                } catch (IOException e) {
                    // 원인(강제 종료로 닫힌 파이프 포함)과 무관하게 출력이 완전하지 않으므로 성공으로 판정하지 않는다.
                    readFailed.set(true);
                }
            });
            try {
                if (!process.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) throw new EvalException(4, failure);
                if (!reader.join(Duration.ofNanos(remaining(deadline)))) throw new EvalException(4, failure);
                if (overflow.get() || readFailed.get() || process.exitValue() != 0) throw new EvalException(4, failure);
                return output.toString(StandardCharsets.UTF_8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EvalException(4, failure);
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                // 읽기 스레드가 막혀 있으면 소유한 스트림을 닫아 풀어 준다. 이 경로는 이미 성공(EOF 로 닫힘)이거나 실패 판정이 끝난 뒤다.
                try { stream.close(); } catch (IOException alreadyFailing) { readFailed.set(true); }
                // 정리 대기 동안만 인터럽트 표시를 내려 두고 끝나면 그대로 되돌린다.
                boolean interrupted = Thread.interrupted();
                try { reader.join(Duration.ofSeconds(10)); process.waitFor(10, TimeUnit.SECONDS); }
                catch (InterruptedException e) { interrupted = true; }
                finally { if (interrupted) Thread.currentThread().interrupt(); }
            }
        };
    }

    private static long remaining(long deadline) { return Math.max(0, deadline - System.nanoTime()); }
}
