package com.nori.ai.gateway.app.hooks;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class PausePointRegistry {
    public enum Point { ADMISSION_FAST_MISS, ADMISSION_AFTER_COMMIT, BEFORE_CLASSIFICATION, TOOL_IN_FLIGHT,
        BEFORE_COMPOSER, TERMINAL_PRECOMMIT, TERMINAL_POSTCOMMIT,
        DISPATCH_HOLD, SWEEPER_HOLD, EXPIRY_CANCELLER_HOLD, PURGE_HOLD }
    private record Key(Point point,String jobId) {}
    private static final class Pause { final CountDownLatch reached=new CountDownLatch(1);final CountDownLatch release=new CountDownLatch(1);final java.util.concurrent.atomic.AtomicInteger hits=new java.util.concurrent.atomic.AtomicInteger(); }
    private final ConcurrentMap<Key,Pause> pauses=new ConcurrentHashMap<>();
    public void hold(Point point,String jobId){pauses.putIfAbsent(new Key(point,jobId),new Pause());}
    public boolean held(Point point){return pauses.containsKey(new Key(point,"*"));}
    public boolean awaitReached(Point point,String jobId,Duration timeout)throws InterruptedException{
        Pause pause=pauses.get(new Key(point,jobId));return pause!=null&&pause.reached.await(timeout.toMillis(),TimeUnit.MILLISECONDS);
    }
    public void hit(Point point,String jobId)throws InterruptedException{
        for(String key:List.of(jobId,"*")){Pause pause=pauses.get(new Key(point,key));if(pause!=null){pause.hits.incrementAndGet();pause.reached.countDown();pause.release.await();}}
    }
    public int reachedCount(Point point,String jobId){var pause=pauses.get(new Key(point,jobId));return pause==null?0:pause.hits.get();}
    public void release(Point point,String jobId){Pause pause=pauses.remove(new Key(point,jobId));if(pause!=null)pause.release.countDown();}
    public void reset(){for(Pause pause:pauses.values())pause.release.countDown();pauses.clear();}
}
