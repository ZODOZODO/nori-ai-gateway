package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.queue.AdmissionLookupRepository;
import java.time.Clock;
import java.util.Optional;

public final class IdempotentAdmissionLookup {
    private final AdmissionLookupRepository repository;
    private final Clock clock;
    public IdempotentAdmissionLookup(AdmissionLookupRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    public Optional<JobAdmissionService.Accepted> find(VerifiedSystemScope scope,String key,String digest){
        if(key==null)return Optional.empty();
        return repository.find(scope,key).map(existing->{
            if(!clock.instant().isBefore(existing.expiresAt()))throw new JobAdmissionService.Rejected(404,null,0);
            if(!existing.requestDigest().equals(digest))throw new JobAdmissionService.Rejected(409,"IDEMPOTENCY_KEY_CONFLICT",0);
            return new JobAdmissionService.Accepted(existing.jobId(),existing.conversationId(),null,0);
        });
    }
}
