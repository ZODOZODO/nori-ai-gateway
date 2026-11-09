package com.nori.ai.gateway.eval.report;

import com.nori.ai.gateway.eval.EvalException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** readiness 값 형식은 추론 readiness 상태 5종(READY·비READY 4종, P19 MODEL_DIGEST_MISMATCH 포함)만 받는다. */
class ManualRecorderTypedTest {
    @Test void readinessAcceptsEveryReadinessStateIncludingDigestMismatch() {
        assertThat(ManualRecorder.typed("readiness", "[\"READY\",\"MODEL_LOADING\",\"INFERENCE_UNAVAILABLE\",\"MODEL_CONTEXT_MISMATCH\",\"MODEL_DIGEST_MISMATCH\"]"))
            .isEqualTo(java.util.List.of("READY", "MODEL_LOADING", "INFERENCE_UNAVAILABLE", "MODEL_CONTEXT_MISMATCH", "MODEL_DIGEST_MISMATCH"));
    }

    @Test void readinessRejectsUnknownStatesAndNonTextItems() {
        for (String invalid : new String[]{"[\"DIGEST_MISMATCH\"]", "[\"model_digest_mismatch\"]", "[\"READY\",1]", "\"READY\""})
            assertThatThrownBy(() -> ManualRecorder.typed("readiness", invalid)).as(invalid)
                .isInstanceOfSatisfying(EvalException.class, e -> { assertThat(e.getMessage()).isEqualTo("USER_RUN_FIELD_INVALID"); assertThat(e.exitCode()).isEqualTo(2); });
    }
}
