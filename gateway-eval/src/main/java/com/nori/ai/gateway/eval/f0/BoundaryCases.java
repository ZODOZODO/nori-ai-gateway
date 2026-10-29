package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * arg-limits 에서 출력 경계 케이스(NORMAL·MAX·ESCAPE)를 만든다. 인자는 자르지 않고 질문 텍스트에 원문 그대로 넣으며,
 * UNRESOLVED 필드가 하나라도 있으면 MAX·ESCAPE 를 만들지 않는다(NORMAL 만 기록 + 차단 표지, exit 2).
 */
public final class BoundaryCases {
    public static final String REFERENCE_TIME = "2026-10-05T09:00:00+09:00";
    static final Map<String,String> Q_VARIANTS = new LinkedHashMap<>();
    static {
        Q_VARIANTS.put("MIXED", "가A1나B2다C3");
        Q_VARIANTS.put("HANGUL", "가나다라마바사아자차카타파하");
        Q_VARIANTS.put("DIGIT", "0123456789");
        Q_VARIANTS.put("ASCII", "abcdefghijklmnopqrstuvwxyz");
    }
    public record Result(List<ObjectNode> rows, List<String> unresolved) {}
    private BoundaryCases() {}

    public static Result generate(ArgLimits limits, RegistryFixture fixture) {
        List<ObjectNode> rows = new ArrayList<>();
        if (!fixture.resources().eqpExists("PHOTO_001")) throw new EvalException(2, "FIXTURE_NORMAL_EQP_MISSING");
        // NORMAL = SPEC §14.5 예시 형태(10월 3일 하루 전체, q:null).
        rows.add(row("NORMAL-01", "NORMAL", "PHOTO_001", "2026-10-03T00:00", "2026-10-04T00:00", null, "2026-10-03 00:00", "2026-10-04 00:00"));
        List<String> unresolved = limits.unresolved();
        if (!unresolved.isEmpty()) return new Result(List.copyOf(rows), unresolved);
        int eqpMax = limits.get("eqp").maxChars(), qMax = limits.get("q").maxChars();
        if (!limits.get("t").format().equals("ENUM:KQ|LQ|INC|GQ|UNS") || limits.get("t").maxChars() < 3) throw new EvalException(2, "ARG_LIMITS_INVALID");
        String maxEqp = fixture.resources().eqpIds().stream().filter(id -> id.codePointCount(0, id.length()) == eqpMax).findFirst().orElseThrow(() -> new EvalException(2, "FIXTURE_MAX_EQP_MISSING"));
        String from = ArgLimits.formatTime(limits.get("from").format(), LocalDateTime.of(2026, 10, 3, 0, 0));
        String to = ArgLimits.formatTime(limits.get("to").format(), LocalDateTime.of(2026, 10, 4, 0, 0));
        if (from.length() != limits.get("from").maxChars() || to.length() != limits.get("to").maxChars()) throw new EvalException(2, "ARG_LIMITS_FORMAT_MISMATCH");
        // 문자 종류별 토큰 수가 다르므로 최대 길이 표본을 여러 문자 종류로 둡니다(최대 토큰 수는 계수 결과에서 고름).
        for (var variant : Q_VARIANTS.entrySet()) rows.add(row("MAX-" + variant.getKey(), "MAX", maxEqp, from, to, fill(variant.getValue(), qMax), from, to));
        String core = "\"\\\u0001";
        if (qMax < core.length()) throw new EvalException(2, "ARG_LIMITS_TOO_SMALL_FOR_ESCAPE");
        String tail = "경로C:\\tc\\log오류\"E-17\"끝";
        String escape = core + tail.substring(0, Math.min(tail.length(), qMax - core.length()));
        rows.add(row("ESCAPE-01", "ESCAPE", "PHOTO_001", from, to, escape, from, to));
        return new Result(List.copyOf(rows), List.of());
    }
    static String fill(String pattern, int length) {
        StringBuilder text = new StringBuilder(); int[] points = pattern.codePoints().toArray();
        for (int i = 0; i < length; i++) text.appendCodePoint(points[i % points.length]);
        return text.toString();
    }
    static ObjectNode row(String caseId, String kind, String eqp, String from, String to, String q, String fromLocal, String toLocal) {
        ObjectNode expected = Json.MAPPER.createObjectNode();
        expected.put("t", "INC"); expected.put("eqp", eqp); expected.put("from", from); expected.put("to", to);
        if (q == null) expected.putNull("q"); else expected.put("q", q);
        ObjectNode context = Json.MAPPER.createObjectNode();
        context.put("timezone", "+09:00"); context.put("referenceTime", REFERENCE_TIME); context.putNull("timeWindow"); context.putArray("selectedResourceRefs");
        ObjectNode row = Json.MAPPER.createObjectNode();
        row.put("caseId", caseId); row.put("kind", kind); row.set("expectedOutput", expected); row.put("expectedJson", Json.canonicalString(expected));
        // 고정 템플릿: 인자 값은 원문 그대로(따옴표·역슬래시·U+0001 포함), 시간은 Context timezone 기준 절대 표기.
        row.put("question", q == null ? eqp + " 설비에서 " + fromLocal + "부터 " + toLocal + "까지 오류 로그 원인 찾아줘"
            : eqp + " 설비에서 " + fromLocal + "부터 " + toLocal + "까지 \"" + q + "\" 오류 로그 원인 찾아줘");
        row.set("context", context);
        return row;
    }
    public static void write(Path out, Result result) {
        StringBuilder text = new StringBuilder();
        for (ObjectNode row : result.rows()) text.append(Json.canonicalString(row)).append('\n');
        Path blocked = blockedPath(out);
        try {
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.write(out, text.toString().getBytes(StandardCharsets.UTF_8));
            if (result.unresolved().isEmpty()) Files.deleteIfExists(blocked);
            else Json.write(blocked, Map.of("blockedKinds", List.of("MAX", "ESCAPE"), "reasonCode", "ARG_LIMITS_UNRESOLVED", "unresolvedFields", result.unresolved()));
        } catch (IOException e) { throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
    }
    public static Path blockedPath(Path out) {
        String name = out.getFileName().toString();
        return out.resolveSibling((name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name) + ".blocked.json");
    }
    /** 케이스 파일 로드·검증: expectedJson 은 expectedOutput 의 RFC 8785 직렬화와 정확히 같아야 합니다. */
    public static List<JsonNode> load(Path path) {
        List<JsonNode> rows = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (String line : new String(Json.bytes(path), StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank()) continue;
            JsonNode row = Json.parse(line.getBytes(StandardCharsets.UTF_8));
            if (!GoldenSet.keys(row).equals(Set.of("caseId", "kind", "expectedOutput", "expectedJson", "question", "context")) || !ids.add(row.path("caseId").asText()) || !Set.of("NORMAL", "MAX", "ESCAPE").contains(row.path("kind").asText())
                || !GoldenSet.keys(row.path("expectedOutput")).equals(new HashSet<>(ArgLimits.FIELDS)) || !Json.canonicalString(row.path("expectedOutput")).equals(row.path("expectedJson").asText())) throw new EvalException(2, "CASES_INVALID");
            rows.add(row);
        }
        if (rows.isEmpty()) throw new EvalException(2, "CASES_INVALID");
        return rows;
    }
}
