package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.config.CapacityProfile;
import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.job.recovery.ReadinessGate;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.persistence.queue.*;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.*;
import org.springframework.context.event.EventListener;

public final class JobTimeoutSweeper implements AutoCloseable {
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("job-sweep-",0).factory());
    private final QueueJobRepository jobs;private final JobTerminalCommitter terminal;private final SlotLedger slots;
    private final CapacityProfile capacity;private final Clock clock;private final Optional<PausePointRegistry> pauses;private final ReadinessGate gate;
    private volatile boolean started;
    public JobTimeoutSweeper(QueueJobRepository jobs,JobTerminalCommitter terminal,SlotLedger slots,CapacityProfile capacity,Clock clock,Optional<PausePointRegistry> pauses,ReadinessGate gate){
        this.jobs=jobs;this.terminal=terminal;this.slots=slots;this.capacity=capacity;this.clock=clock;this.pauses=pauses;this.gate=gate;
    }
    @EventListener public void start(ReadinessGate.RecoveryCompleted event){if(!started){started=true;scheduler.scheduleWithFixedDelay(()->{try{sweepNow();}catch(RuntimeException failure){gate.down();throw failure;}},1,1,TimeUnit.SECONDS);}}
    public void sweepNow(){
        if(pauses.map(p->p.held(PausePointRegistry.Point.SWEEPER_HOLD)).orElse(false))return;
        var now=clock.instant();
        for(var job:jobs.nonterminal()){
            if(job.status().equals("QUEUED")&&now.isAfter(job.createdAt().plus(capacity.queueWaitLimit())))terminal.commit(job.jobId(),TerminalSpec.forCause(TerminalSpec.Cause.QUEUE_TIMEOUT));
            else if(job.status().equals("RUNNING")&&now.isAfter(job.createdAt().plus(capacity.jobLifetimeLimit())))slots.signalIfActive(job.jobId(),CancellationToken.Reason.TIMEOUT);
        }
    }
    public boolean started(){return started;}
    @Override public void close(){started=false;scheduler.shutdownNow();}
}
