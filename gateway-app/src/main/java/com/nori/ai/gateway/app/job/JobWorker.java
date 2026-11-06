package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.config.InferenceModeProperties;
import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.app.security.ScopeAssembler;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.persistence.queue.*;
import com.nori.ai.gateway.persistence.repository.JobRepository;
import java.time.*;
import java.util.*;

public final class JobWorker {
    private final JobDelegationReader delegations;
    private final ScopeAssembler scopes;
    private final ConversationExpiryGuard expiry;
    private final Clock clock;
    private final TcSourceAuthorizationPort authorization;
    private final WorkflowExecutor workflow;
    private final JobTerminalCommitter terminal;
    private final JobRepository jobs;
    private final InferenceModeProperties inference;
    private final Optional<StructuredClassificationHook> classification;
    private final Optional<PausePointRegistry> pauses;
    private final Optional<FaultInjector> faults;
    private final SlotLedger slots;
    public JobWorker(JobDelegationReader delegations,ScopeAssembler scopes,ConversationExpiryGuard expiry,Clock clock,
            TcSourceAuthorizationPort authorization,WorkflowExecutor workflow,JobTerminalCommitter terminal,JobRepository jobs,
            InferenceModeProperties inference,Optional<StructuredClassificationHook> classification,Optional<PausePointRegistry> pauses,Optional<FaultInjector> faults,SlotLedger slots){
        this.delegations=delegations;this.scopes=scopes;this.expiry=expiry;this.clock=clock;this.authorization=authorization;this.workflow=workflow;
        this.terminal=terminal;this.jobs=jobs;this.inference=inference;this.classification=classification;this.pauses=pauses;this.faults=faults;this.slots=slots;
    }
    public void run(JobScopeRecord job,CancellationToken token){
        token.attachWorker(Thread.currentThread());TerminalSpec result;
        try{
            String delegation=delegations.forWorker(job.jobId()).orElse(null);
            expiry.assertLive(job.conversationExpiresAt(),clock.instant());
            var scope=scopes.forWorker(job);
            var persisted=jobs.find(scope,job.jobId());
            if(persisted.isEmpty()){expiry.assertLive(job.conversationExpiresAt(),clock.instant());return;}
            if(persisted.get().cancelRequestedAt()!=null)token.signal(CancellationToken.Reason.CANCELLED);
            if(token.reason().isPresent())result=cancelled(token);
            else{
                TcSourceAuthorizationPort.Outcome outcome;
                try{outcome=authorization.authorize(new TcSourceAuthorizationPort.DelegatedCall(scope.sub(),delegation),Set.of());}
                catch(RuntimeException unavailable){outcome=new TcSourceAuthorizationPort.Unavailable();}
                if(outcome instanceof TcSourceAuthorizationPort.DelegationInvalid)result=TerminalSpec.forCause(TerminalSpec.Cause.START_GATE_DENIED);
                else if(!(outcome instanceof TcSourceAuthorizationPort.Allowed allowed)||allowed.pdg()==null||!allowed.pdg().matches("[0-9a-f]{64}"))result=TerminalSpec.forCause(TerminalSpec.Cause.START_GATE_UNAVAILABLE);
                else{
                    if(token.reason().isPresent()){result=cancelled(token);}
                    else{
                    faults.ifPresent(f->f.haltAt(job.jobId(),PausePointRegistry.Point.BEFORE_CLASSIFICATION));
                    if(pauses.isPresent())pauses.get().hit(PausePointRegistry.Point.BEFORE_CLASSIFICATION,job.jobId());
                    expiry.assertLive(job.conversationExpiresAt(),clock.instant());
                    Map<String,Object> classified=classification.orElseThrow(()->new IllegalStateException("분류 구현이 없습니다")).classify(job.jobId());
                    result=workflow.execute(new JobContext(job.jobId(),job.conversationId(),scope,delegation,job.conversationExpiresAt(),token,inference.mode(),classified));
                    }
                }
            }
        }catch(ConversationExpiryGuard.ConversationExpired expired){token.signal(CancellationToken.Reason.EXPIRED);result=cancelled(token);}
        catch(InterruptedException interrupted){
            if(token.reason().isEmpty())token.signal(CancellationToken.Reason.CANCELLED);
            result=cancelled(token);
        }catch(Exception failure){
            result=token.reason().isPresent()?cancelled(token):TerminalSpec.outcome(JobStatus.FAILED,ResultStatus.FAILED,ErrorCode.CONTRACT_INVALID,Map.of());
        }
        // interrupt 응답과 전송 계층 종료는 다르므로 실제 호출 종료를 끝까지 확인합니다.
        boolean interrupted=false;
        for(;;){try{Thread.interrupted();token.awaitTermination(Duration.ofSeconds(10));break;}catch(InterruptedException retry){interrupted=true;}}
        slots.markTerminated(job.jobId());
        // 종료 표지 뒤에는 외부 신호가 차단되므로 직전 신호의 interrupt를 종결 tx 전에 소비합니다.
        interrupted|=Thread.interrupted();
        if(token.reason().isPresent())result=cancelled(token);
        terminal.commit(job.jobId(),result);
        if(interrupted)Thread.currentThread().interrupt();
    }
    private TerminalSpec cancelled(CancellationToken token){return TerminalSpec.forCause(switch(token.reason().orElse(CancellationToken.Reason.CANCELLED)){
        case EXPIRED->TerminalSpec.Cause.EXPIRY_EDGE;case TIMEOUT->TerminalSpec.Cause.JOB_TIMEOUT;case CANCELLED->TerminalSpec.Cause.CANCELLED;});}
}
