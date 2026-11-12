package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.LlmScriptRegistry;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.core.budget.CallType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 상세 요청(answerMode D)의 구조화 근거 보고 투영과 Composer 연결 계약(Plan §16.3 R4·R5, P20 범위): 기존 table 블록·결과 Source 의존성·Composer 128 요약·
 * L-5 실패 매핑을 바꾸지 않는다. AC-24-REPORT 의 F4 판정 component 가 아니다.
 */
class ReportSectionsContractIT extends LlmStubSupport {
    /** 수집된 근거와 미지원 근거가 한 보고에 함께 있도록 모델 READ 권한을 빼서 N 의 승인 제공자를 모두 비활성으로 둔다(S 는 수집). */
    { perms = PERMS.stream().filter(permission -> !permission.equals("MODEL_READ")).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    private JobAdmissionService.Accepted run(String key, String a, List<TestHookRegistry.LlmReply> tail, Optional<TestHookRegistry.PromptWatch> watch) throws Exception {
        var script = new ArrayList<TestHookRegistry.LlmReply>(); script.add(classify(wire("LQ", "EQP-001", null, null, null, List.of("N"), a))); script.addAll(tail);
        var job = admitScripted(key, "EQP-001 상태랑 정상 조건 보고서로 줘", windowOnly(), script, watch, null);
        terminal(job);
        return job;
    }
    private String deps(String job) { return jdbc().sql("SELECT result_source_deps::text FROM gw.result WHERE job_id=:id").param("id", job).query(String.class).single(); }

    @Test void detailedRequestAddsOneValidatedTableWithStatusSourcesAndMissingItemsWhileBriefDoesNot() throws Exception {
        var detailed = run("report-d", "D", List.of(compose("설비 상태를 확인했다[e1].")), Optional.empty());
        var brief = run("report-b", "B", List.of(compose("설비 상태를 확인했다[e1].")), Optional.empty());
        assertThat(terminal(detailed)).isEqualTo("SUCCEEDED");
        var rows = EvidenceWorkflowContractIT.reportRows(payload(detailed.jobId()).path("blocks"));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).path("evidence").asString()).isEqualTo("현재 상태");
        assertThat(rows.get(0).path("status").asString()).isEqualTo("COLLECTED");
        assertThat(rows.get(0).path("section").asString()).isEqualTo("관측 사실");
        assertThat(rows.get(0).path("sources").toString()).isEqualTo("[\"snapshot\"]");
        assertThat(rows.get(0).path("observedAt").isString()).isTrue();
        assertThat(rows.get(0).path("missingItems").toString()).as("현재 READ 가 제공하지 않는 요청 항목").contains("Port 목록", "진행 LOT");
        assertThat(rows.get(1).path("evidence").asString()).isEqualTo("정상 절차·선행 조건");
        assertThat(rows.get(1).path("section").asString()).isEqualTo("미확인");
        assertThat(rows.get(1).path("serverAdded").asBoolean()).isFalse();
        assertThat(rows.get(0).path("serverAdded").asBoolean()).as("ⓔ 서버 보완 S").isTrue();
        assertThat(EvidenceWorkflowContractIT.reportRows(payload(brief.jobId()).path("blocks"))).as("B 는 보고 블록 0").isEmpty();
        assertThat(payload(detailed.jobId()).path("blocks").size()).isEqualTo(payload(brief.jobId()).path("blocks").size() + 1);
        assertThat(deps(detailed.jobId())).as("보고 블록은 같은 단계 결과의 Source 의존성만 상속").isEqualTo(deps(brief.jobId()));
        assertThat(payload(detailed.jobId()).path("answer").asString()).isEqualTo("설비 상태를 확인했다[e1].");
        assertThat(payload(detailed.jobId()).properties().stream().map(Map.Entry::getKey)).as("공용 API 최상위 필드 추가 0").doesNotContain("coverage", "evidenceCoverage", "reportSections");
    }

    @Test void composerReceivesServerCoverageButNoNewLongNarrativeCall() throws Exception {
        var watch = Optional.of(new TestHookRegistry.PromptWatch(List.of("\"coverage\":[", "\"missingItems\":[\"Port 목록\"", "\"status\":\"UNSUPPORTED\"")));
        var job = run("report-composer", "D", List.of(compose("설비 상태를 확인했다[e1].")), watch);
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).as("D 도 요약 Composer 1회").isOne();
        assertThat(scripts().promptWatch(job.jobId()).orElseThrow()).contains(new LlmScriptRegistry.WatchRow(CallType.COMPOSER, 0, 1),
            new LlmScriptRegistry.WatchRow(CallType.COMPOSER, 1, 1), new LlmScriptRegistry.WatchRow(CallType.COMPOSER, 2, 1));
    }

    @Test void composerFailureKeepsTheVerifiedBlocksAndTheReportAsPartialWithoutReplacementText() throws Exception {
        var failed = run("report-llm-down", "D", List.of(new TestHookRegistry.LlmReply(CallType.COMPOSER, null, "stop", null, 0)), Optional.empty());
        assertThat(terminal(failed)).isEqualTo("PARTIAL");
        assertThat(result(failed.jobId())).containsEntry("error_code", "LLM_UNAVAILABLE");
        assertThat(EvidenceWorkflowContractIT.reportRows(payload(failed.jobId()).path("blocks"))).hasSize(2);
        assertThat(payload(failed.jobId()).has("answer")).isFalse();
    }
}
