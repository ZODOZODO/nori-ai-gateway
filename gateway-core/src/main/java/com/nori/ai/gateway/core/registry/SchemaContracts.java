package com.nori.ai.gateway.core.registry;

import com.networknt.schema.*;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

public final class SchemaContracts {
    public static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final SchemaRegistry SCHEMAS = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private SchemaContracts() {}
    public static JsonNode parse(String text) {
        try { JsonNode node = JSON.readTree(text); if (node == null) throw new IllegalArgumentException("parse failed"); validateUnicode(node); return node; }
        catch (RuntimeException e) { throw new IllegalArgumentException("parse failed"); }
    }
    public static String canonicalize(String text) {
        validateUnicode(parse(text));
        try { return new JsonCanonicalizer(text).getEncodedString(); }
        catch (IOException | RuntimeException e) { throw new IllegalArgumentException("parse failed"); }
    }
    public static String digest(JsonNode input, JsonNode output) {
        var pair = JSON.createObjectNode().set("inputSchema", input).set("outputSchema", output);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonicalize(pair.toString()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e); }
    }
    public static void validateSchema(JsonNode node) {
        rejectExternalReferences(node);
        try {
            Schema meta = SCHEMAS.getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
            if (!meta.validate(node).isEmpty()) throw new IllegalArgumentException("Schema 형식이 잘못되었습니다");
            // networknt의 기본 preload는 참조 오류를 지연하므로 기동 시 검증을 명시적으로 완료합니다.
            SCHEMAS.getSchema(node).initializeValidators();
        } catch (RuntimeException e) { throw new IllegalArgumentException("Schema 형식이 잘못되었습니다"); }
    }
    public static boolean matches(JsonNode schema, JsonNode value) {
        try { return value != null && SCHEMAS.getSchema(schema).validate(value).isEmpty(); }
        catch (RuntimeException e) { return false; }
    }
    private static void rejectExternalReferences(JsonNode node) {
        if (node.isObject()) for (var entry : node.properties()) {
            String key = entry.getKey(); JsonNode value = entry.getValue();
            if ((key.equals("$ref") || key.equals("$dynamicRef")) && (!value.isString() || !value.asString().startsWith("#")))
                throw new IllegalArgumentException("외부 Schema 참조는 허용되지 않습니다");
            if (key.equals("$id") && (!value.isString() || !value.asString().startsWith("#")))
                throw new IllegalArgumentException("외부 Schema 식별자는 허용되지 않습니다");
            if (key.equals("$schema") && !"https://json-schema.org/draft/2020-12/schema".equals(value.asString()))
                throw new IllegalArgumentException("지원하지 않는 Schema 방언입니다");
            rejectExternalReferences(value);
        }
        else if (node.isArray()) node.forEach(SchemaContracts::rejectExternalReferences);
    }
    private static void validateUnicode(JsonNode node) {
        if (node.isString()) validateString(node.asString());
        else if (node.isObject()) for (var entry : node.properties()) { validateString(entry.getKey()); validateUnicode(entry.getValue()); }
        else if (node.isArray()) node.forEach(SchemaContracts::validateUnicode);
    }
    private static void validateString(String text) {
        // JCS 1.1의 숫자 처리를 재사용하되 UTF-8 대체 문자 변환 전에 잘못된 surrogate를 차단합니다.
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("parse failed");
            } else if (Character.isLowSurrogate(ch)) throw new IllegalArgumentException("parse failed");
        }
    }
}
