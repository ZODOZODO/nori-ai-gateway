package com.nori.ai.gateway.app.job;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkerRestartInputIT extends WorkerRestartProcessSupport {
    @Test void twoQueuedJobsInOneConversationRestoreTheirOwnExactSnapshotAfterRealProcessRestart() throws Exception {
        arm("holder","TERMINAL_PRECOMMIT",List.of("TOOL_IN_FLIGHT"));String holder=admit("holder");await(()->state(holder).equals("RUNNING"));
        var create=send("/v1/conversations","POST","{}",null,NOW,false);assertThat(create.statusCode()).isEqualTo(201);String conversation=json.readTree(create.body()).get("id").asString();
        var contextA=new LinkedHashMap<String,Object>();contextA.put("timezone","Asia/Seoul");contextA.put("referenceTime","2026-11-06T09:00:00.123456789+09:00");contextA.put("timeWindow",null);
        var options=new LinkedHashMap<String,Object>();options.put("allowTools",false);options.put("allowKnowledge",null);
        var a=Map.<String,Object>of("context",contextA,"resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001")),"options",options);
        var b=Map.<String,Object>of("context",Map.of("timezone","UTC","referenceTime","2026-11-06T00:00:00.987654321Z","timeWindow",Map.of("from","2026-11-05T00:00:00.123456789Z","to","2026-11-05T01:00:00.987654321Z")),"resourceRefs",List.of(Map.of("type","EQP","ref","EQP_002")));
        var ids=new ArrayList<String>();var messages=new ArrayList<String>();
        for(int index=0;index<2;index++) {
            String key="input-"+index;arm(key,null,List.of());var body=new LinkedHashMap<String,Object>(index==0?a:b);body.put("query",index==0?"첫 질문":"두번째 질문");body.put("conversationId",conversation);
            var response=send("/v1/query","POST",json.writeValueAsString(body),key,NOW,true);assertThat(response.statusCode()).isEqualTo(202);var accepted=json.readTree(response.body());ids.add(accepted.get("jobId").asString());messages.add(accepted.get("messageId").asString());
        }
        jdbc.sql("INSERT INTO gw.context(conversation_id,owner_system,payload,updated_at) VALUES (:conversation,'TC','{\"timezone\":\"UTC\"}',:now)").param("conversation",conversation).param("now",java.sql.Timestamp.from(NOW)).update();
        assertThat(hook("/__test/pause",Map.of("point","TOOL_IN_FLIGHT","jobId",holder,"op","RELEASE")).statusCode()).isEqualTo(204);assertThat(harness.awaitExit(Duration.ofSeconds(15))).isEqualTo(137);
        launch(NOW.plusSeconds(1));for(String id:ids){await(()->state(id).equals("SUCCEEDED"));assertThat(started(id)).isOne();}
        long previous=0;
        for(int index=0;index<2;index++) {
            var payload=json.readTree(jdbc.sql("SELECT payload::text FROM gw.result WHERE job_id=:job").param("job",ids.get(index)).query(String.class).single());
            assertThat(payload.get("inputMessageId").asString()).isEqualTo(messages.get(index));assertThat(payload.get("inputQuestion").asString()).isEqualTo(index==0?"첫 질문":"두번째 질문");
            assertThat(payload.get("requestFields")).isEqualTo(json.valueToTree(index==0?a:b));assertThat(payload.get("inputJobSeq").asLong()).isGreaterThan(previous);previous=payload.get("inputJobSeq").asLong();
        }
    }
}
