package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import java.net.http.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

class WorkflowKnowledgeIT extends P09QueueSupport {
    static final String SOURCE="knowledge://TC/docs/a";
    static final class Documents {
        volatile KnowledgeDocumentStateReader.State state=KnowledgeDocumentStateReader.State.AVAILABLE;
        volatile List<Map<String,Object>> hits=List.of(hit("TC",SOURCE,"a:0","v1"),hit("TC",SOURCE,"a:1","v1"),hit("TC","knowledge://TC/docs/b","b:0","v9"));
        volatile ToolResultEnvelope original;
        final List<com.nori.ai.gateway.core.plan.PlanStep> calls=new CopyOnWriteArrayList<>();
        final List<EvidenceSourceRef> reads=new CopyOnWriteArrayList<>();
        static Map<String,Object> hit(String owner,String source,String chunk,String revision){return Map.of("ownerSystem",owner,"sourceRef",source,"chunkId",chunk,"revision",revision,"text","검증된 문서 근거");}
    }
    @TestConfiguration(proxyBeanMethods=false) static class KnowledgePorts {
        @Bean Documents workflowDocuments(){return new Documents();}
        @Bean ToolExecutor implementedKnowledgeTestTool(Documents documents,Clock clock){return new ToolExecutor(){
            public ToolKind toolKind(){return ToolKind.KNOWLEDGE;}
            public ToolResultEnvelope execute(com.nori.ai.gateway.core.plan.PlanStep step,CapabilityManifest manifest,VerifiedSystemScope scope,ExecutionContext context){documents.calls.add(step);documents.original=ToolResults.result(manifest,scope,documents.hits.isEmpty()?ResultStatus.EMPTY:ResultStatus.SUCCESS,documents.hits,clock.instant(),List.of(),Set.of(),List.of(),"envelope-revision");return documents.original;}
        };}
        @Bean static BeanPostProcessor knowledgeContractFixture(ObjectProvider<Documents> documents,ObjectProvider<ManifestLoader> loader) {return new BeanPostProcessor(){
            @Override public Object postProcessAfterInitialization(Object bean,String name) {
                if(bean instanceof CapabilityRegistry) {
                    var manifests=loader.getObject().loadClasspath("manifests/tc/index.json").stream().map(manifest->{
                        if(manifest.toolKind()!=ToolKind.KNOWLEDGE)return manifest;
                        var tree=(tools.jackson.databind.node.ObjectNode)SchemaContracts.JSON.valueToTree(manifest);tree.put("availability","IMPLEMENTED");return loader.getObject().decode(tree);
                    }).toList();
                    return new CapabilityRegistry(manifests,loader.getObject().loadWorkflowsClasspath("workflows/tc/index.json"),Set.of(ImplementationPhase.PHASE_1),new ContractVersionPolicy());
                }
                if(bean instanceof FailClosedKnowledgeDocumentStateReader)return (KnowledgeDocumentStateReader)(permissions,keys)->{
                    var states=new HashMap<ResultSourceDeps.DocKey,KnowledgeDocumentStateReader.State>();keys.forEach(key->states.put(key,documents.getObject().state));return states;
                };
                if(bean instanceof FailClosedEvidenceSourceReader)return (EvidenceSourceReader)(scope,permissions,ref)->{
                    var fixture=documents.getObject();fixture.reads.add(ref);
                    boolean exact=fixture.hits.stream().anyMatch(hit->hit.get("ownerSystem").equals(ref.ownerSystem().name())&&hit.get("sourceRef").equals(ref.sourceRef())&&hit.get("chunkId").equals(ref.chunkId())&&hit.get("revision").equals(ref.expectedRevision()));
                    return exact&&fixture.state==KnowledgeDocumentStateReader.State.AVAILABLE?new EvidenceSourceReader.Found("검증된 문서 근거"):new EvidenceSourceReader.Unavailable();
                };
                return bean;
            }
        };}
    }
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,KnowledgePorts.class,WorkflowSourceFixture.class};}
    @Override protected VerifiedSystemScope scope(String actor){var original=super.scope(actor);return new VerifiedSystemScope(original.boundSystem(),original.authenticatedServiceRef(),original.actorRef(),original.sub(),Set.of("EQP_READ","AI_DOC_READ"),original.pdg(),original.issuedAt(),original.expiresAt(),original.requestId());}
    @Override protected Map<String,Object> claims(String actor,String delegation){var values=new HashMap<>(super.claims(actor,delegation));values.put("perm",List.of("EQP_READ","AI_DOC_READ"));return values;}
    private JobAdmissionService.Accepted knowledge(String key) {hooks().arm("actor",key,new TestHookRegistry.Scenario(TaskType.KNOWLEDGE_QUERY,Set.of(),null,false));return accept("actor",key,"문서 조회",null,registeredDlg("actor"));}
    private HttpResponse<String> get(String path)throws Exception {return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path)).header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",signer.sign(claims("actor",null),clock().instant())).GET().build(),HttpResponse.BodyHandlers.ofString());}
    @Test void phoneShapedDocumentRevisionIsExactInEveryTypedStoredProjectionAndFreeTextIsStillMasked() throws Exception {
        String revision="01012345678";var fixture=context.getBean(Documents.class);var hit=new LinkedHashMap<>(Documents.hit("TC",SOURCE,"a:0",revision));hit.put("text","연락 01012345678 token=local-secret user@example.com SENTINEL:document");fixture.hits=List.of(hit);
        var accepted=knowledge("phone-revision");await(()->state(accepted.jobId()).equals("SUCCEEDED"));
        var row=jdbc().sql("SELECT payload::text,result_source_deps::text FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query().singleRow();var result=json.readTree((String)row.get("payload"));assertThat(result.path("blocks").get(0).path("content").path("rows").get(0).path("revision").asString()).isEqualTo(revision);assertThat(result.toString()).doesNotContain("local-secret","user@example.com","SENTINEL:document");
        assertThat(json.readTree((String)row.get("result_source_deps")).path("knowledgeDocuments").get(0).path("revision").asString()).isEqualTo(revision);
        var stored=jdbc().sql("SELECT revision FROM gw.evidence WHERE job_id=:job AND chunk_id='a:0'").param("job",accepted.jobId()).query(String.class).single();assertThat(stored).isEqualTo(revision);
        var source=jdbc().sql("SELECT id,source_ref,chunk_id,revision FROM gw.evidence WHERE job_id=:job AND chunk_id='a:0'").param("job",accepted.jobId()).query().singleRow();assertThat(source.get("source_ref")).isEqualTo(SOURCE);assertThat(source.get("chunk_id")).isEqualTo("a:0");assertThat(source.get("revision")).isEqualTo(revision);
        assertThat(get("/v1/analyses/"+accepted.jobId()+"/evidence/"+source.get("id")+"/source").statusCode()).isEqualTo(200);assertThat(fixture.reads.getLast().expectedRevision()).isEqualTo(revision);
        assertThat(json.readTree(get("/v1/analyses/"+accepted.jobId()).body()).path("result").path("payload").path("blocks").get(0).path("content").path("rows").get(0).path("revision").asString()).isEqualTo(revision);
        var scope=observations().workflows.getLast().scope();var conversations=context.getBean(com.nori.ai.gateway.persistence.repository.ConversationRepository.class);
        for(String body:List.of(conversations.findContext(scope,accepted.conversationId()).orElseThrow(),conversations.findSummary(scope,accepted.conversationId()).orElseThrow()))assertThat(json.readTree(body).path("resultSourceDeps").path("knowledgeDocuments").get(0).path("revision").asString()).isEqualTo(revision);
    }
    @Test void validatedServerMetadataFlowsThroughToolRecordDocKeysStoredEvidenceAndExactSourceRead() throws Exception {
        var accepted=knowledge("metadata");await(()->state(accepted.jobId()).equals("SUCCEEDED"));var fixture=context.getBean(Documents.class);
        var row=jdbc().sql("SELECT payload::text,result_source_deps::text FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query().singleRow();
        var deps=json.readTree((String)row.get("result_source_deps"));assertThat(deps.path("knowledgeDocuments")).hasSize(2);assertThat(deps.toString()).contains("v1","v9").doesNotContain("envelope-revision");
        var evidence=jdbc().sql("SELECT id,source_ref,chunk_id,revision FROM gw.evidence WHERE job_id=:job AND chunk_id IS NOT NULL ORDER BY chunk_id").param("job",accepted.jobId()).query().listOfRows();assertThat(evidence).hasSize(3);
        for(var value:evidence) {assertThat(get("/v1/analyses/"+accepted.jobId()+"/evidence/"+value.get("id")+"/source").statusCode()).isEqualTo(200);}
        assertThat(fixture.reads.stream().map(EvidenceSourceRef::expectedRevision)).containsExactly("v1","v1","v9");
        String raw=jdbc().sql("SELECT payload::text FROM gw.tool_execution WHERE job_id=:job").param("job",accepted.jobId()).query(String.class).single();assertThat(json.readTree(raw).path("result").path("toolExecutionId").asString()).isEqualTo(fixture.original.toolExecutionId());assertThat(json.readTree(raw).path("result").path("sourceRevision").asString()).isEqualTo("envelope-revision");
        assertThat(count("cache_entry")).isZero();assertThat(get("/v1/analyses/"+accepted.jobId()+"/blocks/b0/export").statusCode()).isEqualTo(200);
        fixture.state=KnowledgeDocumentStateReader.State.UNAVAILABLE;int reads=fixture.reads.size();assertDenied(get("/v1/analyses/"+accepted.jobId()));assertDenied(get("/v1/analyses/"+accepted.jobId()+"/evidence/"+evidence.getFirst().get("id")+"/source"));assertDenied(get("/v1/analyses/"+accepted.jobId()+"/blocks/b0/export"));assertThat(fixture.reads).hasSize(reads);
        fixture.state=KnowledgeDocumentStateReader.State.UNKNOWN;assertDenied(get("/v1/analyses/"+accepted.jobId()));
    }
    @Test void foreignMetadataProducesOnlyAMinimalFailedInvocationAndNoStoredRawOrDocumentEvidence() throws Exception {
        var fixture=context.getBean(Documents.class);fixture.hits=List.of(Documents.hit("WM","knowledge://WM/docs/private","secret-chunk","secret-revision"));var accepted=knowledge("foreign-metadata");await(()->state(accepted.jobId()).equals("FAILED"));
        String raw=jdbc().sql("SELECT payload::text FROM gw.tool_execution WHERE job_id=:job").param("job",accepted.jobId()).query(String.class).single();assertThat(raw).doesNotContain("secret-chunk","secret-revision","knowledge://WM");assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence WHERE job_id=:job").param("job",accepted.jobId()).query(Integer.class).single()).isZero();
        assertThat(count("cache_entry")).isZero();assertThat(get("/v1/analyses/"+accepted.jobId()).body()).contains("CONTRACT_INVALID");
    }
    @Test void validZeroHitsRemainEmptyAndEveryInvalidMetadataFieldIsRejectedBeforeTheRawRecord() throws Exception {
        var fixture=context.getBean(Documents.class);fixture.hits=List.of();var empty=knowledge("zero-hit");await(()->state(empty.jobId()).equals("SUCCEEDED"));
        assertThat(jdbc().sql("SELECT status FROM gw.result WHERE job_id=:job").param("job",empty.jobId()).query(String.class).single()).isEqualTo("EMPTY");assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence WHERE job_id=:job AND chunk_id IS NOT NULL").param("job",empty.jobId()).query(Integer.class).single()).isZero();
        int index=0;for(String field:List.of("ownerSystem","sourceRef","chunkId","revision"))for(String kind:List.of("missing","null","empty","wrongType")) {
            var row=new HashMap<String,Object>(Documents.hit("TC",SOURCE,"private-chunk","private-revision"));switch(kind){case "missing"->row.remove(field);case "null"->row.put(field,null);case "empty"->row.put(field,"");case "wrongType"->row.put(field,23);default->throw new AssertionError();}
            fixture.hits=List.of(row);var invalid=knowledge("metadata-invalid-"+index++);await(()->state(invalid.jobId()).equals("FAILED"));
            var raw=jdbc().sql("SELECT payload::text FROM gw.tool_execution WHERE job_id=:job").param("job",invalid.jobId()).query(String.class).single();assertThat(raw).contains("CONTRACT_INVALID").doesNotContain("private-chunk","private-revision",SOURCE,"검증된 문서 근거");assertThat(jdbc().sql("SELECT count(*) FROM gw.evidence WHERE job_id=:job").param("job",invalid.jobId()).query(Integer.class).single()).isZero();
        }
        assertThat(count("cache_entry")).isZero();
    }
    @Test void sourceAndDocumentCurrentPermissionsAreBothRequiredAndTheOriginalExpectedRevisionIsNeverRefreshed() throws Exception {
        var accepted=knowledge("current-and");await(()->state(accepted.jobId()).equals("SUCCEEDED"));var fixture=context.getBean(Documents.class);var original=fixture.hits;
        String evidence=jdbc().sql("SELECT id FROM gw.evidence WHERE job_id=:job AND chunk_id='a:0'").param("job",accepted.jobId()).query(String.class).single();String path="/v1/analyses/"+accepted.jobId()+"/evidence/"+evidence+"/source";
        fixture.hits=List.of(Documents.hit("TC",SOURCE,"a:0","changed"));assertThat(get(path).statusCode()).isEqualTo(404);assertThat(fixture.reads.getLast().expectedRevision()).isEqualTo("v1");fixture.hits=original;assertThat(get(path).statusCode()).isEqualTo(200);
        assertThat(fixture.state).isEqualTo(KnowledgeDocumentStateReader.State.AVAILABLE);int reads=fixture.reads.size();for(String deniedPath:List.of("/v1/analyses/"+accepted.jobId(),path,"/v1/analyses/"+accepted.jobId()+"/blocks/b0/export"))assertDenied(getWithPermissions(deniedPath,Set.of("EQP_READ")));assertThat(fixture.reads).hasSize(reads);
    }
    private HttpResponse<String> getWithPermissions(String path,Set<String> permissions)throws Exception {
        var current=new HashMap<String,Object>(claims("actor",null));current.put("perm",permissions.stream().sorted().toList());return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path)).header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",signer.sign(current,clock().instant())).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private void assertDenied(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);var body=json.readTree(response.body());assertThat(body.properties().stream().map(Map.Entry::getKey)).containsExactlyInAnyOrder("jobId","jobStatus","createdAt","terminalAt","resultAccess");assertThat(body.path("resultAccess").asString()).isEqualTo("DENIED");assertThat(response.body()).doesNotContain("검증된 문서 근거",SOURCE,"a:0");
    }
    @Test void implementedOptionalKnowledgeUsesOnlyRealSummaryTermsAndFalseOrEmptyBindingsNeverCallIt() throws Exception {
        var server=context.getBean(WorkflowSourceFixture.Server.class);server.logText="EQP-001 EQUIPMENT_ERROR";
        var first=incidentDocuments("optional-implemented",workflowFields());await(()->state(first.jobId()).equals("SUCCEEDED"));
        assertThat(jdbc().sql("SELECT count(*) FROM gw.tool_execution WHERE job_id=:job AND capability_id='tc.knowledge.search'").param("job",first.jobId()).query(Integer.class).single()).isOne();
        var actual=context.getBean(Documents.class).calls.getFirst();assertThat(actual.resources()).containsExactly(new com.nori.ai.gateway.core.plan.ResourceRef("EQP","EQP-001"));assertThat(actual.arguments().path("eqp").asString()).isEqualTo("EQP-001");assertThat(actual.arguments().path("q").asString()).contains("EQUIPMENT_ERROR");
        var fields=new LinkedHashMap<String,Object>(workflowFields());fields.put("options",Map.of("allowKnowledge",false));hooks().arm("actor","optional-disabled",new TestHookRegistry.Scenario(TaskType.INCIDENT_ANALYSIS,Set.of(),null,false));
        var blocked=context.getBean(JobAdmissionService.class).accept(scope("actor"),new JobAdmissionService.AdmissionRequest("/v1/query",null,"선택 문서 제외",fields,"optional-disabled",registeredDlg("actor")));await(()->state(blocked.jobId()).equals("SUCCEEDED"));
        var payload=json.readTree(jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:job").param("job",blocked.jobId()).query(String.class).single());assertThat(payload.path("trace").toString()).contains("knowledge","OPTIONAL_STEP_OMITTED");assertThat(jdbc().sql("SELECT count(*) FROM gw.tool_execution WHERE job_id=:job AND capability_id='tc.knowledge.search'").param("job",blocked.jobId()).query(Integer.class).single()).isZero();
        server.logText="EQP-001 정상 관측";var noInput=incidentDocuments("optional-empty",workflowFields());await(()->state(noInput.jobId()).equals("SUCCEEDED"));
        var empty=json.readTree(jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:job").param("job",noInput.jobId()).query(String.class).single());assertThat(empty.path("trace").toString()).contains("knowledge","SKIPPED_NO_INPUT");assertThat(jdbc().sql("SELECT count(*) FROM gw.tool_execution WHERE job_id=:job AND capability_id='tc.knowledge.search'").param("job",noInput.jobId()).query(Integer.class).single()).isZero();
        assertThat(jdbc().sql("SELECT count(*) FROM gw.cache_entry WHERE capability_id='tc.knowledge.search'").query(Integer.class).single()).isZero();
    }
    @Test void falseRequiredKnowledgeStopsBeforeAnyResourceToolOrCacheAndReturnsOnlyClarification() throws Exception {
        hooks().arm("actor","required-disabled",new TestHookRegistry.Scenario(TaskType.KNOWLEDGE_QUERY,Set.of(),null,false));var accepted=context.getBean(JobAdmissionService.class).accept(scope("actor"),new JobAdmissionService.AdmissionRequest("/v1/query",null,"문서 차단",Map.of("options",Map.of("allowKnowledge",false)),"required-disabled",registeredDlg("actor")));await(()->state(accepted.jobId()).equals("FAILED"));
        var payload=json.readTree(jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query(String.class).single());assertThat(payload.properties().stream().map(Map.Entry::getKey)).containsExactly("clarification");assertThat(payload.path("clarification").path("reason").asString()).isEqualTo("PLAN_INVALID");assertThat(calls().authorizationRequests()).containsExactly(Set.of());assertThat(jdbc().sql("SELECT count(*) FROM gw.tool_execution").query(Integer.class).single()).isZero();assertThat(count("cache_entry")).isZero();
    }

    private JobAdmissionService.Accepted incidentDocuments(String key,Map<String,Object> fields) {
        hooks().arm("actor",key,new TestHookRegistry.Scenario(TaskType.INCIDENT_ANALYSIS,Set.of(),null,false));
        return context.getBean(JobAdmissionService.class).accept(scope("actor"),new JobAdmissionService.AdmissionRequest("/v1/query",null,"선택 문서 검색",fields,key,registeredDlg("actor")));
    }

}
