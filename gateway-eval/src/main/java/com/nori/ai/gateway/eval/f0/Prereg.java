package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * PREREG.md 의 기계 판독 블록과 사전등록 검사.
 * 등록 commit(registeredCommit — 골든셋·fixture·프롬프트·분석 코드가 들어간 commit)은 PREREG 문서에 적고, PREREG 문서 자신은 그 뒤 별도 commit 으로 올린다.
 * PREREG 는 자기 해시를 담지 않는다(자기 해시 고리 0). 측정 전 아래가 모두 성립하지 않으면 측정 명령은 시작하지 않는다.
 */
public final class Prereg {
    public static final String DEFAULT_PATH = "eval/f0/PREREG.md";
    public static final List<String> REQUIRED_INPUTS = List.of("eval/golden/tc-f0-v1.jsonl", "eval/golden/tc-registry-fixture-v1.json", "eval/f0/prompts/classify-v1.txt", "eval/f0/prompts/plan-v1.txt",
        "eval/f0/arg-limits-v1.json", "eval/f0/output-boundary-cases.jsonl", "eval/f0/usage-probe-input-v1.jsonl", "eval/f0/token-golden-input-v1.jsonl", "eval/f0/chat-renderers-v1.json");
    public record Document(JsonNode block, String registeredCommit, Map<String,String> inputs, String harnessDigest, JsonNode parameters) {
        public int repeats() { return parameters.path("repeats").asInt(); }
        public String generationModel() { return parameters.path("generationModel").asText(); }
        public String embeddingModel() { return parameters.path("embeddingModel").asText(); }
        public java.time.Duration requestTimeout() { return java.time.Duration.ofMillis(parameters.path("requestTimeoutMs").asLong()); }
        public String parameterManifestSha256() { return Json.sha(Json.canonical(parameters)); }
    }
    public record State(Document document, String preregCommit, Instant preregCommitTime) {}
    private Prereg() {}

