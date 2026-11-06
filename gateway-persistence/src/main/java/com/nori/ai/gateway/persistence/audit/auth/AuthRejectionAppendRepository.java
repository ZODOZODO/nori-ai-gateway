package com.nori.ai.gateway.persistence.audit.auth;

import com.nori.ai.gateway.core.audit.AuditEventType;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class AuthRejectionAppendRepository {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AuthRejectionAppendRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }
    public void append(AuthRejectionAppend request) {
        Objects.requireNonNull(request);
        // 응답 이후 외부 트랜잭션에서 커밋되는 경로를 허용하지 않습니다.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalTransactionStateException("인증 거절 감사는 독립 커밋이 필요합니다");
        transactions.executeWithoutResult(status -> {
            int count = jdbc.sql("""
                INSERT INTO gw.audit_event(recorded_at, owner_system, actor_ref, request_id, event_type, decision, status)
                VALUES (:recorded, :owner, :actor, :request, :event, :decision, :status)
                """)
                .param("recorded", Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS)))
                .param("owner", request.ownerSystem().map(Enum::name).orElse(null), Types.VARCHAR)
                .param("actor", request.actorRef().map(actor -> actor.value()).orElse(null), Types.VARCHAR)
                .param("request", request.requestId().toString())
                .param("event", AuditEventType.AUTH_REJECTED.name())
                .param("decision", request.reason().name()).param("status", Integer.toString(request.httpStatus())).update();
            if (count != 1) throw new DataIntegrityViolationException("인증 거절 감사 저장 건수가 잘못되었습니다");
        });
    }
}
