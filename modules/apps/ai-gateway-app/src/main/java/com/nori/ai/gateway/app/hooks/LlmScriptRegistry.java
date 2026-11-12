package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.llm.LlmCallObserver;
import com.nori.ai.gateway.persistence.queue.TerminalCommitObserver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * verify 전용 LLM_STUB 결속(Plan P20 N10). 분류·Composer 호출 범위 동안 그 스레드의 결속 Job 을 표시해 {@link ScriptedChatTransport} 가
 * 그 Job 의 고정 응답을 순번대로 꺼내게 하고, 무장된 sentinel 이 조립 Prompt 에 든 호출 수만 메모리에 센다(Prompt 원문·부분 문자열 보관 0).
 * 결속 Job terminal 뒤 60s 가 지나면 관찰을 지운다.
 */
public final class LlmScriptRegistry implements LlmCallObserver, TerminalCommitObserver {
    static final Duration RETENTION = Duration.ofSeconds(60);
    public record WatchRow(CallType callType, int sentinelIndex, int matchCount) {}
    private static final class State {
        int cursor;
        final Map<CallType,Integer> observed = new EnumMap<>(CallType.class), sent = new EnumMap<>(CallType.class);
        final Map<CallType,int[]> matches = new EnumMap<>(CallType.class);
        Instant terminalAt;
    }
    private final Map<String,State> jobs = new HashMap<>();
    private final ThreadLocal<String> current = new ThreadLocal<>();
    private final TestHookRegistry hooks;
    private final Clock clock;

    public LlmScriptRegistry(TestHookRegistry hooks, Clock clock) {
        this.hooks = Objects.requireNonNull(hooks); this.clock = Objects.requireNonNull(clock);
        hooks.onIdleReset(this::reset);
    }

    @Override public Scope open(String jobId, CallType type, List<ChatMessage> messages) {
        var scenario = hooks.binding(jobId);
        if (scenario.isEmpty()) return () -> {};
        synchronized (this) {
            purge();
            var state = jobs.computeIfAbsent(jobId, key -> new State());
            state.observed.merge(type, 1, Integer::sum);
            scenario.get().promptWatch().ifPresent(watch -> {
                int[] counts = state.matches.computeIfAbsent(type, key -> new int[watch.sentinels().size()]);
                for (int i = 0; i < counts.length; i++) {
                    String sentinel = watch.sentinels().get(i);
                    if (messages.stream().anyMatch(message -> message.content().contains(sentinel))) counts[i]++;
                }
            });
        }
        if (current.get() != null) throw new IllegalStateException("추론 호출 결속이 중첩되었습니다");
        current.set(jobId);
        return current::remove;
    }

    /** 현재 스레드의 결속 Job 에 무장된 고정 응답의 다음 순번. 결속·무장이 없으면 empty(운영 전송으로 위임)이다. */
    Optional<TestHookRegistry.LlmReply> next(CallType type) {
        String jobId = current.get();
        if (jobId == null) return Optional.empty();
        var script = hooks.binding(jobId).map(TestHookRegistry.Scenario::llmScript).orElse(List.of());
        if (script.isEmpty()) return Optional.empty();
        synchronized (this) {
            var state = jobs.computeIfAbsent(jobId, key -> new State());
            if (state.cursor >= script.size()) throw new IllegalStateException("고정 응답이 소진되었습니다");
            var reply = script.get(state.cursor);
            if (reply.callType() != type) throw new IllegalStateException("고정 응답의 호출 유형이 다릅니다");
            state.cursor++;
            state.sent.merge(type, 1, Integer::sum);
            return Optional.of(reply);
        }
    }

    /** 관찰 지점까지 온 호출 수(예산 assert 로 막힌 시도 포함). */
    public synchronized int observedCalls(String jobId, CallType type) { var state = jobs.get(jobId); return state == null ? 0 : state.observed.getOrDefault(type, 0); }
    /** 고정 응답 전송 계층이 실제로 받은 호출 수. */
    public synchronized int scriptedCalls(String jobId, CallType type) { var state = jobs.get(jobId); return state == null ? 0 : state.sent.getOrDefault(type, 0); }

    /** 관찰이 없거나 보관 기한이 지났으면 empty. */
    public Optional<List<WatchRow>> promptWatch(String jobId) {
        // idle reset 은 hooks→이 객체 순서로 잠그므로 hooks 조회를 이 객체 잠금 밖에서 한다.
        if (hooks.binding(jobId).flatMap(TestHookRegistry.Scenario::promptWatch).isEmpty()) return Optional.empty();
        synchronized (this) {
            purge();
            var state = jobs.get(jobId);
            if (state == null) return Optional.empty();
            List<WatchRow> rows = new ArrayList<>();
            state.matches.forEach((type, counts) -> { for (int i = 0; i < counts.length; i++) rows.add(new WatchRow(type, i, counts[i])); });
            return Optional.of(List.copyOf(rows));
        }
    }

    @Override public void beforeCommit(String jobId) {}
    @Override public synchronized void afterCommit(String jobId) { var state = jobs.get(jobId); if (state != null) state.terminalAt = clock.instant(); }

    private void purge() {
        Instant now = clock.instant();
        jobs.values().removeIf(state -> state.terminalAt != null && !now.isBefore(state.terminalAt.plus(RETENTION)));
    }
    private synchronized void reset() { jobs.clear(); }
}
