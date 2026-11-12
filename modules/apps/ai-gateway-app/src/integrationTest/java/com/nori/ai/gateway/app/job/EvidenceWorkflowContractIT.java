package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.workflow.EvidencePlanBuilder;
import com.nori.ai.gateway.tcmock.MockScenarioStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 결속된 근거 요구 → 승인 근거 프로필 → Manifest 기본 경로 + 프로필 단계 → 같은 Dispatcher 의 실제 Job 경로 계약(Plan §16.3 R3·R5, SPEC §28.2·§28.8, P20 범위).
 * 각 사례는 정상 경로에 결함 하나만 둔다. F3 Knowledge 는 PLANNED 로 남아 실행·성공으로 꾸미지 않는다. AC-24-EVIDENCEFLOW 의 F4 판정 component 가 아니다.
 * 호출 한도 초과는 v1 프로필로 실제 Job 에서 만들 수 없어(활성 READ 가 12개 미만이고 같은 Capability 는 한 계획에 한 번) core {@code EvidenceSupplementExecutorTest} 가 맡는다.
 */
class EvidenceWorkflowContractIT extends LlmStubSupport {
    @Override protected Class<?>[] extraSources() { return new Class<?>[]{HarnessObservationsEndpoint.class, WorkflowSourceFixture.class, WorkflowSourceFixture.GitConfiguration.class}; }
    /** git 단계 입력(Summarizer 용어)이 생기도록 같은 로그·Trace fixture 를 쓴다 — 선택 여부만 근거 요구로 갈린다. */
    @org.junit.jupiter.api.BeforeEach void sources() {
        context.getBean(MockScenarioStore.class).select("default");
        var server = context.getBean(WorkflowSourceFixture.Server.class);
        server.logText = "EQP-001 EQUIPMENT_ERROR"; server.traceVersions = List.of("123");
    }
    @org.junit.jupiter.api.AfterEach void defaultScenario() { context.getBean(MockScenarioStore.class).select("default"); }
    private List<String> executed(String job) {
        return jdbc().sql("SELECT capability_id FROM gw.tool_execution WHERE job_id=:id ORDER BY capability_id").param("id", job).query(String.class).list();
    }
    private Map<String,Integer> executionCounts(String job) {
        Map<String,Integer> counts = new TreeMap<>();
        for (String capability : executed(job)) counts.merge(capability, 1, Integer::sum);
        return counts;
    }
    private JobAdmissionService.Accepted run(String key, String t, List<String> n, String a) throws Exception {
        var job = admitScripted(key, "EQP-001 진행이 멈췄어", t.equals("INC") ? workflowFields() : windowOnly(), List.of(classify(wire(t, "EQP-001", null, null, null, n, a)), compose("확인된 자료를 정리했다[e1].")), Optional.empty(), null);
        terminal(job);
        return job;
    }
    private List<String> report(String job) {
        return reportRows(payload(job).path("blocks")).stream().map(row -> row.path("evidence").asString() + ":" + row.path("status").asString()).toList();
    }

    @Test void theServerBoundNeedsSelectOnlyApprovedOptionalStepsAndKeepTheMandatoryDefaultPath() throws Exception {
        var history = run("flow-sh", "INC", List.of("S", "H"), "B");
        assertThat(executed(history.jobId())).as("필수 기본 경로만, git 선택 0").doesNotContain("tc.git.searchCode")
            .contains("tc.eqp.getOperationalSnapshot", "tc.commLogs.search", "tc.logs.search", "tc.trace.search", "tc.dlq.list");
        var implementation = run("flow-shi", "INC", List.of("S", "H", "I"), "B");
        assertThat(executed(implementation.jobId())).as("I 요구일 때만 승인 git 단계").contains("tc.git.searchCode");
        var comparison = run("flow-c", "INC", List.of("C"), "B");
        assertThat(executed(comparison.jobId())).as("ⓒ 서버 보완 I 로 git 선택").contains("tc.git.searchCode");
    }

    @Test void outsideManifestProfileStepsRunInTheFirstRoundThroughTheSameJobPath() throws Exception {
        var state = run("flow-sn", "LQ", List.of("N"), "B");
        assertThat(executed(state.jobId())).as("LQ 의 N → workflow 밖 승인 모델 READ 를 1차에 실행, PLANNED Knowledge·비활성 이벤트 0")
            .containsExactly("tc.eqp.getOperationalSnapshot", "tc.model.read");
        var history = run("flow-lq-h", "LQ", List.of("S", "H"), "D");
        assertThat(executed(history.jobId())).as("LQ 의 H → 필수 이력 READ(통신 로그·로그) 1차, H 수집됨 → 선택 Trace·DLQ 보완 0")
            .containsExactly("tc.commLogs.search", "tc.eqp.getOperationalSnapshot", "tc.logs.search");
        assertThat(report(history.jobId())).contains("실제 발생 이력:COLLECTED");
    }

