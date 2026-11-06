package com.nori.ai.gateway.app.job.recovery;

import com.nori.ai.gateway.persistence.queue.recovery.InstanceEpochRepository.EpochSnapshot;

public interface RestoreRecoveryHandler {
    RestoreOutcome run(EpochSnapshot epoch);
    record RestoreOutcome(boolean completed){}
}
