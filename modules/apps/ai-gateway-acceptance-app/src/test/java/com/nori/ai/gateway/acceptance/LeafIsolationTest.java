package com.nori.ai.gateway.acceptance;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LeafIsolationTest {
    final AtomicInteger active = new AtomicInteger(), resets = new AtomicInteger(), fixtures = new AtomicInteger();
    final LeafIsolation isolation = new LeafIsolation(active::get, resets::incrementAndGet, fixtures::incrementAndGet);

    @Test void nonTerminalJobsBlockResetAndAbortTheRun() {
        active.set(1);
        var aborted = isolation.before(EvidenceBlockWriterTest.CONTEXT);
        assertThat(aborted).hasValueSatisfying(record -> {
            assertThat(record.path("kind").asText()).isEqualTo("RUN");
            assertThat(record.path("status").asText()).isEqualTo("ABORTED");
            assertThat(record.path("reasonCode").asText()).isEqualTo("ISOLATION_NOT_IDLE");
            EvidenceBlockWriterTest.assertSealed(record);
        });
        assertThat(resets).hasValue(0); assertThat(fixtures).hasValue(0);
        assertThat(isolation.after(EvidenceBlockWriterTest.CONTEXT)).isPresent();
    }

    @Test void idleQueueResetsThenReregistersFixtures() {
        assertThat(isolation.before(EvidenceBlockWriterTest.CONTEXT)).isEmpty();
        assertThat(resets).hasValue(1); assertThat(fixtures).hasValue(1);
        assertThat(isolation.after(EvidenceBlockWriterTest.CONTEXT)).isEmpty();
    }
}
