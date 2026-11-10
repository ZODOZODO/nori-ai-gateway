package com.nori.ai.gateway.app.job;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkerRestartExpiryIT extends WorkerRestartProcessSupport {
    @Test void expiredRunningAndQueuedCancelWithoutResultAuthorizationOrExternalEvent()throws Exception{
        haltWithRunningAndQueued();jdbc.sql("UPDATE gw.conversation SET expires_at=:expiry").param("expiry",java.sql.Timestamp.from(NOW.plusSeconds(1))).update();
        // 만료 대화는 복구 취소 뒤 정리 대상이 되므로, 이 재기동만 RecoveryCompleted 전에 PURGE_HOLD를 걸어 종결 행을 관찰합니다.
        var restart=properties(NOW.plusSeconds(1));restart.put(com.nori.ai.gateway.app.harness.HarnessP09TestPorts.PURGE_HOLD_AT_START,"true");
        harness.launch(state,restart,null);harness.awaitReady(state);
        assertThat(jdbc.sql("SELECT status FROM gw.job ORDER BY seq").query(String.class).list()).containsExactly("CANCELLED","CANCELLED");
        assertThat(jdbc.sql("SELECT count(*) FROM gw.result").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM gw.job_event WHERE type='job.cancelled' AND external_suppressed AND published_at IS NULL").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT decision FROM gw.audit_event WHERE event_type='JOB_TERMINAL' ORDER BY id").query(String.class).list()).containsExactly("EXPIRY_EDGE","EXPIRY_EDGE");
        for(String id:jdbc.sql("SELECT id FROM gw.job ORDER BY seq").query(String.class).list())JobAuditLineage.assertLineage(jdbc,id,true);
        assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event WHERE job_id IS NULL AND event_type<>'API_REQUEST'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM gw.job WHERE delegation_id IS NOT NULL OR result_ref IS NOT NULL").query(Integer.class).single()).isZero();
        assertThat(json.readTree(send("/__test/harness-observations","GET",null,null,null,false).body()).get("authorization").asInt()).isZero();
    }
}
