package com.nori.ai.gateway.core.job;

import java.time.Duration;
import java.util.*;

public final class CancellationToken {
    public enum Reason { CANCELLED, TIMEOUT, EXPIRED }
    public interface InFlightCall {
        void abort();
        boolean awaitTermination(Duration timeout) throws InterruptedException;
    }
    private final Set<InFlightCall> calls = new HashSet<>();
    private Reason reason;
    private Thread worker;
    public synchronized void attachWorker(Thread value) { worker = value; if (reason != null) value.interrupt(); }
    public synchronized void register(InFlightCall call) {
        Objects.requireNonNull(call); calls.add(call);
        if (reason != null) call.abort();
    }
    public synchronized void completed(InFlightCall call) { calls.remove(call); }
    public synchronized Optional<Reason> reason() { return Optional.ofNullable(reason); }
    public synchronized void signal(Reason value) {
        if (reason == null || value == Reason.EXPIRED) reason = Objects.requireNonNull(value);
        for (InFlightCall call : calls) call.abort();
        if (worker != null) worker.interrupt();
    }
    public void awaitTermination(Duration timeout) throws InterruptedException {
        for (;;) {
            List<InFlightCall> pending;
            synchronized (this) { pending = List.copyOf(calls); }
            if (pending.isEmpty()) return;
            for (InFlightCall call : pending) if (call.awaitTermination(timeout)) completed(call);
            // 종료 미확인 호출이 있으면 terminal과 슬롯 반납을 보류합니다.
        }
    }
}
