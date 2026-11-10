package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.workflow.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.PlanInjectionSeam;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class WorkflowPlanBuilderTest {
    private final CapabilityRegistry registry=new CapabilityRegistry(new ManifestLoader().loadClasspath("manifests/tc/index.json"),workflows(),Set.of(ImplementationPhase.PHASE_1),new ContractVersionPolicy());
    private final WorkflowPlanBuilder builder=new WorkflowPlanBuilder(registry);
    private JobContext job(TaskType task,Map<String,Object> fields){return new JobContext("request","conversation",scope(),"delegation",NOW.plusSeconds(1000),new CancellationToken(),InferenceMode.DISABLED,Map.of("taskType",task.name()),new JobRequestInput("message",1,"질문",fields));}
    private WorkflowDefinition workflow(TaskType task){return workflows().stream().filter(w->w.taskType()==task).findFirst().orElseThrow();}
    @Test void omittedNullAndTrueOptionsKeepNormalCarrierWhileFalseBlocksOnlyRequiredTools() {
        for(var fields:List.of(Map.<String,Object>of(),Collections.<String,Object>singletonMap("options",null),Map.<String,Object>of("options",Collections.singletonMap("allowTools",null)),Map.<String,Object>of("options",Map.of("allowTools",true))))assertThatCode(()->builder.checkOptions(workflow(TaskType.LIVE_QUERY),job(TaskType.LIVE_QUERY,fields))).doesNotThrowAnyException();
        var blocked=job(TaskType.LIVE_QUERY,Map.of("options",Map.of("allowTools",false,"allowKnowledge",true)));assertThatThrownBy(()->builder.checkOptions(workflow(TaskType.LIVE_QUERY),blocked)).isInstanceOf(WorkflowPlanBuilder.Blocked.class);
        var general=job(TaskType.GENERAL_QUERY,Map.of("options",Map.of("allowTools",false,"allowKnowledge",false)));assertThat(builder.build(workflow(TaskType.GENERAL_QUERY),registry.view(general.scope()),general).steps()).isEmpty();
        var knowledge=job(TaskType.KNOWLEDGE_QUERY,Map.of("options",Map.of("allowTools",true,"allowKnowledge",false)));assertThatThrownBy(()->builder.checkOptions(workflow(TaskType.KNOWLEDGE_QUERY),knowledge)).isInstanceOf(WorkflowPlanBuilder.Blocked.class);
    }
    @Test void processorGraphProjectionKeepsRuntimeQAbsentAndCanonicalCacheFlags() {
        var job=job(TaskType.INCIDENT_ANALYSIS,Map.of("resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001")),"options",Map.of("allowKnowledge",false)));
        var result=builder.build(workflow(TaskType.INCIDENT_ANALYSIS),registry.view(job.scope()),job,Map.of("from","2026-11-04T00:00Z","to","2026-11-04T01:00Z"));
        assertThat(result.steps()).extracting(step->step.definition().stepId()).containsExactly("snapshot","commLogs","logs","trace","dlq","summary","git");
        var git=result.toolPlan().plan().getLast();assertThat(git.dependsOn()).containsExactly("dlq");assertThat(git.arguments().has("q")).isFalse();
        assertThat(result.steps().stream().filter(step->step.definition().cacheable())).extracting(step->step.definition().stepId()).containsExactly("logs","trace","git");
    }
    @Test void implementedKnowledgeKeepsTheCanonicalEquipmentFilterAndAllResourceRejections() {
        var manifests=new ManifestLoader().loadClasspath("manifests/tc/index.json").stream().map(value->value.toolKind()==ToolKind.KNOWLEDGE?modify(value,"availability","IMPLEMENTED"):value).toList();
        var active=new CapabilityRegistry(manifests,workflows(),Set.of(ImplementationPhase.PHASE_1),new ContractVersionPolicy());
        var job=job(TaskType.INCIDENT_ANALYSIS,Map.of("resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001"))));
        var execution=new WorkflowPlanBuilder(active).build(workflow(TaskType.INCIDENT_ANALYSIS),active.view(job.scope()),job,Map.of("from","2026-11-04T00:00Z","to","2026-11-04T01:00Z"));
        var knowledge=execution.toolPlan().plan().getLast();var manifest=active.find(knowledge.capabilityId()).orElseThrow();
        assertThat(manifest.resourceTypes()).containsExactlyInAnyOrder("DOCUMENT","EQP");assertThat(knowledge.capabilityId()).isEqualTo("tc.knowledge.search");
        assertThat(knowledge.arguments().path("eqp").asString()).isEqualTo("EQP_001");assertThat(ResourceInputs.bound(knowledge,manifest)).containsExactly(new ResourceRef("EQP","EQP_001"));
        class CurrentResolver implements WorkflowResourceResolver {
            OwnerSystem owner=OwnerSystem.TC;boolean exists=true;
            public Lookup lookup(ResourceRef ref,VerifiedSystemScope scope){return exists?new Found(new ResourceState(owner,ref.type(),true)):new Absent();}
            public SourceBinding source(CapabilityManifest tool,VerifiedSystemScope scope){return new SourceBinding(tool.ownerSystem(),tool.sourceAllowlistRef(),true);}
            public java.time.ZoneId timezone(VerifiedSystemScope scope){return java.time.ZoneOffset.UTC;}
            public com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall delegatedCall(){return new com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall("actor","d".repeat(22));}
            public void beforeRead(){}
        }
        var resolver=new CurrentResolver();var validator=validator(active,new PlanInjectionSeam.NoOp());
        assertThat(validator.prepare(execution.toolPlan(),job.scope(),resolver)).isInstanceOf(PlanValidator.Prepared.class);
        resolver.owner=OwnerSystem.WM;assertThat(((PlanValidator.Rejected)validator.prepare(execution.toolPlan(),job.scope(),resolver)).outcome().rejectReason()).isEqualTo(RejectReason.OWNER_MISMATCH);
        resolver.owner=OwnerSystem.TC;resolver.exists=false;assertThat(((PlanValidator.Rejected)validator.prepare(execution.toolPlan(),job.scope(),resolver)).outcome().rejectReason()).isEqualTo(RejectReason.RESOURCE_NOT_FOUND);resolver.exists=true;
        for(var resources:List.of(List.of(new ResourceRef("LOT","lot")),List.of(new ResourceRef("EQP","EQP_001"),new ResourceRef("EQP","EQP_002")))) {
            var changed=new PlanStep(knowledge.stepId(),knowledge.capabilityId(),knowledge.dependsOn(),resources,SchemaContracts.JSON.createObjectNode().put("q","EQUIPMENT_ERROR"));
            var steps=new ArrayList<>(execution.toolPlan().plan());steps.set(steps.size()-1,changed);var original=execution.toolPlan();var invalid=new PlanEnvelope(original.requestId(),original.conversationId(),original.userRequest(),original.targetResources(),original.taskType(),steps);
            var rejected=(PlanValidator.Rejected)validator.prepare(invalid,job.scope(),resolver);assertThat(rejected.outcome().rejectReason()).isEqualTo(resources.size()>1?RejectReason.PLAN_INVALID:RejectReason.RESOURCE_NOT_FOUND);
        }
    }
}
