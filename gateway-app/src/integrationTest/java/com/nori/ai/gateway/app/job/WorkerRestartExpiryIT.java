package com.nori.ai.gateway.app.job;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkerRestartExpiryIT extends WorkerRestartProcessSupport {
    @Test void expiredRunningAndQueuedCancelWithoutResultAuthorizationOrExternalEvent()throws Exception{
        haltWithRunningAndQueued();jdbc.sql("UPDATE gw.conversation SET expires_at=:expiry").param("expiry",java.sql.Timestamp.from(NOW.plusSeconds(1))).update();
        launch(NOW.plusSeconds(1));
        assertThat(jdbc.sql("SELECT status FROM gw.job ORDER BY seq").query(String.class).list()).containsExactly("CANCELLED","CANCELLED");
        assertThat(jdbc.sql("SELECT count(*) FROM gw.result").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM gw.job_event WHERE type='job.cancelled' AND external_suppressed AND published_at IS NULL").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT decision FROM gw.audit_event ORDER BY id").query(String.class).list()).containsExactly("EXPIRY_EDGE","EXPIRY_EDGE");
        assertThat(jdbc.sql("SELECT count(*) FROM gw.job WHERE delegation_id IS NOT NULL OR result_ref IS NOT NULL").query(Integer.class).single()).isZero();
        assertThat(json.readTree(send("/__test/harness-observations","GET",null,null,null,false).body()).get("authorization").asInt()).isZero();
    }
}
