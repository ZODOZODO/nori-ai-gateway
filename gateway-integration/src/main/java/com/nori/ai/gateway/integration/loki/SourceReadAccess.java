package com.nori.ai.gateway.integration.loki;

import com.nori.ai.gateway.core.authz.SourceAuthorizationStep;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.*;
import java.time.Clock;
import java.util.Objects;

/** Source의 소유권과 실행 시점 인가는 기존 중앙 계약으로 확인합니다. */
public final class SourceReadAccess {
    public record Request(VerifiedSystemScope scope, DelegatedCall delegatedCall, String sourceAllowlistRef) {
        public Request { Objects.requireNonNull(scope); Objects.requireNonNull(delegatedCall); Objects.requireNonNull(sourceAllowlistRef); }
        @Override public String toString() { return "SourceReadRequest[보호됨]"; }
    }
    private final SourceAuthorizationStep authorization;
    private final Clock clock;
    public SourceReadAccess(SourceAuthorizationStep authorization, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization); this.clock = Objects.requireNonNull(clock);
    }
    public ResultStatus check(Request request, String expectedRef) {
        Objects.requireNonNull(request);
        if (request.scope().boundSystem() != OwnerSystem.TC || !request.scope().validAt(clock.instant())
                || !expectedRef.equals(request.sourceAllowlistRef())) return ResultStatus.DENIED;
        var decision = authorization.authorizeBefore(expectedRef, request.scope(), request.delegatedCall());
        if (decision instanceof SourceAuthorizationStep.Granted) return ResultStatus.SUCCESS;
        return decision instanceof SourceAuthorizationStep.SourceUnavailable ? ResultStatus.SOURCE_UNAVAILABLE : ResultStatus.DENIED;
    }
}
