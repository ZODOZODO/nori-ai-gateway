package com.nori.ai.gateway.app.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 추론 경보 고정 코드(Plan P19)는 인자 없이 제품 logger 에서 나올 때만 통과하고, 목록 밖 본문·인자 붙은 본문은 계속 가려진다. */
class AllowlistLogFilterTest {
    private static final String READINESS_LOGGER = "com.nori.ai.gateway.integration.inference.InferenceReadinessService";
    private static final List<String> INFERENCE_ALERT_CODES = List.of("INFERENCE_ALERT_RAISED_MODEL_LOADING", "INFERENCE_ALERT_RAISED_INFERENCE_UNAVAILABLE",
        "INFERENCE_ALERT_RAISED_MODEL_CONTEXT_MISMATCH", "INFERENCE_ALERT_RAISED_MODEL_DIGEST_MISMATCH", "INFERENCE_ALERT_CLEARED");
    private final LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
    private final AllowlistLogFilter filter = new AllowlistLogFilter();
    private final AllowlistJsonEncoder encoder = new AllowlistJsonEncoder();

    private LoggingEvent event(String logger, String message, Object... arguments) {
        return new LoggingEvent(AllowlistLogFilterTest.class.getName(), context.getLogger(logger), Level.WARN, message, null, arguments);
    }
    private String encoded(LoggingEvent event) { return new String(encoder.encode(event), StandardCharsets.UTF_8); }

    @Test void inferenceAlertCodesPassAndAreWrittenVerbatim() {
        for (String code : INFERENCE_ALERT_CODES) {
            var event = event(READINESS_LOGGER, code);
            assertThat(filter.decide(event)).as(code).isEqualTo(FilterReply.NEUTRAL);
            assertThat(encoded(event)).as(code).contains("\"message\":\"" + code + "\"");
        }
    }

    @Test void unlistedOrArgumentCarryingMessagesStayMasked() {
        for (var event : List.of(event(READINESS_LOGGER, "INFERENCE_READINESS_MODEL_LOADING"), event(READINESS_LOGGER, "INFERENCE_ALERT_RAISED_READY"),
                event(READINESS_LOGGER, "INFERENCE_ALERT_RAISED_MODEL_DIGEST_MISMATCH {}", "sha256:" + "a".repeat(64)),
                event(READINESS_LOGGER, "INFERENCE_ALERT_CLEARED", "http://inference.internal:11434"), event("org.thirdparty.Inference", "INFERENCE_ALERT_CLEARED"))) {
            assertThat(filter.decide(event)).as(event.getMessage()).isEqualTo(FilterReply.DENY);
            assertThat(encoded(event)).as(event.getMessage()).contains("\"message\":\"UNLISTED_MESSAGE\"").doesNotContain("INFERENCE_", "sha256:", "11434");
        }
    }
}
