package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.queue.JobEventPublisher;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class SseHub implements AutoCloseable {
    private record Subscription(VerifiedSystemScope scope,String jobId,GuardedSseWriter writer) {}
    private final Set<Subscription> subscriptions=ConcurrentHashMap.newKeySet();
    private final EventReplayService replay;private final ExpiryCheckpoint expiry;private final ExpiryEmitterCloser closer;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("sse-heartbeat").factory());
    public SseHub(EventReplayService replay,ExpiryCheckpoint expiry,ExpiryEmitterCloser closer,Duration heartbeat) {
        if(heartbeat.isZero()||heartbeat.isNegative())throw new IllegalArgumentException("heartbeat 간격이 잘못되었습니다");
        this.replay=replay;this.expiry=expiry;this.closer=closer;
        scheduler.scheduleAtFixedRate(this::heartbeat,heartbeat.toMillis(),heartbeat.toMillis(),TimeUnit.MILLISECONDS);
    }
    public SseEmitter connect(VerifiedSystemScope scope,String jobId,java.time.Instant expiresAt,String lastEventId) {
        long cursor=replay.cursor(scope,jobId,lastEventId);expiry.assertLive(expiresAt);
        var emitter=new SseEmitter(0L);var writer=new GuardedSseWriter(emitter,expiresAt,expiry,cursor);var subscription=new Subscription(scope,jobId,writer);
        var timer=closer.schedule(expiresAt,writer);
        Runnable remove=()->{subscriptions.remove(subscription);timer.cancel(false);};
        emitter.onCompletion(remove);emitter.onTimeout(()->{writer.complete();remove.run();});emitter.onError(failure->{writer.complete();remove.run();});
        subscriptions.add(subscription);
        // 재생할 새 이벤트가 없어도 연결 응답을 즉시 시작하며 이 쓰기도 만료 guard를 거칩니다.
        if(writer.connected())drain(subscription);else subscriptions.remove(subscription);return emitter;
    }
    @EventListener public void published(JobEventPublisher.Published event) { subscriptions.stream().filter(s->s.jobId().equals(event.jobId())).forEach(this::drain); }
    private void drain(Subscription subscription) {
        synchronized(subscription.writer()) {
            try {for(var event:replay.after(subscription.scope(),subscription.jobId(),subscription.writer().cursor()))if(!subscription.writer().send(subscription.jobId(),event))break;}
            catch(RuntimeException failure) {subscription.writer().complete();}
            if(subscription.writer().closed())subscriptions.remove(subscription);
        }
    }
    public void heartbeat() { for(var subscription:subscriptions)if(!subscription.writer().heartbeat())subscriptions.remove(subscription); }
    @Override public void close() {scheduler.shutdownNow();subscriptions.forEach(s->s.writer().complete());subscriptions.clear();}
}
