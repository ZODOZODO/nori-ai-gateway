package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.persistence.queue.TerminalCommitObserver;

public final class FaultInjector implements TerminalCommitObserver {
    private final TestHookRegistry registry;
    private final PausePointRegistry pauses;
    public FaultInjector(TestHookRegistry registry,PausePointRegistry pauses){this.registry=registry;this.pauses=pauses;}
    public void haltAt(String jobId,PausePointRegistry.Point point){
        if(registry.binding(jobId).map(s->s.haltAt()==point).orElse(false))Runtime.getRuntime().halt(137);
    }
    public void at(String jobId,PausePointRegistry.Point point){
        haltAt(jobId,point);
        try{pauses.hit(point,jobId);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("시험 정지점 대기가 중단되었습니다");}
    }
    @Override public void beforeCommit(String jobId){at(jobId,PausePointRegistry.Point.TERMINAL_PRECOMMIT);}
    @Override public void afterCommit(String jobId){at(jobId,PausePointRegistry.Point.TERMINAL_POSTCOMMIT);}
}
