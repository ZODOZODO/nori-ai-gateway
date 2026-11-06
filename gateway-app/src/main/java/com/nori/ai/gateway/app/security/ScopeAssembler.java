package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.UUID;

public final class ScopeAssembler {
    VerifiedSystemScope forRequest(VerifiedServiceIdentity service, ActorAssertionVerifier.VerifiedAssertion assertion, UUID requestId) {
        if (service.ownerSystem() != assertion.owner()) throw new IllegalArgumentException("검증된 시스템이 일치하지 않습니다");
        var scope = new VerifiedSystemScope(service.ownerSystem(),service.authenticatedServiceRef(),assertion.actor(),assertion.actor().value(),
            assertion.perm(),assertion.pdg(),assertion.issuedAt(),assertion.expiresAt(),requestId.toString());
        if (!scope.validAt(assertion.verifiedAt())) throw new IllegalArgumentException("검증된 유효 기간이 잘못되었습니다");
        return scope;
    }
}
