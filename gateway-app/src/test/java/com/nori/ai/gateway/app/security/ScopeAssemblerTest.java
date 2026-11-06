package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.plan.PlanEnvelope;
import com.nori.ai.gateway.core.plan.PlanValidator;
import com.nori.ai.gateway.core.plan.RejectReason;
import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.registry.ContractVersionPolicy;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.registry.WorkflowDefinition;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.seam.PlanInjectionSeam;
import com.nori.ai.gateway.persistence.queue.JobScopeRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScopeAssemblerTest {
    @Test void workerScopeAllowsExactJobDeadlineAndRejectsItsSuccessor(){
        Instant created=Instant.parse("2026-11-06T00:00:00Z");
        var job=new JobScopeRecord(OwnerSystem.TC,"actor",Set.of(),"a".repeat(64),"job",created,
            "conversation",created.plus(Duration.ofDays(30)),"RUNNING");
        var scope=new ScopeAssembler().forWorker(job);
        var workflow=new WorkflowDefinition(OwnerSystem.TC,TaskType.GENERAL_QUERY,List.of(),
            WorkflowDefinition.AnswerSource.REGISTRY_VIEW,null);
        var registry=new CapabilityRegistry(List.of(),List.of(workflow),Set.of(),new ContractVersionPolicy());
        var plan=new PlanEnvelope(job.jobId(),job.conversationId(),"질문",List.of(),TaskType.GENERAL_QUERY,List.of());
        for(long delta:new long[]{-1,0,1}){
            Instant now=created.plusSeconds(180).plusNanos(delta);
            var outcome=new PlanValidator(registry,new PlanInjectionSeam.NoOp(),Clock.fixed(now,ZoneOffset.UTC),10,16)
                .validate(plan,scope,null);
            if(delta<=0){assertThat(scope.validAt(now)).isTrue();assertThat(outcome.accepted()).isTrue();}
            else{assertThat(scope.validAt(now)).isFalse();assertThat(outcome.rejectReason()).isEqualTo(RejectReason.SYSTEM_SCOPE_MISMATCH);}
        }
    }
}
