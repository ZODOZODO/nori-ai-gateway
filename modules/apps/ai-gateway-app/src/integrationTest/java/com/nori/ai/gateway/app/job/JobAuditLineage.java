package com.nori.ai.gateway.app.job;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.assertj.core.api.Assertions.*;

/** 한 Job의 감사 행 전체가 같은 접수 원점에 결속되는지 확인하는 시험 단언이다. */
public final class JobAuditLineage {
    private JobAuditLineage() {}

    /**
     * 접수 원점 JOB_ACCEPTED 정확히 1행(service·유효 nonzero hex32 trace), 일반 Job 감사 종류만, 모든 행이 그 원점의 request_id·trace_id를 공유,
     * 원점 밖 행 service_ref NULL, 종결이면 JOB_TERMINAL 정확히 1행인지 확인하고 행의 종류 목록을 돌려준다.
     */
    public static List<String> assertLineage(JdbcClient jdbc, String jobId, boolean terminal) {
        var rows = jdbc.sql("SELECT event_type,request_id,trace_id,service_ref FROM gw.audit_event WHERE job_id=:id ORDER BY id").param("id", jobId).query().listOfRows();
        assertThat(rows).as("감사 원점").isNotEmpty();
        var origin = rows.getFirst();
        assertThat(origin.get("event_type")).isEqualTo("JOB_ACCEPTED");
        assertThat((String) origin.get("trace_id")).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        assertThat(origin.get("service_ref")).isNotNull();
        var types = rows.stream().map(row -> (String) row.get("event_type")).toList();
        assertThat(types).allMatch(Set.of("JOB_ACCEPTED", "JOB_CLASSIFIED", "TOOL_CALL", "TOOL_BLOCKED", "JOB_TERMINAL")::contains);
        assertThat(types.stream().filter("JOB_ACCEPTED"::equals)).hasSize(1);
        assertThat(types.stream().filter("JOB_TERMINAL"::equals)).hasSize(terminal ? 1 : 0);
        for (var row : rows.subList(1, rows.size())) {
            assertThat(row.get("request_id")).isEqualTo(origin.get("request_id"));
            assertThat(row.get("trace_id")).isEqualTo(origin.get("trace_id"));
            assertThat(row.get("service_ref")).isNull();
        }
        return types;
    }
}
