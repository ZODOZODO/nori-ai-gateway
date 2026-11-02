package com.nori.ai.gateway.eval.f0;

import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * harnessDigest = eval/f0/**(results·chat-template 디렉터리와 PREREG.md 자신 제외 — 자기 해시 고리 0)·골든셋·Registry fixture·
 * gateway-eval f0 패키지 소스의 정렬 경로 + 바이트 SHA-256 목록을 RFC 8785 JSON 으로 만든 SHA-256.
 */
public final class HarnessDigest {
    public static final List<String> ROOTS = List.of("eval/f0", "eval/golden/tc-f0-v1.jsonl", "eval/golden/tc-registry-fixture-v1.json", "gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0");
    private HarnessDigest() {}
    public static boolean included(String path) {
        if (path.startsWith("eval/f0/results/") || path.startsWith("eval/f0/chat-template/") || path.equals("eval/f0/PREREG.md")) return false;
        for (String root : ROOTS) if (path.equals(root) || path.startsWith(root + "/")) return true;
        return false;
    }
    private static final String SOURCES = "gateway-eval/src/main/java/com/nori/ai/gateway/eval/f0";
    public static String compute(Path root, Prereg.Document document) {
        if (!document.versioned()) return compute(root);
        SortedMap<String,String> selected = new TreeMap<>();
        document.inputs().keySet().forEach(file -> {
            Path path = root.resolve(file);
            if (Files.isRegularFile(path)) selected.put(file, Json.sha(Json.bytes(path)));
        });
        files(root).forEach((file, sha) -> { if (file.startsWith(SOURCES + "/")) selected.put(file, sha); });
        return digest(selected);
    }
    public static String compute(Path repositoryRoot) { return digest(files(repositoryRoot)); }
    public static SortedMap<String,String> files(Path repositoryRoot) {
        SortedMap<String,String> files = new TreeMap<>();
        Path base = repositoryRoot.toAbsolutePath().normalize();
        for (String root : ROOTS) {
            Path start = base.resolve(root);
            if (!Files.exists(start)) continue;
            try (var walk = Files.walk(start)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String relative = base.relativize(file).toString().replace('\\', '/');
                    if (included(relative)) files.put(relative, Json.sha(Json.bytes(file)));
                }
            } catch (IOException e) { throw new EvalException(5, "HARNESS_READ_FAILED"); }
        }
        return files;
    }
    public static String digest(SortedMap<String,String> files) {
        List<List<String>> pairs = new ArrayList<>(); files.forEach((path, sha) -> pairs.add(List.of(path, sha)));
        return Json.sha(Json.canonical(Json.MAPPER.valueToTree(pairs)));
    }
    /** 등록 commit 트리에서 같은 정의로 다시 계산합니다(git 읽기 전용 명령만). */
    public static String computeAtCommit(GitReader git, Path repositoryRoot, String commit) { return computeAtCommit(git, repositoryRoot, commit, null); }
    public static String computeAtCommit(GitReader git, Path repositoryRoot, String commit, Prereg.Document document) {
        List<String> args = new ArrayList<>(List.of("ls-tree", "-r", "-z", "--name-only", commit, "--")); args.addAll(document != null && document.versioned() ? java.util.stream.Stream.concat(document.inputs().keySet().stream(), java.util.stream.Stream.of(SOURCES)).toList() : ROOTS);
        GitReader.Result listing = git.run(repositoryRoot, args);
        if (listing.exit() != 0) throw new EvalException(2, "PREREG_REGISTERED_COMMIT_MISMATCH");
        SortedMap<String,String> files = new TreeMap<>();
        for (String path : new String(listing.stdout(), StandardCharsets.UTF_8).split("\0")) {
            if (path.isEmpty() || (document != null && document.versioned() ? !document.inputs().containsKey(path) && !path.startsWith(SOURCES + "/") : !included(path))) continue;
            GitReader.Result blob = git.run(repositoryRoot, List.of("cat-file", "blob", commit + ":" + path));
            if (blob.exit() != 0) throw new EvalException(2, "PREREG_REGISTERED_COMMIT_MISMATCH");
            files.put(path, Json.sha(blob.stdout()));
        }
        return digest(files);
    }
}
