package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import java.util.*;

/** 원 workflow 일정과 Tool 검증 projection을 같은 Job에 결속합니다. {@code evidence} 는 분류 wire v3 근거 계획이며 결속 근거 요구가 없는 실행(F1 구조화 분류)은 비어 있다. */
public record WorkflowExecutionPlan(WorkflowDefinition definition,PlanEnvelope toolPlan,List<ExecutionStep> steps,Optional<EvidencePlan> evidence) {
    public WorkflowExecutionPlan(WorkflowDefinition definition,PlanEnvelope toolPlan,List<ExecutionStep> steps){this(definition,toolPlan,steps,Optional.empty());}
    public record ExecutionStep(WorkflowDefinition.Step definition,PlanStep toolStep) {
        public ExecutionStep {
            Objects.requireNonNull(definition);
            if ((definition.capabilityId()==null)!=(toolStep==null) || toolStep!=null && (!definition.stepId().equals(toolStep.stepId()) || !definition.capabilityId().equals(toolStep.capabilityId()))) throw new IllegalArgumentException("실행 단계 연결이 잘못되었습니다");
        }
    }
    public WorkflowExecutionPlan {
        Objects.requireNonNull(definition);Objects.requireNonNull(toolPlan);steps=List.copyOf(steps);Objects.requireNonNull(evidence);
        if (definition.taskType()!=toolPlan.taskType()) throw new IllegalArgumentException("실행 분류가 다릅니다");
        int index=-1;Set<String> ids=new HashSet<>();List<PlanStep> projected=new ArrayList<>();
        for (var step:steps) {
            int next=definition.steps().indexOf(step.definition());
            if (next<=index || !ids.add(step.definition().stepId())) throw new IllegalArgumentException("실행 순서가 잘못되었습니다");
            index=next;if(step.toolStep()!=null)projected.add(step.toolStep());
        }
        if (!projected.equals(toolPlan.plan())) throw new IllegalArgumentException("Tool 계획이 다릅니다");
        for (var step:definition.steps()) if (!step.optional()&&!ids.contains(step.stepId())) throw new IllegalArgumentException("필수 단계가 없습니다");
    }
}
