package com.nori.ai.gateway.integration.loki;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LokiPropertiesTest {
    private static final String PREFIX = "ai.gateway.sources.loki";
    private Binder binder(Map<String, String> values) {
        Map<String, String> properties = new HashMap<>();
        values.forEach((key, value) -> properties.put(PREFIX + "." + key, value));
        return new Binder(new MapConfigurationPropertySource(properties));
    }
    private LokiProperties bind(Map<String, String> values) {
        return binder(values).bind(PREFIX, Bindable.of(LokiProperties.class)).orElseThrow(() -> new AssertionError("설정 바인딩이 누락되었습니다"));
    }
    @Test void approvedPropertyNamesBindTheConfiguredPerQueryLimitWithoutSilentDefaults() {
        var properties = bind(Map.of("split-interval", "PT6H", "per-query-limit", "123", "max-lines-total", "1000", "step-deadline", "PT30S"));
        assertThat(properties.splitInterval()).isEqualTo(Duration.ofHours(6));
        assertThat(properties.limitPerQuery()).isEqualTo(123);
        assertThat(properties.maxLinesTotal()).isEqualTo(1000);
        assertThat(properties.stepDeadline()).isEqualTo(Duration.ofSeconds(30));
    }
    @Test void allConfiguredLimitsUseTheExistingCanonicalConstructorContract() {
        assertThat(bind(Map.of("split-interval", "PT3H", "per-query-limit", "123", "max-lines-total", "321", "step-deadline", "PT12S")))
            .isEqualTo(new LokiProperties(Duration.ofHours(3), 123, 321, Duration.ofSeconds(12)));
    }
    @Test void absentAndPartialPropertiesKeepTheApprovedDefaultsAndPublicNoArgConstructor() {
        var defaults = new LokiProperties();
        assertThat(defaults).isEqualTo(new LokiProperties(Duration.ofHours(6), 500, 1000, Duration.ofSeconds(30)));
        assertThat(binder(Map.of()).bindOrCreate(PREFIX, Bindable.of(LokiProperties.class))).isEqualTo(defaults);
        assertThat(bind(Map.of("per-query-limit", "123")))
            .isEqualTo(new LokiProperties(Duration.ofHours(6), 123, 1000, Duration.ofSeconds(30)));
    }
    // 종전 SourceHttpClientTest.transportDefaultsMatchApprovedConnectAndReadTimeouts 의 Loki 기본값 단언(전송 기본값 단언은 source-support 에 남음).
    @Test void lokiDefaultsMatchApprovedSplitLimitAndDeadline() {
        var loki = new LokiProperties();
        assertThat(loki.splitInterval()).isEqualTo(Duration.ofHours(6)); assertThat(loki.limitPerQuery()).isEqualTo(500);
        assertThat(loki.maxLinesTotal()).isEqualTo(1000); assertThat(loki.stepDeadline()).isEqualTo(Duration.ofSeconds(30));
    }
    @Test void zeroAndNegativeLimitsFailThroughTheActualBinderAndExistingValidation() {
        Map<String, List<String>> invalid = Map.of("split-interval", List.of("PT0S", "-PT1S"),
            "per-query-limit", List.of("0", "-1"), "max-lines-total", List.of("0", "-1"), "step-deadline", List.of("PT0S", "-PT1S"));
        invalid.forEach((key, values) -> values.forEach(value -> assertThatThrownBy(() -> bind(Map.of(key, value)))
            .isInstanceOf(BindException.class).hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasStackTraceContaining("로그 조회 설정이 잘못되었습니다")));
    }
    @Test void malformedDurationFractionalOrOutOfRangeIntegerValuesFailInsteadOfUsingDefaults() {
        Map<String, List<String>> invalid = Map.of("split-interval", List.of("invalid", "PT6Z"),
            "per-query-limit", List.of("invalid", "1.5", "2147483648"), "max-lines-total", List.of("invalid", "1.5", "2147483648"),
            "step-deadline", List.of("invalid", "PT30Z"));
        invalid.forEach((key, values) -> values.forEach(value -> assertThatThrownBy(() -> bind(Map.of(key, value))).isInstanceOf(BindException.class)));
    }
}
