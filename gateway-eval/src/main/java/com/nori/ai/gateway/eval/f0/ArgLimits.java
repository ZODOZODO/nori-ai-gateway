package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * G-ARG-LIMITS 기계 판독본. CONFIRMED 는 근거(CODE·SCHEMA = 경로:줄, API_DOC = https URL)와 양의 maxChars 가 있어야 하며,
 * 근거가 없는 필드는 UNRESOLVED(maxChars=null)로만 둘 수 있다. 임의 상한으로 채우는 경로는 없다.
 */
public final class ArgLimits {
    public static final int V2_EQP_MAX_CODEPOINTS = 64, V2_QUERY_MAX_CODEPOINTS = 256;
    public static final List<String> FIELDS = List.of("t", "eqp", "from", "to", "q");
    public record Limit(String field, String format, Integer maxChars, String charUnit, String status, List<JsonNode> evidence) {
        public boolean confirmed() { return status.equals("CONFIRMED"); }
    }
    private final Map<String,Limit> limits;
    private final boolean versioned;
    private ArgLimits(Map<String,Limit> limits, boolean versioned) { this.limits = limits; this.versioned = versioned; }
    public static ArgLimits load(Path path) { return parse(Json.read(path)); }
    public static ArgLimits parse(JsonNode root) {
        JsonNode fields = root.path("fields");
        if (!fields.isArray()) throw new EvalException(2, "ARG_LIMITS_INVALID");
        Map<String,Limit> limits = new LinkedHashMap<>();
        for (JsonNode n : fields) {
            Set<String> allowed = new HashSet<>(Set.of("field", "format", "maxChars", "charUnit", "evidence", "status", "unresolvedReason", "candidates"));
            if (!allowed.containsAll(GoldenSet.keys(n))) throw new EvalException(2, "ARG_LIMITS_INVALID");
            String field = n.path("field").asText(""), status = n.path("status").asText("");
            if (!FIELDS.contains(field) || limits.containsKey(field) || !Set.of("CONFIRMED", "UNRESOLVED").contains(status) || !n.path("evidence").isArray()) throw new EvalException(2, "ARG_LIMITS_INVALID");
            List<JsonNode> evidence = new ArrayList<>(); n.path("evidence").forEach(evidence::add);
            Limit limit;
            if (status.equals("CONFIRMED")) {
                if (!n.path("maxChars").isInt() || n.path("maxChars").asInt() < 1 || !Set.of("CODEPOINT").contains(n.path("charUnit").asText()) || n.path("format").asText("").isBlank()) throw new EvalException(2, "ARG_LIMITS_INVALID");
                if (evidence.isEmpty()) throw new EvalException(2, "ARG_LIMITS_UNSUPPORTED_CONFIRMED");
                for (JsonNode e : evidence) validateEvidence(e);
                limit = new Limit(field, n.path("format").asText(), n.path("maxChars").asInt(), n.path("charUnit").asText(), status, List.copyOf(evidence));
                validateFormat(limit);
            } else {
                if (!n.path("maxChars").isNull() || n.path("unresolvedReason").asText("").isBlank()) throw new EvalException(2, "ARG_LIMITS_INVALID");
                limit = new Limit(field, null, null, null, status, List.copyOf(evidence));
            }
            limits.put(field, limit);
        }
        if (!limits.keySet().equals(new HashSet<>(FIELDS))) throw new EvalException(2, "ARG_LIMITS_INVALID");
        boolean versioned = root.path("schema").asText().equals("f0-arg-limits-v2");
        if (versioned) {
            Map<String,Integer> approved = Map.of("t", 3, "eqp", V2_EQP_MAX_CODEPOINTS, "from", 35, "to", 35, "q", V2_QUERY_MAX_CODEPOINTS);
            for (Limit limit : limits.values()) if (limit.confirmed() && !Objects.equals(limit.maxChars(), approved.get(limit.field()))) throw new EvalException(2, "ARG_LIMITS_FORMAT_MISMATCH");
        }
        return new ArgLimits(Collections.unmodifiableMap(limits), versioned);
    }
    private static void validateEvidence(JsonNode e) {
        String kind = e.path("kind").asText(""), ref = e.path("ref").asText("");
        boolean ok = switch (kind) {
            case "CODE", "SCHEMA" -> ref.matches("[^\\s:]+(?:/[^\\s:]+)*:[0-9]+");
            case "API_DOC" -> ref.matches("https://[^\\s]+");
            default -> false;
        };
        if (!ok) throw new EvalException(2, "ARG_LIMITS_UNSUPPORTED_CONFIRMED");
    }
    // 형식이 선언한 최장 표기의 실제 길이와 maxChars 가 다르면 근거와 기계본이 어긋난 것입니다.
    private static void validateFormat(Limit limit) {
        String format = limit.format();
        if (format.startsWith("ENUM:")) {
            int longest = Arrays.stream(format.substring(5).split("\\|")).mapToInt(s -> s.codePointCount(0, s.length())).max().orElse(0);
            if (longest != limit.maxChars()) throw new EvalException(2, "ARG_LIMITS_FORMAT_MISMATCH");
        } else if (format.startsWith("DATETIME:")) {
            String sample = formatTime(format, LocalDateTime.of(2026, 10, 3, 0, 0));
            if (sample.length() != limit.maxChars()) throw new EvalException(2, "ARG_LIMITS_FORMAT_MISMATCH");
        } else if (!Set.of("TC_EQP_ID", "TEXT").contains(format)) throw new EvalException(2, "ARG_LIMITS_INVALID");
    }
    static String formatTime(String format, LocalDateTime time) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format.substring("DATETIME:".length()), Locale.ROOT);
            return formatter.format(OffsetDateTime.of(time, ZoneOffset.ofHours(9)));
        } catch (RuntimeException e) { throw new EvalException(2, "ARG_LIMITS_INVALID"); }
    }
    /** offset이 없으면 Context 현지 시각, 있으면 같은 instant를 Context로 옮깁니다. 원문의 초·소수 자릿수는 보존합니다. */
    static String normalizeTime(String value, String timezone) {
        if (value == null || !value.matches(ClassificationJudge.TIME) || timezone == null || !timezone.matches("[+-]\\d{2}:\\d{2}")) throw new EvalException(2, "DATETIME_INVALID");
        try {
            ZoneOffset contextOffset = ZoneOffset.of(timezone);
            int suffix = value.endsWith("Z") ? value.length() - 1 : value.length() >= 6 && (value.charAt(value.length() - 6) == '+' || value.charAt(value.length() - 6) == '-') ? value.length() - 6 : value.length();
            boolean hasOffset = suffix < value.length();
            String localText = value.substring(0, suffix);
            LocalDateTime local = LocalDateTime.parse(localText, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            ZoneOffset inputOffset = hasOffset ? ZoneOffset.of(value.substring(suffix)) : contextOffset;
            OffsetDateTime normalized = local.atOffset(inputOffset).withOffsetSameInstant(contextOffset);
            int dot = localText.indexOf('.'), fractionalDigits = dot < 0 ? 0 : localText.length() - dot - 1;
            String pattern = "uuuu-MM-dd'T'HH:mm" + (localText.length() > 16 ? ":ss" : "")
                + (fractionalDigits > 0 ? "." + "S".repeat(fractionalDigits) : "") + (hasOffset ? "xxx" : "");
            String rendered = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(normalized);
            if (!rendered.matches(ClassificationJudge.TIME)) throw new EvalException(2, "DATETIME_INVALID");
            return rendered;
        } catch (DateTimeException e) { throw new EvalException(2, "DATETIME_INVALID"); }
    }
    public boolean versioned() { return versioned; }
    public Limit get(String field) { return limits.get(field); }
    public List<String> unresolved() { return limits.values().stream().filter(l -> !l.confirmed()).map(Limit::field).toList(); }
}
