package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.dispatch.ToolDispatcher.InvocationRecord;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.job.JobContext;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.result.ToolResultEnvelope;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.workflow.WorkflowExecutor.ExecutionRecorder;
import com.nori.ai.gateway.persistence.repository.ToolExecutionRepository;
import java.util.*;

public final class WorkflowExecutionRecords implements ExecutionRecorder {
    private final ToolExecutionRepository executions;private final ContentSanitizer sanitizer;private final ExpiryCheckpoint expiry;
    private final CapabilityRegistry registry;
    public WorkflowExecutionRecords(ToolExecutionRepository executions,ContentSanitizer sanitizer,ExpiryCheckpoint expiry,CapabilityRegistry registry){this.executions=executions;this.sanitizer=sanitizer;this.expiry=expiry;this.registry=registry;}
    @Override public void record(JobContext job,InvocationRecord record) {
        expiry.assertLive(job.conversationExpiresAt(),job.cancellation());
        var payload=SchemaContracts.JSON.createObjectNode().put("id",record.id()).put("capabilityId",record.capabilityId()).put("status",record.status().name());
        record.errorCode().ifPresent(code->payload.put("errorCode",code.name()));
        record.validatedResult().ifPresent(value->{
            var manifest=registry.find(value.capabilityId()).orElseThrow(()->new IllegalStateException("실행 계약을 확인할 수 없습니다"));
            ToolResultEnvelope decoded=WorkflowContent.tool(value,manifest,job.scope(),sanitizer);
            if(!record.id().equals(decoded.toolExecutionId())||!record.capabilityId().equals(decoded.capabilityId())||decoded.ownerSystem()!=job.scope().boundSystem()
                    ||decoded.evidence().stream().anyMatch(item->!job.scope().actorRef().equals(item.actorAccessScope())))throw new IllegalStateException("실행 기록 계약이 다릅니다");
            if(!Objects.equals(value.sourceRevision(),decoded.sourceRevision())||!value.evidence().equals(decoded.evidence())||!Objects.equals(value.metadata(),decoded.metadata())||!Objects.equals(value.dataRef(),decoded.dataRef()))throw new IllegalStateException("실행 기록 식별자가 다릅니다");
            if(!com.nori.ai.gateway.core.evidence.EvidenceAssembler.projectKnowledge(manifest,value,job.scope()).equals(com.nori.ai.gateway.core.evidence.EvidenceAssembler.projectKnowledge(manifest,decoded,job.scope())))throw new IllegalStateException("실행 기록 문서 의존성이 다릅니다");
            for(var block:decoded.blocks())if(block.content()!=null&&!SchemaContracts.matches(manifest.outputSchema(),block.content()))throw new IllegalStateException("실행 기록 내용 계약이 다릅니다");
            payload.set("result",SchemaContracts.JSON.valueToTree(decoded));
        });
        var encoded=SchemaContracts.JSON.writeValueAsString(payload);
        if(!executions.insert(job.scope(),job.jobId(),new ToolExecutionRepository.Entry(record.id(),record.capabilityId(),record.status().name(),encoded))) {
            expiry.assertLive(job.conversationExpiresAt(),job.cancellation());throw new IllegalStateException("실행 기록을 저장할 수 없습니다");
        }
    }
}
