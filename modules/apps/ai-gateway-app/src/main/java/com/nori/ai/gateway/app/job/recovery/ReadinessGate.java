package com.nori.ai.gateway.app.job.recovery;

import java.util.*;
import org.springframework.boot.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;

public final class ReadinessGate implements ApplicationRunner,Ordered {
    public record RecoveryCompleted(){}
    private final List<ReadinessPrecondition> preconditions;private final ApplicationEventPublisher publisher;
    private volatile boolean ready;
    public ReadinessGate(List<ReadinessPrecondition> preconditions,ApplicationEventPublisher publisher){
        this.preconditions=preconditions.stream().sorted(Comparator.comparingInt(ReadinessPrecondition::order)).toList();this.publisher=publisher;
        var routers=this.preconditions.stream().filter(p->p instanceof RecoveryModeRouter).toList();
        if(!routers.isEmpty()&&(routers.size()!=1||this.preconditions.getFirst()!=routers.getFirst()||this.preconditions.stream().filter(p->!(p instanceof RecoveryModeRouter)).anyMatch(p->p.order()==Integer.MIN_VALUE)))throw new IllegalStateException("복구 판정이 첫 번째 사전조건이어야 합니다");
    }
    @Override public int getOrder(){return Ordered.HIGHEST_PRECEDENCE+1;}
    @Override public void run(ApplicationArguments args){
        ready=false;
        try{for(var condition:preconditions)condition.run();}
        catch(RecoveryModeRouter.RestorePathRequired pending){System.getLogger(ReadinessGate.class.getName()).log(System.Logger.Level.WARNING,"RESTORE_PATH_REQUIRED");return;}
        ready=true;publisher.publishEvent(new RecoveryCompleted());
    }
    public boolean ready(){return ready;}
    public void down(){ready=false;}
}
