package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.job.recovery.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class WorkerRestartEpochIT extends P09QueueSupport {
    private boolean restoreMode;
    private boolean handler;
    static int lateCalls,restoreCalls;
    @Override protected Map<String,Object> extraApplicationProperties(){var p=new HashMap<>(super.extraApplicationProperties());p.put("ai.gateway.recovery.restore-mode",restoreMode);p.put("ai.gateway.recovery.restore-handler.enabled",false);return p;}
    @Override protected Class<?>[] extraSources(){return handler?new Class<?>[]{HarnessObservationsEndpoint.class,Late.class,Handler.class}:new Class<?>[]{HarnessObservationsEndpoint.class,Late.class};}
    @TestConfiguration(proxyBeanMethods=false) static class Late{
        @Bean ReadinessPrecondition lateCondition(PausePointRegistry pauses){return new ReadinessPrecondition(){public int order(){return 100;}public void run(){lateCalls++;pauses.hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");}};}
    }
    @TestConfiguration(proxyBeanMethods=false) static class Handler{
        @Bean RestoreRecoveryHandler recordingHandler(){return epoch->{restoreCalls++;return new RestoreRecoveryHandler.RestoreOutcome(true);};}
    }
    @Test void allEightEpochModesUseRealIdentifierAndFailClosedBeforeAnyLaterWrites()throws Exception{
        String identifier=jdbc().sql("SELECT system_identifier::text FROM pg_control_system()").query(String.class).single();
        var parent=JdbcClient.create(new DriverManagerDataSource(database.jdbcUrl(),"aigw_migrator",database.passwordFor("aigw_migrator")));
        for(String which:List.of("a","b","c","d","e","f","g","h")){
            context.close();context=null;
            parent.sql("TRUNCATE gw.conversation,gw.audit_event,gw.instance_epoch CASCADE").update();
            restoreMode=Set.of("c","d","h").contains(which);handler=which.equals("f");lateCalls=0;restoreCalls=0;
            boolean data=!Set.of("a","c").contains(which);
            if(data){
                parent.sql("INSERT INTO gw.conversation(id,owner_system,owner_actor_ref,created_at,updated_at,expires_at,status) VALUES ('epoch-c','TC','actor',:now,:now,:expiry,'ACTIVE')").param("now",java.sql.Timestamp.from(NOW)).param("expiry",java.sql.Timestamp.from(NOW.plusSeconds(1000))).update();
                for(String status:List.of("RUNNING","QUEUED")) {
                    parent.sql("INSERT INTO gw.message(id,conversation_id,owner_system,role,created_at,content_masked,resource_refs,tool_execution_refs) VALUES (:message,'epoch-c','TC','user',:now,'시대 복원 시험','[]','[]')").param("message","epoch-m-"+status).param("now",java.sql.Timestamp.from(NOW)).update();
                    parent.sql("INSERT INTO gw.job(id,conversation_id,owner_system,actor_ref,status,created_at,request_digest,expected_path,request_perm,request_pdg,request_message_id,request_input) VALUES (:id,'epoch-c','TC','actor',:status,:now,'digest','/v1/query',ARRAY['EQP_READ'],:pdg,:message,'{}')").param("id","epoch-"+status).param("status",status).param("now",java.sql.Timestamp.from(NOW)).param("pdg",P09TestPorts.PDG).param("message","epoch-m-"+status).update();
                }
                for(int n=0;n<2;n++)parent.sql("INSERT INTO gw.cache_entry(key_digest,owner_system,actor_ref,pdg,conversation_id,capability_id,value,source_deps,expires_at) VALUES (:key,'TC','actor',:pdg,'epoch-c','fixture','{}','{}',:expiry)").param("key","cache"+n).param("pdg",P09TestPorts.PDG).param("expiry",java.sql.Timestamp.from(NOW.plusSeconds(1000))).update();
            }
            if(!Set.of("a","b","c").contains(which))parent.sql("INSERT INTO gw.instance_epoch(id,db_system_identifier,recorded_at,restore_completed_at) VALUES (1,:identifier,:now,:completed)").param("identifier",Set.of("e","f").contains(which)?"different-identifier":identifier).param("now",java.sql.Timestamp.from(NOW.minusSeconds(1))).param("completed",which.equals("h")?java.sql.Timestamp.from(NOW):null).update();
            String before=databaseHash(parent);
            if(Set.of("b","c","d").contains(which)){
                assertThatThrownBy(()->startApplication(extraSources())).hasMessage(which.equals("d")?"RESTORE_TARGET_IS_LIVE_DB":"RECOVERY_EPOCH_MISSING");
                assertThat(databaseHash(parent)).isEqualTo(before);assertThat(lateCalls).isZero();assertThat(restoreCalls).isZero();
            }else{
                startApplication(extraSources());
                if(which.equals("e")){
                    assertThat(context.getBean(ReadinessGate.class).ready()).isFalse();assertThat(context.getBean(JobDispatcher.class).started()).isFalse();assertThat(context.getBean(JobTimeoutSweeper.class).started()).isFalse();assertThat(lateCalls).isZero();assertThat(databaseHash(parent)).isEqualTo(before);
                }else if(which.equals("f")){assertThat(restoreCalls).isOne();assertThat(parent.sql("SELECT status FROM gw.job ORDER BY seq").query(String.class).list()).containsExactly("RUNNING","QUEUED");}
                else{
                    assertThat(context.getBean(ReadinessGate.class).ready()).isTrue();assertThat(lateCalls).isOne();assertThat(restoreCalls).isZero();
                    assertThat(parent.sql("SELECT count(*) FROM gw.cache_entry").query(Integer.class).single()).isZero();
                    if(data)assertThat(parent.sql("SELECT error_code FROM gw.result WHERE job_id='epoch-RUNNING'").query(String.class).single()).isEqualTo("WORKER_INTERRUPTED");
                    if(which.equals("a"))assertThat(parent.sql("SELECT recorded_at FROM gw.instance_epoch").query(java.sql.Timestamp.class).single().toInstant()).isEqualTo(NOW);
                }
            }
            // 다음 모드 fixture를 쓰기 전에 정상 컨텍스트의 스케줄러부터 중지합니다.
            if(context!=null){context.close();context=null;}
            if(Set.of("b","c","d").contains(which)){restoreMode=false;handler=false;parent.sql("TRUNCATE gw.conversation,gw.audit_event,gw.instance_epoch CASCADE").update();startApplication(extraSources());}
            else if(!which.equals("h")){restoreMode=false;handler=false;parent.sql("TRUNCATE gw.conversation,gw.audit_event,gw.instance_epoch CASCADE").update();startApplication(extraSources());}
        }
    }
    private String databaseHash(JdbcClient jdbc)throws Exception{
        var tables=jdbc.sql("SELECT table_schema,table_name FROM information_schema.tables WHERE table_schema IN ('gw','kn') AND table_type='BASE TABLE' ORDER BY table_schema,table_name").query((rs,n)->rs.getString(1)+"."+rs.getString(2)).list();
        var values=new ArrayList<Object>();for(String table:tables){assertThat(table).matches("[a-z_]+\\.[a-z_]+");values.add(List.of(table,jdbc.sql("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text").query(String.class).list()));}
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(values)));
    }
}
