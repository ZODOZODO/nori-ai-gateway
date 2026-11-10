package com.nori.ai.gateway.integration.gitlab;

import com.nori.ai.gateway.integration.source.SourceHttpClient;
import java.nio.file.*;
import java.io.IOException;

/** 설정에는 토큰 값 대신 읽기 전용 Secret 파일 경로만 보관합니다. */
public final class GitLabReadTokenFile {
    private final Path file;
    public GitLabReadTokenFile(Path file) { this.file = java.util.Objects.requireNonNull(file); }
    String read() {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4096) throw new SourceHttpClient.Failure(false, false);
            String token = Files.readString(file).strip();
            if (token.isBlank() || !token.matches("[\\x21-\\x7e]+")) throw new SourceHttpClient.Failure(false, false);
            return token;
        } catch (IOException | SecurityException failure) { throw new SourceHttpClient.Failure(false, false); }
    }
    @Override public String toString() { return "GitLabReadTokenFile[보호됨]"; }
}
