package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.authz.SourceAuthorizationStep;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.tcmock.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkerAuthorizationOrderIT extends P09QueueSupport {
    @TestConfiguration(proxyBeanMethods=false) static class StageConfiguration {
        @Bean static org.springframework.beans.factory.config.BeanPostProcessor observeActualSnapshot(org.springframework.beans.factory.ObjectProvider<PausePointRegistry> pauses,org.springframework.beans.factory.ObjectProvider<P09TestPorts.Observations> observations) {
            return new org.springframework.beans.factory.config.BeanPostProcessor(){
                @Override public Object postProcessAfterInitialization(Object bean,String name) {
                    if(!(bean instanceof MockTcBusinessReadAdapter business))return bean;
                    var actual=org.mockito.Mockito.spy(business);
                    org.mockito.Mockito.doAnswer(invocation->{var result=invocation.callRealMethod();var job=observations.getObject().workflows.getLast();pauses.getObject().hit(PausePointRegistry.Point.TOOL_IN_FLIGHT,job.jobId());return result;})
                        .when(actual).getEqpSnapshot(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
                    return actual;
                }
            };
        }
    }
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,StageConfiguration.class,WorkflowSourceFixture.class};}
    private JobAdmissionService.Accepted live(String key,Set<PausePointRegistry.Point> pause){return incident(key,pause);}
    private String payload(String job){return jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:id").param("id",job).query(String.class).single();}
    @Test void validEmptyPreflightPrecedesClassificationAndEachSourceAuthorizationWithoutUnion() throws Exception {
        var point=PausePointRegistry.Point.BEFORE_CLASSIFICATION;var job=live("ordered",Set.of(point));
        await(()->pauses().reachedCount(point,job.jobId())==1);
        assertThat(calls().authorizationRequests()).containsExactly(Set.of());assertThat(calls().count("getEqpSnapshot")).isZero();assertThat(calls().count("direct:logs")).isZero();assertThat(observations().workflows).isEmpty();
        pauses().release(point,job.jobId());await(()->state(job.jobId()).equals("SUCCEEDED"));
        assertThat(calls().authorizationRequests().getFirst()).isEmpty();assertThat(calls().authorizationRequests().stream().skip(1)).allSatisfy(required->assertThat(required).hasSize(1));
        assertThat(calls().authorizationRequests()).contains(Set.of("EQP_READ"),Set.of("AI_LOG_READ"),Set.of("AI_TRACE_READ"));
        assertThat(calls().count("getEqpSnapshot")).isOne();assertThat(context.getBean(WorkflowSourceFixture.Server.class).logs).hasValue(4);
    }
    @Test void validGeneralAndUnsupportedFixturesDoNotRequireSourcePermissions() throws Exception {
        for(TaskType type:List.of(TaskType.GENERAL_QUERY,TaskType.UNSUPPORTED_REQUEST)){
            String key=type.name();hooks().arm("actor",key,new TestHookRegistry.Scenario(type,Set.of(),null,false));var base=scope("actor");
            var withoutSourcePermissions=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(base.boundSystem(),base.authenticatedServiceRef(),base.actorRef(),base.sub(),Set.of(),base.pdg(),base.issuedAt(),base.expiresAt(),base.requestId());
            var job=inRequestSpan(()->context.getBean(JobAdmissionService.class).accept(withoutSourcePermissions,new JobAdmissionService.AdmissionRequest("/v1/query",null,"질문",Map.of(),key,registeredDlg("actor"))));await(()->state(job.jobId()).equals("SUCCEEDED"));
        }
        assertThat(calls().authorizationRequests()).containsExactly(Set.of(),Set.of());assertThat(calls().count("getEqpSnapshot")).isZero();assertThat(calls().count("direct:logs")).isZero();
    }
    @Test void invalidAndDownGateStopGeneralAndBusinessAndAgreeWithActualResultGet() throws Exception {
        for(String scenario:List.of("logout","authz-down"))for(TaskType type:List.of(TaskType.GENERAL_QUERY,TaskType.LIVE_QUERY)){
            context.getBean(MockScenarioStore.class).select(scenario);String key=scenario+type.name();hooks().arm("actor",key,new TestHookRegistry.Scenario(type,Set.of(),null,false));var job=accept("actor",key,"질문",null,dlg());await(()->state(job.jobId()).equals("FAILED"));
            String expected=scenario.equals("logout")?"DENIED":"SOURCE_UNAVAILABLE";
            var row=jdbc().sql("SELECT status,error_code FROM gw.result WHERE job_id=:id").param("id",job.jobId()).query().singleRow();assertThat(row.get("status")).isEqualTo(expected);assertThat(row.get("error_code")).isNull();
            assertThat(jdbc().sql("SELECT count(*) FROM gw.result WHERE job_id=:id").param("id",job.jobId()).query(Integer.class).single()).isOne();
            assertThat(jdbc().sql("SELECT count(*) FROM gw.job_event WHERE job_id=:id AND type='job.failed'").param("id",job.jobId()).query(Integer.class).single()).isOne();
            assertThat(jdbc().sql("SELECT delegation_id IS NULL FROM gw.job WHERE id=:id").param("id",job.jobId()).query(Boolean.class).single()).isTrue();
            var request=java.net.http.HttpRequest.newBuilder(uri("/v1/analyses/"+job.jobId())).header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",signer.sign(claims("actor",null),clock().instant())).GET().build();
            var response=java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);assertThat(json.readTree(response.body()).path("result").path("status").asString()).isEqualTo(expected);
        }
        assertThat(observations().workflows).isEmpty();assertThat(calls().count("getEqpSnapshot")).isZero();assertThat(calls().count("direct:logs")).isZero();
    }
    @Test void logoutOrDownAfterFirstStageBlocksOnlyTheNextSource() throws Exception {
        for(String scenario:List.of("logout","authz-down")){
            context.getBean(MockScenarioStore.class).select("default");var point=PausePointRegistry.Point.TOOL_IN_FLIGHT;var job=live("mid-"+scenario,Set.of(point));await(()->pauses().reachedCount(point,job.jobId())==1);
            if(scenario.equals("logout")){
                String delegation=observations().workflows.getLast().delegation();
                context.getBean(MockDelegationRegistry.class).revoke(MockDelegationRegistry.hash(delegation));
            }else context.getBean(MockScenarioStore.class).select(scenario);
            pauses().release(point,job.jobId());await(()->state(job.jobId()).equals("PARTIAL"));
            var stages=json.readTree(payload(job.jobId())).path("toolSummary");
            assertThat(java.util.stream.StreamSupport.stream(stages.spliterator(),false).filter(stage->stage.path("stepId").asString().equals("logs"))).singleElement().satisfies(stage->assertThat(stage.path("status").asString()).isEqualTo(scenario.equals("logout")?"DENIED":"SOURCE_UNAVAILABLE"));
        }
        assertThat(calls().count("getEqpSnapshot")).isEqualTo(2);assertThat(context.getBean(WorkflowSourceFixture.Server.class).logs).hasValue(0);
    }
    @Test void conversationExpiryBeforePreflightKeepsAuthorizationAndResultAtZero() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");var job=live("expiry",Set.of());jdbc().sql("UPDATE gw.conversation SET expires_at=:at WHERE id=:id").param("at",java.sql.Timestamp.from(NOW.plusSeconds(1))).param("id",job.conversationId()).update();clock().advance(1);pauses().release(PausePointRegistry.Point.DISPATCH_HOLD,"*");await(()->state(job.jobId()).equals("CANCELLED"));
        assertThat(calls().count("authorize")).isZero();assertThat(count("result")).isZero();assertThat(observations().workflows).isEmpty();
    }
}
