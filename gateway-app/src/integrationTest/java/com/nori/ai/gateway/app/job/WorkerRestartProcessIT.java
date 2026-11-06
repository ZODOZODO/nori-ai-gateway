package com.nori.ai.gateway.app.job;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class WorkerRestartProcessIT extends WorkerRestartProcessSupport {
    @Test @Tag("AC-18-WORKER-RUN") void runningProcessHaltsAndRestartCommitsInterruptedOnce()throws Exception{
        arm("run","BEFORE_CLASSIFICATION",List.of());try{admit("run");}catch(java.io.IOException interrupted){assertThat(interrupted).isNotNull();}
        assertThat(harness.awaitExit(Duration.ofSeconds(15))).isEqualTo(137);String id=job("run");assertThat(state(id)).isEqualTo("RUNNING");
        assertThat(started(id)).isOne();
        launch(NOW.plusSeconds(1));assertThat(state(id)).isEqualTo("FAILED");assertThat(count("result",id)).isOne();assertThat(count("audit_event",id)).isOne();assertThat(count("job_event",id)).isOne();
        var result=send("/v1/analyses/"+id,"GET",null,null,NOW.plusSeconds(1),false);assertThat(result.statusCode()).isEqualTo(200);assertThat(json.readTree(result.body()).get("result").get("errorCode").asString()).isEqualTo("WORKER_INTERRUPTED");
    }
    @Test @Tag("AC-18-WORKER-QUEUED-VALID") void queuedJobResumesWithPersistedPermissionsAndOriginalScope()throws Exception{
        haltWithRunningAndQueued();String queued=job("queued");launch(NOW.plusSeconds(1));await(()->state(queued).equals("SUCCEEDED"));
        assertThat(started(queued)).isOne();
        var payload=json.readTree(jdbc.sql("SELECT payload::text FROM gw.result WHERE job_id=:id").param("id",queued).query(String.class).single());
        var scope=restoredScope(queued);assertThat(scope.perm()).containsExactlyInAnyOrder("EQP_READ","MODEL_READ");assertThat(scope.pdg()).isEqualTo(P09TestPorts.PDG);assertThat(scope.requestId()).isEqualTo(queued);
        assertThat(scope.issuedAt()).isEqualTo(NOW);assertThat(scope.expiresAt()).isEqualTo(NOW.plusSeconds(180).plusNanos(1));
        var input=requestInput(queued);assertThat(input.messageId()).isEqualTo(jdbc.sql("SELECT request_message_id FROM gw.job WHERE id=:id").param("id",queued).query(String.class).single());
        assertThat(input.maskedQuestion()).isEqualTo("시험 질의");assertThat(((Map<?,?>)input.requestFields().get("context")).get("referenceTime")).isEqualTo("2026-11-06T09:00:00+09:00");
        assertThat(payload.has("requestFields")).isFalse();assertThat(payload.path("blocks").isArray()).isTrue();
        assertThat(count("result",queued)).isOne();var observations=send("/__test/harness-observations","GET",null,null,null,false);assertThat(json.readTree(observations.body()).get("authorization").asInt()).isOne();
    }
    @Test @Tag("AC-18-WORKER-QUEUED-EXPIRED") void queuedAfterOneEightyOneRemainsQueueTimeoutAndRunningIsInterrupted()throws Exception{
        haltWithRunningAndQueued();String queued=job("queued"),running=job("running");launch(NOW.plusSeconds(181));
        assertThat(started(queued)).isZero();assertThat(started(running)).isOne();
        assertThat(state(queued)).isEqualTo("FAILED");assertThat(jdbc.sql("SELECT error_code FROM gw.result WHERE job_id=:id").param("id",queued).query(String.class).single()).isEqualTo("QUEUE_TIMEOUT");
        assertThat(jdbc.sql("SELECT decision FROM gw.audit_event WHERE job_id=:id").param("id",queued).query(String.class).single()).isEqualTo("QUEUE_TIMEOUT");
        assertThat(jdbc.sql("SELECT error_code FROM gw.result WHERE job_id=:id").param("id",running).query(String.class).single()).isEqualTo("WORKER_INTERRUPTED");
        assertThat(json.readTree(send("/__test/harness-observations","GET",null,null,null,false).body()).get("authorization").asInt()).isZero();
    }
    @Test @Tag("AC-18-WORKER-RACE-PRECOMMIT") void haltBeforeTerminalCommitRollsBackResultEventAndAudit()throws Exception{
        arm("pre","TERMINAL_PRECOMMIT",List.of());try{admit("pre");}catch(java.io.IOException interrupted){assertThat(interrupted).isNotNull();}
        assertThat(harness.awaitExit(Duration.ofSeconds(15))).isEqualTo(137);String id=job("pre");assertThat(count("result",id)).isZero();assertThat(count("audit_event",id)).isZero();assertThat(count("job_event",id)).isZero();
        assertThat(started(id)).isOne();
        launch(NOW.plusSeconds(1));assertThat(state(id)).isEqualTo("FAILED");assertThat(count("result",id)).isOne();assertThat(count("audit_event",id)).isOne();
        assertThat(json.readTree(send("/v1/analyses/"+id,"GET",null,null,NOW.plusSeconds(1),false).body()).get("result").get("errorCode").asString()).isEqualTo("WORKER_INTERRUPTED");
    }
    @Test @Tag("AC-18-WORKER-RACE-POSTCOMMIT") void haltAfterCommitKeepsResultAndRepublishesOnlyPendingTerminal()throws Exception{
        arm("post","TERMINAL_POSTCOMMIT",List.of());try{admit("post");}catch(java.io.IOException interrupted){assertThat(interrupted).isNotNull();}
        assertThat(harness.awaitExit(Duration.ofSeconds(15))).isEqualTo(137);String id=job("post");assertThat(state(id)).isEqualTo("SUCCEEDED");assertThat(count("result",id)).isOne();assertThat(count("audit_event",id)).isOne();
        assertThat(started(id)).isOne();
        assertThat(jdbc.sql("SELECT published_at IS NULL FROM gw.job_event WHERE job_id=:id AND type='job.completed'").param("id",id).query(Boolean.class).single()).isTrue();
        launch(NOW.plusSeconds(1));assertThat(state(id)).isEqualTo("SUCCEEDED");assertThat(count("result",id)).isOne();assertThat(count("job_event",id)).isOne();assertThat(jdbc.sql("SELECT published_at IS NOT NULL FROM gw.job_event WHERE job_id=:id AND type='job.completed'").param("id",id).query(Boolean.class).single()).isTrue();
    }
    @Test void mismatchFailsBeforeSpringAndInheritedCiEnvironmentIsAbsent()throws Exception{
        harness.close();assertThat(harness.sentinelCount()).isZero();harness=new com.nori.ai.gateway.app.harness.AppProcessHarness(hookToken);
        harness.launch(state,properties(NOW),"0".repeat(64));assertThat(harness.awaitExit(Duration.ofSeconds(15))).isEqualTo(70);assertThat(java.nio.file.Files.exists(state.resolve("harness-ready.json"))).isFalse();
        var ready=launch(NOW);@SuppressWarnings("unchecked")var keys=(List<String>)ready.get("envKeys");assertThat(keys).noneMatch(k->k.equals("CI")||k.startsWith("CI_")||k.startsWith("GITLAB_")||k.startsWith("AIGW_"));
        String cp=System.getProperty("aigw.harness.classpath");System.clearProperty("aigw.harness.classpath");try{assertThatThrownBy(()->harness.launch(state,properties(NOW),null)).isInstanceOf(IllegalStateException.class);}finally{System.setProperty("aigw.harness.classpath",cp);}
    }
}
