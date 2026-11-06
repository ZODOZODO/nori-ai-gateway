package com.nori.ai.gateway.persistence;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class JobDelegationColumnIT extends PgIntegrationSupport {
    @Test void terminalConstraintRejectsResidueAndAtomicNullUpdateSucceeds() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false); database.migrate();
            try (var connection = database.connection("aigw_app"); var statement = connection.createStatement()) {
                statement.execute("INSERT INTO gw.conversation VALUES ('c','TC','actor',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + interval '1 day','ACTIVE')");
                try (var insert = connection.prepareStatement("INSERT INTO gw.job(id, conversation_id, owner_system, actor_ref, status, created_at, request_digest, expected_path, delegation_id) VALUES ('j','c','TC','actor','QUEUED',CURRENT_TIMESTAMP,'digest','B',?)")) {
                    byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
                    insert.setString(1, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)); insert.executeUpdate();
                }
                for (String status : java.util.List.of("SUCCEEDED", "PARTIAL", "FAILED", "CANCELLED", "TIMED_OUT")) {
                    // DB 내부에서 정확한 제약 이름/SQLSTATE를 판정하여 실패 행의 일시 증표가 오류 로그에 실리지 않게 합니다.
                    statement.execute("DO $$ DECLARE violated text; BEGIN UPDATE gw.job SET status = '" + status + "' WHERE id = 'j'; "
                        + "RAISE EXCEPTION 'terminal 제약이 적용되지 않았습니다'; EXCEPTION WHEN check_violation THEN "
                        + "GET STACKED DIAGNOSTICS violated = CONSTRAINT_NAME; "
                        + "IF SQLSTATE <> '23514' OR violated <> 'job_dlg_terminal_null' THEN RAISE; END IF; END $$");
                }
                assertThat(statement.executeUpdate("UPDATE gw.job SET status = 'CANCELLED', delegation_id = NULL WHERE id = 'j' AND status IN ('QUEUED','RUNNING')")).isEqualTo(1);
                assertThat(statement.executeUpdate("UPDATE gw.job SET status = 'CANCELLED', delegation_id = NULL WHERE id = 'j' AND status IN ('QUEUED','RUNNING')")).isZero();
                try (var rows = statement.executeQuery("SELECT count(*) FROM gw.job WHERE status NOT IN ('QUEUED','RUNNING') AND delegation_id IS NOT NULL")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }
}
