package com.nori.ai.gateway.integration.loki;

import java.util.*;

public final class LokiSourcePolicy {
    public static final String SOURCE_REF = "sources://TC/logs";
    public static final List<String> APPS = List.of("tc-gateway", "tc-business", "tc-ui-backend", "tc-ui-front");
    private LokiSourcePolicy() {}
    public static String retentionSourceId(String app) {
        if (!APPS.contains(app)) throw new IllegalArgumentException("허용되지 않은 로그 앱입니다");
        return SOURCE_REF + "/" + app;
    }
    public static String selector(String app, List<String> terms) {
        if (!APPS.contains(app)) throw new IllegalArgumentException("허용되지 않은 로그 앱입니다");
        StringBuilder query = new StringBuilder("{app=\"").append(app).append("\", namespace=\"tc\"}");
        for (String term : terms) {
            Objects.requireNonNull(term);
            query.append(" |= \"");
            for (int i = 0; i < term.length(); i++) {
                char value = term.charAt(i);
                switch (value) {
                    case '\\' -> query.append("\\\\");
                    case '"' -> query.append("\\\"");
                    case '\n' -> query.append("\\n");
                    case '\r' -> query.append("\\r");
                    case '\t' -> query.append("\\t");
                    default -> { if (value < 32 || value == 127) query.append(String.format(Locale.ROOT, "\\u%04x", (int) value)); else query.append(value); }
                }
            }
            query.append('"');
        }
        return query.toString();
    }
}
