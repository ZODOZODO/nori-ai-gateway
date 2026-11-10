package com.nori.ai.gateway.core.scope;

import java.util.Objects;
public record ActorRef(String value) {
    public ActorRef { Objects.requireNonNull(value, "사용자 참조가 필요합니다"); if (value.isBlank()) throw new IllegalArgumentException("사용자 참조가 필요합니다"); }
}
