package com.nori.ai.gateway.app.ops;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.job.SlotLedger;
import com.nori.ai.gateway.app.job.recovery.ReadinessGate;
import com.nori.ai.gateway.core.job.CancellationToken;
import com.nori.ai.gateway.persistence.queue.JobTerminalCommitter;
import com.nori.ai.gateway.persistence.queue.QueueJobRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.context.event.EventListener;

/**
 * 만료 대화의 비 terminal Job을 끝내는 보조 Job이다(차단 보장은 동기 만료 guard).
 * QUEUED는 잠긴 행이 아직 QUEUED일 때만 만료 종결하고, RUNNING은 진행 호출에 만료 신호만 보내 Worker가 실제 종료 확인 뒤 같은 tx로 종결·슬롯 반납하게 한다.
 */
public final class ExpiryEdgeJobCanceller implements AutoCloseable {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("expiry-cancel-", 0).factory());
    private final QueueJobRepository jobs;
    private final JobTerminalCommitter terminal;
    private final SlotLedger slots;
    private final Clock clock;
    private final Optional<PausePointRegistry> pauses;
    private volatile boolean started;

    public ExpiryEdgeJobCanceller(QueueJobRepository jobs, JobTerminalCommitter terminal, SlotLedger slots, Clock clock, Optional<PausePointRegistry> pauses) {
        this.jobs = Objects.requireNonNull(jobs); this.terminal = Objects.requireNonNull(terminal); this.slots = Objects.requireNonNull(slots);
        this.clock = Objects.requireNonNull(clock); this.pauses = Objects.requireNonNull(pauses);
    }
    @EventListener public void start(ReadinessGate.RecoveryCompleted event) {
        if (started) return;
        started = true;
        scheduler.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
    }
    private void tick() {
        try { cancelExpiredNow(); }
        catch (RuntimeException failure) {
            // 다음 주기에 같은 행을 다시 판정합니다. 만료 데이터 노출 차단은 동기 guard가 보장합니다.
            System.getLogger(ExpiryEdgeJobCanceller.class.getName()).log(System.Logger.Level.WARNING, "EXPIRY_CANCEL_FAILED");
        }
    }
    void cancelExpiredNow() {
        if (pauses.map(registry -> registry.held(PausePointRegistry.Point.EXPIRY_CANCELLER_HOLD)).orElse(false)) return;
        var now = clock.instant();
        for (var job : jobs.nonterminal()) {
            if (now.isBefore(job.conversationExpiresAt())) continue;
            // 스냅샷 뒤 Dispatcher가 먼저 RUNNING으로 바꿨으면 쓰기 없이 넘어가고 다음 주기 RUNNING 분기가 처리합니다.
            if (job.status().equals("QUEUED")) terminal.commitExpiredQueued(job.jobId());
            else slots.signalIfActive(job.jobId(), CancellationToken.Reason.EXPIRED);
        }
    }
    public boolean started() { return started; }
    @Override public void close() { started = false; scheduler.shutdownNow(); }
}
