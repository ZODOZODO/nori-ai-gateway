package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.util.UUID;

public final class ScopeAssembler {
    public VerifiedSystemScope forWorker(com.nori.ai.gateway.persistence.queue.JobScopeRecord job) {
        // Job의 180초 정각 허용을 배타적 만료 상한으로 표현하며 요청 단언의 만료는 바꾸지 않습니다.
        return new VerifiedSystemScope(job.ownerSystem(), "job-worker", new com.nori.ai.gateway.core.scope.ActorRef(job.actorRef()),
            job.actorRef(), job.requestPerm(), job.requestPdg(), job.createdAt(), job.createdAt().plusSeconds(180).plusNanos(1), job.jobId());
    }
    VerifiedSystemScope forRequest(VerifiedServiceIdentity service, ActorAssertionVerifier.VerifiedAssertion assertion, UUID requestId) {
        if (service.ownerSystem() != assertion.owner()) throw new IllegalArgumentException("검증된 시스템이 일치하지 않습니다");
        var scope = new VerifiedSystemScope(service.ownerSystem(),service.authenticatedServiceRef(),assertion.actor(),assertion.actor().value(),
            assertion.perm(),assertion.pdg(),assertion.issuedAt(),assertion.expiresAt(),requestId.toString());
        if (!scope.validAt(assertion.verifiedAt())) throw new IllegalArgumentException("검증된 유효 기간이 잘못되었습니다");
        return scope;
    }
}
