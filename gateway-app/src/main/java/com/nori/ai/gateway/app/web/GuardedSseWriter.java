package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.expiry.*;
import com.nori.ai.gateway.persistence.repository.JobEventRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

public final class GuardedSseWriter {
    private static final Set<String> FIELDS=Set.of("type","jobId","seq","queuePosition","toolId","status","jobStatus","errorCode");
    private final SseEmitter emitter;private final Instant expiresAt;private final ExpiryCheckpoint expiry;
    private final JsonMapper json=JsonMapper.builder().build();private boolean closed;private long cursor;
    public GuardedSseWriter(SseEmitter emitter,Instant expiresAt,ExpiryCheckpoint expiry,long cursor) { this.emitter=emitter;this.expiresAt=expiresAt;this.expiry=expiry;this.cursor=cursor; }
    public synchronized boolean send(String jobId,JobEventRepository.Event event) {
        if(event.externalSuppressed()||event.id()<=cursor)return !closed;
        var payload=json.createObjectNode();var saved=json.readTree(event.payload());
        if(saved.isObject())for(var field:saved.properties())if(FIELDS.contains(field.getKey())&&(field.getValue().isString()||field.getValue().isNumber()||field.getValue().isNull()))payload.set(field.getKey(),field.getValue());
        payload.put("type",event.type());payload.put("jobId",jobId);
        if(!write(SseEmitter.event().id(Long.toString(event.id())).name(event.type()).data(payload)))return false;
        cursor=event.id();return true;
    }
    public synchronized boolean heartbeat() { return write(SseEmitter.event().comment("heartbeat")); }
    public synchronized boolean connected() { return write(SseEmitter.event().comment("connected")); }
    private boolean write(SseEmitter.SseEventBuilder event) {
        if(closed)return false;
        try { expiry.assertLive(expiresAt);emitter.send(event);return true; }
        catch(ConversationExpiryGuard.ConversationExpired expired) { complete();return false; }
        catch(IOException|IllegalStateException failure) { complete();return false; }
    }
    public synchronized long cursor() { return cursor; }
    public synchronized boolean closed() { return closed; }
    public synchronized void complete() { if(!closed){closed=true;emitter.complete();} }
}
