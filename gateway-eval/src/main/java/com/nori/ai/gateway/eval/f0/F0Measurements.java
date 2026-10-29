package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * F0 측정 명령 본체. 모든 결과는 원문 대신 SHA·정제 필드만 쓰며, 측정 중 endpoint 오류는 지금까지의 결과를 complete=false 로 보존한 뒤 exit 5.
 * 실패·timeout 회차도 결과 행으로 남겨 분모를 유지한다.
 */
public final class F0Measurements {
    private F0Measurements() {}

    /** f0 run: 회차 r 에서 모든 문항을 고정 순서로 1회씩(라운드로빈), 그다음 회차. */
    public static void run(MeasurementSession session, String path, GoldenSet golden, RegistryFixture fixture, MessageAssembler assembler, int repeats, String runId, Path out) {
        ObjectNode result = header(runId, repeats); result.put("path", path); result.put("questionCount", golden.questions().size());
        result.put("goldenSha256", Json.sha(golden.bytes())); result.put("fixtureSha256", Json.sha(fixture.bytes())); result.put("promptSha256", Json.sha(assembler.promptBytes())); result.put("maxTokens", assembler.maxTokens());
        ArrayNode attempts = result.putArray("attempts"); Path file = out.resolve("f0-results.json");
        String model = session.prereg().document().generationModel();
        guarded(result, file, () -> {
            for (int round = 1; round <= repeats; round++) for (GoldenSet.Question q : golden.questions()) {
                session.ensureResident();
                ClassificationJudge.Outcome outcome = ClassificationJudge.execute(m -> session.client().chat(session.verified(), model, m, assembler.maxTokens()), assembler.messages(q.question(), q.context()), assembler.maxTokens(), assembler.kind());
                ObjectNode row = outcomeRow(outcome); row.put("questionId", q.id()); row.put("round", round);
                row.set("predicted", outcome.verdict() != null && outcome.verdict().valid() ? PlanJudge.sanitize(outcome.verdict(), fixture, assembler.kind()) : Json.MAPPER.nullNode());
                attempts.add(row); Json.write(file, result);
            }
        });
    }
    /** f0 boundary-gen: Path B 와 같은 분류 메시지 조립으로 케이스당 R 회 생성, oracle 은 회차별(완결 JSON·스키마·인자 코드포인트 정확 일치·≤128). */
    public static void boundaryGen(MeasurementSession session, List<JsonNode> cases, MessageAssembler assembler, int repeats, String runId, Path out) {
        if (assembler.kind() != MessageAssembler.Kind.CLASSIFY) throw new EvalException(2, "PROMPT_INVALID");
        ObjectNode result = header(runId, repeats); ArrayNode rows = result.putArray("cases"); Path file = out.resolve("boundary-results.json");
        Map<String,ObjectNode> byCase = new LinkedHashMap<>();
        for (JsonNode c : cases) { ObjectNode row = rows.addObject(); row.put("caseId", c.path("caseId").asText()); row.put("kind", c.path("kind").asText()); row.putArray("attempts"); row.put("passRounds", 0); row.put("totalRounds", 0); byCase.put(c.path("caseId").asText(), row); }
        String model = session.prereg().document().generationModel();
        guarded(result, file, () -> {
            for (int round = 1; round <= repeats; round++) for (JsonNode c : cases) {
                session.ensureResident();
                ClassificationJudge.Outcome outcome = ClassificationJudge.execute(m -> session.client().chat(session.verified(), model, m, 128), assembler.messages(c.path("question").asText(), c.path("context")), 128, MessageAssembler.Kind.CLASSIFY);
                boolean exact = argsExact(outcome.verdict(), c.path("expectedOutput"));
                ObjectNode attempt = Json.MAPPER.createObjectNode();
                attempt.put("round", round); attempt.put("status", outcome.status()); attempt.put("retried", outcome.retried());
                attempt.put("finishReason", outcome.finishReasons().isEmpty() ? null : outcome.finishReasons().getLast());
                if (outcome.completionTokens().isEmpty()) attempt.putNull("completionTokens"); else attempt.put("completionTokens", outcome.completionTokens().getLast());
                attempt.put("schemaValid", outcome.verdict() != null && outcome.verdict().valid()); attempt.put("argsExact", exact); attempt.put("latencyMs", outcome.latencyMs());
                attempt.put("outputSha256", outcome.outputSha256().isEmpty() ? null : outcome.outputSha256().getLast());
                ObjectNode row = byCase.get(c.path("caseId").asText());
                ((ArrayNode)row.get("attempts")).add(attempt);
                row.put("totalRounds", row.path("totalRounds").asInt() + 1);
                if (outcome.status().equals("OK") && exact) row.put("passRounds", row.path("passRounds").asInt() + 1);
                Json.write(file, result);
            }
        });
    }
    static boolean argsExact(ClassificationJudge.Verdict verdict, JsonNode expected) {
        if (verdict == null || !verdict.valid() || !expected.path("t").asText().equals(verdict.t())) return false;
        for (String field : GoldenSet.ARG_FIELDS) {
            JsonNode e = expected.path(field); String actual = verdict.args().get(field);
            if (e.isNull() ? actual != null : !e.asText().equals(actual)) return false;
        }
        return true;
    }
    /** f0 gguf-count: 각 계수 직전 flush(첫 토큰부터 다른 raw 프롬프트) 후 기대 JSON 의 prompt_eval_count. 기대 출력 문자열의 토큰 수이며 생성 증거가 아니다. */
    public static void ggufCount(MeasurementSession session, List<JsonNode> cases, String runId, Path out) {
        ObjectNode result = header(runId, 1); ArrayNode rows = result.putArray("rows"), calibration = result.putArray("calibration"); Path file = out.resolve("gguf-count.json");
        String model = session.prereg().document().generationModel(); int[] flush = {0};
        guarded(result, file, () -> {
            for (String[] c : CALIBRATION) {
                ObjectNode row = calibration.addObject(); row.put("id", c[0]); row.put("ggufTokens", count(session, model, c[1], flush)); row.put("expectedTokens", Integer.parseInt(c[2]));
                Json.write(file, result);
            }
            for (JsonNode c : cases) {
                ObjectNode row = rows.addObject(); row.put("caseId", c.path("caseId").asText()); row.put("kind", c.path("kind").asText()); row.put("ggufTokens", count(session, model, c.path("expectedJson").asText(), flush));
                Json.write(file, result);
            }
        });
    }
    /** SPEC §14.5 과거 검증 예시(공식 57토큰, q 생략 53토큰) — 같은 방식으로 계수해 보정 확인. */
    public static final String[][] CALIBRATION = {
        {"SPEC-14.5-OLD-EXAMPLE", "{\"t\":\"INC\",\"eqp\":\"PHOTO_001\",\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-03T23:59\",\"q\":null}", "57"},
        {"SPEC-14.5-OLD-EXAMPLE-NO-Q", "{\"t\":\"INC\",\"eqp\":\"PHOTO_001\",\"from\":\"2026-10-03T00:00\",\"to\":\"2026-10-03T23:59\"}", "53"}};
    private static int count(MeasurementSession session, String model, String text, int[] flush) {
        session.ensureResident();
        session.client().generateRawPromptEvalCount(session.verified(), model, "flush-" + (++flush[0]));
        return session.client().generateRawPromptEvalCount(session.verified(), model, text);
    }
    /** f0 chat-template: /api/show 의 template 필드만 UTF-8 LF 로 저장, SHA·모델 digest 기록(그 밖 응답 필드 저장 0). */
    public static void chatTemplate(MeasurementSession session, Path out, String fileStem) {
        String template = session.template();
        if (template.indexOf('\r') >= 0) throw new EvalException(5, "TEMPLATE_NOT_LF");
        byte[] bytes = template.getBytes(StandardCharsets.UTF_8);
        MeasurementSession.writeNew(out.resolve(fileStem + ".template"), bytes);
        MeasurementSession.writeNew(out.resolve(fileStem + ".template.sha256"), (Json.sha(bytes) + "\n").getBytes(StandardCharsets.UTF_8));
        MeasurementSession.writeNew(out.resolve("model-digest.txt"), (session.runtime().modelDigest() + "\n").getBytes(StandardCharsets.UTF_8));
    }
    /** f0 usage-probe: flush → U1 cold → U1 warm → U2 shared → flush → U2 cold 를 chat usage 와 generate raw 두 지표에 각각 독립 순서로 실행. */
    public static void usageProbe(MeasurementSession session, Map<String,List<Map<String,String>>> inputs, ChatRenderers.Renderer renderer, String runId, Path out) {
        ObjectNode result = header(runId, 1); ArrayNode calls = result.putArray("calls");
        String model = session.prereg().document().generationModel(); int[] flush = {0};
        String[][] sequence = {{"FLUSH", ""}, {"U1", "cold"}, {"U1", "warm"}, {"U2", "shared"}, {"FLUSH", ""}, {"U2", "cold"}};
        Map<String,ObjectNode> byCall = new LinkedHashMap<>();
        for (String[] step : sequence) if (!step[0].equals("FLUSH")) { ObjectNode call = calls.addObject(); call.put("id", step[0]); call.put("phase", step[1]); call.putNull("promptTokens"); call.putNull("promptEvalCount"); byCall.put(step[0] + "/" + step[1], call); }
        guarded(result, out, () -> {
            for (String metric : List.of("promptTokens", "promptEvalCount")) for (String[] step : sequence) {
                session.ensureResident();
                if (step[0].equals("FLUSH")) { session.client().generateRawPromptEvalCount(session.verified(), model, "flush-" + (++flush[0])); continue; }
                List<Map<String,String>> messages = inputs.get(step[0]);
                int value = metric.equals("promptTokens") ? session.client().chat(session.verified(), model, messages, 1).promptTokens() : session.client().generateRawPromptEvalCount(session.verified(), model, renderer.render(messages));
                byCall.get(step[0] + "/" + step[1]).put(metric, value);
                Json.write(out, result);
            }
            ObjectNode equal = result.putObject("equalOnCacheHit");
            for (String metric : List.of("promptTokens", "promptEvalCount")) {
                boolean same = byCall.get("U1/warm").path(metric).equals(byCall.get("U1/cold").path(metric)) && byCall.get("U2/shared").path(metric).equals(byCall.get("U2/cold").path(metric));
                equal.put(metric.equals("promptTokens") ? "usage" : "evalCount", same);
            }
        });
    }
    /** f0 token-golden: 행마다 flush 뒤 chat max_tokens:1 의 usage.prompt_tokens(cold 값만). */
    public static void tokenGolden(MeasurementSession session, List<Map.Entry<String,List<Map<String,String>>>> inputs, Path out) {
        String model = session.prereg().document().generationModel(); int flush = 0; StringBuilder lines = new StringBuilder();
        // 측정 중 오류면 완료 파일을 만들지 않고 지금까지의 행을 .partial 로 보존합니다(exit 5 는 호출자로 전파).
        Path partial = out.resolveSibling(out.getFileName() + ".partial");
        for (var input : inputs) {
            session.ensureResident();
            session.client().generateRawPromptEvalCount(session.verified(), model, "flush-" + (++flush));
            int tokens = session.client().chat(session.verified(), model, input.getValue(), 1).promptTokens();
            ObjectNode row = Json.MAPPER.createObjectNode();
            row.put("id", input.getKey()); row.put("messagesSha256", Json.sha(Json.canonical(Json.MAPPER.valueToTree(input.getValue())))); row.put("promptTokens", tokens);
            row.put("cacheExcluded", true); row.put("modelDigest", session.runtime().modelDigest()); row.put("templateSha256", session.runtime().templateSha256());
            lines.append(Json.canonicalString(row)).append('\n');
            writeText(partial, lines.toString());
        }
        writeText(out, lines.toString());
        try { Files.deleteIfExists(partial); } catch (java.io.IOException e) { throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
    }
    private static void writeText(Path path, String text) {
        try { Files.createDirectories(path.toAbsolutePath().getParent()); Files.write(path, text.getBytes(StandardCharsets.UTF_8)); }
        catch (java.io.IOException e) { throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
    }
    static ObjectNode header(String runId, int repeats) {
        ObjectNode result = Json.MAPPER.createObjectNode(); result.put("runId", runId); result.put("repeats", repeats); result.put("complete", false); result.putNull("abortReason"); return result;
    }
    static ObjectNode outcomeRow(ClassificationJudge.Outcome outcome) {
        ObjectNode row = Json.MAPPER.createObjectNode();
        row.put("status", outcome.status()); row.put("retried", outcome.retried()); row.put("firstValid", outcome.firstValid()); row.put("latencyMs", outcome.latencyMs());
        row.set("finishReasons", Json.MAPPER.valueToTree(outcome.finishReasons())); row.set("promptTokens", Json.MAPPER.valueToTree(outcome.promptTokens()));
        row.set("completionTokens", Json.MAPPER.valueToTree(outcome.completionTokens())); row.set("outputSha256", Json.MAPPER.valueToTree(outcome.outputSha256())); row.set("errorCodes", Json.MAPPER.valueToTree(outcome.errorCodes()));
        return row;
    }
    /** 측정 중 endpoint 오류 = 지금까지 결과를 complete=false·abortReason 으로 보존하고 exit 5. 정상 종료 = complete=true. */
    private static void guarded(ObjectNode result, Path file, Runnable body) {
        try { body.run(); }
        catch (EvalException e) {
            if (e.exitCode() != 5) throw e;
            result.put("abortReason", e.getMessage()); Json.write(file, result); throw e;
        }
        result.put("complete", true); Json.write(file, result);
    }
}
