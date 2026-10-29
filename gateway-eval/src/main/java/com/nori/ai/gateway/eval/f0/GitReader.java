package com.nori.ai.gateway.eval.f0;

import com.nori.ai.gateway.eval.EvalException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 사전등록 commit 확인용 git 읽기. 쓰기 명령(commit·checkout·reset·stash 등)은 허용 목록에 없어 실행할 수 없다. */
public interface GitReader {
    record Result(int exit, byte[] stdout) {}
    Set<String> READ_ONLY = Set.of("rev-parse", "status", "log", "merge-base", "ls-tree", "cat-file", "ls-files");
    Result run(Path repositoryRoot, List<String> args);

    static GitReader process() {
        return (root, args) -> {
            if (args.isEmpty() || !READ_ONLY.contains(args.getFirst())) throw new EvalException(2, "GIT_COMMAND_FORBIDDEN");
            List<String> command = new ArrayList<>(List.of("git", "-C", root.toAbsolutePath().normalize().toString(), "-c", "core.quotepath=off")); command.addAll(args);
            try {
                Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).redirectInput(ProcessBuilder.Redirect.from(nullFile())).start();
                byte[] out;
                try (InputStream input = process.getInputStream()) { out = input.readAllBytes(); }
                if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new EvalException(2, "GIT_UNAVAILABLE"); }
                return new Result(process.exitValue(), out);
            } catch (IOException e) { return new Result(127, new byte[0]); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new EvalException(2, "GIT_UNAVAILABLE"); }
        };
    }
    private static java.io.File nullFile() { return new java.io.File(System.getProperty("os.name", "").startsWith("Windows") ? "NUL" : "/dev/null"); }
}
