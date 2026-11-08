package com.nori.ai.gateway.acceptance;

import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OracleQueriesTest {
    /** SQL 은 enum 상수뿐이고 공개 API 어디에도 SQL 문자열 입력이 없다. */
    @Test void onlyNamedFixedQueriesExistAndNoneReadsVectorsOrBodiesOut() {
        for (var method : OracleQueries.class.getDeclaredMethods()) if (Modifier.isPublic(method.getModifiers()))
            assertThat(method.getName()).isNotIn("query", "execute", "sql", "raw");
        for (var query : OracleQueries.Query.values()) {
            String sql = query.sql.toLowerCase(Locale.ROOT);
            assertThat(sql).startsWith("select ").doesNotContain(";", "embedding", "vector", " kn.", "insert", "update", "delete", "drop", "alter", "grant");
            // 행 본문(row_to_json)은 residue 검사 안에서만 메모리로 쓰이며 반환 타입은 건수·위치뿐이다.
            if (!query.name().startsWith("RESIDUE_")) assertThat(sql).doesNotContain("row_to_json", "content_masked", "payload");
        }
        assertThat(OracleQueries.DATABASES).containsExactlyInAnyOrder("ai_gateway", "postgres", "template1");
        var queries = new OracleQueries(database -> { throw new SQLException("unused", "08001"); });
        assertThatThrownBy(() -> queries.jobCountByStatus("QUEUED' OR '1'='1")).hasMessage("JOB_STATUS_INVALID");
        assertThatThrownBy(() -> queries.resultRowCount("x; DROP TABLE gw.job")).hasMessage("ID_INVALID");
        for (String invalid : List.of("prometheus", "loki", "tempo", "gitlab"))
            assertThatThrownBy(() -> queries.retentionProbe(invalid)).hasMessage("SOURCE_ID_INVALID");
        assertThatThrownBy(() -> OracleQueries.postgres("db", "pw-value").open("other_db")).hasMessage("DATABASE_NOT_ALLOWED");
    }

    private static Connection working() {
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(OracleQueriesTest.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> java.lang.reflect.Proxy.newProxyInstance(OracleQueriesTest.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> switch (m.getName()) {
                case "executeQuery" -> java.lang.reflect.Proxy.newProxyInstance(OracleQueriesTest.class.getClassLoader(), new Class<?>[]{ResultSet.class}, new java.lang.reflect.InvocationHandler() {
                    boolean first = true;
                    public Object invoke(Object r, java.lang.reflect.Method rm, Object[] ra) { return switch (rm.getName()) { case "next" -> { boolean value = first; first = false; yield value; } case "getInt" -> 1; default -> null; }; }
                });
                default -> null;
            });
            default -> null;
        });
    }
    private static OracleQueries.Connector connector(String otherSqlState, boolean otherAllowed) {
        return database -> {
            if (database.equals("ai_gateway") || otherAllowed) return working();
            throw new SQLException("no pg_hba.conf entry for host secret-host user aigw_acceptance_ro", otherSqlState);
        };
    }

    @Test void databaseBoundaryRequiresHbaRejectionOfOtherDatabases() throws Exception {
        new OracleQueries(connector("28000", false)).verifyDatabaseBoundary();
        assertThatThrownBy(() -> new OracleQueries(connector("28000", true)).verifyDatabaseBoundary()).hasMessage("ACCEPTANCE_DB_OTHER_DATABASE_ALLOWED");
        // 비밀번호 실패(28P01)·통신 오류(08001)는 HBA 거부가 아니므로 수용을 중단한다. 메시지에 호스트·사용자·자격 값이 없다.
        for (String state : List.of("28P01", "08001"))
            assertThatThrownBy(() -> new OracleQueries(connector(state, false)).verifyDatabaseBoundary())
                .hasMessage("ACCEPTANCE_DB_OTHER_DATABASE_NOT_HBA_REJECTED").hasMessageNotContaining("secret-host").hasMessageNotContaining("aigw_acceptance_ro");
        assertThatThrownBy(() -> new OracleQueries(database -> { throw new SQLException("password pw-value rejected", "28P01"); }).verifyDatabaseBoundary())
            .hasMessage("ACCEPTANCE_DB_SELECT_FAILED").hasMessageNotContaining("pw-value");
    }

    /** 조회 키는 P14 producer(SourceRetentionProbeScheduler)가 저장하는 {@code sources://TC/logs/<app>} 와 같아야 한다. */
    @Test void retentionProbeLooksUpTheStoredLokiSourceId() {
        var bound = new ArrayList<Object>();
        OracleQueries.Connector capturing = database -> (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> switch (m.getName()) {
                case "setObject" -> { bound.add(a[1]); yield null; }
                case "executeQuery" -> java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (r, rm, ra) -> rm.getName().equals("next") ? false : null);
                default -> null;
            });
            default -> null;
        });
        for (String app : com.nori.ai.gateway.integration.loki.LokiSourcePolicy.APPS) assertThat(new OracleQueries(capturing).retentionProbe(app)).isEmpty();
        assertThat(bound).containsExactly("sources://TC/logs/tc-gateway", "sources://TC/logs/tc-business", "sources://TC/logs/tc-ui-backend", "sources://TC/logs/tc-ui-front");
    }
}
