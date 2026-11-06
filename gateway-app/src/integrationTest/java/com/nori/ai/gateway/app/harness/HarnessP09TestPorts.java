package com.nori.ai.gateway.app.harness;

import com.nori.ai.gateway.app.job.P09TestPorts;
import com.nori.ai.gateway.app.job.HarnessObservationsEndpoint;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.app.job.recovery.ReadinessPrecondition;
import com.nori.ai.gateway.core.registry.TaskType;
import java.util.Set;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** P11·P13에서 각각 인가·workflow fake를 제거하고 같은 프로세스 시험을 유지합니다. */
@TestConfiguration(proxyBeanMethods=false)
@Import({P09TestPorts.class,HarnessObservationsEndpoint.class})
public class HarnessP09TestPorts {
    @Bean ReadinessPrecondition recoveredClassificationFixture(JdbcClient jdbc,TestHookRegistry hooks,TransactionTemplate tx){
        return new ReadinessPrecondition(){
            @Override public int order(){return -100;}
            @Override public void run(){
                // 재시작 때 무장 메모리는 비어 있습니다. 미구현 분류기의 시험 입력만 새로 제공합니다.
                var rows=jdbc.sql("SELECT id,actor_ref,idempotency_key FROM gw.job WHERE status='QUEUED'").query((rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3)}).list();
                for(var row:rows){hooks.arm(row[1],row[2],new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false));tx.executeWithoutResult(s->hooks.bindOnAdmission(row[1],row[2],row[0]));}
            }
        };
    }
}
