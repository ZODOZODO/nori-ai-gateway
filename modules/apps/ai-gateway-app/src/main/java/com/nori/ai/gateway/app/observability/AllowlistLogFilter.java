package com.nori.ai.gateway.app.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.nori.ai.gateway.core.job.TerminalSpec;
import com.nori.ai.gateway.core.plan.RejectReason;
import com.nori.ai.gateway.core.result.ErrorCode;
import com.nori.ai.gateway.core.result.JobStatus;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * OTLP log appender 앞단 필터다. logback 필터는 속성을 지울 수 없으므로, 제품 logger의 고정 제품 코드 본문이고 인자·예외가 없으며
 * MDC 값이 모두 닫힌 값인 이벤트만 통과시키고 나머지는 전송하지 않는다. 형식만 맞는 대문자 문자열은 허용하지 않는다.
 */
public final class AllowlistLogFilter extends Filter<ILoggingEvent> {
    static final Set<String> PRODUCT_CODES = Set.of("RESTORE_PATH_REQUIRED", "RETENTION_PROBE_FAILED", "RETENTION_PURGE_FAILED",
        "EXPIRY_CANCEL_FAILED", "AUDIT_WRITE_FAILED", "INFERENCE_ALERT_RAISED_MODEL_LOADING", "INFERENCE_ALERT_RAISED_INFERENCE_UNAVAILABLE",
        "INFERENCE_ALERT_RAISED_MODEL_CONTEXT_MISMATCH", "INFERENCE_ALERT_RAISED_MODEL_DIGEST_MISMATCH", "INFERENCE_ALERT_CLEARED");
    static final String PRODUCT_LOGGER_PREFIX = "com.nori.ai.gateway.";
    /** OTel appender가 capture하는 MDC 키다. 값은 {@link #allowsMdc}의 닫힌 값만 통과한다. */
    static final String MDC_KEYS = "requestId,conversationId,jobId,traceId,ownerSystem,status,errorCode,decision";
    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern TRACE = Pattern.compile("[0-9a-f]{32}");

    @Override public FilterReply decide(ILoggingEvent event) {
        if (!listed(event) || event.getThrowableProxy() != null) return FilterReply.DENY;
        for (var entry : mdc(event).entrySet()) if (!allowsMdc(entry.getKey(), entry.getValue())) return FilterReply.DENY;
        return FilterReply.NEUTRAL;
    }
    /** 제품 logger가 인자 없이 남긴 고정 제품 코드인지 판정한다. */
    static boolean listed(ILoggingEvent event) {
        Object[] arguments = event.getArgumentArray();
        return event.getLoggerName() != null && event.getLoggerName().startsWith(PRODUCT_LOGGER_PREFIX)
            && PRODUCT_CODES.contains(event.getMessage()) && (arguments == null || arguments.length == 0);
    }
    /** 닫힌 값을 통과한 MDC 값만 정렬해 돌려준다. */
    static Map<String,String> allowedMdc(ILoggingEvent event) {
        var allowed = new TreeMap<String,String>();
        mdc(event).forEach((key, value) -> { if (allowsMdc(key, value)) allowed.put(key, value); });
        return allowed;
    }
    static boolean allowsMdc(String key, String value) {
        if (key == null || value == null) return false;
        return switch (key) {
            case "requestId", "conversationId", "jobId" -> UUID.matcher(value).matches();
            case "traceId" -> TRACE.matcher(value).matches() && !value.equals("0".repeat(32));
            case "ownerSystem" -> named(OwnerSystem.values(), value);
            case "status" -> named(ResultStatus.values(), value) || named(JobStatus.values(), value);
            case "errorCode" -> named(ErrorCode.values(), value);
            case "decision" -> named(RejectReason.values(), value) || named(TerminalSpec.Cause.values(), value);
            default -> false;
        };
    }
    private static boolean named(Enum<?>[] values, String value) { return Arrays.stream(values).anyMatch(constant -> constant.name().equals(value)); }
    private static Map<String,String> mdc(ILoggingEvent event) { return event.getMDCPropertyMap() == null ? Map.of() : event.getMDCPropertyMap(); }
}
