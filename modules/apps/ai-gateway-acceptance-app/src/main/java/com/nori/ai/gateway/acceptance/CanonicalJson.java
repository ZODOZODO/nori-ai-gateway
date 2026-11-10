package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 증거 레코드의 RFC 8785 정규 JSON·SHA-256. {@code gateway-acceptance} 는 {@code gateway-eval} 에 의존할 수 없으므로(§3.2)
 * eval {@code io.Json} 과 같은 규칙을 자체 구현하며 {@code EvidenceBlockWriterTest} 가 두 구현의 일치를 고정한다.
 */
public final class CanonicalJson {
    public static final ObjectMapper MAPPER = new ObjectMapper();
    private CanonicalJson() {}

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 을 사용할 수 없습니다"); }
    }
    public static byte[] bytes(JsonNode node) { return string(node).getBytes(StandardCharsets.UTF_8); }

    /** {@code contentSha256} 를 뺀 정규 JSON 의 SHA-256 을 다시 넣는다. */
    public static ObjectNode seal(ObjectNode node) {
        node.remove("contentSha256");
        node.put("contentSha256", sha256(bytes(node)));
        return node;
    }

    public static String string(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isBoolean()) return node.asText();
        if (node.isNumber()) return number(node.doubleValue());
        if (node.isTextual()) return quote(node.textValue());
        if (node.isArray()) {
            var values = new ArrayList<String>(); node.forEach(child -> values.add(string(child)));
            return "[" + String.join(",", values) + "]";
        }
        if (node.isObject()) {
            var names = new ArrayList<String>(); node.fieldNames().forEachRemaining(names::add); Collections.sort(names);
            var parts = new ArrayList<String>(); for (String name : names) parts.add(quote(name) + ":" + string(node.get(name)));
            return "{" + String.join(",", parts) + "}";
        }
        throw new IllegalArgumentException("INVALID_JSON");
    }
    private static String quote(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException("INVALID_UNICODE"); }
            else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("INVALID_UNICODE");
        }
        var escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\t' -> escaped.append("\\t");
                case '\n' -> escaped.append("\\n");
                case '\f' -> escaped.append("\\f");
                case '\r' -> escaped.append("\\r");
                default -> { if (c < 32) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); else escaped.append(c); }
            }
        }
        return escaped.append('"').toString();
    }
    // RFC 8785 는 ECMAScript 의 최소 왕복 십진수 표기를 요구한다.
    private static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("INVALID_JSON_NUMBER");
        if (value == 0) return "0";
        boolean negative = value < 0;
        double absolute = Math.abs(value);
        BigDecimal exact = new BigDecimal(absolute), decimal = exact;
        for (int precision = 1; precision <= 17; precision++) {
            BigDecimal rounded = exact.round(new MathContext(precision, RoundingMode.HALF_EVEN));
            if (Double.doubleToLongBits(rounded.doubleValue()) == Double.doubleToLongBits(absolute)) { decimal = rounded; break; }
        }
        decimal = decimal.stripTrailingZeros();
        String rendered;
        if (absolute >= 1e-6 && absolute < 1e21) rendered = decimal.toPlainString();
        else {
            String digits = decimal.unscaledValue().abs().toString();
            int exponent = digits.length() - decimal.scale() - 1;
            rendered = digits.charAt(0) + (digits.length() > 1 ? "." + digits.substring(1) : "") + "e" + (exponent >= 0 ? "+" : "") + exponent;
        }
        return negative ? "-" + rendered : rendered;
    }
}
