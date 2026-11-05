package com.nori.ai.gateway.core.dispatch;

import com.nori.ai.gateway.core.plan.*;
import java.util.Objects;
import java.util.function.Consumer;
public record ExecutionContext(ResourceResolver resolver, Consumer<DispatchTrace> trace) {
    public record DispatchTrace(String stepId, RejectReason reason) {}
    public ExecutionContext { Objects.requireNonNull(resolver); Objects.requireNonNull(trace); }
}
