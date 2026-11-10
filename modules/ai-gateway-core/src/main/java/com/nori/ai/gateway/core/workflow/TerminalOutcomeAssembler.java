package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.result.*;
import java.util.*;

/** Spec §5.4의 명시 실패와 자료 관측 집합을 분리해 집계합니다. */
public final class TerminalOutcomeAssembler {
    private static final Set<ResultStatus> DATA=Set.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.PARTIAL,ResultStatus.STALE,ResultStatus.CONFLICT);
    private static final Set<ResultStatus> ALLOWED=Set.of(ResultStatus.SUCCESS,ResultStatus.EMPTY,ResultStatus.PARTIAL,ResultStatus.OUT_OF_RETENTION,ResultStatus.STALE,ResultStatus.CONFLICT,ResultStatus.DENIED,ResultStatus.SOURCE_UNAVAILABLE,ResultStatus.FAILED);
    public TerminalSpec assemble(WorkflowOutcome outcome,ComposerOutcome composer,InferenceMode inference) {
        if(inference==InferenceMode.DISABLED&&composer!=ComposerOutcome.NOT_INVOKED)throw new IllegalArgumentException("비활성 서술 상태가 잘못되었습니다");
        if(outcome.rejection().isPresent()&&outcome.rejection().get().clarification()!=null) {
            var rejected=outcome.rejection().get();
            return TerminalSpec.outcome(JobStatus.FAILED,ResultStatus.FAILED,rejected.errorCode(),Map.of("clarification",rejected.clarification()));
        }
        var payload=new LinkedHashMap<String,Object>();payload.put("blocks",outcome.blocks());payload.put("evidence",outcome.evidence());payload.put("trace",outcome.trace());
        // 거부 사유는 닫힌 enum과 순번만 보존하며 확인 요청의 별도 비노출 분기는 그대로 둡니다.
        outcome.rejection().filter(value->value.status()==ResultStatus.UNSUPPORTED).ifPresent(value->payload.put("validationTrace",value.trace()));
        var summary=new ArrayList<Map<String,Object>>();
        for(var stage:outcome.stages()) {
            var result=stage.result();
            if(!ALLOWED.contains(result.status())||result.status()==ResultStatus.FAILED&&result.errorCode()!=ErrorCode.CONTRACT_INVALID)throw new IllegalArgumentException("단계 결과 상태가 잘못되었습니다");
            var row=new LinkedHashMap<String,Object>();row.put("stepId",stage.stepId());row.put("capabilityId",result.capabilityId());row.put("status",result.status().name());row.put("observed",stage.observed());
            if(stage.observed()){row.put("count",stage.count());row.put("observedAt",result.observedAt());}
            if(result.errorCode()!=null)row.put("errorCode",result.errorCode().name());if(!result.missingFields().isEmpty())row.put("missingFields",result.missingFields());
            if(result.metadata()!=null&&!result.metadata().missingRanges().isEmpty())row.put("missingRanges",result.metadata().missingRanges());summary.add(row);
        }
        payload.put("toolSummary",summary);
        ResultStatus status;ErrorCode error=null;
        if(outcome.rejection().isPresent()) {
            var rejected=outcome.rejection().get();status=rejected.status();error=rejected.errorCode();
            if(rejected.clarification()!=null)payload.put("clarification",rejected.clarification());
        } else {
            var stages=outcome.stages();var statuses=stages.stream().map(stage->stage.result().status()).toList();
            boolean data=stages.stream().anyMatch(stage->stage.observed()&&DATA.contains(stage.result().status()));
            boolean failed=statuses.stream().anyMatch(s->Set.of(ResultStatus.DENIED,ResultStatus.SOURCE_UNAVAILABLE,ResultStatus.FAILED).contains(s));
            if(stages.isEmpty())status=ResultStatus.SUCCESS;
            else if(failed&&data)status=ResultStatus.PARTIAL;
            else if(failed){status=statuses.contains(ResultStatus.FAILED)?ResultStatus.FAILED:statuses.contains(ResultStatus.SOURCE_UNAVAILABLE)?ResultStatus.SOURCE_UNAVAILABLE:ResultStatus.DENIED;if(status==ResultStatus.FAILED)error=ErrorCode.CONTRACT_INVALID;}
            else if(statuses.contains(ResultStatus.PARTIAL)||data&&statuses.contains(ResultStatus.OUT_OF_RETENTION))status=ResultStatus.PARTIAL;
            else if(statuses.stream().allMatch(s->s==ResultStatus.OUT_OF_RETENTION))status=ResultStatus.OUT_OF_RETENTION;
            else if(data){status=List.of(ResultStatus.CONFLICT,ResultStatus.STALE,ResultStatus.SUCCESS,ResultStatus.EMPTY).stream().filter(statuses::contains).findFirst().orElseThrow();}
            else throw new IllegalArgumentException("관측 없는 단계 자료를 집계할 수 없습니다");
        }
        if(inference==InferenceMode.ENABLED&&composer==ComposerOutcome.INPUT_BUDGET_EXCEEDED){status=ResultStatus.FAILED;error=ErrorCode.INPUT_BUDGET_EXCEEDED;}
        if(inference==InferenceMode.ENABLED&&composer==ComposerOutcome.LLM_UNAVAILABLE){status=outcome.blocks().isEmpty()&&outcome.evidence().isEmpty()?ResultStatus.FAILED:ResultStatus.PARTIAL;error=ErrorCode.LLM_UNAVAILABLE;payload.put("missingFields",List.of("answer"));}
        payload.put("status",status.name());
        JobStatus job=status==ResultStatus.PARTIAL?JobStatus.PARTIAL:Set.of(ResultStatus.FAILED,ResultStatus.DENIED,ResultStatus.SOURCE_UNAVAILABLE).contains(status)?JobStatus.FAILED:JobStatus.SUCCEEDED;
        String event=job==JobStatus.PARTIAL?"job.partial":job==JobStatus.FAILED?"job.failed":"job.completed";
        Map<String,Object> deps=SchemaContracts.JSON.convertValue(outcome.sourceDeps(),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
        return new TerminalSpec(job,status,error,event,false,payload,deps,TerminalSpec.Cause.NORMAL);
    }
}
