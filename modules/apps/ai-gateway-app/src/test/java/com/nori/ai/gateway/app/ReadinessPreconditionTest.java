package com.nori.ai.gateway.app;

import com.nori.ai.gateway.app.job.recovery.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReadinessPreconditionTest {
    @Test void executesInOrderStopsAtFirstFailureAndPublishesOnlyAfterAllSuccess(){
        var calls=new ArrayList<Integer>();var events=new ArrayList<Object>();
        var gate=new ReadinessGate(List.of(condition(3,false,calls),condition(1,false,calls),condition(2,true,calls)),events::add);
        assertThatThrownBy(()->gate.run(null)).isInstanceOf(IllegalStateException.class);assertThat(calls).containsExactly(1,2);assertThat(events).isEmpty();assertThat(gate.ready()).isFalse();
        calls.clear();new ReadinessGate(List.of(condition(2,false,calls),condition(1,false,calls)),events::add).run(null);
        assertThat(calls).containsExactly(1,2);assertThat(events).singleElement().isInstanceOf(ReadinessGate.RecoveryCompleted.class);
    }
    private ReadinessPrecondition condition(int order,boolean fail,List<Integer> calls){return new ReadinessPrecondition(){public int order(){return order;}public void run(){calls.add(order);if(fail)throw new IllegalStateException("시험 실패");}};}
}
