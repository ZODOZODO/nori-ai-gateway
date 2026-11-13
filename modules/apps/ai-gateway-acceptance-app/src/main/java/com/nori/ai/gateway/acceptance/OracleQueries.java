package com.nori.ai.gateway.acceptance;

import com.nori.ai.gateway.integration.source.TcSourcePolicy;
import com.nori.ai.gateway.tcmock.verify.ResidueScanner;
import com.nori.ai.gateway.integration.loki.LokiSourcePolicy;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * 단일 DB 계정(앱과 같은 자격, 이름은 env 로만)으로 이름 붙은 고정 SELECT 만 실행한다(H-2 — 하네스 코드 규약이며 DB 권한으로 강제되지 않는다). 임의 SQL 입력 경로가 없고 결과는 건수·ID·상태값·시각·digest 만 돌려준다.
 * 행 본문은 {@link #residueScan(Set)} 안에서 메모리로만 {@link ResidueScanner} 에 넘긴다.
 */
public final class OracleQueries {
    /** 연결 경계. 데이터베이스 이름은 {@link #DATABASE} 고정 값만 들어온다. */
    @FunctionalInterface public interface Connector { Connection open(String database) throws SQLException; }
    public static final class BoundaryViolation extends Exception { BoundaryViolation(String code) { super(code); } }

    public static final String DATABASE = "nori_ai_gateway";
    static final Set<String> JOB_STATUSES = Set.of("QUEUED", "RUNNING", "SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED", "TIMED_OUT");
    static final List<String> RESIDUE_TABLES = List.of("conversation", "message", "job", "job_event", "result", "audit_event", "tool_execution", "evidence", "cache_entry");

    enum Query {
        JOB_COUNT_BY_STATUS("SELECT count(*) FROM gw.job WHERE status = ?"),
        NON_TERMINAL_JOB_COUNT("SELECT count(*) FROM gw.job WHERE status IN ('QUEUED','RUNNING')"),
        JOB_COUNT("SELECT count(*) FROM gw.job"),
        AUTH_REJECTION("SELECT count(*) FROM gw.audit_event WHERE request_id = ? AND event_type = 'AUTH_REJECTED' AND decision = ? AND status = ?"),
        TOOL_PROJECTION("SELECT payload::text FROM gw.tool_execution WHERE job_id = ? AND capability_id = ? ORDER BY created_at, id"),
        OWNER_BLOCK_COUNT("SELECT count(*) FROM gw.audit_event WHERE job_id = ? AND event_type = 'TOOL_BLOCKED' AND decision = 'OWNER_MISMATCH'"),
        SOURCE_BLOCK_COUNT("SELECT count(*) FROM gw.audit_event WHERE job_id = ? AND event_type = 'TOOL_BLOCKED' AND decision = 'SOURCE_NOT_ALLOWED'"),
        JOB_DELEGATION_NON_NULL("SELECT count(*) FROM gw.job WHERE delegation_id IS NOT NULL"),
        RESULT_ROW_COUNT("SELECT count(*) FROM gw.result WHERE job_id = ?"),
        EVENT_COUNT("SELECT count(*) FROM gw.job_event WHERE job_id = ? AND type = ?"),
        FLYWAY_LATEST("SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"),
        RETENTION_PROBE("SELECT verified_oldest_at, probe_limit_at, probed_at FROM gw.source_retention_probe WHERE source_id = ?"),
        JOB_TIMINGS("SELECT id, status, created_at, started_at, terminal_at FROM gw.job WHERE id = ANY (?) ORDER BY id"),
        TOOL_STATUS("SELECT capability_id, status FROM gw.tool_execution WHERE job_id = ? ORDER BY created_at, id"),
        JOB_OWNER("SELECT owner_system FROM gw.job WHERE id = ?"),
        CLASSIFIED_TASK_TYPE("SELECT task_type FROM gw.audit_event WHERE job_id = ? AND event_type = 'JOB_CLASSIFIED' ORDER BY id"),
        LLM_CALLS("SELECT token_usage::text, reduction::text, status, error_code FROM gw.audit_event WHERE job_id = ? AND event_type = 'LLM_CALL' ORDER BY id"),
        RESIDUE_CONVERSATION("SELECT 'conversation:' || id, row_to_json(t)::text FROM gw.conversation t"),
        RESIDUE_MESSAGE("SELECT 'message:' || id, row_to_json(t)::text FROM gw.message t"),
        RESIDUE_JOB("SELECT 'job:' || id, row_to_json(t)::text FROM gw.job t"),
        RESIDUE_TOOL_EXECUTION("SELECT 'tool_execution:' || id, row_to_json(t)::text FROM gw.tool_execution t"),
        RESIDUE_EVIDENCE("SELECT 'evidence:' || id, row_to_json(t)::text FROM gw.evidence t"),
        RESIDUE_CACHE_ENTRY("SELECT 'cache_entry:' || row_number() OVER (ORDER BY key_digest, owner_system, actor_ref, pdg), row_to_json(t)::text FROM gw.cache_entry t"),
        RESIDUE_JOB_EVENT("SELECT 'job_event:' || id, row_to_json(t)::text FROM gw.job_event t"),
        RESIDUE_RESULT("SELECT 'result:' || job_id, row_to_json(t)::text FROM gw.result t"),
        RESIDUE_AUDIT_EVENT("SELECT 'audit_event:' || id, row_to_json(t)::text FROM gw.audit_event t");
        final String sql;
        Query(String sql) { this.sql = sql; }
    }
    public record RetentionProbe(Optional<Instant> verifiedOldestAt, Optional<Instant> probeLimitAt, Instant probedAt) {}
    public record JobTiming(String jobId, String status, Instant createdAt, Optional<Instant> startedAt, Optional<Instant> terminalAt) {}
    public record Range(Instant from, Instant to) {}
    /** Tool 실행 행의 Capability ID·저장 상태만(본문 0). */
    public record ToolRow(String capabilityId, String status) {}
    /** LLM 호출 시도 감사(LLM_CALL) 1행의 토큰·축소 값(SPEC §14.5·§12.3, 원문 0). {@code promptTokens} 는 응답 usage 가 있을 때만이다. */
    public record LlmCall(String callType, boolean sent, int inputTokens, int maxTokens, Optional<Integer> promptTokens, List<ReductionStep> reduction, String status, Optional<String> errorCode) {}
    public record ReductionStep(int stage, int beforeTokens, int afterTokens) {}
    public record CommSource(String source, String status, int itemCount, Set<String> warnings, List<Range> missing, List<Range> unverified,
            long count, long maximum, Optional<Instant> oldest, long retentionHours, boolean applied, boolean deletionPathsExcluded) {}
    public record ToolObservation(String status, String errorCode, int itemCount, Set<String> warnings, Set<String> apps, Set<String> namespaces,
            List<Range> missing, List<CommSource> sources, int blockCount, int evidenceCount, int claimCount, int citationCount, int composerInputCount) {}

    private final Connector connector;
    public OracleQueries(Connector connector) { this.connector = connector; }

    /** 운영 연결: 공유 서버 직접 연결 포트 15432, 사용자는 env 의 단일 계정 이름, 비밀번호는 Secret 파일 값(출력 0). */
    public static Connector postgres(String host, String user, String password) {
        return database -> {
            if (!DATABASE.equals(database)) throw new SQLException("DATABASE_NOT_ALLOWED");
            var properties = new Properties(); properties.setProperty("user", user); properties.setProperty("password", password);
            properties.setProperty("connectTimeout", "10"); properties.setProperty("readOnly", "true");
            return DriverManager.getConnection("jdbc:postgresql://" + host + ":15432/" + database, properties);
        };
    }

    /**
     * suite 시작 전 DB 경계: {@code nori_ai_gateway} 고정 SELECT 가 성공해야 한다. 실패하면 수용을 중단하며 오류 메시지·자격 값은 담지 않는다.
     * 다른 DB 의 HBA 거부 판정은 수용 전용 역할이 없어(단일 계정, Plan §11.7 2026-11-10) 하지 않는다.
     */
    public void verifyDatabaseBoundary() throws BoundaryViolation {
        try (Connection connection = connector.open(DATABASE); var statement = connection.prepareStatement("SELECT 1"); var rows = statement.executeQuery()) {
            if (!rows.next() || rows.getInt(1) != 1) throw new BoundaryViolation("ACCEPTANCE_DB_SELECT_FAILED");
        } catch (SQLException e) { throw new BoundaryViolation("ACCEPTANCE_DB_SELECT_FAILED"); }
    }

    public long jobCountByStatus(String status) {
        if (!JOB_STATUSES.contains(status)) throw new IllegalArgumentException("JOB_STATUS_INVALID");
        return count(Query.JOB_COUNT_BY_STATUS, status);
    }
    public int nonTerminalJobCount() { return Math.toIntExact(count(Query.NON_TERMINAL_JOB_COUNT)); }
    public long jobCount() { return count(Query.JOB_COUNT); }
    public long ownerBlockCount(String job) { return count(Query.OWNER_BLOCK_COUNT,id(job)); }
    public long sourceBlockCount(String job) { return count(Query.SOURCE_BLOCK_COUNT,id(job)); }
    /** Tool 본문은 메모리에서만 투영하며 로그 텍스트·자격·업무 행은 밖으로 반환하지 않습니다. */
    public List<ToolObservation> tools(String job,String capability) {
        if(capability==null||!capability.matches("tc\\.[A-Za-z0-9.]{1,100}"))throw new IllegalArgumentException("CAPABILITY_INVALID");
        return query(Query.TOOL_PROJECTION,rows->{var observations=new ArrayList<ToolObservation>();while(rows.next()) {
            try {
                var root=CanonicalJson.MAPPER.readTree(rows.getString(1));var result=root.path("result");
                int itemCount=0;var apps=new TreeSet<String>();var namespaces=new TreeSet<String>();var sources=new ArrayList<CommSource>();
                for(var block:result.path("blocks"))for(var item:block.path("content").path("items")) {
                    itemCount++;if(item.has("app"))apps.add(item.path("app").asText());if(item.has("namespace"))namespaces.add(item.path("namespace").asText());
                    if(capability.equals("tc.commLogs.search")) {
                        var complete=item.path("completeness");String oldest=complete.path("oldestRetainedAt").asText("");
                        sources.add(new CommSource(item.path("source").asText(),item.path("status").asText(),item.path("items").size(),strings(item.path("warnings")),ranges(item.path("retentionMeta").path("missingRanges")),ranges(item.path("retentionMeta").path("unverifiedRanges")),complete.path("count").asLong(-1),complete.path("maxEntriesPerKey").asLong(-1),oldest.isEmpty()?Optional.empty():Optional.of(Instant.parse(oldest)),complete.path("retentionHours").asLong(-1),complete.path("appliedValuesVerified").asBoolean(),complete.path("otherDeletionPathsExcluded").asBoolean()));
                    }
                }
                observations.add(new ToolObservation(root.path("status").asText(),root.path("errorCode").asText(""),itemCount,strings(result.path("warnings")),Set.copyOf(apps),Set.copyOf(namespaces),ranges(result.path("metadata").path("missingRanges")),List.copyOf(sources),result.path("blocks").size(),result.path("evidence").size(),result.path("claims").size(),result.path("citations").size(),result.path("composerInputs").size()));
            }catch(java.io.IOException|java.time.DateTimeException invalid){throw new IllegalStateException("ORACLE_TOOL_INVALID");}
        }return List.copyOf(observations);},id(job),capability);
    }
    private static Set<String> strings(com.fasterxml.jackson.databind.JsonNode values){var result=new TreeSet<String>();values.forEach(value->result.add(value.asText()));return Set.copyOf(result);}
    private static List<Range> ranges(com.fasterxml.jackson.databind.JsonNode values){var result=new ArrayList<Range>();for(var value:values){String from=value.path("from").asText(value.path("startAt").asText());String to=value.path("to").asText(value.path("endAt").asText());result.add(new Range(Instant.parse(from),Instant.parse(to)));}return List.copyOf(result);}
    /** 같은 Job 의 Tool 실행 행(실행 순서). 본문은 읽지 않는다. */
    public List<ToolRow> toolRows(String job) {
        return query(Query.TOOL_STATUS, rows -> { var values = new ArrayList<ToolRow>(); while (rows.next()) values.add(new ToolRow(rows.getString(1), rows.getString(2))); return List.copyOf(values); }, id(job));
    }
    public Optional<String> jobOwner(String job) { return query(Query.JOB_OWNER, rows -> rows.next() ? Optional.of(rows.getString(1)) : Optional.<String>empty(), id(job)); }
    /** JOB_CLASSIFIED 감사의 taskType 값(감사 행마다 하나). */
    public List<String> classifiedTaskTypes(String job) {
        return query(Query.CLASSIFIED_TASK_TYPE, rows -> { var values = new ArrayList<String>(); while (rows.next()) values.add(rows.getString(1)); return List.copyOf(values); }, id(job));
    }
    /** 같은 Job 의 LLM 호출 시도 감사(시도 순서). 토큰 수·축소 단계·결과 코드만 읽는다. */
    public List<LlmCall> llmCalls(String job) {
        return query(Query.LLM_CALLS, rows -> {
            var values = new ArrayList<LlmCall>();
            while (rows.next()) {
                try {
                    var tokens = CanonicalJson.MAPPER.readTree(rows.getString(1)); var steps = new ArrayList<ReductionStep>();
                    for (var step : CanonicalJson.MAPPER.readTree(rows.getString(2))) steps.add(new ReductionStep(step.path("stage").asInt(), step.path("beforeTokens").asInt(), step.path("afterTokens").asInt()));
                    var prompt = tokens.path("promptTokens");
                    values.add(new LlmCall(tokens.path("callType").asText(), tokens.path("sent").asBoolean(), tokens.path("inputTokens").asInt(), tokens.path("maxTokens").asInt(),
                        prompt.isIntegralNumber() ? Optional.of(prompt.asInt()) : Optional.empty(), List.copyOf(steps), rows.getString(3), Optional.ofNullable(rows.getString(4))));
                } catch (java.io.IOException invalid) { throw new IllegalStateException("ORACLE_LLM_CALL_INVALID"); }
            }
            return List.copyOf(values);
        }, id(job));
    }
    public long authRejectionCount(String requestId, String decision, int status) {
        if (decision == null || !decision.matches("[A-Z_]{1,64}") || status < 400 || status > 599) throw new IllegalArgumentException("AUTH_REJECTION_INVALID");
        return count(Query.AUTH_REJECTION, id(requestId), decision, Integer.toString(status));
    }
    public long jobDelegationNonNull() { return count(Query.JOB_DELEGATION_NON_NULL); }
    public long resultRowCount(String jobId) { return count(Query.RESULT_ROW_COUNT, id(jobId)); }
    public long eventCount(String jobId, String type) {
        if (!type.matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("EVENT_TYPE_INVALID");
        return count(Query.EVENT_COUNT, id(jobId), type);
    }
    public Optional<String> flywayLatest() {
        return query(Query.FLYWAY_LATEST, rows -> rows.next() ? Optional.of(rows.getString(1)) : Optional.<String>empty());
    }
    /** P14 저장 키 그대로: {@code SourceRetentionProbeScheduler} 가 쓰는 {@link LokiSourcePolicy#retentionSourceId} (허용 앱 목록 포함)를 재사용한다. */
    public Optional<RetentionProbe> retentionProbe(String lokiApp) {
        if (!TcSourcePolicy.APPS.contains(lokiApp)) throw new IllegalArgumentException("SOURCE_ID_INVALID");
        String sourceId = LokiSourcePolicy.retentionSourceId(lokiApp);
        return query(Query.RETENTION_PROBE, rows -> rows.next() ? Optional.of(new RetentionProbe(instant(rows, 1), instant(rows, 2), instant(rows, 3).orElseThrow())) : Optional.<RetentionProbe>empty(), sourceId);
    }
    public List<JobTiming> jobTimings(List<String> jobIds) {
        jobIds.forEach(OracleQueries::id);
        try (Connection connection = connector.open(DATABASE)) {
            var statement = connection.prepareStatement(Query.JOB_TIMINGS.sql);
            statement.setArray(1, connection.createArrayOf("text", jobIds.toArray()));
            try (var rows = statement.executeQuery()) {
                var timings = new ArrayList<JobTiming>();
                while (rows.next()) timings.add(new JobTiming(rows.getString(1), rows.getString(2), instant(rows, 3).orElseThrow(), instant(rows, 4), instant(rows, 5)));
                return List.copyOf(timings);
            }
        } catch (SQLException e) { throw new IllegalStateException("ORACLE_QUERY_FAILED:" + Query.JOB_TIMINGS); }
    }
    /** 고정 표의 행 텍스트를 메모리에서만 sentinel 검사한다. 반환 = 일치 건수·위치(표:ID). */
    public ResidueScanner.Report residueScan(Set<String> sentinels) {
        var sources = new LinkedHashMap<String,String>();
        for (Query query : List.of(Query.RESIDUE_CONVERSATION, Query.RESIDUE_MESSAGE, Query.RESIDUE_JOB, Query.RESIDUE_TOOL_EXECUTION,
                Query.RESIDUE_EVIDENCE, Query.RESIDUE_CACHE_ENTRY, Query.RESIDUE_JOB_EVENT, Query.RESIDUE_RESULT, Query.RESIDUE_AUDIT_EVENT))
            query(query, rows -> { while (rows.next()) sources.put(rows.getString(1), rows.getString(2)); return null; });
        return ResidueScanner.scan(sources, sentinels);
    }

    @FunctionalInterface private interface Rows<T> { T read(ResultSet rows) throws SQLException; }
    private long count(Query query, Object... parameters) { return query(query, rows -> { rows.next(); return rows.getLong(1); }, parameters); }
    private <T> T query(Query query, Rows<T> reader, Object... parameters) {
        try (Connection connection = connector.open(DATABASE); var statement = connection.prepareStatement(query.sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            try (var rows = statement.executeQuery()) { return reader.read(rows); }
        } catch (SQLException e) { throw new IllegalStateException("ORACLE_QUERY_FAILED:" + query); }
    }
    private static Optional<Instant> instant(ResultSet rows, int column) throws SQLException {
        Timestamp value = rows.getTimestamp(column); return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }
    private static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_:-]{1,160}")) throw new IllegalArgumentException("ID_INVALID");
        return value;
    }
}
