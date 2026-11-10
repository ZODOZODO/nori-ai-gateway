package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.persistence.audit.auth.*;
import com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository;
import com.nori.ai.gateway.persistence.repository.AuditRepository;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class AuthRejectionAuditIT extends PgIntegrationSupport {
    static final Instant BASE = Instant.parse("2026-11-06T00:00:00.123456999Z");
    static class MutableClock extends Clock {
        Instant now = BASE;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    static class ClockConfiguration { @Bean MutableClock testClock() { return new MutableClock(); } }
    private AuthRejectionAppend request(UUID id, OwnerSystem owner, String actor, AuthRejectionReason reason) {
        return new AuthRejectionAppend(id, Optional.ofNullable(owner), Optional.ofNullable(actor).map(ActorRef::new), reason);
    }
    private VerifiedSystemScope scope(OwnerSystem owner, String actor) {
        return new VerifiedSystemScope(owner, "service:" + owner, new ActorRef(actor), actor, Set.of(), "a".repeat(64), BASE.minusSeconds(1), BASE.plusSeconds(59), UUID.randomUUID().toString());
    }
    @Test void actualConstraintsAndTypedAppendPreserveOptionalIdentityAndClosedPayload() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false); database.migrate();
            try (var context = application(database, ClockConfiguration.class)) {
                var jdbc = context.getBean(JdbcClient.class);
                assertThat(jdbc.sql("SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1").query(String.class).single()).isEqualTo("7");
                assertThat(jdbc.sql("SELECT count(*) FROM pg_constraint WHERE conrelid = 'gw.audit_event'::regclass AND contype = 'c' AND convalidated").query(Integer.class).single()).isEqualTo(5);
                var append = context.getBean(AuthRejectionAppendRepository.class);
                UUID id = UUID.randomUUID();
                append.append(request(id, null, null, AuthRejectionReason.SERVICE_TOKEN_INVALID));
                append.append(request(id, OwnerSystem.TC, null, AuthRejectionReason.SIGNATURE_INVALID));
                append.append(request(id, OwnerSystem.TC, "a", AuthRejectionReason.SYSTEM_SCOPE_MISMATCH));
                append.append(request(id, OwnerSystem.WM, "a", AuthRejectionReason.SYSTEM_SCOPE_MISMATCH));
                var rows = jdbc.sql("SELECT recorded_at, owner_system, actor_ref, event_type, decision, status, conversation_id, job_id, trace_id, tool_id, source_ref, error_code, timings, token_usage, versions, reduction, service_ref, task_type, result_status FROM gw.audit_event ORDER BY id").query().listOfRows();
                assertThat(rows).hasSize(4);
                assertThat(rows.getFirst().get("owner_system")).isNull();
                assertThat(rows.getFirst().get("actor_ref")).isNull();
                assertThat(rows.get(1).get("owner_system")).isEqualTo("TC");
                assertThat(rows.get(1).get("actor_ref")).isNull();
                for (var row : rows) {
                    assertThat(row.get("event_type")).isEqualTo("AUTH_REJECTED");
                    assertThat(((java.sql.Timestamp)row.get("recorded_at")).toInstant()).isEqualTo(BASE.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
                    for (String column : List.of("conversation_id", "job_id", "trace_id", "tool_id", "source_ref", "error_code", "timings", "token_usage", "versions", "reduction", "service_ref", "task_type", "result_status")) assertThat(row.get(column)).isNull();
                }
                try (var connection = database.connection("aigw_app")) {
                    for (String[] values : List.of(new String[]{null,null,"TEST"}, new String[]{"TC",null,"TEST"}, new String[]{null,"a","TEST"}, new String[]{null,"a","AUTH_REJECTED"}, new String[]{"OTHER","a","AUTH_REJECTED"})) {
                        try (var statement = connection.prepareStatement("INSERT INTO gw.audit_event(recorded_at,owner_system,actor_ref,request_id,event_type) VALUES (now(),?,?,?,?)")) {
                            statement.setString(1,values[0]); statement.setString(2,values[1]); statement.setString(3,id.toString()); statement.setString(4,values[2]);
                            assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("23514"));
                        }
                    }
                    // V7: 인증 거절은 새 세 열이 모두 NULL이고, 인입 원점 행은 service와 유효 nonzero hex32 trace가 필요합니다.
                    String trace = "0af7651916cd43dd8448eb211c80319c";
                    for (String[] values : List.of(new String[]{"AUTH_REJECTED","service:TC",null,null,null}, new String[]{"AUTH_REJECTED",null,"INCIDENT_ANALYSIS",null,null},
                            new String[]{"AUTH_REJECTED",null,null,"SUCCESS",null}, new String[]{"API_REQUEST",null,null,null,trace}, new String[]{"API_REQUEST","service:TC",null,null,null},
                            new String[]{"JOB_ACCEPTED","service:TC",null,null,"0".repeat(32)}, new String[]{"JOB_ACCEPTED","service:TC",null,null,trace.toUpperCase()},
                            new String[]{"API_REQUEST","service:TC",null,null,trace.substring(1)})) {
                        try (var statement = connection.prepareStatement("INSERT INTO gw.audit_event(recorded_at,owner_system,actor_ref,request_id,event_type,service_ref,task_type,result_status,trace_id) VALUES (now(),'TC','a',?,?,?,?,?,?)")) {
                            statement.setString(1,id.toString()); for (int i = 0; i < values.length; i++) statement.setString(i + 2, values[i]);
                            assertThatThrownBy(statement::executeUpdate).as(String.join(",", Arrays.stream(values).map(String::valueOf).toList()))
                                .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("23514"));
                        }
                    }
                }
                assertThatThrownBy(() -> context.getBean(TransactionTemplate.class).executeWithoutResult(tx -> append.append(request(id,null,null,AuthRejectionReason.SERVICE_TOKEN_MISSING))))
                    .isInstanceOf(IllegalTransactionStateException.class);
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isEqualTo(4);
            }
        }
    }
    @Test void normalScopedIsolationAndExactNinetyDayPurgeIncludeNullRows() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false); database.migrate();
            try (var context = application(database, ClockConfiguration.class)) {
                var clock = context.getBean(MutableClock.class);
                var append = context.getBean(AuthRejectionAppendRepository.class);
                var audit = context.getBean(AuditRepository.class);
                UUID id = UUID.randomUUID();
                append.append(request(id,null,null,AuthRejectionReason.SERVICE_TOKEN_MISSING));
                append.append(request(id,OwnerSystem.TC,null,AuthRejectionReason.SUBJECT_INVALID));
                for (var identity : List.of(scope(OwnerSystem.TC,"a"), scope(OwnerSystem.TC,"b"), scope(OwnerSystem.WM,"a"))) {
                    assertThat(audit.insert(identity, new AuditRepository.Event(id.toString(),null,null,null,"TEST",null,null,null,null,null,null,null,null,null))).isTrue();
                    append.append(request(id,identity.boundSystem(),identity.sub(),AuthRejectionReason.SYSTEM_SCOPE_MISMATCH));
                    assertThat(audit.findByRequest(identity,id.toString())).hasSize(2).allMatch(row -> row.eventType().equals("TEST") || row.eventType().equals("AUTH_REJECTED"));
                }
                var jdbc = context.getBean(JdbcClient.class);
                var stored = jdbc.sql("SELECT min(recorded_at) FROM gw.audit_event").query(java.sql.Timestamp.class).single().toInstant();
                var purge = context.getBean(RetentionMaintenanceRepository.class);
                clock.now = stored.plus(Duration.ofDays(90)).minusNanos(1);
                assertThat(purge.purgeExpiredAudit()).isZero();
                assertThat(audit.findByRequest(scope(OwnerSystem.TC,"a"),id.toString())).hasSize(2);
                clock.now = stored.plus(Duration.ofDays(90));
                assertThat(audit.findByRequest(scope(OwnerSystem.TC,"a"),id.toString())).isEmpty();
                assertThat(purge.purgeExpiredAudit()).isEqualTo(8);
                clock.now = clock.now.plusNanos(1);
                assertThat(purge.purgeExpiredAudit()).isZero();
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isZero();
            }
        }
    }
    @Test void generalWriterRejectsAuthRejectedBeforeStorageAndNullIdentityRowsArePurgedInBatches() throws Exception {
        try (var database = rawDatabase()) {
            database.bootstrap(false); database.migrate();
            try (var context = application(database, ClockConfiguration.class)) {
                var clock = context.getBean(MutableClock.class);
                var jdbc = context.getBean(JdbcClient.class);
                var writer = context.getBean(com.nori.ai.gateway.persistence.audit.AuditWriter.class);
                var identity = scope(OwnerSystem.TC, "a");
                String trace = "0af7651916cd43dd8448eb211c80319c";
                assertThatThrownBy(() -> writer.write(new com.nori.ai.gateway.core.audit.AuditEvent(identity, com.nori.ai.gateway.core.audit.AuditEventType.AUTH_REJECTED,
                    null, null, null, null, null, null, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isZero();
                writer.write(com.nori.ai.gateway.core.audit.AuditEvent.apiRequest(identity, trace));
                assertThat(jdbc.sql("SELECT request_id, service_ref, trace_id, event_type FROM gw.audit_event").query().singleRow())
                    .containsEntry("request_id", identity.requestId()).containsEntry("service_ref", "service:TC").containsEntry("trace_id", trace).containsEntry("event_type", "API_REQUEST");
                // Job 행은 같은 Job의 접수 원점이 없으면 저장하지 않고 writer가 예외로 알립니다.
                String job = UUID.randomUUID().toString();
                assertThatThrownBy(() -> writer.writeForJob(com.nori.ai.gateway.core.audit.AuditEvent.jobClassified(identity, UUID.randomUUID().toString(), job,
                    com.nori.ai.gateway.core.registry.TaskType.LIVE_QUERY))).isInstanceOf(IllegalStateException.class);
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isEqualTo(1);
                var append = context.getBean(AuthRejectionAppendRepository.class);
                for (int i = 0; i < 5; i++) append.append(request(UUID.randomUUID(), null, null, AuthRejectionReason.SERVICE_TOKEN_MISSING));
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isEqualTo(6);
                var stored = jdbc.sql("SELECT max(recorded_at) FROM gw.audit_event").query(java.sql.Timestamp.class).single().toInstant();
                var purge = context.getBean(RetentionMaintenanceRepository.class);
                clock.now = stored.plus(Duration.ofDays(90)).minusNanos(1);
                assertThat(purge.purgeExpiredAuditBatch(2)).isZero();
                clock.now = stored.plus(Duration.ofDays(90));
                assertThat(purge.purgeExpiredAuditBatch(2)).isEqualTo(2);
                assertThat(purge.purgeExpiredAuditBatch(2)).isEqualTo(2);
                assertThat(purge.purgeExpiredAuditBatch(2)).isEqualTo(2);
                assertThat(purge.purgeExpiredAuditBatch(2)).isZero();
                assertThat(jdbc.sql("SELECT count(*) FROM gw.audit_event").query(Integer.class).single()).isZero();
            }
        }
    }
    @Test void recordRejectsMissingAndUnverifiedIdentityCombinations() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new AuthRejectionAppend(null,Optional.empty(),Optional.empty(),AuthRejectionReason.SERVICE_TOKEN_MISSING)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuthRejectionAppend(id,null,Optional.empty(),AuthRejectionReason.SERVICE_TOKEN_MISSING)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AuthRejectionAppend(id,Optional.empty(),null,AuthRejectionReason.SERVICE_TOKEN_MISSING)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> request(id,null,null,null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> request(id,null,"a",AuthRejectionReason.SYSTEM_SCOPE_MISMATCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(id,OwnerSystem.TC,"a",AuthRejectionReason.SUBJECT_INVALID)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(id,OwnerSystem.TC,null,AuthRejectionReason.SERVICE_TOKEN_INVALID)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(id,OwnerSystem.TC,"invalid actor",AuthRejectionReason.SYSTEM_SCOPE_MISMATCH)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Arrays.stream(AuthRejectionReason.values()).filter(reason -> reason.httpStatus() == 403)).containsExactly(AuthRejectionReason.SYSTEM_SCOPE_MISMATCH);
        assertThat(Arrays.stream(AuthRejectionReason.values()).filter(reason -> reason != AuthRejectionReason.SYSTEM_SCOPE_MISMATCH)).allMatch(reason -> reason.httpStatus() == 401);
    }
}
