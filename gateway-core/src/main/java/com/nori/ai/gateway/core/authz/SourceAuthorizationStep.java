package com.nori.ai.gateway.core.authz;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.scope.*;
import java.util.*;

public final class SourceAuthorizationStep {
    public sealed interface Decision permits Granted, Denied, SourceUnavailable {}
    public record Granted(Set<String> allowed, String pdg) implements Decision {
        public Granted { allowed = Set.copyOf(allowed); }
    }
    public record Denied() implements Decision {}
    public record SourceUnavailable() implements Decision {}
    private final SourcePermissionMap permissions;
    private final TcSourceAuthorizationPort authorization;
    public SourceAuthorizationStep(SourcePermissionMap permissions, TcSourceAuthorizationPort authorization) {
        this.permissions = Objects.requireNonNull(permissions);
        this.authorization = Objects.requireNonNull(authorization);
    }
    public Decision authorizeBefore(String stepSource, VerifiedSystemScope scope, DelegatedCall call) {
        Objects.requireNonNull(scope); Objects.requireNonNull(call);
        if (scope.boundSystem() != OwnerSystem.TC || !scope.sub().equals(call.sub())) return new Denied();
        var mapping = permissions.find(stepSource);
        if (mapping.isEmpty()) return new Denied();
        Outcome outcome;
        try { outcome = authorization.authorize(call, mapping.get().requiredPermCodes()); }
        catch (RuntimeException failure) { return new SourceUnavailable(); }
        if (outcome == null || outcome instanceof Unavailable) return new SourceUnavailable();
        if (!(outcome instanceof Allowed allowed)) return new Denied();
        if (allowed.pdg() == null || !allowed.pdg().matches("[0-9a-f]{64}")) return new SourceUnavailable();
        if (!mapping.get().requiredPermCodes().containsAll(allowed.allowed())) return new SourceUnavailable();
        // TC의 기존 URL 인가는 ANY-of입니다. 빈 사전 확인 결과로 Source를 열지 않습니다.
        if (Collections.disjoint(allowed.allowed(), mapping.get().requiredPermCodes())) return new Denied();
        return new Granted(allowed.allowed(), allowed.pdg());
    }
}