    public static Document parse(Path path) {
        String text = new String(Json.bytes(path), StandardCharsets.UTF_8);
        int start = text.indexOf("```json\n"), end = start < 0 ? -1 : text.indexOf("\n```", start + 8);
        if (start < 0 || end < 0 || text.indexOf("```json\n", end) >= 0) throw new EvalException(2, "PREREG_INVALID");
        JsonNode block = Json.parse(text.substring(start + 8, end + 1).getBytes(StandardCharsets.UTF_8));
        if (!block.path("schema").asText().equals("f0-prereg-v1")) throw new EvalException(2, "PREREG_INVALID");
        Map<String,String> inputs = new LinkedHashMap<>();
        for (JsonNode input : block.path("inputs")) {
            String file = input.path("path").asText(""), sha = input.path("sha256").asText("");
            if (!sha.matches("[0-9a-f]{64}") || !file.matches("[A-Za-z0-9_./-]+") || file.contains("..") || inputs.put(file, sha) != null) throw new EvalException(2, "PREREG_INVALID");
        }
        if (!inputs.keySet().containsAll(REQUIRED_INPUTS) || !block.path("harnessDigest").asText("").matches("[0-9a-f]{64}")) throw new EvalException(2, "PREREG_INVALID");
        JsonNode p = block.path("parameters");
        if (!p.path("repeats").isInt() || p.path("repeats").asInt() < 1 || !p.path("temperature").isNumber() || p.path("temperature").asDouble() != 0 || p.path("maxTokens").path("classify").asInt() != 128
            || p.path("maxTokens").path("plan").asInt() != 160 || p.path("requestTimeoutMs").asLong() < 1 || !p.path("generationModel").asText().equals(F0RunnerCli.GENERATION_MODEL) || !p.path("embeddingModel").asText().equals(F0RunnerCli.EMBEDDING_MODEL)
            || !p.path("promptSha256").path("classify").asText().equals(inputs.get("eval/f0/prompts/classify-v1.txt")) || !p.path("promptSha256").path("plan").asText().equals(inputs.get("eval/f0/prompts/plan-v1.txt"))) throw new EvalException(2, "PREREG_INVALID");
        JsonNode commit = block.path("registeredCommit");
        String registered = commit.isNull() || commit.isMissingNode() ? null : commit.asText();
        if (registered != null && !registered.matches("[0-9a-f]{40}")) throw new EvalException(2, "PREREG_INVALID");
        return new Document(block, registered, Collections.unmodifiableMap(inputs), block.path("harnessDigest").asText(), p.deepCopy());
    }
    /** 현재 작업 트리 바이트가 등록값과 같은지(커밋 여부와 무관한 로컬 검사). */
    public static List<String> localMismatches(Path root, Document document) {
        List<String> mismatches = new ArrayList<>();
        document.inputs().forEach((file, sha) -> { Path p = root.resolve(file); if (!Files.isRegularFile(p) || !Json.sha(Json.bytes(p)).equals(sha)) mismatches.add(file); });
        if (!HarnessDigest.compute(root).equals(document.harnessDigest())) mismatches.add("harnessDigest");
        return mismatches;
    }
    /** 측정 전 필수 검사: 등록 commit 존재·PREREG 별도 commit·순서·등록 바이트 일치·작업 트리 clean. 하나라도 아니면 exit 2. */
    public static State verify(Path root, Path preregPath, GitReader git) {
        Document document = parse(preregPath);
        if (document.registeredCommit() == null) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        if (!localMismatches(root, document).isEmpty()) throw new EvalException(2, "PREREG_INPUT_CHANGED");
        GitReader.Result inside = git.run(root, List.of("rev-parse", "--is-inside-work-tree"));
        if (inside.exit() != 0 || !text(inside).equals("true")) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        String relative = root.toAbsolutePath().normalize().relativize(preregPath.toAbsolutePath().normalize()).toString().replace('\\', '/');
        List<String> statusArgs = new ArrayList<>(List.of("status", "--porcelain", "--untracked-files=all", "--", relative)); statusArgs.addAll(document.inputs().keySet()); statusArgs.addAll(HarnessDigest.ROOTS);
        GitReader.Result status = git.run(root, statusArgs);
        if (status.exit() != 0) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        for (String line : text(status).split("\n")) {
            if (line.isBlank()) continue;
            String file = line.length() > 3 ? line.substring(3).replace("\"", "") : line;
            if (!file.startsWith("eval/f0/results/") && !file.startsWith("eval/f0/chat-template/")) throw new EvalException(2, "PREREG_WORKTREE_DIRTY");
        }
        GitReader.Result log = git.run(root, List.of("log", "-1", "--format=%H %ct", "--", relative));
        String[] head = text(log).split(" ");
        if (log.exit() != 0 || head.length != 2 || !head[0].matches("[0-9a-f]{40}")) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        String preregCommit = head[0];
        if (preregCommit.equals(document.registeredCommit()) || git.run(root, List.of("merge-base", "--is-ancestor", document.registeredCommit(), preregCommit)).exit() != 0) throw new EvalException(2, "PREREG_COMMIT_ORDER_INVALID");
        for (var input : document.inputs().entrySet()) {
            GitReader.Result blob = git.run(root, List.of("cat-file", "blob", document.registeredCommit() + ":" + input.getKey()));
            if (blob.exit() != 0 || !Json.sha(blob.stdout()).equals(input.getValue())) throw new EvalException(2, "PREREG_REGISTERED_COMMIT_MISMATCH");
        }
        if (!HarnessDigest.computeAtCommit(git, root, document.registeredCommit()).equals(document.harnessDigest())) throw new EvalException(2, "PREREG_REGISTERED_COMMIT_MISMATCH");
        Instant committed;
        try { committed = Instant.ofEpochSecond(Long.parseLong(head[1])); } catch (NumberFormatException e) { throw new EvalException(2, "PREREG_NOT_COMMITTED"); }
        return new State(document, preregCommit, committed);
    }
    private static String text(GitReader.Result result) { return new String(result.stdout(), StandardCharsets.UTF_8).strip(); }
}
