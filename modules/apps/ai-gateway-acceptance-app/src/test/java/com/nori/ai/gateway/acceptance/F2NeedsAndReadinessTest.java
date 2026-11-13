package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import static org.assertj.core.api.Assertions.*;

/** AC-32-NEEDS 사례표 판정과 readiness stub leaf 판정을 loopback 대역으로 실행한다(대역 값은 시험 지정이며 제품 증거가 아님). */
class F2NeedsAndReadinessTest {
    private static final Map<String,List<String>> BOUND = Map.of("[\"S\",\"H\"]", List.of("S", "H"), "[\"H\",\"S\"]", List.of("S", "H"), "[\"C\"]", List.of("H", "N", "I", "C"), "[\"N\"]", List.of("S", "N"));
    private static final Map<String,List<String>> ADDED = Map.of("[\"C\"]", List.of("H", "N", "I"), "[\"N\"]", List.of("S"));

    /** 시험이 기대 정규화 결과를 상세 보고 표 행으로 돌려주는 대역. {@code wrongAdded} 이면 ⓒ 의 서버 보완을 일부러 틀린다. */
    private static F2LocalFixture.Outcome needs(Map.Entry<JsonNode,String> entry, boolean wrongAdded) {
        var script = entry.getKey().path("llmScript");
        JsonNode wire; try { wire = CanonicalJson.MAPPER.readTree(script.get(0).path("content").asText()); } catch (Exception e) { wire = CanonicalJson.MAPPER.nullNode(); }
        if (script.size() > 1 && script.get(1).path("callType").asText().equals("CLASSIFY")) return new F2LocalFixture.Outcome().failed("PLAN_INVALID").watch("CLASSIFY", 0, 2).watch("CLASSIFY", 1, 1);
        var outcome = new F2LocalFixture.Outcome().watch("CLASSIFY", 0, 1);
        String n = wire.path("n").toString();
        if (wire.path("t").asText().equals("KQ") || !BOUND.containsKey(n)) { if (!BOUND.containsKey(n) && !wire.path("t").asText().equals("KQ")) outcome.failed("RESOURCE_NOT_FOUND"); return outcome; }
        var added = new ArrayList<>(ADDED.getOrDefault(n, List.of())); if (wrongAdded && n.equals("[\"C\"]")) added.remove("I");
        var rows = new ArrayList<Map<String,Object>>();
        for (String code : BOUND.get(n)) rows.add(Map.of("evidence", F2JobChecks.NEED_LABELS.get(code), "serverAdded", added.contains(code)));
        outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of(Map.of("type", "snapshot"), Map.of("type", "table", "content", Map.of("columns", F2JobChecks.REPORT_COLUMNS, "rows", rows)))));
        return outcome;
    }

    @Test void needsCasesCheckBoundEvidenceAndServerAddedThroughTheDetailedReportAndLeaveUnobservableCasesNotRun() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            fixture.responder = entry -> needs(entry, false);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.of(120)).run("AC-32-NEEDS")).isInstanceOf(TestAbortedException.class).hasMessage("NEEDS_CASE_UNOBSERVED_d");
            assertThat(fixture.jobs).as("ⓐ~ⓞ 사례마다 새 Job").isEqualTo(15);
            var scenarios = new ArrayList<>(fixture.armed.values());
            assertThat(scenarios.get(0).path("llmScript").get(0).path("content").asText()).contains("\"n\":[\"S\",\"H\"]", "\"a\":\"D\"");
            assertThat(scenarios.get(5).path("promptWatch").path("sentinels").get(1).asText()).isEqualTo("오류: SCHEMA_KEYS_INVALID)");
            var maximum = scenarios.getLast().path("llmScript").get(0);
            assertThat(maximum.path("usage").path("completionTokens").asInt()).as("ⓞ 하네스 tokenizer 계수를 그대로 무장").isEqualTo(120);
            assertThat(fixture.questions.getLast()).contains("\"" + "검".repeat(256) + "\"");
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-32-NEEDS")).hasMessage("NEEDS_CASE_UNOBSERVED_do");
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.of(257)).run("AC-32-NEEDS")).isInstanceOf(AssertionError.class).hasMessage("F2_NEEDS_MAX_WIRE_BUDGET");
            fixture.responder = entry -> needs(entry, true);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.of(120)).run("AC-32-NEEDS")).isInstanceOf(AssertionError.class).hasMessage("F2_NEEDS_SERVER_ADDED_c");
        }
    }

    private static F2ReadinessChecks readiness(F2LocalFixture fixture, F2ReadinessChecks.AlertLog alerts) { return new F2ReadinessChecks(fixture.gateway, fixture.hooks, fixture.oracle, alerts); }

    @Test void readinessLeavesUseOnlyTheStubHookObserve503AndAlwaysRestoreReady() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            readiness(fixture, (code, since) -> code.equals("INFERENCE_ALERT_RAISED_MODEL_CONTEXT_MISMATCH") ? 1 : 0).run("AC-33-CTX");
            assertThat(fixture.readiness).isEqualTo("READY"); assertThat(fixture.jobs).isZero();
            readiness(fixture, (code, since) -> 0).run("AC-44");
            readiness(fixture, (code, since) -> 0).run("AC-33-LOADING");
            readiness(fixture, (code, since) -> code.equals("INFERENCE_ALERT_RAISED_MODEL_DIGEST_MISMATCH") ? 1 : 0).run("AC-33-DIGEST");
            fixture.retryAfter = false;
            assertThatThrownBy(() -> readiness(fixture, (code, since) -> code.endsWith("DIGEST_MISMATCH") ? 1 : 0).run("AC-33-DIGEST")).hasMessage("F2_ADMISSION_RETRY_AFTER_MODEL_DIGEST_MISMATCH");
            assertThat(fixture.readiness).as("실패해도 OFF 로 되돌린다").isEqualTo("READY");
            readiness(fixture, (code, since) -> code.endsWith("CONTEXT_MISMATCH") ? 1 : 0).run("AC-33-CTX");
            fixture.retryAfter = true;
            assertThatThrownBy(() -> readiness(fixture, (code, since) -> code.endsWith("CONTEXT_MISMATCH") ? 2 : 0).run("AC-33-CTX")).hasMessage("F2_IMMEDIATE_ALERT_MODEL_CONTEXT_MISMATCH");
            assertThatThrownBy(() -> readiness(fixture, F2ReadinessChecks.unavailableAlerts()).run("AC-33-CTX")).isInstanceOf(TestAbortedException.class).hasMessage("LOKI_READBACK_MISSING");
            assertThat(fixture.readiness).isEqualTo("READY");
        }
    }

    @Test void persistentAlertAdvancesOnlyTheControlledClockAndFailsWhenClearedBeforeReady() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            readiness(fixture, (code, since) -> code.equals("INFERENCE_ALERT_RAISED_MODEL_LOADING") ? 1 : 0).run("AC-33-PERSIST");
            assertThat(fixture.clockOps).hasSize(4);
            assertThat(fixture.clockOps.get(0)).contains("FREEZE_AT"); assertThat(fixture.clockOps.get(1)).contains("\"seconds\":601"); assertThat(fixture.clockOps.get(2)).contains("\"seconds\":3600"); assertThat(fixture.clockOps.get(3)).contains("RESUME_SYSTEM");
            assertThatThrownBy(() -> readiness(fixture, (code, since) -> code.endsWith("MODEL_LOADING") || code.endsWith("CLEARED") ? 1 : 0).run("AC-33-PERSIST")).hasMessage("F2_PERSIST_ALERT_CLEARED_BEFORE_READY");
            fixture.restoredState = "MODEL_LOADING";
            assertThatThrownBy(() -> readiness(fixture, (code, since) -> 0).run("AC-44")).hasMessage("F2_READINESS_NOT_READY_BEFORE");
        }
    }

    @Test void readinessRestorationFailureIsReportedOnlyWhenTheLeafItselfPassed() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            var calls = new AtomicBoolean();
            var checks = readiness(fixture, (code, since) -> { if (calls.getAndSet(true)) return 1; fixture.restoredState = "INFERENCE_UNAVAILABLE"; return 1; });
            assertThatThrownBy(() -> checks.run("AC-33-CTX")).hasMessage("F2_READINESS_NOT_RESTORED");
        }
    }
}
