package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;

/**
 * suite 안 leaf 사이 격리(N10): 비 terminal Job 이 0 이고 큐가 빈 상태일 때만 hook reset 뒤 fixture 를 재등록한다.
 * 비 terminal 이 남아 있으면 reset 하지 않고 그 회차를 {@code ISOLATION_NOT_IDLE} 로 중단한다.
 */
public final class LeafIsolation {
    private final IntSupplier nonTerminalJobs;
    private final Runnable reset;
    private final Runnable registerFixtures;

    public LeafIsolation(IntSupplier nonTerminalJobs, Runnable reset, Runnable registerFixtures) {
        this.nonTerminalJobs = nonTerminalJobs; this.reset = reset; this.registerFixtures = registerFixtures;
    }

    /** 격리되면 빈 값, 아니면 그 회차의 RUN ABORTED 레코드. */
    public Optional<ObjectNode> before(EvidenceBlockWriter.Context context) {
        if (nonTerminalJobs.getAsInt() != 0) return Optional.of(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "ISOLATION_NOT_IDLE")));
        reset.run();
        registerFixtures.run();
        return Optional.empty();
    }
    /** leaf 종료 뒤에도 남은 비 terminal Job 이 있으면 다음 leaf 를 시작하지 않는다. */
    public Optional<ObjectNode> after(EvidenceBlockWriter.Context context) {
        if (nonTerminalJobs.getAsInt() != 0) return Optional.of(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "ISOLATION_NOT_IDLE")));
        return Optional.empty();
    }
}