    @Test void anInactiveRegistryCapabilityIsNeverExecutedAndStaysUnsupported() throws Exception {
        perms = new HashSet<>(PERMS); perms.remove("MODEL_READ");
        var job = run("flow-inactive", "LQ", List.of("S", "N"), "D");
        assertThat(executed(job.jobId())).as("권한 밖 모델 READ 는 Registry view 에 없어 실행 0").containsExactly("tc.eqp.getOperationalSnapshot");
        assertThat(report(job.jobId())).contains("정상 절차·선행 조건:UNSUPPORTED", "현재 상태:COLLECTED");
    }

    @Test void emptyHistoryRunsExactlyOneSupplementRoundAndNeverRepeatsAQuery() throws Exception {
        context.getBean(MockScenarioStore.class).select("empty");
        context.getBean(WorkflowSourceFixture.Server.class).logText = null;
        context.getBean(WorkflowSourceFixture.Server.class).traceVersions = List.of();
        var job = run("flow-supplement", "LQ", List.of("S", "H"), "D");
        assertThat(executionCounts(job.jobId())).as("1차 3건 + 보완 round 1회 Trace·DLQ, 각 조회 1회")
            .containsExactly(Map.entry("tc.commLogs.search", 1), Map.entry("tc.dlq.list", 1), Map.entry("tc.eqp.getOperationalSnapshot", 1), Map.entry("tc.logs.search", 1), Map.entry("tc.trace.search", 1));
        assertThat(report(job.jobId())).as("보완 뒤에도 비었지만 2회째 round·이벤트 READ 는 없다").contains("실제 발생 이력:NO_DATA");
        assertThat(executed(job.jobId())).doesNotContain("tc.event.getRecent");
    }

    @Test void plannedKnowledgeIsNeverExecutedOrReportedAsAnImplementedSource() throws Exception {
        var knowledge = run("flow-kq", "KQ", List.of("N"), "B");
        assertThat(executed(knowledge.jobId())).isEmpty();
        assertThat(result(knowledge.jobId())).containsEntry("status", "UNSUPPORTED");
        assertThat(scripts().scriptedCalls(knowledge.jobId(), CallType.COMPOSER)).as("거부된 계획에는 Composer 0").isZero();
        var incident = run("flow-n", "INC", List.of("H", "N"), "D");
        assertThat(executed(incident.jobId())).doesNotContain("tc.knowledge.search").contains("tc.model.read");
        assertThat(report(incident.jobId())).as("정상 절차 근거는 승인 모델 READ 로만 채운다").contains("정상 절차·선행 조건:COLLECTED");
    }

    @Test void concurrentJobsOfOneConversationEachUseOnlyTheirOwnBoundNeeds() throws Exception {
        var first = admitScripted("flow-a", "EQP-001 상태", workflowFields(), List.of(classify(wire("INC", "EQP-001", null, null, null, List.of("S", "H"), "B")), compose("정리[e1].")), Optional.empty(), null);
        var second = admitScripted("flow-b", "EQP-001 코드까지", workflowFields(), List.of(classify(wire("INC", "EQP-001", null, null, null, List.of("S", "H", "I"), "B")), compose("정리[e1].")), Optional.empty(), first.conversationId());
        terminal(first); terminal(second);
        assertThat(observations().workflows.stream().filter(job -> job.jobId().equals(first.jobId())).findFirst().orElseThrow().classification().get("evidenceNeeds")).isEqualTo(List.of("S", "H"));
        assertThat(observations().workflows.stream().filter(job -> job.jobId().equals(second.jobId())).findFirst().orElseThrow().classification().get("evidenceNeeds")).isEqualTo(List.of("S", "H", "I"));
        assertThat(executed(first.jobId())).doesNotContain("tc.git.searchCode");
        assertThat(executed(second.jobId())).contains("tc.git.searchCode");
    }

    @Test void theOperationalProfileIsTheSingleApprovedPlannerBean() {
        assertThat(context.getBeansOfType(EvidencePlanBuilder.class)).hasSize(1);
        assertThat(context.getBean(EvidencePlanBuilder.class).profile().version()).isEqualTo("tc-evidence-profile-v1");
    }

    static List<tools.jackson.databind.JsonNode> reportRows(tools.jackson.databind.JsonNode blocks) {
        for (var block : blocks) if (block.path("content").path("columns").toString().contains("\"section\"")) {
            List<tools.jackson.databind.JsonNode> rows = new ArrayList<>(); block.path("content").path("rows").forEach(rows::add); return rows;
        }
        return List.of();
    }
}
