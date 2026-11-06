package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.plan.ValidationOutcome;
import com.nori.ai.gateway.core.result.*;
import java.util.*;

public record WorkflowOutcome(List<Stage> stages,List<ResultBlock> blocks,List<Evidence> evidence,ResultSourceDeps sourceDeps,
        List<Trace> trace,List<String> toolExecutionRefs,Optional<ValidationOutcome> rejection) {
    public record Stage(String stepId,ToolResultEnvelope result,boolean observed,int count) {}
    public enum TraceOutcome { OPTIONAL_STEP_OMITTED, SKIPPED_NO_INPUT }
    public record Trace(String stepId,TraceOutcome outcome) {}
    public WorkflowOutcome {stages=List.copyOf(stages);blocks=List.copyOf(blocks);evidence=List.copyOf(evidence);trace=List.copyOf(trace);toolExecutionRefs=List.copyOf(toolExecutionRefs);Objects.requireNonNull(sourceDeps);Objects.requireNonNull(rejection);}
}
