package com.nori.ai.gateway.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * 수용 Job 의 첫 단계(M-10): 마운트된 gzip SPEC 을 스트리밍으로 풀며 원본 바이트의 SHA-256 만 계산한다(디스크 쓰기·본문 로그 0).
 * 기대값은 stage gate 가 기준선 마지막 항목에서 넘긴 {@code AIGW_EXPECTED_SPEC_SHA256} 이다.
 */
public final class SpecInputVerifier {
    public static final class Failure extends Exception {
        private final String code;
        Failure(String code) { super(code); this.code = code; }
        public String code() { return code; }
    }
    /** eval {@code SpecInputPackage} 과 같은 입력 프로토콜 상한(압축 2 MiB·해제 4 MiB). 넘으면 사용할 수 없는 입력이다. */
    static final long MAX_COMPRESSED = 2_097_152L, MAX_EXPANDED = 4_194_304L;
    private SpecInputVerifier() {}

    /** 일치하면 계산한 SHA 를 돌려준다. 파일·기대값 부재·해제 실패·상한 초과 = SPEC_INPUT_MISSING, 불일치 = SPEC_BASELINE_MISMATCH. */
    public static String verify(Path gzipFile, String expectedSha256) throws Failure {
        if (gzipFile == null || expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}") || !Files.isRegularFile(gzipFile)) throw new Failure("SPEC_INPUT_MISSING");
        String actual;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (Files.size(gzipFile) > MAX_COMPRESSED) throw new Failure("SPEC_INPUT_MISSING");
            try (InputStream in = new DigestInputStream(new GZIPInputStream(Files.newInputStream(gzipFile)), digest)) {
                // 상한을 넘는 순간 멈춘다(압축 폭탄을 끝까지 풀지 않는다). 본문은 메모리·디스크에 남기지 않는다.
                byte[] buffer = new byte[8192];
                long total = 0;
                for (int count; (count = in.read(buffer)) >= 0;) if ((total += count) > MAX_EXPANDED) throw new Failure("SPEC_INPUT_MISSING");
            }
            actual = HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) { throw new Failure("SPEC_INPUT_MISSING"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 을 사용할 수 없습니다"); }
        if (!actual.equals(expectedSha256)) throw new Failure("SPEC_BASELINE_MISMATCH");
        return actual;
    }
}
