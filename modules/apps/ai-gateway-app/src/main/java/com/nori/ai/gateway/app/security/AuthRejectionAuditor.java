package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.audit.auth.*;
import java.util.*;

public final class AuthRejectionAuditor {
    private final AuthRejectionAppendRepository repository;
    public AuthRejectionAuditor(AuthRejectionAppendRepository repository) { this.repository = Objects.requireNonNull(repository); }
    public void unauthenticated(UUID requestId, AuthRejectionReason reason) {
        if (!tokenReason(reason)) throw new IllegalArgumentException("서비스 인증 거절 사유가 필요합니다");
        repository.append(new AuthRejectionAppend(requestId,Optional.empty(),Optional.empty(),reason));
    }
    public void serviceVerified(VerifiedServiceIdentity service, UUID requestId, AuthRejectionReason reason) {
        if (tokenReason(Objects.requireNonNull(reason))) throw new IllegalArgumentException("사용자 인증 거절 사유가 필요합니다");
        repository.append(new AuthRejectionAppend(requestId,Optional.of(service.ownerSystem()),Optional.empty(),reason));
    }
    public void actorVerified(VerifiedServiceIdentity service, VerifiedSystemScope scope, UUID requestId, AuthRejectionReason reason) {
        if (reason != AuthRejectionReason.SYSTEM_SCOPE_MISMATCH || scope.boundSystem() != service.ownerSystem() || !scope.requestId().equals(requestId.toString()))
            throw new IllegalArgumentException("검증된 요청 범위가 일치하지 않습니다");
        repository.append(new AuthRejectionAppend(requestId,Optional.of(service.ownerSystem()),Optional.of(scope.actorRef()),reason));
    }
    private static boolean tokenReason(AuthRejectionReason reason) {
        return reason == AuthRejectionReason.SERVICE_TOKEN_MISSING || reason == AuthRejectionReason.SERVICE_TOKEN_INVALID;
    }
}
