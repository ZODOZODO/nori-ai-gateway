package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.plan.ValidationOutcome;
import com.nori.ai.gateway.core.result.*;
import java.util.*;

/** {@code coverage} 는 근거 계획이 있는 실행(분류 wire v3)의 요구 근거별 서버 수집 상태다(SPEC §28.8). 근거 계획이 없으면 비어 있다. */
public record WorkflowOutcome(List<Stage> stages,List<ResultBlock> blocks,List<Evidence> evidence,ResultSourceDeps sourceDeps,
        List<Trace> trace,List<String> toolExecutionRefs,Optional<ValidationOutcome> rejection,List<EvidenceCoverage> coverage) {
    public record Stage(String stepId,ToolResultEnvelope result,boolean observed,int count) {}
    /** SUPPLEMENT_REJECTED = 근거 보완 round 계획이 PlanValidator 준비를 통과하지 못해 그 후보를 실행하지 않음. */
    public enum TraceOutcome { OPTIONAL_STEP_OMITTED, SKIPPED_NO_INPUT, TOOL_LIMIT_REACHED, SUPPLEMENT_REJECTED }
    public record Trace(String stepId,TraceOutcome outcome) {}
    public WorkflowOutcome(List<Stage> stages,List<ResultBlock> blocks,List<Evidence> evidence,ResultSourceDeps sourceDeps,
            List<Trace> trace,List<String> toolExecutionRefs,Optional<ValidationOutcome> rejection) {
        this(stages,blocks,evidence,sourceDeps,trace,toolExecutionRefs,rejection,List.of());
    }
    public WorkflowOutcome {stages=List.copyOf(stages);blocks=List.copyOf(blocks);evidence=List.copyOf(evidence);trace=List.copyOf(trace);toolExecutionRefs=List.copyOf(toolExecutionRefs);Objects.requireNonNull(sourceDeps);Objects.requireNonNull(rejection);coverage=List.copyOf(coverage);}
    public WorkflowOutcome withCoverage(List<EvidenceCoverage> value){return new WorkflowOutcome(stages,blocks,evidence,sourceDeps,trace,toolExecutionRefs,rejection,value);}
}
