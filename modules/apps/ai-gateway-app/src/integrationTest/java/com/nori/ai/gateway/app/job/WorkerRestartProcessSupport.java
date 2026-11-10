package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.app.harness.*;
import com.nori.ai.gateway.persistence.testing.PgTestDatabase;
import java.nio.file.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@Tag("stage-F4") @Tag("label-MOCK_API")
abstract class WorkerRestartProcessSupport extends StackContextSupport {
    protected static final Instant NOW=P09QueueSupport.NOW;
    protected AppProcessHarness harness;
    protected JdbcClient jdbc;
    protected final String hookToken=UUID.randomUUID().toString();
    protected final JsonMapper json=JsonMapper.builder().build();
    protected Path state;
    @BeforeEach void prepareProcess()throws Exception{
        database=new PgTestDatabase(System.getProperty("aigw.pgTestImage"),Path.of(System.getProperty("aigw.rootDir"))).start();database.bootstrap(true);database.migrate();prepareSecurityCredentials();
        Files.writeString(securityDirectory.resolve("hook.sha256"),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(hookToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+"\n");
        jdbc=JdbcClient.create(new DriverManagerDataSource(database.jdbcUrl(),"aigw_app",database.passwordFor("aigw_app")));
        state=securityDirectory.resolve("state");harness=new AppProcessHarness(hookToken);launch(NOW);
    }
    protected Map<String,String> properties(Instant at){
        harness.watch(serviceToken);harness.watch(database.passwordFor("aigw_app"));
        var p=new LinkedHashMap<String,String>();securityProperties().forEach((k,v)->p.put(k,v.toString()));
        p.putAll(Map.of("spring.datasource.url",database.jdbcUrl(),"spring.datasource.username","aigw_app","spring.datasource.password",database.passwordFor("aigw_app"),"server.address","127.0.0.1","server.port","0","spring.flyway.enabled","false"));
        p.putAll(Map.of("ai.gateway.capacity.max-running-jobs","1","ai.gateway.capacity.generation-concurrency","1","ai.gateway.capacity.queued-depth","4","ai.gateway.capacity.queue-wait-limit","60s","ai.gateway.capacity.job-lifetime-limit","180s","ai.gateway.inference.mode","disabled"));
        p.putAll(Map.of("ai.gateway.test-hooks.enabled","true","ai.gateway.test-hooks.token-sha256-file",securityDirectory.resolve("hook.sha256").toString(),"ai.gateway.test-hooks.clock.mode","FROZEN","ai.gateway.test-hooks.clock.initial",at.toString(),"ai.gateway.tcmock.state-dir",state.toString()));
        p.putAll(Map.of("spring.kafka.bootstrap-servers","127.0.0.1:1","spring.kafka.listener.auto-startup","false","nori.storage.s3.enabled","true","nori.storage.s3.endpoint","http://127.0.0.1:1","nori.storage.s3.bucket","p09-fixture","nori.storage.s3.access-key","p09-dummy","nori.storage.s3.secret-key","p09-dummy","nori.storage.s3.region","us-east-1"));return p;
    }
    protected Map<String,Object> launch(Instant at)throws Exception{harness.launch(state,properties(at),null);return harness.awaitReady(state);}
    protected HttpResponse<String> hook(String path,Object body)throws Exception{return send(path,"POST",json.writeValueAsString(body),null,null,true);}
    protected void arm(String key,String halt,List<String> pause)throws Exception{
        var scenario=new HashMap<String,Object>(Map.of("taskType","GENERAL_QUERY","pauseAt",pause));if(halt!=null)scenario.put("haltAt",halt);
        assertThat(hook("/__test/arm",Map.of("actorSub","actor","requestKey",key,"scenario",scenario)).statusCode()).isEqualTo(204);
    }
    protected String admit(String key)throws Exception{
        var response=send("/v1/query","POST",json.writeValueAsString(Map.of("query","시험 질의","context",Map.of("timezone","Asia/Seoul","referenceTime","2026-11-06T09:00:00+09:00"),"resourceRefs",List.of())),key,NOW,true);
        assertThat(response.statusCode()).isEqualTo(202);return json.readTree(response.body()).get("jobId").asString();
    }
    protected HttpResponse<String> send(String path,String method,String body,String key,Instant at,boolean job)throws Exception{
        var builder=HttpRequest.newBuilder(harness.uri(path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json");
        if(path.startsWith("/__test"))builder.header("X-AIGW-Hook-Token",hookToken);
        else{
            var claims=new HashMap<String,Object>(Map.of("iss","tc-ui-backend","aud","ai-gateway","sys","TC","sub","actor","scope","READ","perm",List.of("MODEL_READ","EQP_READ","EQP_READ"),"pdg",P09TestPorts.PDG));if(job){String delegation=UUID.randomUUID().toString().replace("-","");harness.watch(delegation);
                assertThat(send("/__test/tcmock/delegations","POST",json.writeValueAsString(Map.of("sub","actor","dlg",delegation)),null,null,false).statusCode()).isEqualTo(204);claims.put("dlg",delegation);}
            String assertion=signer.sign(claims,at);harness.watch(assertion);
            builder.header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",assertion);if(key!=null)builder.header("Idempotency-Key",key);
        }
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    protected String job(String key)throws Exception{await(()->jdbc.sql("SELECT count(*) FROM gw.job WHERE idempotency_key=:key").param("key",key).query(Integer.class).single()==1);return jdbc.sql("SELECT id FROM gw.job WHERE idempotency_key=:key").param("key",key).query(String.class).single();}
    protected String state(String id){return jdbc.sql("SELECT status FROM gw.job WHERE id=:id").param("id",id).query(String.class).single();}
    protected int count(String table,String id){if(!Set.of("result","audit_event","job_event").contains(table))throw new IllegalArgumentException();return jdbc.sql("SELECT count(*) FROM gw."+table+" WHERE job_id=:id"+(table.equals("audit_event")?" AND event_type='JOB_TERMINAL'":"")+(table.equals("job_event")?" AND type IN ('job.completed','job.partial','job.failed','job.cancelled')":"")).param("id",id).query(Integer.class).single();}
    protected int started(String id){return jdbc.sql("SELECT count(*) FROM gw.job_event WHERE job_id=:id AND type='job.started'").param("id",id).query(Integer.class).single();}
    protected void await(java.util.function.BooleanSupplier test)throws Exception{long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();while(!test.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(20);assertThat(test.getAsBoolean()).isTrue();}
    protected void haltWithRunningAndQueued()throws Exception{
        arm("running","TERMINAL_PRECOMMIT",List.of("BEFORE_CLASSIFICATION"));String running=admit("running");await(()->state(running).equals("RUNNING"));
        arm("queued",null,List.of());admit("queued");assertThat(hook("/__test/pause",Map.of("point","BEFORE_CLASSIFICATION","jobId",running,"op","RELEASE")).statusCode()).isEqualTo(204);
        assertThat(harness.awaitExit(Duration.ofSeconds(15))).isEqualTo(137);
        assertThat(started(running)).isOne();assertThat(started(job("queued"))).isZero();
    }
    protected com.nori.ai.gateway.core.job.JobRequestInput requestInput(String job) {
        var scope=restoredScope(job);
        var source=new DriverManagerDataSource(database.jdbcUrl(),"aigw_app",database.passwordFor("aigw_app"));var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(source));
        return new com.nori.ai.gateway.persistence.repository.JobRepository(JdbcClient.create(source),tx,Clock.fixed(NOW.plusSeconds(1),ZoneOffset.UTC)).findRequestInput(scope,job).orElseThrow();
    }
    protected com.nori.ai.gateway.core.scope.VerifiedSystemScope restoredScope(String job) {
        var persisted=jdbc.sql("SELECT j.owner_system,j.actor_ref,j.request_perm,j.request_pdg,j.id,j.created_at,j.conversation_id,c.expires_at,j.status FROM gw.job j JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system WHERE j.id=:id")
            .param("id",job).query((rs,n)->new com.nori.ai.gateway.persistence.queue.JobScopeRecord(com.nori.ai.gateway.core.scope.OwnerSystem.valueOf(rs.getString(1)),rs.getString(2),Set.copyOf(Arrays.asList((String[])rs.getArray(3).getArray())),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getString(7),rs.getTimestamp(8).toInstant(),rs.getString(9))).single();
        return new com.nori.ai.gateway.app.security.ScopeAssembler().forWorker(persisted);
    }
    @Override @AfterEach protected void close(){try{if(harness!=null){harness.close();assertThat(harness.sentinelCount()).isZero();}}finally{super.close();}}
}
