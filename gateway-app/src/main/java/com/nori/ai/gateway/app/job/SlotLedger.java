package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.job.CancellationToken;
import java.util.*;

public final class SlotLedger {
    private final Map<String,CancellationToken> active=new HashMap<>();
    private final Set<String> released=new HashSet<>();
    private final Set<String> terminated=new HashSet<>();
    public synchronized CancellationToken claim(String jobId){
        if(active.containsKey(jobId)||released.contains(jobId))throw new IllegalStateException("실행 슬롯을 중복 확보할 수 없습니다");
        var token=new CancellationToken();active.put(jobId,token);return token;
    }
    public synchronized Optional<CancellationToken> token(String jobId){return Optional.ofNullable(active.get(jobId));}
    public synchronized void signalIfActive(String jobId,CancellationToken.Reason reason){
        // 종료 표지와 신호 전달을 직렬화하여 늦은 interrupt가 종결 tx를 중단하지 않게 합니다.
        if(!terminated.contains(jobId)){
            var token=active.get(jobId);if(token!=null)token.signal(reason);
        }
    }
    public synchronized void markTerminated(String jobId){
        if(active.containsKey(jobId))terminated.add(jobId);
        else if(!released.contains(jobId))throw new IllegalStateException("확보한 실행 슬롯이 없습니다");
    }
    public synchronized boolean terminated(String jobId){return terminated.contains(jobId);}
    public synchronized void release(String jobId){
        if(released.contains(jobId))throw new IllegalStateException("실행 슬롯이 중복 반납되었습니다");
        if(active.remove(jobId)!=null){terminated.remove(jobId);released.add(jobId);}
    }
    public synchronized int activeCount(){return active.size();}
    public synchronized int releaseCount(){return released.size();}
}
