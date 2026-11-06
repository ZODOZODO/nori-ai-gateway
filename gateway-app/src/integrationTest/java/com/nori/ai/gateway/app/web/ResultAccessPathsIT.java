package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.persistence.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ResultAccessPathsIT extends ApiWebSupport {
    @Test void knowledgeUnknownDeniesEveryPathWithoutExposingPayloadAndNoDepsRemainAccessible() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");
        var denied=accept("actor","denied","질문",null,dlg());var allowed=accept("actor","allowed","질문",null,dlg());
        var repository=context.getBean(ResultRepository.class);var messages=context.getBean(MessageRepository.class);var evidence=context.getBean(EvidenceRepository.class);
        String payload=json.writeValueAsString(com.nori.ai.gateway.core.result.ResultBlockIds.assign(json.readTree("{\"answer\":\"PRIVATE ANSWER\",\"blocks\":[{\"type\":\"text\",\"dataRef\":{\"ownerSystem\":\"TC\",\"ref\":\"result://TC/result/same\"},\"content\":{\"id\":\"business-a\",\"text\":\"PRIVATE BLOCK A\"}},{\"type\":\"text\",\"dataRef\":{\"ownerSystem\":\"TC\",\"ref\":\"result://TC/result/same\"},\"content\":{\"id\":\"business-b\",\"text\":\"PRIVATE BLOCK B\"}}]}")));
        String deps="{\"sources\":[],\"knowledgeDocuments\":[{\"ownerSystem\":\"TC\",\"sourceRef\":\"doc\",\"revision\":\"old\"}]}";
        for(var item:List.of(denied,allowed)) {
            assertThat(repository.insert(scope("actor"),item.jobId(),new ResultRepository.Result("SUCCESS",null,payload,null,item==denied?deps:"{}"))).isTrue();
            assertThat(messages.insert(scope("actor"),item.conversationId(),new MessageRepository.Message("response-"+item.jobId(),"assistant","PRIVATE HISTORY","[]","[]",item.jobId(),null))).isTrue();
            assertThat(evidence.insert(scope("actor"),item.jobId(),new EvidenceRepository.Entry("e-"+item.jobId(),"doc","chunk","old","{}"))).isTrue();
        }
        var allowedResponse=request("GET","/v1/analyses/"+allowed.jobId(),"actor",null,null,false);assertThat(allowedResponse.statusCode()).isEqualTo(200);
        var savedBlocks=json.readTree(allowedResponse.body()).get("result").get("payload").get("blocks");String blockId=savedBlocks.get(0).get("id").asString();
        assertThat(blockId).isEqualTo("b0");assertThat(savedBlocks.get(1).get("id").asString()).isEqualTo("b1");assertThat(savedBlocks.get(0).get("dataRef")).isEqualTo(savedBlocks.get(1).get("dataRef"));
        for(String path:List.of("/v1/analyses/"+denied.jobId(),"/v1/analyses/"+denied.jobId()+"/evidence/e-"+denied.jobId()+"/source","/v1/analyses/"+denied.jobId()+"/blocks/"+blockId+"/export","/v1/audit/jobs/"+denied.jobId())) {
            var response=request("GET",path,"actor",null,null,false);assertThat(response.statusCode()).isEqualTo(200);
            var body=json.readTree(response.body());assertThat(body.propertyNames()).containsExactlyInAnyOrder("jobId","jobStatus","createdAt","terminalAt","resultAccess");
            assertThat(body.get("resultAccess").asString()).isEqualTo("DENIED");assertThat(response.body()).doesNotContain("PRIVATE","knowledgeDocuments","sourceRef","old");
        }
        var history=request("GET","/v1/conversations/"+denied.conversationId(),"actor",null,null,false);assertThat(history.statusCode()).isEqualTo(200);assertThat(history.body()).contains("DENIED").doesNotContain("PRIVATE");
        assertThat(request("GET","/v1/analyses/"+allowed.jobId(),"actor",null,null,false).body()).contains("PRIVATE ANSWER").doesNotContain("DENIED");
        assertThat(request("GET","/v1/conversations/"+allowed.conversationId(),"actor",null,null,false).body()).contains("PRIVATE ANSWER");
        for(int repeat=0;repeat<2;repeat++) {
            var fetched=json.readTree(request("GET","/v1/analyses/"+allowed.jobId(),"actor",null,null,false).body());assertThat(fetched.get("result").get("payload").get("blocks")).isEqualTo(savedBlocks);
            var historyRows=json.readTree(request("GET","/v1/conversations/"+allowed.conversationId(),"actor",null,null,false).body()).get("messages");
            for(var message:historyRows)if(message.get("role").asString().equals("assistant"))assertThat(message.get("content").get("result").get("payload").get("blocks")).isEqualTo(savedBlocks);
            for(var block:savedBlocks) {
                String id=block.get("id").asString();var exported=request("GET","/v1/analyses/"+allowed.jobId()+"/blocks/"+id+"/export","actor",null,null,false);
                assertThat(exported.statusCode()).isEqualTo(200);assertThat(json.readTree(exported.body())).isEqualTo(block);
            }
        }
        for(String actor:List.of("actor","other")) {
            var response=request("GET","/v1/analyses/"+allowed.jobId()+"/blocks/"+(actor.equals("actor")?"unknown":blockId)+"/export",actor,null,null,false);assertThat(response.statusCode()).isEqualTo(404);assertThat(response.body()).isEmpty();
        }
        // 접근 평가 ALLOW 후에도 P23 전의 원문 리더는 비가용을 균일 404로 응답합니다.
        var source=request("GET","/v1/analyses/"+allowed.jobId()+"/evidence/e-"+allowed.jobId()+"/source","actor",null,null,false);assertThat(source.statusCode()).isEqualTo(404);assertThat(source.body()).isEmpty();
        assertThat(calls().count("authorize")).isZero();assertThat(observations().workflows).isEmpty();
        var duplicate=json.readTree(payload);((tools.jackson.databind.node.ObjectNode)duplicate.get("blocks").get(1)).put("id",blockId);
        jdbc().sql("UPDATE gw.result SET payload=CAST(:payload AS jsonb) WHERE job_id=:job").param("payload",json.writeValueAsString(duplicate)).param("job",allowed.jobId()).update();
        var ambiguous=request("GET","/v1/analyses/"+allowed.jobId()+"/blocks/"+blockId+"/export","actor",null,null,false);assertThat(ambiguous.statusCode()).isEqualTo(404);assertThat(ambiguous.body()).isEmpty();
        var current=scope("actor");var wm=new com.nori.ai.gateway.core.scope.VerifiedSystemScope(com.nori.ai.gateway.core.scope.OwnerSystem.WM,current.authenticatedServiceRef(),current.actorRef(),current.sub(),current.perm(),current.pdg(),current.issuedAt(),current.expiresAt(),current.requestId());
        var foreign=inRequestSpan(()->context.getBean(com.nori.ai.gateway.app.job.JobAdmissionService.class).accept(wm,new com.nori.ai.gateway.app.job.JobAdmissionService.AdmissionRequest("/v1/query",null,"타 시스템 질문",Map.of(),"export-foreign",registeredDlg("actor"))));
        var foreignResponse=request("GET","/v1/analyses/"+foreign.jobId()+"/blocks/"+blockId+"/export","actor",null,null,false);assertThat(foreignResponse.statusCode()).isEqualTo(404);assertThat(foreignResponse.body()).isEmpty();
        jdbc().sql("UPDATE gw.conversation SET expires_at=:expiry WHERE id=:id").param("expiry",java.sql.Timestamp.from(NOW.plusSeconds(1))).param("id",allowed.conversationId()).update();clock().advance(1);
        var expired=request("GET","/v1/analyses/"+allowed.jobId()+"/blocks/"+blockId+"/export","actor",null,null,false);assertThat(expired.statusCode()).isEqualTo(404);assertThat(expired.body()).isEmpty();
    }
    @Test void malformedNonemptyDependenciesFailClosedAndCurrentSourcePermissionsUseAndSemantics() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");var accepted=accept("actor","contracts","질문",null,dlg());
        var result=context.getBean(ResultRepository.class);assertThat(result.insert(scope("actor"),accepted.jobId(),new ResultRepository.Result("SUCCESS",null,"{\"answer\":\"PRIVATE\"}",null,"{}"))).isTrue();
        for(String deps:List.of("{\"source\":[]}","{\"sources\":[]}","{\"sources\":null,\"knowledgeDocuments\":[]}","{\"sources\":[],\"knowledgeDocuments\":[],\"extra\":true}","{\"sources\":[{\"sourceId\":\"a\",\"permCodes\":[\"EQP_READ\"]},{\"sourceId\":\"b\",\"permCodes\":[\"OTHER\"]}],\"knowledgeDocuments\":[]}")) {
            jdbc().sql("UPDATE gw.result SET result_source_deps=CAST(:deps AS jsonb) WHERE job_id=:job").param("deps",deps).param("job",accepted.jobId()).update();
            var response=request("GET","/v1/analyses/"+accepted.jobId(),"actor",null,null,false);assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("DENIED").doesNotContain("PRIVATE");
        }
    }
}
