package com.nori.ai.gateway.core.budget;

import com.nori.ai.gateway.core.budget.TruncationDetector.Result;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TruncationDetectorTest {
    private final TruncationDetector detector = new TruncationDetector();

    @Test void usageBelowTheGatewayCountIsTruncationAndEqualOrAboveIsNot() {
        // P03 G-USAGE U1 계수 1,056 기준
        assertThat(detector.check(1_056, 1_056)).isEqualTo(Result.OK);
        assertThat(detector.check(1_056, 1_055)).isEqualTo(Result.TRUNCATED);
        assertThat(detector.check(1_056, 0)).isEqualTo(Result.TRUNCATED);
        assertThat(detector.check(1_056, 1_057)).isEqualTo(Result.OK);
    }

    @Test void rejectsImpossibleCounts() {
        assertThatThrownBy(() -> detector.check(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> detector.check(10, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
