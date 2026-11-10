package com.nori.ai.gateway.core.retention;

import com.nori.ai.gateway.core.result.Warning;
import java.time.*;
import java.util.*;

public final class RetentionClassifier {
    public sealed interface Classification permits OutOfRetention, Full, Partial {}
    public record OutOfRetention(List<TimeWindow> missingRanges) implements Classification {
        public OutOfRetention { missingRanges = List.copyOf(missingRanges); }
    }
    public record Full(TimeWindow queryWindow, Set<Warning> warnings) implements Classification {
        public Full { warnings = Set.copyOf(warnings); }
    }
    public record Partial(TimeWindow queryWindow, List<TimeWindow> missingRanges, Set<Warning> warnings) implements Classification {
        public Partial { missingRanges = List.copyOf(missingRanges); warnings = Set.copyOf(warnings); }
    }
    public Classification classify(TimeWindow window, SourceRetentionAttributes attrs, Instant now) {
        Objects.requireNonNull(window); Objects.requireNonNull(attrs); Objects.requireNonNull(now);
        Instant boundary;
        if (attrs instanceof SourceRetentionAttributes.Configured configured) boundary = now.minus(configured.retention());
        else if (attrs instanceof SourceRetentionAttributes.VerifiedOldest oldest) boundary = oldest.verifiedOldestAt();
        else if (attrs instanceof SourceRetentionAttributes.TimeAndCountTrim trim && trim.appliedValuesVerified())
            boundary = now.minus(Duration.ofHours(trim.retentionHours()));
        else return new Full(window, Set.of(Warning.RETENTION_UNVERIFIED));
        if (!window.endAt().isAfter(boundary)) return new OutOfRetention(List.of(window));
        if (!window.startAt().isBefore(boundary)) return new Full(window, Set.of());
        return new Partial(new TimeWindow(boundary, window.endAt()), List.of(new TimeWindow(window.startAt(), boundary)), Set.of());
    }
}
