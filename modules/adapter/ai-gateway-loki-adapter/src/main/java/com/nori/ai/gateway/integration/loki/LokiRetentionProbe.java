package com.nori.ai.gateway.integration.loki;

import com.nori.ai.gateway.integration.source.*;
import com.nori.ai.gateway.core.retention.TimeWindow;
import java.time.*;
import java.util.*;

/** 탐침의 실제 조회 범위도 함께 반환하며 저장과 스케줄은 호출자가 담당합니다. */
public final class LokiRetentionProbe {
    public record ProbeObservation(Instant verifiedOldestAt, Instant probeLimit) {
        public ProbeObservation { Objects.requireNonNull(verifiedOldestAt); Objects.requireNonNull(probeLimit); if (verifiedOldestAt.isBefore(probeLimit)) throw new IllegalArgumentException("탐침 범위가 잘못되었습니다"); }
    }
    private final SourceHttpClient http;
    private final TimeWindow probeWindow;
    public LokiRetentionProbe(SourceHttpClient http, TimeWindow probeWindow) { this.http = Objects.requireNonNull(http); this.probeWindow = Objects.requireNonNull(probeWindow); }
    public Optional<ProbeObservation> measure(String app) {
        var batch = LokiLogConnector.query(http, app, List.of(), probeWindow, 1, "forward", http.deadline(Duration.ofSeconds(30)));
        if (batch.rejectedLabels() != 0) throw new SourceHttpClient.Failure(false, false);
        return batch.items().stream().findFirst().map(line -> new ProbeObservation(line.occurredAt(), probeWindow.startAt()));
    }
}
