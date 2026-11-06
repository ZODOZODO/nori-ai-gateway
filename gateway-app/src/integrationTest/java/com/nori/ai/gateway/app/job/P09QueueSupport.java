package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.scope.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@Tag("stage-F4") @Tag("label-MOCK_API")
public abstract class P09QueueSupport extends StackContextSupport {
    protected static final Instant NOW=Instant.parse("2026-11-06T00:00:00Z");
    protected final String hookToken=UUID.randomUUID().toString();
    protected final JsonMapper json=JsonMapper.builder().build();
    @Override protected WebApplicationType applicationType(){return WebApplicationType.SERVLET;}
    @Override protected void prepareSecurityCredentials(){super.prepareSecurityCredentials();try{Files.writeString(securityDirectory.resolve("hook.sha256"),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(hookToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+"\n");}catch(Exception failure){throw new IllegalStateException("시험 훅 자격 준비 실패");}}
    @Override protected Map<String,Object> extraApplicationProperties(){return Map.of("ai.gateway.test-hooks.enabled",true,"ai.gateway.test-hooks.token-sha256-file",securityDirectory.resolve("hook.sha256").toString(),"ai.gateway.test-hooks.clock.mode","FROZEN","ai.gateway.test-hooks.clock.initial",NOW.toString());}
    protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class};}
    @BeforeEach protected void setupQueue(){start(extraSources());}
    @Override @AfterEach protected void close(){
        try{if(context!=null){pauses().reset();context.getBeansOfType(WorkflowSourceFixture.Server.class).values().forEach(server->server.release.countDown());long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();while(context.getBean(SlotLedger.class).activeCount()>0&&System.nanoTime()<deadline)Thread.sleep(10);}}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("시험 종료 대기가 중단되었습니다");}
        finally{super.close();}
    }
    protected JdbcClient jdbc(){return context.getBean(JdbcClient.class);}
    protected ControllableClock clock(){return context.getBean(ControllableClock.class);}
    protected TestHookRegistry hooks(){return context.getBean(TestHookRegistry.class);}
    protected PausePointRegistry pauses(){return context.getBean(PausePointRegistry.class);}
    protected P09TestPorts.Observations observations(){return context.getBean(P09TestPorts.Observations.class);}
    protected com.nori.ai.gateway.tcmock.MockCallCounter calls(){return context.getBean(com.nori.ai.gateway.tcmock.MockCallCounter.class);}
    protected String registeredDlg(String actor){String value=dlg();context.getBean(com.nori.ai.gateway.tcmock.MockDelegationRegistry.class).register(actor,value);return value;}
    protected VerifiedSystemScope scope(String actor){var now=clock().instant();return new VerifiedSystemScope(OwnerSystem.TC,"fixture",new ActorRef(actor),actor,Set.of("EQP_READ","MODEL_READ"),P09TestPorts.PDG,now,now.plusSeconds(60),UUID.randomUUID().toString());}
    protected JobAdmissionService.Accepted admit(String actor,String key,Set<PausePointRegistry.Point> points){hooks().arm(actor,key,new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,points,null,false));return accept(actor,key,"질문",null,dlg());}
    protected Map<String,Object> workflowFields(){return Map.of("context",Map.of("timezone","UTC","referenceTime",NOW.toString(),"timeWindow",Map.of("from","2026-11-05T23:00:00.123456789Z","to","2026-11-05T23:30:00.987654321Z")),"resourceRefs",List.of(Map.of("type","EQP","ref","EQP-001")));}
    protected JobAdmissionService.Accepted incident(String key,Set<PausePointRegistry.Point> points){hooks().arm("actor",key,new TestHookRegistry.Scenario(TaskType.INCIDENT_ANALYSIS,points,null,false));return context.getBean(JobAdmissionService.class).accept(scope("actor"),new JobAdmissionService.AdmissionRequest("/v1/query",null,"장애 조회",workflowFields(),key,registeredDlg("actor")));}
    protected JobAdmissionService.Accepted accept(String actor,String key,String question,String conversation,String dlg){context.getBean(com.nori.ai.gateway.tcmock.MockDelegationRegistry.class).register(actor,dlg);return context.getBean(JobAdmissionService.class).accept(scope(actor),new JobAdmissionService.AdmissionRequest("/v1/query",conversation,question,Map.of(),key,dlg));}
    protected String dlg(){return UUID.randomUUID().toString().replace("-","");}
    protected String state(String id){return jdbc().sql("SELECT status FROM gw.job WHERE id=:id").param("id",id).query(String.class).single();}
    protected int count(String table){if(!Set.of("job","message","conversation","job_event","result","audit_event","cache_entry").contains(table))throw new IllegalArgumentException();return jdbc().sql("SELECT count(*) FROM gw."+table).query(Integer.class).single();}
    protected void await(BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(10);assertThat(condition.getAsBoolean()).as("비동기 조건").isTrue();}
    protected URI uri(String path){return URI.create("http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort()+path);}
    protected Map<String,Object> claims(String actor,String dlg){var claims=new HashMap<String,Object>(Map.of("iss","tc-ui-backend","aud","ai-gateway","sys","TC","sub",actor,"scope","READ","perm",List.of("EQP_READ","MODEL_READ"),"pdg",P09TestPorts.PDG));if(dlg!=null){context.getBean(com.nori.ai.gateway.tcmock.MockDelegationRegistry.class).register(actor,dlg);claims.put("dlg",dlg);}return claims;}
    protected HttpResponse<String> postQuery(String actor,String key,String question)throws Exception{
        var request=HttpRequest.newBuilder(uri("/v1/query")).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",signer.sign(claims(actor,dlg()),clock().instant())).header("Idempotency-Key",key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("query",question,"context",Map.of("timezone","Asia/Seoul","referenceTime","2026-11-06T09:00:00+09:00"),"resourceRefs",List.of())))).build();
        return HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
    }
    protected HttpResponse<String> hook(String path,String method,String body,String token)throws Exception{
        var request=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));if(token!=null)request.header("X-AIGW-Hook-Token",token);return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
