package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.observability.AuditRecorder;
import com.nori.ai.gateway.core.audit.AuditEvent;
import com.nori.ai.gateway.core.evidence.EvidenceAssembler;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.time.TimeWindowResolver;
import com.nori.ai.gateway.core.workflow.*;
import com.nori.ai.gateway.persistence.repository.MessageRepository;
import java.time.ZoneId;
import java.util.*;

public final class WorkflowExecutionAdapter implements com.nori.ai.gateway.core.job.WorkflowExecutor {
    private final CapabilityRegistry registry;private final WorkflowPlanBuilder builder;private final com.nori.ai.gateway.core.workflow.WorkflowExecutor executor;
    private final WorkflowResourceResolverFactory resolvers;private final TimeWindowResolver windows;private final MessageRepository messages;
    private final ExpiryCheckpoint expiry;private final ContentSanitizer sanitizer;private final TerminalOutcomeAssembler assembler;private final ConversationSummaryBuilder summaries;
    private final EvidenceAssembler evidenceAssembler;private final AuditRecorder audit;
    public WorkflowExecutionAdapter(CapabilityRegistry registry,WorkflowPlanBuilder builder,com.nori.ai.gateway.core.workflow.WorkflowExecutor executor,WorkflowResourceResolverFactory resolvers,
            TimeWindowResolver windows,MessageRepository messages,ExpiryCheckpoint expiry,ContentSanitizer sanitizer,TerminalOutcomeAssembler assembler,ConversationSummaryBuilder summaries,EvidenceAssembler evidenceAssembler,AuditRecorder audit) {
        this.registry=registry;this.builder=builder;this.executor=executor;this.resolvers=resolvers;this.windows=windows;this.messages=messages;this.expiry=expiry;this.sanitizer=sanitizer;this.assembler=assembler;this.summaries=summaries;
        this.evidenceAssembler=evidenceAssembler;this.audit=Objects.requireNonNull(audit);
    }
    @Override public TerminalSpec execute(JobContext job) {
        expiry.assertLive(job.conversationExpiresAt(),job.cancellation());
        TaskType task=TaskType.valueOf((String)job.classification().get("taskType"));
        audit.writeForJob(AuditEvent.jobClassified(job.scope(),job.conversationId(),job.jobId(),task));
        var definition=registry.workflow(job.scope(),task).orElse(null);
        if(definition==null)return TerminalSpec.outcome(JobStatus.SUCCEEDED,ResultStatus.UNSUPPORTED,null,Map.of("status",ResultStatus.UNSUPPORTED.name()));
        try{builder.checkOptions(definition,job);}catch(WorkflowPlanBuilder.Blocked blocked){return finishClarification(job,ErrorCode.PLAN_INVALID,List.of("options"));}
        var context=object(job.requestInput().requestFields().get("context"));
        TimeWindowResolver.Resolved resolved;
        try {
            resolved=windows.resolve(job.classification(),context,()->{
                try{return messages.findPreviousResolvedTimeWindow(job.scope(),job.jobId(),job.requestInput().messageId());}
                catch(RuntimeException failure){expiry.assertLive(job.conversationExpiresAt(),job.cancellation());throw failure;}
            },definition);
        }catch(TimeWindowResolver.Failure invalid){return finishClarification(job,invalid.code(),List.of("timeWindow"));}
        if(resolved.window().isPresent()) {
            try {
                if(!messages.updateResolvedTimeWindow(job.scope(),job.jobId(),job.requestInput().messageId(),resolved.values())) {
                    expiry.assertLive(job.conversationExpiresAt(),job.cancellation());throw new IllegalStateException("요청 시간 구간을 저장할 수 없습니다");
                }
            }catch(RuntimeException failure){expiry.assertLive(job.conversationExpiresAt(),job.cancellation());throw failure;}
        }
        var plan=builder.build(definition,registry.view(job.scope()),job,resolved.values());
        ZoneId zone=context.get("timezone") instanceof String value?ZoneId.of(value):null;
        var outcome=executor.run(job,plan,resolvers.forJob(job,zone));
        var spec=assembler.assemble(outcome,ComposerOutcome.NOT_INVOKED,job.inferenceMode());
        return complete(job,spec,outcome,resolved.values(),context);
    }
    private TerminalSpec finishClarification(JobContext job,ErrorCode code,List<String> fields) {
        var spec=TerminalSpec.outcome(JobStatus.FAILED,ResultStatus.FAILED,code,Map.of("clarification",new Clarification(code,fields)));
        return complete(job,spec,null,Map.of(),object(job.requestInput().requestFields().get("context")));
    }
    private TerminalSpec complete(JobContext job,TerminalSpec initial,WorkflowOutcome outcome,Map<String,Object> window,Map<String,Object> inputContext) {
        expiry.assertLive(job.conversationExpiresAt(),job.cancellation());
        boolean clarification=initial.resultPayload().containsKey("clarification");
        var pairs=new ArrayList<TerminalSpec.StoredEvidence>();
        if(outcome!=null&&!clarification) {
            outcome.evidence().forEach(value->pairs.add(new TerminalSpec.StoredEvidence(value,Optional.empty())));
            var documents=new LinkedHashSet<ResultSourceDeps.DocKey>();
            for(var stage:outcome.stages()) {
                var manifest=registry.find(stage.result().capabilityId()).orElse(null);if(manifest==null)continue;
                for(var hit:EvidenceAssembler.projectKnowledge(manifest,stage.result(),job.scope()))documents.add(hit.document());
                EvidenceAssembler.knowledgeEvidence(manifest,stage.result(),job.scope()).forEach(value->pairs.add(new TerminalSpec.StoredEvidence(value.value(),Optional.of(value.source()))));
            }
            if(!documents.equals(new LinkedHashSet<>(outcome.sourceDeps().knowledgeDocuments())))throw new IllegalArgumentException("문서 의존성 연결이 다릅니다");
        }
        var finalEvidence=finalEvidence(job,initial.resultPayload(),pairs);
        var canonical=maskPayload(job,finalEvidence.payload(),outcome);
        var assigned=ResultBlockIds.assign(canonical);
        Map<String,Object> payload=SchemaContracts.JSON.convertValue(assigned,new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
        var deps=initial.resultSourceDeps().isEmpty()?new ResultSourceDeps(List.of(),List.of()):SchemaContracts.JSON.convertValue(initial.resultSourceDeps(),ResultSourceDeps.class);
        var spec=new TerminalSpec(initial.jobStatus(),initial.resultStatus(),initial.errorCode(),initial.eventType(),initial.externalSuppressed(),payload,initial.resultSourceDeps(),initial.terminalCause());
        List<TerminalSpec.StoredEvidence> stored=finalEvidence.values();
        List<String> executions=outcome==null?List.of():outcome.toolExecutionRefs();
        Map<String,Object> context=new LinkedHashMap<>();context.put("ownerSystem",job.scope().boundSystem().name());context.put("selectedResourceRefs",job.requestInput().selectedResourceRefs());
        for(String key:List.of("timezone","referenceTime"))if(inputContext.containsKey(key))context.put(key,inputContext.get(key));
        if(inputContext.containsKey("sourceVersions"))context.put("sourceVersions",WorkflowContent.mask(SchemaContracts.JSON.valueToTree(inputContext.get("sourceVersions")),sanitizer));
        if(!window.isEmpty())context.put("timeWindow",window);
        context.put("jobId",job.jobId());context.put("requestMessageId",job.requestInput().messageId());context.put("jobSeq",job.requestInput().jobSeq());context.put("resultSourceDeps",deps);context.put("knowledgeDocuments",deps.knowledgeDocuments());
        List<ResultBlock> finalBlocks=canonical.has("blocks")?SchemaContracts.JSON.convertValue(canonical.get("blocks"),new tools.jackson.core.type.TypeReference<List<ResultBlock>>(){}):List.of();
        Map<String,Object> summary=summaries.build(job,window,new ConversationSummaryBuilder.TerminalSpecView(spec.resultStatus(),finalBlocks,deps));
        if(payload.containsKey("blocks"))summary.put("blocks",payload.get("blocks"));
        var assistant=new TerminalSpec.AssistantMessage(UUID.randomUUID().toString(),"",job.requestInput().selectedResourceRefs(),executions,job.jobId());
        return spec.withCommitData(new TerminalSpec.CommitData(job.scope(),job.jobId(),job.conversationId(),job.requestInput().messageId(),job.requestInput().jobSeq(),stored,Optional.of(assistant),
            Optional.of(serverMap(context)),Optional.of(serverMap(summary)),deps));
    }
    private record FinalEvidence(tools.jackson.databind.node.ObjectNode payload,List<TerminalSpec.StoredEvidence> values) {}
    private FinalEvidence finalEvidence(JobContext job,Map<String,Object> payload,List<TerminalSpec.StoredEvidence> source) {
        var originals=new LinkedHashMap<String,TerminalSpec.StoredEvidence>();
        for(var pair:source) {
            var previous=originals.putIfAbsent(pair.value().evidenceId(),pair);
            if(previous!=null&&!previous.equals(pair))throw new IllegalArgumentException("중복 근거의 내용이 다릅니다");
        }
        evidenceAssembler.validate(originals.values().stream().map(TerminalSpec.StoredEvidence::value).toList(),job.scope());
        var ids=new LinkedHashMap<String,String>();var values=new ArrayList<TerminalSpec.StoredEvidence>();
        for(var pair:originals.values()) {
            var value=pair.value();
            String id=UUID.randomUUID().toString();ids.put(value.evidenceId(),id);
            values.add(new TerminalSpec.StoredEvidence(new Evidence(id,value.ownerSystem(),value.sourceType(),value.sourceRef(),value.eventTime(),value.observedAt(),value.traceId(),value.deploymentRef(),value.verificationStatus(),value.actorAccessScope()),pair.source()));
        }
        var copy=(tools.jackson.databind.node.ObjectNode)SchemaContracts.JSON.valueToTree(payload);
        if(copy.has("evidence"))copy.set("evidence",SchemaContracts.JSON.valueToTree(values.stream().map(TerminalSpec.StoredEvidence::value).toList()));
        var ui=new com.nori.ai.gateway.core.blocks.UiSchemaValidator();
        if(copy.has("blocks"))for(var block:copy.get("blocks")) {
            if("evidence-link".equals(block.path("type").asString())) {
                var content=(tools.jackson.databind.node.ObjectNode)block.get("content");
                content.put("evidenceId",mappedEvidence(ids,content.path("evidenceId").asString()));
            }
            ui.validate(SchemaContracts.JSON.convertValue(block,ResultBlock.class),job.scope().boundSystem());
        }
        if(copy.has("claims"))for(var claim:copy.get("claims"))rebindReferences(claim.get("evidenceRefs"),ids,job);
        rebindReferences(copy.get("evidenceRefs"),ids,job);rebindReferences(copy.get("citations"),ids,job);
        evidenceAssembler.validate(values.stream().map(TerminalSpec.StoredEvidence::value).toList(),job.scope());
        return new FinalEvidence(copy,List.copyOf(values));
    }
    private String mappedEvidence(Map<String,String> ids,String old) {
        String id=ids.get(old);if(id==null)throw new IllegalArgumentException("근거 참조의 저장 결속을 확인할 수 없습니다");return id;
    }
    private void rebindReferences(tools.jackson.databind.JsonNode references,Map<String,String> ids,JobContext job) {
        if(references==null)return;
        if(!references.isArray())throw new IllegalArgumentException("근거 참조 형식이 잘못되었습니다");
        for(var reference:references) {
            var owned=SchemaContracts.JSON.convertValue(reference,com.nori.ai.gateway.core.scope.OwnedReference.class);
            if(!owned.belongsTo(job.scope().boundSystem()))throw new IllegalArgumentException("근거 소유자를 확인할 수 없습니다");
            var uri=java.net.URI.create(owned.ref());String path=uri.getRawPath();int slash=path.lastIndexOf('/');
            if(!"evidence".equals(uri.getScheme())||slash<0)throw new IllegalArgumentException("근거 식별 형식을 확인할 수 없습니다");
            String id=mappedEvidence(ids,path.substring(slash+1));
            ((tools.jackson.databind.node.ObjectNode)reference).put("ref","evidence://"+uri.getRawAuthority()+path.substring(0,slash+1)+id);
        }
    }
    private tools.jackson.databind.JsonNode maskPayload(JobContext job,tools.jackson.databind.node.ObjectNode original,WorkflowOutcome outcome) {
        var copy=(tools.jackson.databind.node.ObjectNode)WorkflowContent.mask(original,sanitizer);
        if(original.has("evidence"))copy.set("evidence",original.get("evidence").deepCopy());
        if(original.has("claims")) {
            var claims=copy.putArray("claims");for(var value:original.get("claims")){var claim=SchemaContracts.JSON.convertValue(value,Claim.class);claims.add(SchemaContracts.JSON.valueToTree(new Claim(claim.claimId(),claim.verdict(),WorkflowContent.text(claim.text(),sanitizer),claim.evidenceRefs())));}
        }
        for(String field:List.of("citations","evidenceRefs"))if(original.has(field))copy.set(field,original.get(field).deepCopy());
        if(outcome!=null&&original.has("trace"))copy.set("trace",SchemaContracts.JSON.valueToTree(outcome.trace()));
        Map<com.nori.ai.gateway.core.scope.OwnedReference,List<EvidenceAssembler.KnowledgeHit>> documents=new HashMap<>();
        if(outcome!=null)for(var stage:outcome.stages()) {
            var manifest=registry.find(stage.result().capabilityId()).orElse(null);if(manifest==null)continue;
            var hits=EvidenceAssembler.projectKnowledge(manifest,stage.result(),job.scope());
            if(!hits.isEmpty())for(var block:stage.result().blocks())documents.put(block.dataRef(),hits);
        }
        if(original.has("blocks")) {
            var blocks=copy.putArray("blocks");
            for(var value:original.get("blocks")) {
                var block=SchemaContracts.JSON.convertValue(value,ResultBlock.class);var masked=WorkflowContent.block(block,documents.getOrDefault(block.dataRef(),List.of()),"rows",sanitizer);
                if("evidence-link".equals(block.type())) {var content=(tools.jackson.databind.node.ObjectNode)masked.content();content.set("evidenceId",block.content().get("evidenceId"));masked=new ResultBlock(block.type(),block.dataRef(),content);}
                new com.nori.ai.gateway.core.blocks.UiSchemaValidator().validate(masked,job.scope().boundSystem());blocks.add(SchemaContracts.JSON.valueToTree(masked));
            }
        }
        if(original.has("toolSummary"))for(int i=0;i<original.get("toolSummary").size();i++) {
            var source=original.get("toolSummary").get(i);var target=(tools.jackson.databind.node.ObjectNode)copy.get("toolSummary").get(i);
            for(String field:List.of("stepId","capabilityId","observedAt"))if(source.has(field))target.set(field,source.get(field).deepCopy());
        }
        return copy;
    }
    private Map<String,Object> serverMap(Map<String,Object> value){return SchemaContracts.JSON.convertValue(SchemaContracts.JSON.valueToTree(value),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});}
    private Map<String,Object> object(Object value){if(value==null)return Map.of();if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("저장 요청 객체가 잘못되었습니다");var copy=new LinkedHashMap<String,Object>();map.forEach((key,item)->copy.put((String)key,item));return copy;}
}
