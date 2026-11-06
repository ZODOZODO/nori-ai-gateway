package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.job.*;
import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.core.port.JobRequestInputReader;
import com.nori.ai.gateway.persistence.repository.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ApiInputIT extends ApiWebSupport {
    @Test @Tag("AC-18-RESUME") void sameClockConversationKeepsTwoExactInputsAndReplayDoesNotMutateThem() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        var created=request("POST","/v1/conversations","actor",Map.of(),null,false);assertThat(created.statusCode()).isEqualTo(201);
        String conversation=json.readTree(created.body()).get("id").asString();
        var ctx=new LinkedHashMap<String,Object>();ctx.put("timezone","Asia/Seoul");ctx.put("referenceTime","2026-11-06T09:00:00.123456789+09:00");ctx.put("timeWindow",null);
        var options=new LinkedHashMap<String,Object>();options.put("allowTools",false);options.put("allowKnowledge",null);
        var fields=Map.<String,Object>of("context",ctx,"resourceRefs",List.of(Map.of("type","EQP","ref","EQP_001")),"options",options);
        var first=new LinkedHashMap<String,Object>(fields);first.put("query","첫 질문 api_key=PRIVATEVALUE");
        String path="/v1/conversations/"+conversation+"/messages";
        var a=request("POST",path,"actor",first,"first",true);assertThat(a.statusCode()).isEqualTo(202);
        var second=new LinkedHashMap<String,Object>();second.put("query","두번째 질문");second.put("conversationId",conversation);second.put("context",null);second.put("resourceRefs",null);
        var b=request("POST","/v1/analyses","actor",second,"second",true);assertThat(b.statusCode()).isEqualTo(202);
        String aid=json.readTree(a.body()).get("jobId").asString(),bid=json.readTree(b.body()).get("jobId").asString();
        var reader=context.getBean(JobRequestInputReader.class);var ia=reader.findRequestInput(scope("actor"),aid).orElseThrow();var ib=reader.findRequestInput(scope("actor"),bid).orElseThrow();
        assertThat(ia.messageId()).isEqualTo(json.readTree(a.body()).get("messageId").asString());assertThat(ib.messageId()).isNotEqualTo(ia.messageId());assertThat(ia.jobSeq()).isLessThan(ib.jobSeq());
        assertThat(ia.maskedQuestion()).contains("[마스킹]").doesNotContain("PRIVATEVALUE");assertThat(ib.maskedQuestion()).isEqualTo("두번째 질문");
        assertThat(ia.requestFields()).isEqualTo(fields);assertThat(ib.requestFields()).containsEntry("context",null).containsEntry("resourceRefs",null).doesNotContainKey("options");
        assertThat(ia.selectedResourceRefs()).containsExactly(new com.nori.ai.gateway.core.plan.ResourceRef("EQP","EQP_001"));assertThat(ib.selectedResourceRefs()).isEmpty();
        assertThat(context.getBean(ConversationRepository.class).saveContext(scope("actor"),conversation,"{\"timezone\":\"UTC\"}")).isTrue();
        assertThat(reader.findRequestInput(scope("actor"),aid)).contains(ia);
        var replay=request("POST",path,"actor",first,"first",true);assertThat(replay.statusCode()).isEqualTo(202);assertThat(json.readTree(replay.body()).get("messageId").asString()).isEqualTo(ia.messageId());
        var changed=new LinkedHashMap<String,Object>(first);changed.put("options",Map.of("allowTools",true));assertThat(request("POST",path,"actor",changed,"first",true).statusCode()).isEqualTo(409);
        changed=new LinkedHashMap<>(first);changed.put("resourceRefs",List.of(Map.of("type","EQP","ref","EQP_002")));assertThat(request("POST",path,"actor",changed,"first",true).statusCode()).isEqualTo(409);
        assertThat(count("job")).isEqualTo(2);assertThat(count("message")).isEqualTo(2);assertThat(count("job_event")).isEqualTo(2);
    }
    @Test @Tag("ISO-05-READ") @Tag("ISO-05-MESSAGE") @Tag("ISO-08-JOB") @Tag("AC-24-RESULTSCOPE") @Tag("AC-24-RESULT-NODLG")
    void everyApiUsesCurrentActorAndBoundOwnerWithUniformEmpty404() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");var accepted=accept("actor","owner","질문",null,dlg());
        var wm=scope("actor");var owner=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(com.nori.ai.gateway.core.scope.OwnerSystem.WM,wm.authenticatedServiceRef(),wm.actorRef(),wm.sub(),wm.perm(),wm.pdg(),wm.issuedAt(),wm.expiresAt(),wm.requestId());
        var foreign=context.getBean(JobAdmissionService.class).accept(owner,new JobAdmissionService.AdmissionRequest("/v1/query",null,"타 시스템 질문",Map.of(),"foreign",registeredDlg("actor")));
        for(String actor:List.of("other","actor"))for(String path:List.of("/v1/analyses/"+(actor.equals("actor")?foreign.jobId():accepted.jobId()),"/v1/conversations/"+(actor.equals("actor")?foreign.conversationId():accepted.conversationId()))) {
            var response=request("GET",path,actor,null,null,false);assertThat(response.statusCode()).isEqualTo(404);assertThat(response.body()).isEmpty();
        }
        assertThat(request("GET","/v1/analyses/"+accepted.jobId(),"actor",null,null,false).statusCode()).isEqualTo(200);
        assertThat(request("POST","/v1/conversations/"+accepted.conversationId()+"/messages","other",Map.of("query","질문"),"denied",true).statusCode()).isEqualTo(404);
        assertThat(request("POST","/v1/analyses/"+accepted.jobId()+"/cancel","other",Map.of(),null,false).statusCode()).isEqualTo(404);
        assertThat(context.getBean(JobRequestInputReader.class).findRequestInput(scope("other"),accepted.jobId())).isEmpty();
        assertThat(context.getBean(JobRequestInputReader.class).findRequestInput(owner,accepted.jobId())).isEmpty();
        jdbc().sql("UPDATE gw.message SET role='assistant' WHERE id=:id").param("id",accepted.messageId()).update();
        assertThat(context.getBean(JobRequestInputReader.class).findRequestInput(scope("actor"),accepted.jobId())).isEmpty();
        assertThat(context.getBean(JobRepository.class).insert(scope("actor"),new JobRepository.NewJob("bad-role",accepted.conversationId(),null,"digest","/v1/query",accepted.messageId(),Map.of()),null)).isFalse();
        assertThat(context.getBean(JobRepository.class).insert(scope("actor"),new JobRepository.NewJob("bad-conversation",foreign.conversationId(),null,"digest","/v1/query",accepted.messageId(),Map.of()),null)).isFalse();
    }
    @Test void invalidKnownInputDoesNotBecomeAnArbitrarySnapshotChannel() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        for(Object contextValue:List.of(Map.of("timezone","Asia/Seoul","referenceTime","2026-02-30T10:00:00+09:00"),Map.of("timezone","UTC","referenceTime","2026-11-06T00:00:00Z","timeWindow",Map.of("from","2026-11-07T00:00:00Z","to","2026-11-06T00:00:00Z")))) {
            assertThat(request("POST","/v1/query","actor",Map.of("query","질문","context",contextValue),UUID.randomUUID().toString(),true).statusCode()).isEqualTo(400);
        }
        assertThat(request("POST","/v1/query","actor",Map.of("query","질문","options",Map.of("allowTools","yes")),"invalid",true).statusCode()).isEqualTo(400);
        assertThat(count("job")).isZero();assertThat(count("message")).isZero();
    }
}
