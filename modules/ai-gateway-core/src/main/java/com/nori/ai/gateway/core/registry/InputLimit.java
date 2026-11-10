package com.nori.ai.gateway.core.registry;

public record InputLimit(int maxChars, String charUnit, String format, String status) {
    public InputLimit {
        if (maxChars < 1 || !"CODEPOINT".equals(charUnit) || !"CONFIRMED".equals(status)
            || format == null || format.isBlank()) throw new IllegalArgumentException("확정 입력 한도가 필요합니다");
    }
}
