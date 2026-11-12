package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import java.util.*;

public final class WorkflowPlanBuilder {
    public static final class Blocked extends RuntimeException { public Blocked() { super("요청 옵션으로 필수 조회가 차단되었습니다"); } }
    private final CapabilityRegistry registry;
    public WorkflowPlanBuilder(CapabilityRegistry registry) { this.registry=registry; }
    public void checkOptions(WorkflowDefinition definition,JobContext context) {
        for(var step:definition.steps()) if(step.capabilityId()!=null&&!step.optional()&&blocked(step,context))throw new Blocked();
    }
    private boolean blocked(WorkflowDefinition.Step step,JobContext job) {
        Object value=job.requestInput().requestFields().get("options");
        if (!(value instanceof Map<?,?> options)) return false;
        if (Boolean.FALSE.equals(options.get("allowTools"))) return true;
        CapabilityManifest manifest=registry.find(step.capabilityId()).orElseThrow();
        return manifest.toolKind()==ToolKind.KNOWLEDGE&&Boolean.FALSE.equals(options.get("allowKnowledge"));
    }
    public WorkflowExecutionPlan build(WorkflowDefinition definition,RegistryView view,JobContext job) {
        Object raw=job.requestInput().requestFields().get("context");
        Map<String,Object> values=new LinkedHashMap<>();
        if(raw instanceof Map<?,?> context&&context.get("timeWindow") instanceof Map<?,?> window)window.forEach((key,value)->values.put((String)key,value));
        return build(definition,view,job,values);
    }
    public WorkflowExecutionPlan build(WorkflowDefinition definition,RegistryView view,JobContext job,Map<String,Object> resolvedWindow) {
        if(definition.ownerSystem()!=job.scope().boundSystem()||view.ownerSystem()!=job.scope().boundSystem()||!job.jobId().equals(job.scope().requestId())
                ||!Objects.equals(job.classification().get("taskType"),definition.taskType().name()))throw new IllegalArgumentException("실행 소유 범위가 다릅니다");
        checkOptions(definition,job);
        var resources=resources(job);
        Map<String,WorkflowDefinition.Step> byId=new LinkedHashMap<>();definition.steps().forEach(step->byId.put(step.stepId(),step));
        List<WorkflowExecutionPlan.ExecutionStep> steps=new ArrayList<>();List<PlanStep> tools=new ArrayList<>();Set<String> selected=new LinkedHashSet<>();
        for(var step:definition.steps()) {
            if(step.capabilityId()==null){steps.add(new WorkflowExecutionPlan.ExecutionStep(step,null));selected.add(step.stepId());continue;}
            var manifest=registry.find(step.capabilityId()).orElseThrow();
            if(step.optional()&&(blocked(step,job)||view.find(step.capabilityId()).isEmpty()))continue;
            if(step.cacheable()&&(manifest.toolKind()==ToolKind.BUSINESS||manifest.toolKind()==ToolKind.KNOWLEDGE))throw new IllegalArgumentException("캐시 실행 정책이 다릅니다");
            var args=SchemaContracts.JSON.createObjectNode();var props=manifest.inputSchema().get("properties");
            for(var property:props.properties()) {
                String key=property.getKey();var type=property.getValue().get("x-resourceType");
                if(type!=null&&type.isString()) {
                    var refs=resources.stream().filter(ref->ref.type().equals(type.asString())).toList();
                    if(refs.size()==1)args.put(key,refs.getFirst().ref());
                } else if(resolvedWindow.get(key) instanceof String text)args.put(key,text);
                else if("q".equals(key)&&job.classification().get("q") instanceof String q)args.put(key,q);
            }
            LinkedHashSet<String> dependencies=new LinkedHashSet<>();
            for(String dependency:step.dependsOn())ancestors(dependency,byId,selected,dependencies);
            PlanStep tool=new PlanStep(step.stepId(),step.capabilityId(),List.copyOf(dependencies),resources,args);
            tools.add(tool);steps.add(new WorkflowExecutionPlan.ExecutionStep(step,tool));selected.add(step.stepId());
        }
        // 실행 Tool이 없는 도움말은 선택 입력을 보존하되 업무 리소스 조회를 만들지 않습니다.
        var targets=tools.isEmpty()?List.<ResourceRef>of():resources;
        var envelope=new PlanEnvelope(job.jobId(),job.conversationId(),job.requestInput().maskedQuestion(),targets,definition.taskType(),tools);
        return new WorkflowExecutionPlan(definition,envelope,steps);
    }
    /**
     * 화면 선택 리소스에 질문이 명시한 설비(분류 {@code eqp})를 더한다. 같은 값이면 하나로 두고, 다르면 둘 다 남겨 임의로 하나를 고르지 않는다
     * (SPEC §21.3 "식별이 모호하면 임의의 대상을 선택하지 않는다" — 단일 인자에 결속되지 않아 기존 입력 검증의 확인 요청이 된다).
     * 추가된 값은 Plan Validator ⑧ 의 존재·타입 검증을 받는다.
     */
    private static List<ResourceRef> resources(JobContext job) {
        var selected=job.requestInput().selectedResourceRefs();
        if(!(job.classification().get("eqp") instanceof String eqp))return selected;
        var explicit=new ResourceRef("EQP",eqp);
        if(selected.contains(explicit))return selected;
        var combined=new ArrayList<>(selected);combined.add(explicit);return List.copyOf(combined);
    }
    private void ancestors(String id,Map<String,WorkflowDefinition.Step> steps,Set<String> selected,Set<String> result) {
        var step=steps.get(id);if(step==null)throw new IllegalArgumentException("선행 단계가 없습니다");
        if(step.capabilityId()!=null&&selected.contains(id))result.add(id);
        else for(String dependency:step.dependsOn())ancestors(dependency,steps,selected,result);
    }
}
