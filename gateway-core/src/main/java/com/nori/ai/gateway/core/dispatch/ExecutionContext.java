package com.nori.ai.gateway.core.dispatch;

import com.nori.ai.gateway.core.plan.*;
import java.util.Objects;
import java.util.*;
import com.nori.ai.gateway.core.authz.StepAttempt;
import com.nori.ai.gateway.core.workflow.StepBinding;
import java.util.function.Consumer;
public record ExecutionContext(ResourceResolver resolver, Consumer<DispatchTrace> trace,Optional<BoundInputs> boundInputs) {
    public ExecutionContext(ResourceResolver resolver,Consumer<DispatchTrace> trace){this(resolver,trace,Optional.empty());}
    public record BoundInputs(StepAttempt attempt,Map<String,StepBinding> bindings) {
        public BoundInputs {
            Objects.requireNonNull(attempt);Objects.requireNonNull(bindings);var copy=new LinkedHashMap<String,StepBinding>();
            bindings.forEach((key,value)->{Objects.requireNonNull(key);Objects.requireNonNull(value);copy.put(key,new StepBinding(value.ownerSystem(),value.dataRef(),value.value()));});bindings=Collections.unmodifiableMap(copy);
        }
        @Override public String toString(){return "BoundInputs[보호됨]";}
    }
    /** error는 reject가 응답에 싣는 ErrorCode이며 없으면 null이다. */
    public record DispatchTrace(String stepId, RejectReason reason, com.nori.ai.gateway.core.result.ErrorCode error) {}
    public ExecutionContext { Objects.requireNonNull(resolver); Objects.requireNonNull(trace);Objects.requireNonNull(boundInputs); }
}
