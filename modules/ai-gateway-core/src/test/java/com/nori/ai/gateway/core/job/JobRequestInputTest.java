package com.nori.ai.gateway.core.job;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JobRequestInputTest {
    @Test void snapshotPreservesAbsentNullValueAndDeeplySeparatesCallerMutations() {
        var context=new LinkedHashMap<String,Object>();context.put("timezone","Asia/Seoul");context.put("timeWindow",null);context.put("referenceTime","2026-11-06T10:00:00.123456789+09:00");
        var options=new LinkedHashMap<String,Object>();options.put("allowTools",false);options.put("allowKnowledge",null);
        var refs=new ArrayList<Object>();refs.add(new LinkedHashMap<>(Map.of("type","EQP","ref","EQP_001")));
        var input=new JobRequestInput("message",1,"마스킹 질문",Map.of("context",context,"options",options,"resourceRefs",refs));
        context.clear();options.clear();refs.clear();
        assertThat(((Map<?,?>)input.requestFields().get("context")).containsKey("timeWindow")).isTrue();
        assertThat(((Map<?,?>)input.requestFields().get("options")).get("allowTools")).isEqualTo(false);
        assertThat(input.selectedResourceRefs()).containsExactly(new com.nori.ai.gateway.core.plan.ResourceRef("EQP","EQP_001"));
        assertThatThrownBy(()->((Map<String,Object>)input.requestFields().get("context")).put("x",1)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new JobRequestInput("m",2,"질문",Map.of()).requestFields()).doesNotContainKey("context");
        assertThat(input.toString()).doesNotContain("마스킹 질문","referenceTime");
    }
    @Test void brokenPersistedInputAndMutableNonJsonScalarsCannotBecomeExecutableSnapshots() {
        assertThatThrownBy(()->new JobRequestInput("m",1,"질문",Map.of("context",false))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JobRequestInput("m",1,"질문",Map.of("resourceRefs",List.of("EQP")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JobRequestInput("m",1,"질문",Map.of("options",Map.of("allowTools","true")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JobRequestInput("m",1,"질문",Map.of("value",new java.util.concurrent.atomic.AtomicInteger()))).isInstanceOf(IllegalArgumentException.class);
    }
}
