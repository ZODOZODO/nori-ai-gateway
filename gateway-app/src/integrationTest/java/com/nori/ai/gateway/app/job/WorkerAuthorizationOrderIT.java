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
        @Bean P09TestPorts.StageProbe realAuthorizationStageFixture(MockTcBusinessReadAdapter business,SourceAuthorizationStep authorization,MockCallCounter calls,PausePointRegistry pauses) {
            return job->{
                var call=new DelegatedCall(job.scope().sub(),job.delegation());
                var eqp=business.getEqpSnapshot(call,"EQP-001");
                pauses.hit(PausePointRegistry.Point.TOOL_IN_FLIGHT,job.jobId());
                var source=authorization.authorizeBefore("sources://TC/logs",job.scope(),call);
                String status;
                if(source instanceof SourceAuthorizationStep.Granted){calls.increment("direct:logs");status="SUCCESS";}
                else status=source instanceof SourceAuthorizationStep.Denied?"DENIED":"SOURCE_UNAVAILABLE";
                boolean complete=eqp.status()==ResultStatus.SUCCESS&&status.equals("SUCCESS");
                return TerminalSpec.outcome(complete?JobStatus.SUCCEEDED:JobStatus.PARTIAL,complete?ResultStatus.SUCCESS:ResultStatus.PARTIAL,null,
                    Map.of("eqpStatus",eqp.status().name(),"logsStatus",status));
            };
        }
    }
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,StageConfiguration.class};}
    private JobAdmissionService.Accepted live(String key,Set<PausePointRegistry.Point> pause){hooks().arm("actor",key,new TestHookRegistry.Scenario(TaskType.LIVE_QUERY,pause,null,false));return accept("actor",key,"업무 조회",null,dlg());}
    private String payload(String job){return jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:id").param("id",job).query(String.class).single();}
    @Test void validEmptyPreflightPrecedesClassificationAndEachSourceAuthorizationWithoutUnion() throws Exception {
        var point=PausePointRegistry.Point.BEFORE_CLASSIFICATION;var job=live("ordered",Set.of(point));
        await(()->pauses().reachedCount(point,job.jobId())==1);
        assertThat(calls().authorizationRequests()).containsExactly(Set.of());assertThat(calls().count("getEqpSnapshot")).isZero();assertThat(calls().count("direct:logs")).isZero();assertThat(observations().workflows).isEmpty();
        pauses().release(point,job.jobId());await(()->state(job.jobId()).equals("SUCCEEDED"));
        assertThat(calls().authorizationRequests()).containsExactly(Set.of(),Set.of("EQP_READ"),Set.of("AI_LOG_READ"));
        assertThat(calls().count("getEqpSnapshot")).isOne();assertThat(calls().count("direct:logs")).isOne();
    }
    @Test void validGeneralAndUnsupportedFixturesDoNotRequireSourcePermissions() throws Exception {
        for(TaskType type:List.of(TaskType.GENERAL_QUERY,TaskType.UNSUPPORTED_REQUEST)){
            String key=type.name();hooks().arm("actor",key,new TestHookRegistry.Scenario(type,Set.of(),null,false));var base=scope("actor");
            var withoutSourcePermissions=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(base.boundSystem(),base.authenticatedServiceRef(),base.actorRef(),base.sub(),Set.of(),base.pdg(),base.issuedAt(),base.expiresAt(),base.requestId());
            var job=context.getBean(JobAdmissionService.class).accept(withoutSourcePermissions,new JobAdmissionService.AdmissionRequest("/v1/query",null,"질문",Map.of(),key,registeredDlg("actor")));await(()->state(job.jobId()).equals("SUCCEEDED"));
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
            assertThat(json.readTree(payload(job.jobId())).path("logsStatus").asString()).isEqualTo(scenario.equals("logout")?"DENIED":"SOURCE_UNAVAILABLE");
        }
        assertThat(calls().count("getEqpSnapshot")).isEqualTo(2);assertThat(calls().count("direct:logs")).isZero();
    }
    @Test void conversationExpiryBeforePreflightKeepsAuthorizationAndResultAtZero() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");var job=live("expiry",Set.of());jdbc().sql("UPDATE gw.conversation SET expires_at=:at WHERE id=:id").param("at",java.sql.Timestamp.from(NOW.plusSeconds(1))).param("id",job.conversationId()).update();clock().advance(1);pauses().release(PausePointRegistry.Point.DISPATCH_HOLD,"*");await(()->state(job.jobId()).equals("CANCELLED"));
        assertThat(calls().count("authorize")).isZero();assertThat(count("result")).isZero();assertThat(observations().workflows).isEmpty();
    }
}
