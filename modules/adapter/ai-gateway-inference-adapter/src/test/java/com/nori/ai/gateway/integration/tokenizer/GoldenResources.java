package com.nori.ai.gateway.integration.tokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nori.ai.gateway.core.budget.ChatMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** P03 golden 사본 읽기. 사본은 원본과 바이트가 같아야 하므로 각 시험이 SHA-256 을 고정값과 대조한다. */
final class GoldenResources {
    static final ObjectMapper MAPPER = new ObjectMapper();
    private GoldenResources() {}

    static byte[] bytes(String resource) {
        try (InputStream in = GoldenResources.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("시험 자원이 없습니다: " + resource);
            return in.readAllBytes();
        } catch (IOException failure) { throw new IllegalStateException("시험 자원을 읽을 수 없습니다: " + resource, failure); }
    }
    static String sha256(String resource) { return HfTokenizerCounter.sha256(bytes(resource)); }
    static JsonNode json(String resource) {
        try { return MAPPER.readTree(bytes(resource)); }
        catch (IOException failure) { throw new IllegalStateException("JSON 시험 자원 형식 오류: " + resource); }
    }
    static List<JsonNode> jsonLines(String resource) {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : new String(bytes(resource), StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank()) continue;
            try { rows.add(MAPPER.readTree(line)); }
            catch (IOException failure) { throw new IllegalStateException("JSONL 시험 자원 형식 오류: " + resource); }
        }
        return rows;
    }
    static List<ChatMessage> messages(JsonNode array) {
        List<ChatMessage> messages = new ArrayList<>();
        for (JsonNode m : array) messages.add(new ChatMessage(ChatMessage.Role.valueOf(m.path("role").asText().toUpperCase(Locale.ROOT)), m.path("content").asText()));
        return messages;
    }

    /** F0 하네스 InputAudit.messagesSha256 과 같은 RFC 8785 직렬화(문자열·배열·객체만)로 메시지 목록 해시를 만든다. */
    static String messagesSha256(JsonNode array) { return HfTokenizerCounter.sha256(canonical(array).getBytes(StandardCharsets.UTF_8)); }
    private static String canonical(JsonNode node) {
        if (node.isTextual()) return quote(node.textValue());
        if (node.isArray()) { List<String> values = new ArrayList<>(); node.forEach(n -> values.add(canonical(n))); return "[" + String.join(",", values) + "]"; }
        if (node.isObject()) {
            List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); names.sort(null);
            List<String> fields = new ArrayList<>(); for (String name : names) fields.add(quote(name) + ":" + canonical(node.get(name)));
            return "{" + String.join(",", fields) + "}";
        }
        throw new IllegalArgumentException("메시지 목록에 문자열 아닌 값이 있습니다");
    }
    private static String quote(String value) {
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
                default -> { if (c < 32) escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); else escaped.append(c); }
            }
        }
        return escaped.append('"').toString();
    }
}
