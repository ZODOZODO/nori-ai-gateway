package com.nori.ai.gateway.tcmock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class PdgCanonical {
    private PdgCanonical() {}
    public static String compute(String catalogRevision, List<String> perms) {
        if (catalogRevision == null || catalogRevision.isEmpty() || catalogRevision.indexOf('\r') >= 0 || catalogRevision.indexOf('\n') >= 0 || perms == null)
            throw new IllegalArgumentException("권한 카탈로그 입력이 잘못되었습니다");
        // UTF-16 자연 순서는 보충 평면 문자의 코드포인트 순서를 보장하지 않습니다.
        var sorted = new TreeSet<String>((left, right) -> Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray()));
        for (String perm : perms) {
            if (perm == null || perm.isEmpty() || perm.codePoints().anyMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85))
                throw new IllegalArgumentException("권한 코드 입력이 잘못되었습니다");
            sorted.add(perm);
        }
        String serialized = catalogRevision + "\n" + String.join("\n", sorted);
        try {
            var bytes = StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(serialized));
            byte[] input = new byte[bytes.remaining()]; bytes.get(input);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (java.nio.charset.CharacterCodingException failure) {
            throw new IllegalArgumentException("권한 입력의 문자 형식이 잘못되었습니다");
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("권한 해시를 계산할 수 없습니다"); }
    }
}
