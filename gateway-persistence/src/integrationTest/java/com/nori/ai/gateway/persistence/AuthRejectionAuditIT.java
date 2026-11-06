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
                assertThat(jdbc.sql("SELECT version FROM gw_flyway.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1").query(String.class).single()).isEqualTo("3.1");
                assertThat(jdbc.sql("SELECT count(*) FROM pg_constraint WHERE conrelid = 'gw.audit_event'::regclass AND contype = 'c' AND convalidated").query(Integer.class).single()).isEqualTo(3);
                var append = context.getBean(AuthRejectionAppendRepository.class);
                UUID id = UUID.randomUUID();
                append.append(request(id, null, null, AuthRejectionReason.SERVICE_TOKEN_INVALID));
                append.append(request(id, OwnerSystem.TC, null, AuthRejectionReason.SIGNATURE_INVALID));
                append.append(request(id, OwnerSystem.TC, "a", AuthRejectionReason.SYSTEM_SCOPE_MISMATCH));
                append.append(request(id, OwnerSystem.WM, "a", AuthRejectionReason.SYSTEM_SCOPE_MISMATCH));
                var rows = jdbc.sql("SELECT recorded_at, owner_system, actor_ref, event_type, decision, status, conversation_id, job_id, trace_id, tool_id, source_ref, error_code, timings, token_usage, versions, reduction FROM gw.audit_event ORDER BY id").query().listOfRows();
                assertThat(rows).hasSize(4);
                assertThat(rows.getFirst().get("owner_system")).isNull();
                assertThat(rows.getFirst().get("actor_ref")).isNull();
                assertThat(rows.get(1).get("owner_system")).isEqualTo("TC");
                assertThat(rows.get(1).get("actor_ref")).isNull();
                for (var row : rows) {
                    assertThat(row.get("event_type")).isEqualTo("AUTH_REJECTED");
                    assertThat(((java.sql.Timestamp)row.get("recorded_at")).toInstant()).isEqualTo(BASE.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
                    for (String column : List.of("conversation_id", "job_id", "trace_id", "tool_id", "source_ref", "error_code", "timings", "token_usage", "versions", "reduction")) assertThat(row.get(column)).isNull();
                }
                try (var connection = database.connection("aigw_app")) {
                    for (String[] values : List.of(new String[]{null,null,"TEST"}, new String[]{"TC",null,"TEST"}, new String[]{null,"a","TEST"}, new String[]{null,"a","AUTH_REJECTED"}, new String[]{"OTHER","a","AUTH_REJECTED"})) {
                        try (var statement = connection.prepareStatement("INSERT INTO gw.audit_event(recorded_at,owner_system,actor_ref,request_id,event_type) VALUES (now(),?,?,?,?)")) {
                            statement.setString(1,values[0]); statement.setString(2,values[1]); statement.setString(3,id.toString()); statement.setString(4,values[2]);
                            assertThatThrownBy(statement::executeUpdate).isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("23514"));
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
