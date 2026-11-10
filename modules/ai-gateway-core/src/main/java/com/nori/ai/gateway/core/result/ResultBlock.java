package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.OwnedReference;
import tools.jackson.databind.JsonNode;
public record ResultBlock(String type, OwnedReference dataRef, JsonNode content) {
    public ResultBlock { content = content == null ? null : content.deepCopy(); }
    @Override public JsonNode content() { return content == null ? null : content.deepCopy(); }
}
