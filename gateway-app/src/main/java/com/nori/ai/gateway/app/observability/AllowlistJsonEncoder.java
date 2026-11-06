package com.nori.ai.gateway.app.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * stdout 한 줄 JSON encoder다. 시각·level·고정 제품 코드·닫힌 MDC 값·예외 클래스명만 쓴다.
 * 제품 logger의 고정 제품 코드가 아니거나 인자가 있는 메시지는 본문 대신 `UNLISTED_MESSAGE`로 남겨 URL·SQL·질문이 섞이지 않게 한다.
 */
public final class AllowlistJsonEncoder extends EncoderBase<ILoggingEvent> {
    static final String UNLISTED = "UNLISTED_MESSAGE";
    @Override public byte[] headerBytes() { return null; }
    @Override public byte[] footerBytes() { return null; }
    @Override public byte[] encode(ILoggingEvent event) {
        var line = new StringBuilder(160).append("{\"timestamp\":\"").append(Instant.ofEpochMilli(event.getTimeStamp()))
            .append("\",\"level\":\"").append(event.getLevel()).append("\",\"message\":\"")
            .append(AllowlistLogFilter.listed(event) ? event.getMessage() : UNLISTED).append('"');
        AllowlistLogFilter.allowedMdc(event).forEach((key, value) -> line.append(",\"").append(key).append("\":\"").append(value).append('"'));
        // 예외 메시지·스택은 원문을 담을 수 있으므로 클래스명만 남깁니다.
        if (event.getThrowableProxy() != null) line.append(",\"errorClass\":\"").append(className(event.getThrowableProxy().getClassName())).append('"');
        return line.append("}\n").toString().getBytes(StandardCharsets.UTF_8);
    }
    private static String className(String name) { return name != null && name.matches("[A-Za-z_$][A-Za-z0-9_$.]{0,255}") ? name : "UNKNOWN"; }
}
