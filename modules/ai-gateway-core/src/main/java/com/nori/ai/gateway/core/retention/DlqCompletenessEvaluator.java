package com.nori.ai.gateway.core.retention;

import com.nori.ai.gateway.core.result.*;
import java.util.*;

public final class DlqCompletenessEvaluator {
    public record Observation(boolean scanSucceeded, long scannedKeys, long loadedEntries, long decodeFailures) {
        public Observation {
            if (scannedKeys < 0 || loadedEntries < 0 || decodeFailures < 0 || loadedEntries > scannedKeys
                    || decodeFailures > scannedKeys - loadedEntries) throw new IllegalArgumentException("DLQ 완전성 정보가 잘못되었습니다");
        }
    }
    public record Evaluation(ResultStatus status, List<String> missingFields, Set<Warning> warnings) {
        public Evaluation { missingFields = List.copyOf(missingFields); warnings = Set.copyOf(warnings); }
    }
    public Evaluation evaluate(Observation observation) {
        Objects.requireNonNull(observation);
        if (!observation.scanSucceeded()) return new Evaluation(ResultStatus.SOURCE_UNAVAILABLE, List.of(), Set.of());
        if (observation.decodeFailures() > 0) return new Evaluation(ResultStatus.PARTIAL,
            List.of("decodeFailures=" + observation.decodeFailures()), Set.of(Warning.RETENTION_UNVERIFIED));
        return new Evaluation(observation.loadedEntries() == 0 ? ResultStatus.EMPTY : ResultStatus.SUCCESS, List.of(), Set.of(Warning.RETENTION_UNVERIFIED));
    }
    public ResultStatus aggregate(List<Evaluation> sources) {
        if (sources.isEmpty()) throw new IllegalArgumentException("DLQ별 조회 결과가 필요합니다");
        if (sources.stream().allMatch(s -> s.status() == ResultStatus.SOURCE_UNAVAILABLE)) return ResultStatus.SOURCE_UNAVAILABLE;
        if (sources.stream().anyMatch(s -> s.status() == ResultStatus.SOURCE_UNAVAILABLE || s.status() == ResultStatus.PARTIAL)) return ResultStatus.PARTIAL;
        return sources.stream().anyMatch(s -> s.status() == ResultStatus.SUCCESS) ? ResultStatus.SUCCESS : ResultStatus.EMPTY;
    }
}
