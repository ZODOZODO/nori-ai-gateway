package com.nori.ai.gateway.core.plan;

import com.nori.ai.gateway.core.registry.*;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;

public final class InputValidator {
    private static final Pattern TIME = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(?::[0-9]{2}(?:\\.[0-9]{1,9})?)?(?:Z|[+-][0-9]{2}:[0-9]{2})?");
    public record Failure(RejectReason reason, List<String> fields) { public Failure { fields = List.copyOf(fields); } }
    public Failure validate(CapabilityManifest manifest, JsonNode args, ZoneId timezone) {
        if (args == null || !args.isObject() || args.has("qRef")) return invalid();
        for (var entry : manifest.inputLimits().entrySet()) {
            JsonNode value = args.get(entry.getKey());
            if (value == null || value.isNull()) continue;
            if (!value.isString()) return invalid();
            String text = value.asString(); InputLimit limit = entry.getValue();
            if (text.codePointCount(0, text.length()) > limit.maxChars())
                return new Failure(entry.getKey().equals("q") ? RejectReason.INPUT_BUDGET_EXCEEDED : RejectReason.PLAN_INVALID, List.of(entry.getKey()));
            if (text.isEmpty()) return invalid();
        }
        if (!SchemaContracts.matches(manifest.inputSchema(), args)) return invalid();
        try {
            Instant from = instant(args.get("from"), timezone), to = instant(args.get("to"), timezone);
            if (from != null && to != null && !from.isBefore(to)) return invalid();
        } catch (DateTimeException | IllegalArgumentException e) { return invalid(); }
        return null;
    }
    public Instant parseTime(String value, ZoneId timezone) {
        if (value == null || value.codePointCount(0, value.length()) > 35 || !TIME.matcher(value).matches())
            throw new IllegalArgumentException("시간 형식이 잘못되었습니다");
        var parsed = DateTimeFormatter.ISO_DATE_TIME.withResolverStyle(ResolverStyle.STRICT).parseBest(value, OffsetDateTime::from, LocalDateTime::from);
        if (parsed instanceof OffsetDateTime offset) return offset.toInstant();
        LocalDateTime local = (LocalDateTime) parsed;
        if (timezone == null || timezone.getRules().getValidOffsets(local).size() != 1)
            throw new IllegalArgumentException("시간대를 확정할 수 없습니다");
        return local.atZone(timezone).toInstant();
    }
    private Instant instant(JsonNode value, ZoneId timezone) {
        return value == null || value.isNull() ? null : parseTime(value.asString(), timezone);
    }
    private Failure invalid() { return new Failure(RejectReason.PLAN_INVALID, List.of()); }
}
