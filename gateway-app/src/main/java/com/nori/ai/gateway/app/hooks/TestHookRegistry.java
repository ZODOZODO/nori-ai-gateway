package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.registry.TaskType;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.*;

public final class TestHookRegistry {
    public record Scenario(TaskType taskType, Set<PausePointRegistry.Point> pauseAt, PausePointRegistry.Point haltAt, boolean failSanitizer, Optional<SeamRegistry.Directive> seam) {
        public Scenario { Objects.requireNonNull(taskType);pauseAt=Set.copyOf(pauseAt);seam=Objects.requireNonNull(seam); }
        public Scenario(TaskType taskType, Set<PausePointRegistry.Point> pauseAt, PausePointRegistry.Point haltAt, boolean failSanitizer) {
            this(taskType, pauseAt, haltAt, failSanitizer, Optional.empty());
        }
    }
    private record Key(String actor,String requestKey){}
    private static final class Armed { final Scenario scenario;final Instant expires;String reservation;
        Armed(Scenario scenario,Instant expires){this.scenario=scenario;this.expires=expires;} }
    private final Map<Key,Armed> arms=new HashMap<>();
    private final Map<String,Scenario> bindings=new HashMap<>();
    private final Set<Key> consumed=new HashSet<>();
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
                if(status==STATUS_COMMITTED){arms.remove(identity,arm);consumed.add(identity);if(clock instanceof ControllableClock controlled)controlled.admissionCommitted();}
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
        arms.clear();bindings.clear();consumed.clear();pauses.reset();reservations.set(0);if(clock instanceof ControllableClock controlled)controlled.reset();return true;
    }
    public synchronized void freezeAt(Instant at,java.util.function.BooleanSupplier noActiveJobs){
        if(!(clock instanceof ControllableClock controlled))throw new IllegalStateException("제어 시계가 필요합니다");
        controlled.freezeAt(at,admitting==0&&noActiveJobs.getAsBoolean());
    }
    public static final class Conflict extends RuntimeException { public Conflict(String message){super(message);} }
}
