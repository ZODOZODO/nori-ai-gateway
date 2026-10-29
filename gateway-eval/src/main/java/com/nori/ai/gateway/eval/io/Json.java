package com.nori.ai.gateway.eval.io;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.nori.ai.gateway.eval.EvalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.*;
import java.util.*;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private Json() {}
    public static JsonNode read(Path path) {
        try { return parse(Files.readAllBytes(path)); }
        catch (IOException | RuntimeException e) { throw new EvalException(2, "INVALID_JSON"); }
    }
    public static JsonNode parse(byte[] bytes) {
        try { JsonNode node = MAPPER.readTree(bytes); if (node == null) throw new EvalException(2, "INVALID_JSON"); return node; }
        catch (IOException | RuntimeException e) { throw new EvalException(2, "INVALID_JSON"); }
    }
    public static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static byte[] bytes(Path path) {
        try { return Files.readAllBytes(path); }
        catch (IOException e) { throw new EvalException(2, "INPUT_MISSING"); }
    }
    public static void write(Path path, Object value) {
        byte[] bytes = canonical(MAPPER.valueToTree(value));
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".aigw-", ".tmp");
            try { Files.write(temp, bytes); Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
    }
    public static byte[] canonical(JsonNode node) { return canonicalString(node).getBytes(StandardCharsets.UTF_8); }
    public static String canonicalString(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isBoolean()) return node.asText();
        if (node.isNumber()) return number(node.doubleValue());
        if (node.isTextual()) return quote(node.textValue());
        if (node.isArray()) {
            List<String> values = new ArrayList<>(); node.forEach(n -> values.add(canonicalString(n)));
            return "[" + String.join(",", values) + "]";
        }
        if (node.isObject()) {
            List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); Collections.sort(names);
            return "{" + names.stream().map(k -> quote(k) + ":" + canonicalString(node.get(k))).reduce((a,b) -> a + "," + b).orElse("") + "}";
        }
        throw new EvalException(2, "INVALID_JSON");
    }
    private static String quote(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new EvalException(2, "INVALID_UNICODE"); }
            else if (Character.isLowSurrogate(c)) throw new EvalException(2, "INVALID_UNICODE");
        }
        StringBuilder escaped = new StringBuilder("\"");
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
                default -> { if (c < 32) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int)c)); else escaped.append(c); }
            }
        }
        return escaped.append('"').toString();
    }
    // RFC 8785는 Java의 지수 표기가 아니라 ECMAScript의 최소 왕복 십진수를 요구합니다.
    private static String number(double value) {
        if (!Double.isFinite(value)) throw new EvalException(2, "INVALID_JSON_NUMBER");
        if (value == 0) return "0";
        boolean negative = value < 0;
        double absolute = Math.abs(value);
        BigDecimal exact = new BigDecimal(absolute);
        BigDecimal decimal = exact;
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
    public static String required(JsonNode node, String key) {
        if (!node.hasNonNull(key) || !node.get(key).isTextual() || node.get(key).textValue().isBlank()) throw new EvalException(2, "FIELD_MISSING");
        return node.get(key).textValue();
    }
}
