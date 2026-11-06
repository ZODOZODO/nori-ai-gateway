package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.repository.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

public final class ResultResponseAssembler {
    private final JobRepository jobs;
    private final ConversationRepository conversations;
    private final ResultRepository results;
    private final ResultAccessEvaluator evaluator;
    private final KnowledgeDocumentStateReader documents;
    private final ExpiryCheckpoint expiry;
    private final JsonMapper json=JsonMapper.builder().build();
    public record Access(JobRepository.Job job,ConversationRepository.Conversation conversation,Map<String,Object> response,boolean allowed) {}
    public ResultResponseAssembler(JobRepository jobs,ConversationRepository conversations,ResultRepository results,ResultAccessEvaluator evaluator,KnowledgeDocumentStateReader documents,ExpiryCheckpoint expiry) {
        this.jobs=jobs;this.conversations=conversations;this.results=results;this.evaluator=evaluator;this.documents=documents;this.expiry=expiry;
    }
    public Access assemble(VerifiedSystemScope scope,String jobId) {
        var job=jobs.find(scope,jobId).orElseThrow(UniformNotFound::new);
        var conversation=conversations.find(scope,job.conversationId()).orElseThrow(UniformNotFound::new);
        var response=new LinkedHashMap<String,Object>();response.put("jobId",jobId);response.put("jobStatus",job.status());response.put("createdAt",job.createdAt());response.put("terminalAt",job.terminalAt());
        var result=results.find(scope,jobId);boolean allowed=true;
        if(result.isPresent()) {
            var value=result.get();
            try {
                var root=json.readTree(value.resultSourceDeps());
                if(!root.isObject())throw new IllegalArgumentException("결과 의존 계약이 잘못되었습니다");
                ResultSourceDeps deps;
                // P09의 Source 호출 없는 종결 producer가 기록한 정확한 빈 객체만 호환합니다.
                if(root.isEmpty())deps=new ResultSourceDeps(List.of(),List.of());
                else {
                    if(root.size()!=2||!root.has("sources")||!root.has("knowledgeDocuments")||!root.get("sources").isArray()||!root.get("knowledgeDocuments").isArray())throw new IllegalArgumentException("결과 의존 계약이 잘못되었습니다");
                    for(var source:root.get("sources")) {
                        if(!source.isObject()||source.size()!=2||!source.path("sourceId").isString()||!source.path("permCodes").isArray())throw new IllegalArgumentException("Source 의존 계약이 잘못되었습니다");
                        for(var permission:source.get("permCodes"))if(!permission.isString()||permission.asString().isBlank())throw new IllegalArgumentException("Source 권한 계약이 잘못되었습니다");
                    }
                    for(var document:root.get("knowledgeDocuments"))if(!document.isObject()||document.size()!=3||!document.path("ownerSystem").isString()||!document.path("sourceRef").isString()||!document.path("revision").isString())throw new IllegalArgumentException("문서 의존 계약이 잘못되었습니다");
                    deps=json.treeToValue(root,ResultSourceDeps.class);
                }
                if(deps==null)throw new IllegalArgumentException("결과 의존 계약이 없습니다");
                var states=deps.knowledgeDocuments().isEmpty()?Map.<ResultSourceDeps.DocKey,KnowledgeDocumentStateReader.State>of():documents.states(scope.perm(),Set.copyOf(deps.knowledgeDocuments()));
                allowed=evaluator.evaluate(scope.perm(),deps,states)==ResultAccessEvaluator.Decision.ALLOW
                    && deps.knowledgeDocuments().stream().allMatch(key->key.ownerSystem().equals(scope.boundSystem().name()))
                    && referencesMatch(json.readTree(value.payload()),scope.boundSystem());
            } catch(RuntimeException failure) { allowed=false; }
            if(allowed) {
                var payload=new LinkedHashMap<String,Object>();payload.put("status",value.status());payload.put("errorCode",value.errorCode());payload.put("payload",json.readTree(value.payload()));
                payload.put("clarification",value.clarification()==null?null:json.readTree(value.clarification()));response.put("result",payload);
            } else response.put("resultAccess","DENIED");
        } else response.put("result",null);
        expiry.assertLive(conversation.expiresAt());return new Access(job,conversation,response,allowed);
    }
    public void assertLive(Access access) { expiry.assertLive(access.conversation().expiresAt()); }
    private boolean referencesMatch(tools.jackson.databind.JsonNode node,com.nori.ai.gateway.core.scope.OwnerSystem owner) {
        if(node.isObject())for(var field:node.properties()) {
            if(Set.of("ownerSystem","systemId").contains(field.getKey())&&(!field.getValue().isString()||!owner.name().equals(field.getValue().asString())))return false;
            if(!referencesMatch(field.getValue(),owner))return false;
        }
        if(node.isArray())for(var item:node)if(!referencesMatch(item,owner))return false;
        if(node.isString()) {String value=node.asString();if((value.startsWith("system://")||value.startsWith("result://"))&&!new com.nori.ai.gateway.core.scope.OwnedReference(owner,value).belongsTo(owner))return false;}
        return true;
    }
}
