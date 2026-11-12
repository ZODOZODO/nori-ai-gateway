package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.registry.TaskType;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.*;

public final class TestHookRegistry {
    /**
     * {@code llmScript} = 결속 Job 의 추론 호출에 순번대로 돌려줄 고정 응답(LLM_STUB, 하네스 fixture), {@code promptWatch} = Prompt 관찰 sentinel(Plan P20 N10).
     * 둘 다 이 Job 에만 적용된다.
     */
    public record Scenario(TaskType taskType, Set<PausePointRegistry.Point> pauseAt, PausePointRegistry.Point haltAt, boolean failSanitizer, Optional<SeamRegistry.Directive> seam,
            List<LlmReply> llmScript, Optional<PromptWatch> promptWatch) {
        public Scenario { Objects.requireNonNull(taskType);pauseAt=Set.copyOf(pauseAt);seam=Objects.requireNonNull(seam);llmScript=List.copyOf(llmScript);promptWatch=Objects.requireNonNull(promptWatch); }
        public Scenario(TaskType taskType, Set<PausePointRegistry.Point> pauseAt, PausePointRegistry.Point haltAt, boolean failSanitizer, Optional<SeamRegistry.Directive> seam) {
            this(taskType, pauseAt, haltAt, failSanitizer, seam, List.of(), Optional.empty());
        }
        public Scenario(TaskType taskType, Set<PausePointRegistry.Point> pauseAt, PausePointRegistry.Point haltAt, boolean failSanitizer) {
            this(taskType, pauseAt, haltAt, failSanitizer, Optional.empty());
        }
    }
    /** 고정 응답 1건. {@code content} null 은 빈 응답, {@code promptTokens} null 은 실제 Gateway 계수를 usage 로 쓴다. 원문은 toString·로그에 싣지 않는다. */
    public record LlmReply(com.nori.ai.gateway.core.budget.CallType callType, String content, String finishReason, Integer promptTokens, int completionTokens) {
        public LlmReply { Objects.requireNonNull(callType);Objects.requireNonNull(finishReason);if(completionTokens<0||promptTokens!=null&&promptTokens<0)throw new IllegalArgumentException("고정 응답 사용량이 잘못되었습니다"); }
        @Override public String toString(){return "LlmReply["+callType+"]";}
    }
    /** sentinel 16개 이하, 각 1~64 코드포인트. */
    public record PromptWatch(List<String> sentinels) {
        public PromptWatch {
            sentinels=List.copyOf(sentinels);
            if(sentinels.isEmpty()||sentinels.size()>16||sentinels.stream().anyMatch(value->value.isEmpty()||value.codePointCount(0,value.length())>64))throw new IllegalArgumentException("Prompt 관찰 sentinel 이 잘못되었습니다");
        }
        @Override public String toString(){return "PromptWatch["+sentinels.size()+"]";}
    }
    private record Key(String actor,String requestKey){}
    private static final class Armed { final Scenario scenario;final Instant expires;String reservation;
        Armed(Scenario scenario,Instant expires){this.scenario=scenario;this.expires=expires;} }
    private final Map<Key,Armed> arms=new HashMap<>();
    private final Map<String,Scenario> bindings=new HashMap<>();
    private final Set<Key> consumed=new HashSet<>();
    private final Map<String,Set<String>> requestKeys=new HashMap<>();
    private final Clock clock;
    private final PausePointRegistry pauses;
    private final AtomicInteger reservations=new AtomicInteger();
    private final List<Runnable> idleResetCallbacks=new ArrayList<>();
    public synchronized void onIdleReset(Runnable callback){idleResetCallbacks.add(Objects.requireNonNull(callback));}
    private int admitting;
    public TestHookRegistry(Clock clock,PausePointRegistry pauses){this.clock=clock;this.pauses=pauses;}
    public synchronized void arm(String actor,String requestKey,Scenario scenario){
        if(actor==null||actor.isBlank()||requestKey==null||requestKey.isBlank())throw new IllegalArgumentException("무장 식별자가 필요합니다");
        var key=new Key(actor,requestKey);var old=arms.get(key);
        if(consumed.contains(key)||old!=null&&(old.reservation!=null||clock.instant().isBefore(old.expires)))throw new Conflict("ALREADY_ARMED");
        arms.put(key,new Armed(scenario,clock.instant().plusSeconds(60)));
    }
    public synchronized Optional<Scenario> armed(String actor,String key){
        var arm=arms.get(new Key(actor,key));return arm!=null&&clock.instant().isBefore(arm.expires)?Optional.of(arm.scenario):Optional.empty();
    }
    public synchronized void bindOnAdmission(String actor,String key,String jobId){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("결속에는 접수 트랜잭션이 필요합니다");
        var identity=new Key(actor,key);var arm=arms.get(identity);
        if(arm==null||!clock.instant().isBefore(arm.expires))return;
        if(arm.reservation!=null)throw new Conflict("ALREADY_RESERVED");
        reservations.incrementAndGet();arm.reservation=jobId;bindings.put(jobId,arm.scenario);
        for(var point:arm.scenario.pauseAt())pauses.hold(point,jobId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public int getOrder(){return Integer.MAX_VALUE-1;}
            @Override public void afterCompletion(int status){synchronized(TestHookRegistry.this){
                if(status==STATUS_COMMITTED){arms.remove(identity,arm);consumed.add(identity);requestKeys.computeIfAbsent(key,k->new LinkedHashSet<>()).add(jobId);if(clock instanceof ControllableClock controlled)controlled.admissionCommitted();}
                else{bindings.remove(jobId);arm.reservation=null;for(var point:arm.scenario.pauseAt())pauses.release(point,jobId);}
            }}
        });
    }
    public synchronized void beginAdmission(){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("접수 트랜잭션이 필요합니다");
        admitting++;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){synchronized(TestHookRegistry.this){admitting--;}}
        });
    }
    public synchronized Optional<Scenario> binding(String jobId){return Optional.ofNullable(bindings.get(jobId));}
    /** 접수 커밋된 무장의 Idempotency-Key 로 결속 Job 을 찾는다(다른 actor 가 같은 키를 쓰면 여러 개). */
    public synchronized List<String> jobsForRequestKey(String requestKey){return List.copyOf(requestKeys.getOrDefault(requestKey,Set.of()));}
    public int reservationCount(){return reservations.get();}
    public void admissionCommitted(){if(clock instanceof ControllableClock controlled)controlled.admissionCommitted();}
    public synchronized boolean resetIfIdle(java.util.function.BooleanSupplier noActiveJobs){
        return resetIfIdle(noActiveJobs, () -> {});
    }
    public synchronized boolean resetIfIdle(java.util.function.BooleanSupplier noActiveJobs,Runnable resetMock){
        // 아직 커밋되지 않은 접수의 결속도 reset으로 지우지 않습니다.
        if(admitting!=0||!noActiveJobs.getAsBoolean())return false;
        resetMock.run();
        idleResetCallbacks.forEach(Runnable::run);
        arms.clear();bindings.clear();consumed.clear();requestKeys.clear();pauses.reset();reservations.set(0);if(clock instanceof ControllableClock controlled)controlled.reset();return true;
    }
    public synchronized void freezeAt(Instant at,java.util.function.BooleanSupplier noActiveJobs){
        if(!(clock instanceof ControllableClock controlled))throw new IllegalStateException("제어 시계가 필요합니다");
        controlled.freezeAt(at,admitting==0&&noActiveJobs.getAsBoolean());
    }
    public static final class Conflict extends RuntimeException { public Conflict(String message){super(message);} }
}
