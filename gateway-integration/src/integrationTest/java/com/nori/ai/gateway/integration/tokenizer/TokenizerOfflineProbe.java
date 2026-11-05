package com.nori.ai.gateway.integration.tokenizer;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class TokenizerOfflineProbe {
    private TokenizerOfflineProbe() {}
    public static void main(String[] args) throws Exception {
        if (!Boolean.getBoolean("ai.djl.offline")) throw new IllegalStateException("오프라인 설정이 필요합니다.");
        Path file = Path.of(args[0]);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        if (!sha.equals(args[1])) throw new IllegalStateException("고정 tokenizer 해시가 다릅니다.");
        try (var tokenizer = HuggingFaceTokenizer.newInstance(file)) {
            if (tokenizer.encode("설비 상태를 조회해 주세요.").getIds().length == 0) throw new IllegalStateException("토큰 생성에 실패했습니다.");
        }
        System.out.println("P04_TOKENIZER_OFFLINE_OK");
    }
}
