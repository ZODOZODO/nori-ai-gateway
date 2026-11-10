package com.nori.ai.gateway.core.retention;

import com.nori.ai.gateway.core.result.*;
import java.time.*;
import java.util.*;

public final class TrimCompletenessEvaluator {
    public record KeyObservation(boolean available, long count, long maxEntriesPerKey, Instant oldestRetainedAt,
            long retentionHours, boolean appliedValuesVerified, boolean otherDeletionPathsExcluded, List<TimeWindow> truncatedRanges) {
        public KeyObservation {
            truncatedRanges = List.copyOf(truncatedRanges);
            if (count < 0 || maxEntriesPerKey <= 0 || retentionHours <= 0 || (count > 0 && oldestRetainedAt == null))
                throw new IllegalArgumentException("통신 로그 완전성 정보가 잘못되었습니다");
        }
    }
    public record Evaluation(ResultStatus status, List<TimeWindow> missingRanges, List<TimeWindow> unverifiedRanges, Set<Warning> warnings) {
        public Evaluation { missingRanges = List.copyOf(missingRanges); unverifiedRanges = List.copyOf(unverifiedRanges); warnings = Set.copyOf(warnings); }
    }
    public Evaluation evaluate(TimeWindow window, KeyObservation key, Instant now) {
        Objects.requireNonNull(window); Objects.requireNonNull(key); Objects.requireNonNull(now);
        if (!key.available()) return new Evaluation(ResultStatus.SOURCE_UNAVAILABLE, List.of(), List.of(), Set.of());
        boolean verified = key.appliedValuesVerified() && key.otherDeletionPathsExcluded();
        var classification = new RetentionClassifier().classify(window,
            new SourceRetentionAttributes.TimeAndCountTrim(key.retentionHours(), key.maxEntriesPerKey(), verified), now);
        if (classification instanceof RetentionClassifier.OutOfRetention outside)
            return new Evaluation(ResultStatus.OUT_OF_RETENTION, outside.missingRanges(), List.of(), Set.of());
        List<TimeWindow> missing = new ArrayList<>();
        Set<Warning> warnings = new HashSet<>();
        if (classification instanceof RetentionClassifier.Partial partial) missing.addAll(partial.missingRanges());
        if (!verified) warnings.add(Warning.RETENTION_UNVERIFIED);
        List<TimeWindow> unverified = new ArrayList<>();
        if (verified && key.count() >= key.maxEntriesPerKey()) {
            Instant start = window.startAt().isAfter(now.minus(Duration.ofHours(key.retentionHours())))
                ? window.startAt() : now.minus(Duration.ofHours(key.retentionHours()));
            Instant end = window.endAt().isBefore(key.oldestRetainedAt()) ? window.endAt() : key.oldestRetainedAt();
            if (start.isBefore(end)) { unverified.add(new TimeWindow(start, end)); warnings.add(Warning.RETENTION_UNVERIFIED); }
        }
        missing.addAll(key.truncatedRanges());
        return new Evaluation(missing.isEmpty() ? (key.count() == 0 ? ResultStatus.EMPTY : ResultStatus.SUCCESS) : ResultStatus.PARTIAL,
            missing, unverified, warnings);
    }
    public ResultStatus aggregate(List<Evaluation> keys) {
        if (keys.isEmpty()) throw new IllegalArgumentException("키별 조회 결과가 필요합니다");
        if (keys.stream().allMatch(k -> k.status() == ResultStatus.SOURCE_UNAVAILABLE)) return ResultStatus.SOURCE_UNAVAILABLE;
        if (keys.stream().anyMatch(k -> k.status() == ResultStatus.SOURCE_UNAVAILABLE || k.status() == ResultStatus.PARTIAL)) return ResultStatus.PARTIAL;
        if (keys.stream().allMatch(k -> k.status() == ResultStatus.OUT_OF_RETENTION)) return ResultStatus.OUT_OF_RETENTION;
        if (keys.stream().anyMatch(k -> k.status() == ResultStatus.OUT_OF_RETENTION)) return ResultStatus.PARTIAL;
        return keys.stream().anyMatch(k -> k.status() == ResultStatus.SUCCESS) ? ResultStatus.SUCCESS : ResultStatus.EMPTY;
    }
}
