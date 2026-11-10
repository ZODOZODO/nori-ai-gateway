package com.nori.ai.gateway.core.result;

import java.util.List;
public record Clarification(ErrorCode reason, List<String> fields) {
    public Clarification { fields = List.copyOf(fields); }
}
