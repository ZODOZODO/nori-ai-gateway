package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import static org.assertj.core.api.Assertions.*;

/**
 * F2 Job 판정기를 loopback 대역으로 실행한다. 각 시험은 양성 경로와, 그 판정이 실제로 잡아야 하는 실패(호출 수·상태·Tool·서술·관측 부재)를 함께 둔다.
 * 대역 결과는 시험이 지정한 값이며 제품 동작의 증거가 아니다.
 */
class F2JobChecksTest {
    private static List<String> types(JsonNode scenario) { var values = new ArrayList<String>(); scenario.path("llmScript").forEach(reply -> values.add(reply.path("callType").asText())); return values; }
    private static String sentinel(JsonNode scenario) { return scenario.path("promptWatch").path("sentinels").get(0).asText(); }

    @Test void stubToolRejectionArmsTwoViolatingClassificationsAndFailsWhenTheBoundSystemOrToolBoundaryBreaks() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("PLAN_INVALID").watch("CLASSIFY", 0, 2);
            fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-STUB-TOOL");
            var scenario = fixture.armed.values().iterator().next();
            assertThat(types(scenario)).containsExactly("CLASSIFY", "CLASSIFY");
            assertThat(scenario.path("llmScript").get(0).path("content").asText()).contains("\"capabilityId\":\"tc.unknown.tool\"");
            assertThat(fixture.questions.getFirst()).startsWith(sentinel(scenario) + " ");
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome().failed("PLAN_INVALID").watch("CLASSIFY", 0, 2); outcome.owner = "WM"; return outcome; };
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-STUB-SYSTEM")).isInstanceOf(AssertionError.class).hasMessage("F2_BOUND_SYSTEM_CHANGED");
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("PLAN_INVALID").watch("CLASSIFY", 0, 2).tool("wm.inventory.get", "SUCCESS");
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-STUB-SYSTEM")).hasMessage("F2_REJECTED_PLAN_EXECUTED_TOOL");
        }
    }

    @Test void schemaAndLengthFailuresRequireExactlyTwoClassificationCallsAndNoComposer() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("PLAN_INVALID").clarification("PLAN_INVALID", List.of("q")).watch("CLASSIFY", 0, 2);
            fixture.checks(Optional.empty(), Optional.empty()).run("AC-32-LENGTH");
            var length = fixture.armed.values().iterator().next().path("llmScript");
            assertThat(length.get(0).path("finishReason").asText()).isEqualTo("length"); assertThat(length.get(1)).isEqualTo(length.get(0));
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("PLAN_INVALID").clarification("PLAN_INVALID", List.of("q")).watch("CLASSIFY", 0, 1);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-32-SCHEMA")).hasMessage("F2_CLASSIFY_RETRY_COUNT");
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("PLAN_INVALID").clarification("PLAN_INVALID", List.of("q")).watch("CLASSIFY", 0, 2).watch("COMPOSER", 0, 1);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-32-SCHEMA")).hasMessage("F2_FAILED_CLASSIFY_PROCEEDED");
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("LLM_UNAVAILABLE").watch("CLASSIFY", 0, 2);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-32-SCHEMA")).hasMessage("F2_EXPECTED_FAILED_PLAN_INVALID");
        }
    }

    @Test void overBudgetClassificationIsArmedWithoutAnyClassificationReplySoASentCallCannotLookLikeABudgetStop() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("INPUT_BUDGET_EXCEEDED").watch("CLASSIFY", 0, 1);
            fixture.checks(Optional.empty(), Optional.empty()).run("AC-43-CLS");
            assertThat(types(fixture.armed.values().iterator().next())).containsExactly("COMPOSER");
            // 분류가 실제로 전송됐다면 고정 응답 유형 불일치로 LLM_UNAVAILABLE 이 된다.
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("LLM_UNAVAILABLE").watch("CLASSIFY", 0, 2);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-43-CLS")).hasMessage("F2_EXPECTED_FAILED_INPUT_BUDGET_EXCEEDED");
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("INPUT_BUDGET_EXCEEDED").watch("CLASSIFY", 0, 2);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-43-CLS")).hasMessage("F2_CLS_BUDGET_CALLS");
        }
    }

    @Test void unobservableDeploymentInputsAreNotRunNeverPass() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-31")).isInstanceOf(TestAbortedException.class).hasMessage("COMPOSER_BUDGET_OBSERVATION_UNAVAILABLE");
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-43-COMP")).isInstanceOf(TestAbortedException.class).hasMessage("COMPOSER_OVERSIZE_FIXTURE_UNAVAILABLE");
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-38-F2")).isInstanceOf(TestAbortedException.class).hasMessage("LOKI_POSITIVE_CONTROL_UNAVAILABLE");
            assertThat(fixture.jobs).as("관측 수단이 없으면 Job 을 만들지 않는다").isZero();
            fixture.scenarios.add(F2JobChecks.COMPOSER_OVERSIZE_SCENARIO);
            fixture.responder = entry -> new F2LocalFixture.Outcome().failed("INPUT_BUDGET_EXCEEDED").watch("CLASSIFY", 0, 1);
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-43-COMP")).as("결정론 블록이 사라지면 FAIL").hasMessage("F2_COMP_BUDGET_BLOCKS_DROPPED");
        }
    }

    @Test void egressNeedsBothPositiveControlsBeforeAnyBlockedTargetCanPass() throws Exception {
        String targets = "[{\"id\":\"inference\",\"expect\":\"ALLOWED\"},{\"id\":\"db\",\"expect\":\"ALLOWED\"},{\"id\":\"internet\",\"expect\":\"BLOCKED\"},{\"id\":\"svc\",\"expect\":\"BLOCKED\"}]";
        try (var fixture = new F2LocalFixture()) {
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-35-EGRESS")).isInstanceOf(TestAbortedException.class).hasMessage("EGRESS_TARGETS_INPUT_MISSING");
            assertThatThrownBy(() -> fixture.checks(Optional.of("[{\"id\":\"a\",\"expect\":\"ALLOWED\",\"host\":\"10.0.0.1\"}]"), Optional.empty()).run("AC-35-EGRESS")).hasMessage("EGRESS_TARGETS_INPUT_INVALID");
            fixture.egress.put("inference", true);
            assertThatThrownBy(() -> fixture.checks(Optional.of(targets), Optional.empty()).run("AC-35-EGRESS")).isInstanceOf(TestAbortedException.class).hasMessage("EGRESS_POSITIVE_CONTROL_FAILED");
            fixture.egress.put("db", true); fixture.egress.put("svc", true);
            assertThatThrownBy(() -> fixture.checks(Optional.of(targets), Optional.empty()).run("AC-35-EGRESS")).isInstanceOf(AssertionError.class).hasMessage("F2_EGRESS_BLOCKED_CONNECTED");
            fixture.egress.put("svc", false);
            fixture.checks(Optional.of(targets), Optional.empty()).run("AC-35-EGRESS");
        }
    }

    @Test void generalHelpComparesRowsToTheManifestViewAndComposerInputToManifestPhrasesOnly() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            var rows = F2Fixture.GQ_HELP_ROWS.stream().map(id -> Map.of("capabilityId", id)).toList();
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome().watch("CLASSIFY", 0, 1).watch("COMPOSER", 1, 1).watch("COMPOSER", 2, 0); outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of(Map.of("type", "table", "content", Map.of("rows", rows))), "evidence", List.of())); outcome.taskTypes = List.of("GENERAL_QUERY"); return outcome; };
            fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-GQ-HELP");
            var watch = fixture.armed.values().iterator().next().path("promptWatch").path("sentinels");
            assertThat(watch.get(1).asText()).isEqualTo(F2Fixture.GQ_MANIFEST_SNIPPET); assertThat(watch.get(2).asText()).isEqualTo("tc.knowledge.search");
            var leaked = new ArrayList<Map<String,String>>(rows); leaked.add(Map.of("capabilityId", "tc.knowledge.search"));
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome().watch("CLASSIFY", 0, 1).watch("COMPOSER", 1, 1); outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of(Map.of("type", "table", "content", Map.of("rows", leaked))))); outcome.taskTypes = List.of("GENERAL_QUERY"); return outcome; };
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-GQ-HELP")).hasMessage("F2_GQ_HELP_ROWS");
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome().watch("CLASSIFY", 0, 1); outcome.taskTypes = List.of("GENERAL_QUERY"); outcome.calls.put("getEqpSnapshot", 1); return outcome; };
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-GQ-HELP")).hasMessage("F2_GQ_BUSINESS_READ");
        }
    }

    @Test void generalBypassRecordsOneRubricTargetPerPreregisteredFixtureAndForbiddenTextFails() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome().watch("CLASSIFY", 0, 1); outcome.taskTypes = List.of("GENERAL_QUERY");
                outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of(), "evidence", List.of(), "answer", entry.getKey().path("llmScript").get(1).path("content").asText())); return outcome; };
            fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-GQ-BYPASS-XSYS");
            var script = F2Fixture.fixtures("AC-22-GQ-BYPASS-XSYS").getFirst().composerScript().orElseThrow();
            assertThat(F2RubricTargets.snapshot()).singleElement().satisfies(target -> {
                assertThat(target.fixtureId()).isEqualTo("f2-gq-bypass-xsys"); assertThat(target.answerSha256()).isEqualTo(CanonicalJson.sha256(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            });
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome().watch("CLASSIFY", 0, 1); outcome.taskTypes = List.of("GENERAL_QUERY");
                outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of(), "answer", "EQP_001 은 CONNECTED 상태입니다")); return outcome; };
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-GQ-BYPASS-DATA")).hasMessage("F2_GQ_FORBIDDEN_CLAIM");
        }
    }

    @Test void liveGoldenSetIsUnarmedRecordsSamplesAndRubricTargetsAndRejectsToolsOutsideTheRegistry() throws Exception {
        try (var fixture = new F2LocalFixture()) {
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome().tool("tc.eqp.getOperationalSnapshot", "SUCCESS").tool("tc.logs.search", "FAILED"); outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of(), "answer", "근거 범위에서 원인 후보를 정리했다[e1].")); return outcome; };
            fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-LIVE");
            assertThat(fixture.armed).as("LLM_LIVE 는 무장 없이 운영 전송").isEmpty();
            assertThat(F2RubricTargets.snapshot()).hasSize(3).extracting(RubricViewer.Target::fixtureId).containsExactly("f2-live-incident-comm", "f2-live-incident-dlq", "f2-live-incident-stall");
            assertThat(F2LiveSamples.snapshot()).hasSize(3).allSatisfy(sample -> { assertThat(sample.values()).containsEntry("toolCount", 2).containsEntry("toolFailedCount", 1L).containsEntry("unregisteredToolCount", 0L); });
            F2RubricTargets.clear(); F2LiveSamples.clear();
            fixture.responder = entry -> new F2LocalFixture.Outcome().tool("tc.unknown.tool", "SUCCESS");
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-LIVE")).hasMessage("F2_LIVE_TOOL_OUTSIDE_REGISTRY");
            assertThat(F2LiveSamples.snapshot()).as("위반 Job 도 표본·관찰은 남는다").singleElement().satisfies(sample -> assertThat(sample.values()).containsEntry("unregisteredToolCount", 1L));
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome(); outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of(), "answer", "WM 에서 조회한 결과 재고가 있다")); return outcome; };
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-22-LIVE")).hasMessage("F2_LIVE_FORBIDDEN_CLAIM");
            fixture.responder = entry -> new F2LocalFixture.Outcome();
            assertThatThrownBy(() -> fixture.checks(Optional.empty(), Optional.empty()).run("AC-29")).hasMessage("F2_FOREIGN_NOT_UNSUPPORTED");
        }
    }

    @Test void latencyMeasurementUsesOnlyStoredJobTimesAsIntegerMillisecondsWithoutAggregates() {
        F2LiveSamples.clear();
        try {
            F2LiveSamples.add("AC-22-LIVE", "f2-live-incident-comm", "job-1", Map.of("toolCount", 1));
            Instant created = Instant.parse("2026-11-13T00:00:00Z");
            var timing = new OracleQueries.JobTiming("job-1", "SUCCEEDED", created, Optional.of(created.plusMillis(250)), Optional.of(created.plusMillis(4_250)));
            var values = F2LiveSamples.measurement(F2LiveSamples.snapshot(), List.of(timing));
            var samples = CanonicalJson.MAPPER.valueToTree(values.get("samples"));
            assertThat(samples).hasSize(3);
            assertThat(samples.findValuesAsText("callType")).containsExactly("QUEUE", "EXECUTION", "E2E");
            assertThat(samples.findValues("latencyMs")).extracting(JsonNode::asLong).containsExactly(250L, 4_000L, 4_250L);
            assertThat(CanonicalJson.MAPPER.valueToTree(values).toString()).doesNotContain("p50", "p95");
            var unfinished = new OracleQueries.JobTiming("job-1", "RUNNING", created, Optional.of(created), Optional.empty());
            assertThatThrownBy(() -> F2LiveSamples.measurement(F2LiveSamples.snapshot(), List.of(unfinished))).hasMessage("LIVE_SAMPLE_TIMING_MISSING");
            assertThatThrownBy(() -> F2LiveSamples.measurement(List.of(), List.of())).hasMessage("LIVE_SAMPLES_EMPTY");
        } finally { F2LiveSamples.clear(); }
    }
}
