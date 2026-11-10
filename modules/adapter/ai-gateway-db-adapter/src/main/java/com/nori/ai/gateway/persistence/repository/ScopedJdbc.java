package com.nori.ai.gateway.persistence.repository;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

abstract class ScopedJdbc {
    protected final JdbcClient jdbc;
    protected final TransactionTemplate transactions;
    protected final Clock clock;
    protected ScopedJdbc(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc); this.transactions = Objects.requireNonNull(transactions); this.clock = Objects.requireNonNull(clock);
    }
    protected JdbcClient.StatementSpec scoped(VerifiedSystemScope scope, String sql) {
        Objects.requireNonNull(scope, "인증 범위가 필요합니다");
        return jdbc.sql(sql).param("bound", scope.boundSystem().name()).param("actor", scope.actorRef().value())
            // PostgreSQL의 마이크로초 정밀도로 내림하여 JDBC 반올림이 미래의 만료 경계를 넘지 않게 합니다.
            .param("now", Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
    protected <T> T transaction(Supplier<T> action) { return transactions.execute(status -> action.get()); }
    // 자식 자체의 owner 값만으로 인가하지 않고 정본 부모의 소유권과 만료를 함께 검사합니다.
    protected static final String CONVERSATION = """
        SELECT 1 FROM gw.conversation c WHERE c.id = :conversation AND c.owner_system = :bound
        AND c.owner_actor_ref = :actor AND c.expires_at > :now
        """;
    protected static final String JOB = """
        SELECT 1 FROM gw.job j JOIN gw.conversation c ON c.id = j.conversation_id AND c.owner_system = j.owner_system
        WHERE j.id = :job AND j.owner_system = :bound AND j.actor_ref = :actor
        AND c.owner_system = :bound AND c.owner_actor_ref = :actor AND c.expires_at > :now
        """;
}
