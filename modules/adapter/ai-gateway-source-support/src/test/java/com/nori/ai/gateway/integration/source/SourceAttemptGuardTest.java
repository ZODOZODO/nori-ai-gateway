package com.nori.ai.gateway.integration.source;

import com.nori.ai.gateway.integration.ConnectorTestSupport;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

// connector 별 시도 guard 시험은 각 adapter 의 {Loki,Tempo,GitLab}SourceAttemptGuardTest 에 있다. 여기에는 전송기 공통 부분만 남는다.
class SourceAttemptGuardTest {
    @Test void timeSpentInTheGuardConsumesTheSameDeadlineBeforeTransportStarts() throws Exception {
        try(var stub=new ConnectorTestSupport()) {
            var nanos=new AtomicLong();var http=new SourceHttpClient(stub.base(),"loki",SourceObservability.noop(),nanos::get);var calls=new AtomicInteger();
            assertThatThrownBy(()->http.json(http.uri(List.of("test"),Map.of()),null,http.deadline(Duration.ofSeconds(1)),()->{calls.incrementAndGet();nanos.addAndGet(Duration.ofSeconds(1).toNanos());})).isInstanceOf(SourceHttpClient.Failure.class);
            assertThat(calls).hasValue(1);assertThat(stub.requests).isEmpty();
        }
    }
}
