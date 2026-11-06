package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.config.CapacityProfile;
import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.job.recovery.ReadinessGate;
import com.nori.ai.gateway.core.job.InferenceReadiness;
import com.nori.ai.gateway.persistence.maintenance.QueueMaintenanceRepository;
import com.nori.ai.gateway.persistence.queue.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;

public final class JobDispatcher implements AutoCloseable {
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("job-dispatch-",0).factory());
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final TransactionTemplate transactions;
    private final QueueMaintenanceRepository control;
    private final QueueJobRepository queue;
    private final JobWorker worker;
    private final InferenceReadiness inference;
    private final ReadinessGate gate;
    private final CapacityProfile capacity;
    private final Clock clock;
    private final SlotLedger slots;
    private final Optional<PausePointRegistry> pauses;
    private final JobEventPublisher publisher;
    private volatile boolean started;
    private volatile Throwable failure;
    public JobDispatcher(TransactionTemplate transactions,QueueMaintenanceRepository control,QueueJobRepository queue,JobWorker worker,
            InferenceReadiness inference,ReadinessGate gate,CapacityProfile capacity,Clock clock,SlotLedger slots,Optional<PausePointRegistry> pauses,JobEventPublisher publisher){
        this.transactions=transactions;this.control=control;this.queue=queue;this.worker=worker;this.inference=inference;this.gate=gate;
        this.capacity=capacity;this.clock=clock;this.slots=slots;this.pauses=pauses;this.publisher=publisher;
    }
    @EventListener public void start(ReadinessGate.RecoveryCompleted event){if(!started){started=true;scheduler.scheduleWithFixedDelay(this::dispatchSafely,0,250,TimeUnit.MILLISECONDS);}}
    @EventListener public void wake(JobAdmissionService.AdmissionCommitted event){if(started)scheduler.execute(()->{publisher.publishQueued(event.jobId());dispatchSafely();});}
    @EventListener public void finished(JobEventPublisher.TerminalCommitted event){slots.release(event.jobId());if(started)scheduler.execute(this::dispatchSafely);}
    private void dispatchSafely(){try{dispatch();}catch(RuntimeException failed){failure=failed;gate.down();}}
    private void dispatch(){
        if(!gate.ready()||!inference.ready()||pauses.map(p->p.held(PausePointRegistry.Point.DISPATCH_HOLD)).orElse(false))return;
        var selected=transactions.execute(tx->{
            control.lockControl();
            if(!gate.ready()||queue.count("RUNNING")>=capacity.maxRunningJobs()||!inference.ready())return Optional.<JobScopeRecord>empty();
            var job=queue.nextQueued();
            if(job.isPresent()&&clock.instant().isAfter(job.get().createdAt().plus(capacity.queueWaitLimit())))return Optional.<JobScopeRecord>empty();
            if(job.isPresent()&&!queue.start(job.get().jobId(),clock.instant()))return Optional.<JobScopeRecord>empty();return job;
        });
        if(selected!=null)selected.ifPresent(job->{
            publisher.publishQueued(job.jobId());publisher.publishStarted(job.jobId());
            var token=slots.claim(job.jobId());workers.execute(()->worker.run(job,token));
        });
    }
    public boolean started(){return started;}
    public Optional<Throwable> failure(){return Optional.ofNullable(failure);}
    @Override public void close(){started=false;scheduler.shutdownNow();workers.shutdownNow();}
}
