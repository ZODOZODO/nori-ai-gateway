package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.registry.TaskType;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 분류 직후 소속 대화 expiresAt 도달 → Composer Prompt 조립·호출 0, 결과 저장 0(Plan P20 LlmExpiryRaceIT). */
class LlmExpiryRaceIT extends LlmStubSupport {
    @Test void expiryReachedAfterClassificationStopsComposerAssemblyAndCallAndWritesNoResult() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        pauses().hold(PausePointRegistry.Point.SWEEPER_HOLD, "*");
        pauses().hold(PausePointRegistry.Point.EXPIRY_CANCELLER_HOLD, "*");
        var watch = Optional.of(new TestHookRegistry.PromptWatch(List.of("만료표지QZ")));
        hooks().arm("actor", "expiry-race", new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY, Set.of(PausePointRegistry.Point.BEFORE_COMPOSER), null, false, Optional.empty(),
            List.of(classify(wire("GQ", null, null, null, null)), compose("서술")), watch));
        var job = admitUnarmed("expiry-race", "만료표지QZ 무엇을 물을 수 있나", Map.of(), null);
        // 워커가 읽는 만료 시각을 접수 직후(디스패치 전) 1초 뒤로 당긴다.
        jdbc().sql("UPDATE gw.conversation SET expires_at=:at WHERE id=:id").param("at", java.sql.Timestamp.from(NOW.plusSeconds(1))).param("id", job.conversationId()).update();
        pauses().release(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        assertThat(pauses().awaitReached(PausePointRegistry.Point.BEFORE_COMPOSER, job.jobId(), Duration.ofSeconds(15))).isTrue();
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).isOne();
        clock().advance(1);
        pauses().release(PausePointRegistry.Point.BEFORE_COMPOSER, job.jobId());
        assertThat(terminal(job)).isEqualTo("CANCELLED");
        assertThat(scripts().observedCalls(job.jobId(), CallType.COMPOSER)).as("Composer Prompt 관찰").isZero();
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).as("Composer 전송").isZero();
        assertThat(scripts().promptWatch(job.jobId()).orElseThrow()).noneMatch(row -> row.callType() == CallType.COMPOSER);
        assertThat(jdbc().sql("SELECT count(*) FROM gw.result WHERE job_id=:id").param("id", job.jobId()).query(Integer.class).single()).isZero();
        assertThat(jdbc().sql("SELECT external_suppressed FROM gw.job_event WHERE job_id=:id AND type='job.cancelled'").param("id", job.jobId()).query(Boolean.class).single()).isTrue();
    }
}
