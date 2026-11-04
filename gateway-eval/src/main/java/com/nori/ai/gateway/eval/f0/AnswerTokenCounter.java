package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * v4 답변 토큰 계수 방법 (b) — 측정 중 서버 응답의 답변 content 와 사고문(분리 필드)을 등록 tokenizer pin 으로 로컬 계수합니다(SPEC §14.5 열린 항목 ⓒ,
 * 2026-11-04 사용자 결정). 계수는 입력 audit 과 같은 Python tokenizers 엔진(eval/py/count_answer_tokens_v4.py)을 측정 세션당 한 번 띄운 상주 프로세스가 하며,
 * 문자열은 stdin 한 줄(JSON, 비 ASCII 는 \\u 이스케이프)로만 넘기고 stdout 한 줄의 개수만 받습니다. 파일·로그·예외 메시지에 원문을 남기지 않고,
 * 프로세스 stderr 는 버립니다(실패는 고정 코드 + exit 코드만).
 */
public final class AnswerTokenCounter {
    /** 등록 inputs 에 반드시 담는 계수기(answerTokenMethod=LOCAL_TOKENIZER_PIN_V1 일 때). */
    public static final String SCRIPT = "eval/py/count_answer_tokens_v4.py";
    /** 측정 PC 의 Python 3.12(eval/py/requirements.txt 설치 venv) 실행 파일 절대 경로. */
    public static final String PYTHON_ENV = "AIGW_PYTHON";
    private static final ObjectMapper ASCII = new ObjectMapper().enable(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature());
    /** 측정 세션이 계수기를 띄우는 방법. 운영 경로는 python 이고, 시험만 같은 줄 규약의 다른 프로세스를 넣습니다. */
    public interface Launcher { AnswerTokenCounter start(Path root, Map<String,String> env, Prereg.RuntimeProfile profile, String tokenizerSha256); }

    private final Process process;
    private final BufferedWriter requests;
    private final BufferedReader counts;

    private AnswerTokenCounter(Process process) {
        this.process = process;
        this.requests = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.US_ASCII));
        this.counts = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII));
    }
    /** 운영 계수기: `$AIGW_PYTHON -I -B eval/py/count_answer_tokens_v4.py --tokenizer <pin 파일 쌍>`(등록 profile 의 tokenizer, -B = bytecode 캐시도 쓰지 않음). */
    public static AnswerTokenCounter python(Path root, Map<String,String> env, Prereg.RuntimeProfile profile, String tokenizerSha256) {
        String python = env.get(PYTHON_ENV);
        if (python == null || python.isBlank()) throw new EvalException(2, "AIGW_PYTHON_MISSING");
        return start(List.of(python, "-I", "-B", root.resolve(SCRIPT).toString(), "--tokenizer", root.resolve(profile.tokenizerFile()).toString(),
            "--expect-sha256-file", root.resolve(profile.tokenizerPin()).toString()), tokenizerSha256);
    }
    /** 계수기 프로세스를 띄우고 READY 줄의 tokenizer SHA 가 세션 tokenizer pin 과 같을 때만 돌려줍니다(추론 호출 전, 실패 = exit 2). */
    static AnswerTokenCounter start(List<String> command, String tokenizerSha256) {
        Process process;
        try { process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
        catch (IOException e) { throw new EvalException(2, "ANSWER_COUNTER_UNAVAILABLE"); }
        AnswerTokenCounter counter = new AnswerTokenCounter(process);
        String ready;
        try { ready = counter.counts.readLine(); } catch (IOException e) { ready = null; }
        String[] parts = ready == null ? new String[0] : ready.split(" ");
        if (parts.length != 3 || !parts[0].equals("READY")) { counter.close(); throw new EvalException(2, "ANSWER_COUNTER_UNAVAILABLE_EXIT_" + exitCode(process)); }
        if (!parts[1].equals(tokenizerSha256)) { counter.close(); throw new EvalException(2, "ANSWER_COUNTER_TOKENIZER_MISMATCH"); }
        return counter;
    }
    /**
     * 답변·사고문 한 쌍의 pin 토큰 수. null 문자열·짝 없는 surrogate 는 그 항목만 null(셀 수 없음)입니다. 프로세스가 죽거나 응답 형식이 다르면
     * 측정 중 오류(exit 5 — 결과는 complete=false 로 보존)입니다.
     */
    public synchronized Counts count(String answer, String thinking) {
        JsonNode reply;
        try {
            requests.write(ASCII.writeValueAsString(Arrays.asList(answer, thinking))); requests.write('\n'); requests.flush();
            String line = counts.readLine();
            if (line == null) throw new EvalException(5, "ANSWER_COUNTER_FAILED");
            reply = Json.MAPPER.readTree(line);
        } catch (IOException e) { throw new EvalException(5, "ANSWER_COUNTER_FAILED"); }
        if (reply == null || !reply.isArray() || reply.size() != 2 || !countOrNull(reply.get(0)) || !countOrNull(reply.get(1))) throw new EvalException(5, "ANSWER_COUNTER_FAILED");
        return new Counts(reply.get(0).isNull() ? null : reply.get(0).intValue(), reply.get(1).isNull() ? null : reply.get(1).intValue());
    }
    /** 측정 명령이 끝나면 stdin 을 닫아 계수기를 끝냅니다(JVM 종료 때도 stdin EOF 로 끝남). */
    void close() {
        try { requests.close(); } catch (IOException ignored) { /* 이미 끝난 프로세스의 파이프 */ }
        process.destroy();
    }
    private static boolean countOrNull(JsonNode node) { return node.isNull() || OllamaChatClient.isCount(node); }
    private static String exitCode(Process process) {
        try { return process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) ? String.valueOf(process.exitValue()) : "NONE"; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return "NONE"; }
    }
    /** 답변 content·사고문의 pin 토큰 수(셀 수 없으면 null). */
    public record Counts(Integer answerTokens, Integer thinkingTokens) {}
}
