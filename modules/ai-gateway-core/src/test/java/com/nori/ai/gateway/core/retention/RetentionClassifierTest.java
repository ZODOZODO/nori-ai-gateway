package com.nori.ai.gateway.core.retention;

import com.nori.ai.gateway.core.result.Warning;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RetentionClassifierTest {
    private final Instant now = Instant.parse("2026-11-06T00:00:00Z");
    private final Instant boundary = now.minusSeconds(3600);
    private final RetentionClassifier classifier = new RetentionClassifier();
    @Test void exactBoundariesAndCrossingAreExclusive() {
        for (SourceRetentionAttributes attrs : List.of(new SourceRetentionAttributes.Configured(Duration.ofHours(1)),
                new SourceRetentionAttributes.VerifiedOldest(boundary), new SourceRetentionAttributes.TimeAndCountTrim(1, 5000, true))) {
            var outside = new TimeWindow(boundary.minusNanos(1), boundary);
            assertThat(classifier.classify(outside, attrs, now)).isEqualTo(new RetentionClassifier.OutOfRetention(List.of(outside)));
            assertThat(classifier.classify(new TimeWindow(boundary, now), attrs, now)).isInstanceOf(RetentionClassifier.Full.class);
            var crossing = (RetentionClassifier.Partial) classifier.classify(new TimeWindow(boundary.minusNanos(1), now), attrs, now);
            assertThat(crossing.queryWindow()).isEqualTo(new TimeWindow(boundary, now));
            assertThat(crossing.missingRanges()).containsExactly(new TimeWindow(boundary.minusNanos(1), boundary));
        }
    }
    @Test void unverifiedAlwaysQueriesFullWindowWithWarning() {
        var window = new TimeWindow(boundary.minusSeconds(3600), boundary);
        for (SourceRetentionAttributes attrs : List.of(new SourceRetentionAttributes.Unverified(), new SourceRetentionAttributes.TimeAndCountTrim(1, 5000, false))) {
            var full = (RetentionClassifier.Full) classifier.classify(window, attrs, now);
            assertThat(full.queryWindow()).isEqualTo(window); assertThat(full.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
        }
    }
    @Test void invalidWindowOrRetentionRejected() {
        assertThatThrownBy(() -> new TimeWindow(now, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeWindow(now, boundary)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRetentionAttributes.Configured(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRetentionAttributes.TimeAndCountTrim(0, 1, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
