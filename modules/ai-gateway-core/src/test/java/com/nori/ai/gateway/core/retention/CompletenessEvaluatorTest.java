package com.nori.ai.gateway.core.retention;

import com.nori.ai.gateway.core.result.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CompletenessEvaluatorTest {
    private final Instant now = Instant.parse("2026-11-06T00:00:00Z");
    private final TimeWindow window = new TimeWindow(now.minusSeconds(4000), now);
    private final TrimCompletenessEvaluator trim = new TrimCompletenessEvaluator();
    private TrimCompletenessEvaluator.KeyObservation key(boolean available, long count, boolean verified, boolean excluded, List<TimeWindow> truncated) {
        return new TrimCompletenessEvaluator.KeyObservation(available, count, 5000, count == 0 ? null : now.minusSeconds(600), 1, verified, excluded, truncated);
    }
    @Test void trimCapProducesUnverifiedRangeRatherThanConfirmedMissingRange() {
        var evaluated = trim.evaluate(window, key(true, 5000, true, true, List.of()), now);
        assertThat(evaluated.status()).isEqualTo(ResultStatus.PARTIAL);
        assertThat(evaluated.missingRanges()).containsExactly(new TimeWindow(window.startAt(), now.minusSeconds(3600)));
        assertThat(evaluated.unverifiedRanges()).containsExactly(new TimeWindow(now.minusSeconds(3600), now.minusSeconds(600)));
        assertThat(evaluated.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
        var inside = trim.evaluate(new TimeWindow(now.minusSeconds(3000), now), key(true, 5000, true, true, List.of()), now);
        assertThat(inside.status()).isEqualTo(ResultStatus.SUCCESS); assertThat(inside.missingRanges()).isEmpty();
    }
    @Test void belowCapUsesOnlyTimeBoundaryAndUnknownApplicationWarnsForEmptyAndHit() {
        var inside = new TimeWindow(now.minusSeconds(3000), now);
        assertThat(trim.evaluate(inside, key(true, 4999, true, true, List.of()), now).warnings()).isEmpty();
        for (long count : List.of(0L, 1L)) {
            for (boolean applied : List.of(true, false)) {
                var evaluated = trim.evaluate(window, key(true, count, applied, false, List.of()), now);
                assertThat(evaluated.status()).isEqualTo(count == 0 ? ResultStatus.EMPTY : ResultStatus.SUCCESS);
                assertThat(evaluated.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED); assertThat(evaluated.missingRanges()).isEmpty();
            }
        }
    }
    @Test void truncationAndIndependentKeyFailureRemainPartial() {
        var inside = new TimeWindow(now.minusSeconds(3000), now);
        var success = trim.evaluate(inside, key(true, 1, true, true, List.of()), now);
        var failed = trim.evaluate(inside, key(false, 0, true, true, List.of()), now);
        assertThat(trim.aggregate(List.of(success, failed))).isEqualTo(ResultStatus.PARTIAL);
        assertThat(trim.aggregate(List.of(failed, failed))).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        var truncated = trim.evaluate(inside, key(true, 1, true, true, List.of(inside)), now);
        assertThat(truncated.status()).isEqualTo(ResultStatus.PARTIAL); assertThat(truncated.missingRanges()).containsExactly(inside);
        assertThat(trim.evaluate(new TimeWindow(now.minusSeconds(4000), now.minusSeconds(3600)), key(true, 1, true, true, List.of()), now).status())
            .isEqualTo(ResultStatus.OUT_OF_RETENTION);
    }
    @Test void dlqScanFailureDecodeLossAndNormalEmptyAreDistinct() {
        var evaluator = new DlqCompletenessEvaluator();
        var failed = evaluator.evaluate(new DlqCompletenessEvaluator.Observation(false, 0, 0, 0));
        var partial = evaluator.evaluate(new DlqCompletenessEvaluator.Observation(true, 2, 1, 1));
        var empty = evaluator.evaluate(new DlqCompletenessEvaluator.Observation(true, 0, 0, 0));
        assertThat(failed.status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        assertThat(partial.status()).isEqualTo(ResultStatus.PARTIAL); assertThat(partial.missingFields()).containsExactly("decodeFailures=1");
        assertThat(empty.status()).isEqualTo(ResultStatus.EMPTY); assertThat(empty.warnings()).containsExactly(Warning.RETENTION_UNVERIFIED);
        assertThat(evaluator.aggregate(List.of(failed, empty))).isEqualTo(ResultStatus.PARTIAL);
        assertThat(evaluator.aggregate(List.of(failed, failed))).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        assertThatThrownBy(() -> new DlqCompletenessEvaluator.Observation(true, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
