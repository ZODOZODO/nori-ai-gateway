package com.nori.ai.gateway.app.observability;

import com.nori.ai.gateway.core.audit.AuditEvent;
import com.nori.ai.gateway.persistence.audit.AuditWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/**
 * 일반 감사 생산자 4곳이 쓰는 단일 진입점이다. 저장 실패는 무라벨 counter와 고정 코드 로그를 먼저 남기고 같은 예외를 재전파하므로,
 * 호출부가 원 예외를 우선해 이 예외를 suppressed로만 붙여도 감사 누락이 관측된다.
 */
public final class AuditRecorder {
    static final String FAILURE_CODE = "AUDIT_WRITE_FAILED";
    private final AuditWriter writer;
    private final Counter failures;

    public AuditRecorder(AuditWriter writer, MeterRegistry meters) {
        this.writer = Objects.requireNonNull(writer);
        this.failures = Counter.builder(MetricsCatalog.AUDIT_WRITE_FAILURES).register(meters);
    }

    public void write(AuditEvent event) {
        try { writer.write(event); }
        catch (RuntimeException | Error failure) { failed(); throw failure; }
    }
    public void writeForJob(AuditEvent event) {
        try { writer.writeForJob(event); }
        catch (RuntimeException | Error failure) { failed(); throw failure; }
    }
    private void failed() {
        failures.increment();
        // 인자·예외 본문 없이 고정 코드만 남깁니다.
        System.getLogger(AuditRecorder.class.getName()).log(System.Logger.Level.WARNING, FAILURE_CODE);
    }
}
