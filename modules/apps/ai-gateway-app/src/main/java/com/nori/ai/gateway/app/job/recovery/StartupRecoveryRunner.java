package com.nori.ai.gateway.app.job.recovery;

import com.nori.ai.gateway.app.config.CapacityProfile;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.job.TerminalSpec;
import com.nori.ai.gateway.persistence.queue.*;
import com.nori.ai.gateway.persistence.queue.recovery.CacheInvalidationRepository;
import java.time.Clock;

public final class StartupRecoveryRunner {
    private final QueueJobRepository jobs;private final JobTerminalCommitter terminal;private final JobEventPublisher events;
    private final CacheInvalidationRepository caches;private final Clock clock;private final ConversationExpiryGuard expiry;private final CapacityProfile capacity;
    public StartupRecoveryRunner(QueueJobRepository jobs,JobTerminalCommitter terminal,JobEventPublisher events,CacheInvalidationRepository caches,Clock clock,ConversationExpiryGuard expiry,CapacityProfile capacity){
        this.jobs=jobs;this.terminal=terminal;this.events=events;this.caches=caches;this.clock=clock;this.expiry=expiry;this.capacity=capacity;
    }
    public void run(){
        for(var job:jobs.nonterminal()){
            try{expiry.assertLive(job.conversationExpiresAt(),clock.instant());}
            catch(ConversationExpiryGuard.ConversationExpired expired){terminal.commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.EXPIRY_EDGE));continue;}
            if(job.status().equals("RUNNING"))terminal.commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.WORKER_INTERRUPTED));
            else if(clock.instant().isAfter(job.createdAt().plus(capacity.queueWaitLimit())))terminal.commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.QUEUE_TIMEOUT));
        }
        events.republishTerminal();caches.deleteAllCacheEntries();
    }
}
