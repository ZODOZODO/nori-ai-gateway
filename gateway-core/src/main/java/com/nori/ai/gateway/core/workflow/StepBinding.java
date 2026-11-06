package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.scope.*;
import tools.jackson.databind.JsonNode;
import java.util.Objects;

public record StepBinding(OwnerSystem ownerSystem,OwnedReference dataRef,JsonNode value) {
    public StepBinding {
        Objects.requireNonNull(ownerSystem);Objects.requireNonNull(dataRef);Objects.requireNonNull(value);
        if(!dataRef.belongsTo(ownerSystem))throw new IllegalArgumentException("단계 자료 소유 범위가 다릅니다");
        value=value.deepCopy();
    }
    @Override public JsonNode value(){return value.deepCopy();}
    @Override public String toString(){return "StepBinding[보호됨]";}
}
