package com.nori.ai.gateway.core.sanitize;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ContentSanitizer {
    public static final String RULE_SET_VERSION = "mask-v1";
    private static final List<Pattern> RULES = List.of(
        Pattern.compile("(?i)(?:bearer\\s+)[A-Za-z0-9._~+/-]+=*"),
        Pattern.compile("(?i)(?:api[_-]?key|password|passwd|secret|token)\\s*[:=]\\s*[\\\"']?[^\\s\\\"',;}]+"),
        Pattern.compile("(?i)[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9-]+(?:\\.[A-Z0-9-]+)+"),
        Pattern.compile("(?<!\\d)(?:\\+82[- .]?)?0?1[016789][- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"),
        Pattern.compile("(?:표시\\s*이름|이름|성명|display[_ -]?name)\\s*[:=]\\s*[^\\s,;]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(?:AIGW[_-])?SENTINEL[_:-][A-Za-z0-9_@.:-]+"));

    public Sanitized sanitize(String text) {
        String masked = Objects.requireNonNull(text, "저장할 내용이 필요합니다");
        for (Pattern rule : RULES) masked = rule.matcher(masked).replaceAll("[마스킹]");
        // 규칙 실행과 후조건 확인이 모두 끝난 값만 저장 경계에 넘깁니다.
        for (Pattern rule : RULES) if (rule.matcher(masked).find()) throw new IllegalStateException("마스킹 적용을 확인할 수 없습니다");
        return new Sanitized(masked, RULE_SET_VERSION, true);
    }
    public record Sanitized(String text, String ruleSetVersion, boolean applied) {
        public Sanitized {
            Objects.requireNonNull(text);
            if (!RULE_SET_VERSION.equals(ruleSetVersion) || !applied) throw new IllegalArgumentException("마스킹 적용 확인이 필요합니다");
        }
    }
}
