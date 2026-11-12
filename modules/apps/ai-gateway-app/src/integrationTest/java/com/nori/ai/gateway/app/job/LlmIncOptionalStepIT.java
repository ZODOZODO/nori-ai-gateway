package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.budget.CallType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** M-9: LLM_STUB 분류 t=INC 가 F2 상태(Knowledge PLANNED)에서 PlanValidator 를 통과해 실행되고 Git 검색어는 Summarizer 추출값만 쓴다. */
class LlmIncOptionalStepIT extends LlmStubSupport {
    @Override protected Class<?>[] extraSources() { return new Class<?>[]{HarnessObservationsEndpoint.class, WorkflowSourceFixture.class, WorkflowSourceFixture.GitConfiguration.class}; }

    @Test void classifiedIncidentRunsWithPlannedKnowledgeOmittedAndGitSearchesOnlyTheSummarizedTerm() throws Exception {
        var server = context.getBean(WorkflowSourceFixture.Server.class);
        server.logText = "EQP-001 EQUIPMENT_ERROR"; server.traceVersions = List.of("123");
        var job = admitScripted("inc-optional", "EQP-001 \"QUOTEDLITERAL\" 장애 원인 찾아줘", workflowFields(),
            List.of(classify(wire("INC", "EQP-001", null, null, "Q1")), compose("장애 로그와 코드 근거를 찾았다[e1].")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("SUCCEEDED");
        assertThat(observations().workflows.getLast().classification()).containsEntry("taskType", "INCIDENT_ANALYSIS").containsEntry("q", "QUOTEDLITERAL");
        var trace = new ArrayList<String>();
        payload(job.jobId()).path("trace").forEach(value -> trace.add(value.path("stepId").asString() + ":" + value.path("outcome").asString()));
        assertThat(trace).contains("knowledge:OPTIONAL_STEP_OMITTED");
        assertThat(calls().authorizationRequests()).doesNotContain(Set.of("AI_DOC_READ"));
        assertThat(jdbc().sql("SELECT capability_id FROM gw.tool_execution WHERE job_id=:id").param("id", job.jobId()).query(String.class).list())
            .contains("tc.git.searchCode").doesNotContain("tc.knowledge.search");
        assertThat(server.codeSearches).isNotEmpty().allSatisfy(term -> assertThat(term).isEqualTo("EQUIPMENT_ERROR"));
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isOne();
    }
}
