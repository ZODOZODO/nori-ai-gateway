package com.nori.ai.gateway.acceptance;

import com.nori.ai.gateway.tcmock.verify.ResidueScanner;
import com.nori.ai.gateway.integration.loki.LokiSourcePolicy;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * {@code aigw_acceptance_ro} 의 이름 붙은 고정 SELECT 만 실행한다(H-2). 임의 SQL 입력 경로가 없고 결과는 건수·ID·상태값·시각·digest 만 돌려준다.
 * 행 본문은 {@link #residueScan(Set)} 안에서 메모리로만 {@link ResidueScanner} 에 넘긴다.
 */
public final class OracleQueries {
    /** 연결 경계. 데이터베이스 이름은 {@link #DATABASES} 의 고정 값만 들어온다. */
    @FunctionalInterface public interface Connector { Connection open(String database) throws SQLException; }
    public static final class BoundaryViolation extends Exception { BoundaryViolation(String code) { super(code); } }

    public static final String DATABASE = "ai_gateway";
    static final Set<String> DATABASES = Set.of(DATABASE, "postgres", "template1");
    static final Set<String> JOB_STATUSES = Set.of("QUEUED", "RUNNING", "SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED", "TIMED_OUT");
    static final List<String> RESIDUE_TABLES = List.of("conversation", "message", "job_event", "result", "audit_event");

    enum Query {
        JOB_COUNT_BY_STATUS("SELECT count(*) FROM gw.job WHERE status = ?"),
        NON_TERMINAL_JOB_COUNT("SELECT count(*) FROM gw.job WHERE status IN ('QUEUED','RUNNING')"),
        JOB_DELEGATION_NON_NULL("SELECT count(*) FROM gw.job WHERE delegation_id IS NOT NULL"),
        RESULT_ROW_COUNT("SELECT count(*) FROM gw.result WHERE job_id = ?"),
        EVENT_COUNT("SELECT count(*) FROM gw.job_event WHERE job_id = ? AND type = ?"),
        FLYWAY_LATEST("SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"),
        RETENTION_PROBE("SELECT verified_oldest_at, probe_limit_at, probed_at FROM gw.source_retention_probe WHERE source_id = ?"),
        JOB_TIMINGS("SELECT id, status, created_at, started_at, terminal_at FROM gw.job WHERE id = ANY (?) ORDER BY id"),
        RESIDUE_CONVERSATION("SELECT 'conversation:' || id, row_to_json(t)::text FROM gw.conversation t"),
        RESIDUE_MESSAGE("SELECT 'message:' || id, row_to_json(t)::text FROM gw.message t"),
        RESIDUE_JOB_EVENT("SELECT 'job_event:' || id, row_to_json(t)::text FROM gw.job_event t"),
        RESIDUE_RESULT("SELECT 'result:' || job_id, row_to_json(t)::text FROM gw.result t"),
        RESIDUE_AUDIT_EVENT("SELECT 'audit_event:' || id, row_to_json(t)::text FROM gw.audit_event t");
        final String sql;
        Query(String sql) { this.sql = sql; }
    }
    public record RetentionProbe(Optional<Instant> verifiedOldestAt, Optional<Instant> probeLimitAt, Instant probedAt) {}
    public record JobTiming(String jobId, String status, Instant createdAt, Optional<Instant> startedAt, Optional<Instant> terminalAt) {}

    private final Connector connector;
    public OracleQueries(Connector connector) { this.connector = connector; }

    /** 운영 연결: 고정 사용자·포트, 비밀번호는 Secret 파일 값(출력 0). */
    public static Connector postgres(String host, String password) {
        return database -> {
            if (!DATABASES.contains(database)) throw new SQLException("DATABASE_NOT_ALLOWED");
            var properties = new Properties(); properties.setProperty("user", "aigw_acceptance_ro"); properties.setProperty("password", password);
            properties.setProperty("connectTimeout", "10"); properties.setProperty("readOnly", "true");
            return DriverManager.getConnection("jdbc:postgresql://" + host + ":5432/" + database, properties);
        };
    }

    /**
     * suite 시작 전 DB 경계: {@code ai_gateway} 고정 SELECT 는 성공하고 {@code postgres}·{@code template1} TCP 접속은 HBA 거부(SQLSTATE 28000)여야 한다.
     * 다른 DB 접속이 허용되거나 HBA 거부가 아닌 인증·통신 오류면 수용을 중단한다. 오류 메시지·자격 값은 담지 않는다.
     */
    public void verifyDatabaseBoundary() throws BoundaryViolation {
        try (Connection connection = connector.open(DATABASE); var statement = connection.prepareStatement("SELECT 1"); var rows = statement.executeQuery()) {
            if (!rows.next() || rows.getInt(1) != 1) throw new BoundaryViolation("ACCEPTANCE_DB_SELECT_FAILED");
        } catch (SQLException e) { throw new BoundaryViolation("ACCEPTANCE_DB_SELECT_FAILED"); }
        for (String other : List.of("postgres", "template1")) {
            try (Connection ignored = connector.open(other)) { throw new BoundaryViolation("ACCEPTANCE_DB_OTHER_DATABASE_ALLOWED"); }
            catch (SQLException e) { if (!"28000".equals(e.getSQLState())) throw new BoundaryViolation("ACCEPTANCE_DB_OTHER_DATABASE_NOT_HBA_REJECTED"); }
        }
    }

    public long jobCountByStatus(String status) {
        if (!JOB_STATUSES.contains(status)) throw new IllegalArgumentException("JOB_STATUS_INVALID");
        return count(Query.JOB_COUNT_BY_STATUS, status);
    }
    public int nonTerminalJobCount() { return Math.toIntExact(count(Query.NON_TERMINAL_JOB_COUNT)); }
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
        if (!LokiSourcePolicy.APPS.contains(lokiApp)) throw new IllegalArgumentException("SOURCE_ID_INVALID");
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
        for (Query query : List.of(Query.RESIDUE_CONVERSATION, Query.RESIDUE_MESSAGE, Query.RESIDUE_JOB_EVENT, Query.RESIDUE_RESULT, Query.RESIDUE_AUDIT_EVENT))
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
