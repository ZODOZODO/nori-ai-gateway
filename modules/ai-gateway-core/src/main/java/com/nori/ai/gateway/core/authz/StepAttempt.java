package com.nori.ai.gateway.core.authz;

import com.nori.ai.gateway.core.scope.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 인가 영수증은 이 시도와 목적에만 결속되며 다음 단계로 재사용하지 않습니다. */
public final class StepAttempt {
    public enum Purpose { DISPATCH, CACHE_LOOKUP, CACHE_PUT }
    public enum Mode { DIRECT_SOURCE, BACKEND_READ_REQUIRED }
    public record Binding(OwnerSystem ownerSystem, ActorRef actorRef, String sub, String requestId, String jobId,
            String stepId, String capabilityId, String sourceRef, Set<String> manifestRequiredPermissions,
            String contractVersion, String schemaDigest, String argumentsDigest, String resourcesDigest) {
        public Binding { manifestRequiredPermissions = Set.copyOf(manifestRequiredPermissions); }
        @Override public String toString() { return "Binding[보호됨]"; }
    }
    private final Object preparationIdentity;
    private final Object invocationIdentity = new Object();
    private final Binding binding;
    private final Purpose purpose;
    private final Mode mode;
    private final AtomicInteger state = new AtomicInteger();
    private StepAttempt(Object identity, Binding binding, Purpose purpose, Mode mode) {
        this.preparationIdentity = Objects.requireNonNull(identity); this.binding = Objects.requireNonNull(binding);
        this.purpose = Objects.requireNonNull(purpose); this.mode = Objects.requireNonNull(mode);
    }
    public static StepAttempt create(Object identity, Binding binding, Purpose purpose, Mode mode) { return new StepAttempt(identity,binding,purpose,mode); }
    public boolean belongsTo(Object identity) { return preparationIdentity == identity; }
    public Binding binding() { return binding; }
    public Purpose purpose() { return purpose; }
    public Mode mode() { return mode; }
    public void claimEvaluation() { if (!state.compareAndSet(0,1)) throw new IllegalStateException("실행 시도를 재평가할 수 없습니다"); }
    public boolean evaluated() { return state.get() == 1; }
    public boolean fresh() { return state.get() == 0; }
    public void consume(Purpose expected) {
        if (purpose != expected || !state.compareAndSet(mode == Mode.DIRECT_SOURCE ? 1 : 0,2)) throw new IllegalStateException("실행 시도를 재사용할 수 없습니다");
    }
    public void close() { state.set(3); }
    @Override public String toString() { return "StepAttempt[보호됨]"; }
}
