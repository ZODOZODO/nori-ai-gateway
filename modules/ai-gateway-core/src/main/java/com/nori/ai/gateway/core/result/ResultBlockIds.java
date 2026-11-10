package com.nori.ai.gateway.core.result;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** 최종 배열 순서가 확정된 저장 직전에만 호출하며 upstream의 업무 식별자는 보존합니다. */
public final class ResultBlockIds {
    private static final String ID_PREFIX="b";
    private ResultBlockIds() {}
    public static JsonNode assign(JsonNode payload) {
        Objects.requireNonNull(payload);
        if(!payload.isObject())throw new IllegalArgumentException("최종 결과 객체가 필요합니다");
        JsonNode copy=payload.deepCopy();var blocks=copy.get("blocks");
        if(blocks==null||blocks.isNull())return copy;
        if(!blocks.isArray())throw new IllegalArgumentException("최종 블록 배열이 필요합니다");
        int index=0;
        for(var block:blocks) {
            if(!(block instanceof ObjectNode object))throw new IllegalArgumentException("블록 객체가 필요합니다");
            object.put("id",ID_PREFIX+index++);
        }
        return copy;
    }
}
