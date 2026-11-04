package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * 시험 전용 계수기 프로세스: count_answer_tokens_v4.py 와 같은 줄 규약(READY 한 줄 → 요청 한 줄마다 응답 한 줄)을 쓰되 토큰 대신 코드포인트를 셉니다
 * (공식 tokenizer 계수 정확성은 Python 시험이 실제 pin 으로 확인). 요청 줄에 비 ASCII 바이트가 있으면 exit 9(이스케이프 송신 계약 위반),
 * `--fail-after N` 이면 N 번째 요청 뒤 응답 없이 끝납니다(측정 중 계수기 사망 재현). 아무 파일도 쓰지 않습니다.
 */
public final class FakeAnswerCounter {
    public static void main(String[] args) throws IOException {
        String ready = args[0]; int failAfter = Integer.parseInt(args[1]);
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.US_ASCII);
        if (ready.equals("NONE")) System.exit(4);
        out.println("READY " + ready + " fake");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.ISO_8859_1));
        int served = 0;
        for (String line; (line = in.readLine()) != null; ) {
            if (line.chars().anyMatch(c -> c > 0x7f)) System.exit(9);
            if (failAfter >= 0 && served >= failAfter) System.exit(5);
            JsonNode request = Json.MAPPER.readTree(line);
            out.println("[" + codepoints(request.get(0)) + "," + codepoints(request.get(1)) + "]");
            served++;
        }
    }
    private static String codepoints(JsonNode text) { return text.isNull() ? "null" : String.valueOf(text.textValue().codePointCount(0, text.textValue().length())); }

    /** 같은 JVM·시험 classpath 로 이 프로세스를 띄우는 명령. */
    static List<String> command(String ready, int failAfter) {
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"), FakeAnswerCounter.class.getName(), ready, String.valueOf(failAfter));
    }
    /** 측정 세션용 launcher: 세션 tokenizer pin SHA 를 READY 로 돌려줍니다. */
    static AnswerTokenCounter.Launcher launcher(int failAfter, List<List<String>> launched) {
        return (root, env, profile, sha) -> { List<String> command = command(sha, failAfter); launched.add(command); return AnswerTokenCounter.start(command, sha); };
    }
    private FakeAnswerCounter() {}
}
