package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.time.TimeWindowResolver;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class TimeWindowResolverTest {
    private final TimeWindowResolver resolver=new TimeWindowResolver();
    private final WorkflowDefinition live=workflows().stream().filter(w->w.taskType()==TaskType.LIVE_QUERY).findFirst().orElseThrow();
    private Map<String,Object> window(){return Map.of("from","2026-11-04T09:00:00.123456789+09:00","to","2026-11-04T10:00:00.987654321+09:00");}
    private Map<String,Object> context(){return Map.of("timezone","Asia/Seoul","referenceTime","2026-11-05T09:00+09:00");}
    @Test void currentWindowAndExplicitExtractionPreserveInstantPrecisionAndNeverReadPrevious() {
        var context=new HashMap<>(context());context.put("timeWindow",window());var calls=new AtomicInteger();
        var result=resolver.resolve(Map.of(),context,()->{calls.incrementAndGet();throw new AssertionError();},live);
        assertThat(calls).hasValue(0);assertThat(result.values()).isEqualTo(window());assertThat(result.window().orElseThrow().startAt()).isEqualTo(Instant.parse("2026-11-04T00:00:00.123456789Z"));
        result=resolver.resolve(Map.of("from","2026-11-03T00:00","to","2026-11-03T01:00"),context,()->{throw new AssertionError();},live);
        assertThat(result.contextConflict()).isTrue();assertThat(result.values()).containsEntry("from","2026-11-03T00:00+09:00");
    }
    @Test void previousCallbackRunsOnceOnlyAtFourthPriorityAndNoWindowRequiresClarification() {
        var calls=new AtomicInteger();var result=resolver.resolve(Map.of(),context(),()->{calls.incrementAndGet();return Optional.of(window());},live);
        assertThat(calls).hasValue(1);assertThat(result.values()).isEqualTo(window());
        assertThatThrownBy(()->resolver.resolve(Map.of(),context(),Optional::empty,live)).isInstanceOf(TimeWindowResolver.Failure.class).extracting(f->((TimeWindowResolver.Failure)f).code()).isEqualTo(ErrorCode.TIME_WINDOW_REQUIRED);
        var general=workflows().stream().filter(w->w.taskType()==TaskType.GENERAL_QUERY).findFirst().orElseThrow();resolver.resolve(Map.of(),Map.of(),()->{throw new AssertionError();},general);
    }
    @Test void invalidDatesNanoOverflowFutureAndReversedInputNeverFallback() {
        for(Map<String,Object> value:List.<Map<String,Object>>of(Map.of("from","2026-02-30T00:00","to","2026-03-01T00:00"),Map.of("from","2026-11-04T00:00:00.1234567890Z","to","2026-11-04T01:00Z"),Map.of("from","2026-11-06T00:00Z","to","2026-11-06T01:00Z"),Map.of("from","2026-11-04T02:00Z","to","2026-11-04T01:00Z"))) {
            assertThatThrownBy(()->resolver.resolve(value,context(),()->{throw new AssertionError();},live)).isInstanceOf(TimeWindowResolver.Failure.class).extracting(f->((TimeWindowResolver.Failure)f).code()).isEqualTo(ErrorCode.PLAN_INVALID);
        }
    }
    @Test void persistentSupplierFailureIsSameExceptionAndNeverAnInputClarification() {
        for(RuntimeException failure:List.of(new IllegalArgumentException("손상 저장값"),new IllegalStateException("잘못된 결속")))assertThatThrownBy(()->resolver.resolve(Map.of(),context(),()->{throw failure;},live)).isSameAs(failure);
    }
    @Test void offsetNormalizationKeepsSecondsPresenceAndAllFractionDigits() {
        var result=resolver.resolve(Map.of("from","2026-11-04T00:00:00.120000000Z","to","2026-11-04T01:00:00.000000001Z"),context(),Optional::empty,live);
        assertThat(result.values()).containsEntry("from","2026-11-04T09:00:00.120000000+09:00").containsEntry("to","2026-11-04T10:00:00.000000001+09:00");
    }
    @Test void minuteSecondsAndEveryFractionWidthPreserveTheirLexicalPrecision() {
        for(String suffix:List.of("",":00",":00.1",":00.12",":00.123",":00.1234",":00.12345",":00.123456",":00.1234567",":00.12345678",":00.123456789")) {
            String from="2026-11-04T09:00"+suffix+"+09:00",to="2026-11-04T10:00"+suffix+"+09:00";
            var result=resolver.resolve(Map.of("from",from,"to",to),context(),()->{throw new AssertionError();},live);
            assertThat(result.values()).containsEntry("from",from).containsEntry("to",to);
        }
    }
    @Test void leapCalendarAndDstGapOverlapUseStrictCommonParser() {
        var leap=resolver.resolve(Map.of("from","2024-02-29T00:00Z","to","2024-03-01T00:00Z"),context(),()->{throw new AssertionError();},live);
        assertThat(leap.window().orElseThrow().startAt()).isEqualTo(Instant.parse("2024-02-29T00:00:00Z"));
        assertThatThrownBy(()->resolver.resolve(Map.of("from","2025-02-29T00:00Z","to","2025-03-01T00:00Z"),context(),()->{throw new AssertionError();},live)).isInstanceOf(TimeWindowResolver.Failure.class);
        Map<String,Object> ny=Map.of("timezone","America/New_York","referenceTime","2026-11-05T00:00-05:00");
        for(var window:List.of(Map.<String,Object>of("from","2026-03-08T02:15","to","2026-03-08T03:15"),Map.<String,Object>of("from","2026-11-01T01:15","to","2026-11-01T02:15")))assertThatThrownBy(()->resolver.resolve(window,ny,()->{throw new AssertionError();},live)).isInstanceOf(TimeWindowResolver.Failure.class);
        var explicit=resolver.resolve(Map.of("from","2026-11-01T01:15-04:00","to","2026-11-01T01:45-04:00"),ny,()->{throw new AssertionError();},live);
        assertThat(explicit.window().orElseThrow().startAt()).isEqualTo(Instant.parse("2026-11-01T05:15:00Z"));
    }
}
