package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.result.ResultStatus;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SourceAuthzNegativeSeamIT extends P09QueueSupport {
    private VerifiedSystemScope runtimeScope(String job){var now=clock().instant();return new VerifiedSystemScope(OwnerSystem.TC,"job-worker",new ActorRef("actor"),"actor",Set.of("EQP_READ"),P09TestPorts.PDG,now.minusSeconds(1),now.plusSeconds(60),job);}
    private final String capability="tc.eqp.getOperationalSnapshot";
    private PlanStep step(){return new PlanStep("one",capability,List.of(),List.of(new ResourceRef("EQP","EQP_001")),SchemaContracts.parse("{\"eqp\":\"EQP_001\"}"));}
    private ResourceResolver resolver(){return new ResourceResolver(){
        public ResourceState resolve(ResourceRef ref,VerifiedSystemScope scope){return new ResourceState(OwnerSystem.TC,"EQP",true);}
        public SourceBinding source(CapabilityManifest manifest,VerifiedSystemScope scope){return new SourceBinding(OwnerSystem.TC,manifest.sourceAllowlistRef(),true);}
        public CurrentAuthorization authorize(CapabilityManifest manifest,PlanStep step,VerifiedSystemScope scope){return new CurrentAuthorization(OwnerSystem.TC,manifest.sourceAllowlistRef(),scope.actorRef(),scope.pdg(),true,scope.perm(),Set.of("READ"));}
        public ZoneId timezone(VerifiedSystemScope scope){return ZoneOffset.UTC;}
    };}
    private JobAdmissionService.Accepted armed(String key,Map<String,Object> directive)throws Exception{
        assertThat(hook("/__test/arm","POST",json.writeValueAsString(Map.of("actorSub","actor","requestKey",key,"scenario",Map.of("taskType","GENERAL_QUERY","pauseAt",List.of("BEFORE_CLASSIFICATION"),"seam",directive))),hookToken).statusCode()).isEqualTo(204);
        var job=accept("actor",key,"주입 시험",null,dlg());await(()->pauses().reachedCount(PausePointRegistry.Point.BEFORE_CLASSIFICATION,job.jobId())==1);return job;
    }
    @Test void actualArmAdmissionBindingAndDispatcherSourceOverrideAffectOneJobOnly()throws Exception{
        var job=armed("source",Map.of("kind","SOURCE_ALLOWLIST_REF_OVERRIDE","capabilityId",capability,"ownerSystem","WM"));var scope=runtimeScope(job.jobId());
        var plan=new PlanEnvelope(job.jobId(),job.conversationId(),"질문",List.of(),TaskType.LIVE_QUERY,List.of(step()));
        assertThat(context.getBean(PlanValidator.class).validate(plan,scope,resolver()).accepted()).isTrue();
        var traces=new ArrayList<ExecutionContext.DispatchTrace>();
        assertThat(context.getBean(ToolDispatcher.class).dispatch(step(),scope,new ExecutionContext(resolver(),traces::add)).status()).isEqualTo(ResultStatus.DENIED);
        assertThat(traces).extracting(ExecutionContext.DispatchTrace::reason).containsExactly(RejectReason.SOURCE_NOT_ALLOWED);
        var original=context.getBean(CapabilityRegistry.class).find(capability).orElseThrow();assertThat(original.sourceAllowlistRef()).startsWith("sources://TC/");
        assertThat(context.getBean(SeamRegistry.class).inspect(new DispatchTarget(original,List.of(),resolver().source(original,scope)),runtimeScope("another-job")).source().ownerSystem()).isEqualTo(OwnerSystem.TC);
        assertThat(hook("/__test/reset","POST","{}",hookToken).statusCode()).isEqualTo(409);
        int reservations=hooks().reservationCount();assertThat(accept("actor","source","주입 시험",null,dlg()).jobId()).isEqualTo(job.jobId());assertThat(hooks().reservationCount()).isEqualTo(reservations);
        pauses().release(PausePointRegistry.Point.BEFORE_CLASSIFICATION,job.jobId());await(()->state(job.jobId()).equals("SUCCEEDED"));
    }
    @Test void permissionMapDropDeniesOnlyMatchingJobBeforeAnyFurtherTcOrSourceCall()throws Exception{
        var job=armed("drop",Map.of("kind","PERMISSION_MAP_DROP","sourceId","sources://TC/logs"));String delegation=registeredDlg("actor");var step=context.getBean(SourceAuthorizationStep.class);long before=calls().count("authorize");
        assertThat(step.authorizeBefore("sources://TC/logs",runtimeScope(job.jobId()),new DelegatedCall("actor",delegation))).isInstanceOf(SourceAuthorizationStep.Denied.class);
        assertThat(calls().count("authorize")).isEqualTo(before);
        assertThat(step.authorizeBefore("sources://TC/logs",runtimeScope("next-job"),new DelegatedCall("actor",delegation))).isInstanceOf(SourceAuthorizationStep.Granted.class);
        assertThat(context.getBean(SourcePermissionMap.class).find("sources://TC/logs")).isPresent();
        pauses().release(PausePointRegistry.Point.BEFORE_CLASSIFICATION,job.jobId());await(()->state(job.jobId()).equals("SUCCEEDED"));
    }
    @Test void validatorUsesBoundScopeForImmutablePlannedProjectionAndNextJobIsUnaffected()throws Exception{
        var job=armed("planned",Map.of("kind","PLANNED","capabilityId",capability));var plan=new PlanEnvelope(job.jobId(),job.conversationId(),"질문",List.of(),TaskType.LIVE_QUERY,List.of(step()));
        assertThat(context.getBean(PlanValidator.class).validate(plan,runtimeScope(job.jobId()),resolver()).rejectReason()).isEqualTo(RejectReason.PLANNED);
        var next=new PlanEnvelope("next",job.conversationId(),"질문",List.of(),TaskType.LIVE_QUERY,List.of(step()));assertThat(context.getBean(PlanValidator.class).validate(next,runtimeScope("next"),resolver()).accepted()).isTrue();
        pauses().release(PausePointRegistry.Point.BEFORE_CLASSIFICATION,job.jobId());await(()->state(job.jobId()).equals("SUCCEEDED"));
    }
}
