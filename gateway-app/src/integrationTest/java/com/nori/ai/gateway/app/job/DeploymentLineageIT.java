package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.*;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.tcmock.MockDelegationRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DeploymentLineageIT extends P09QueueSupport {
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,WorkflowSourceFixture.class,WorkflowSourceFixture.GitConfiguration.class};}
    @Override protected VerifiedSystemScope scope(String actor){var original=super.scope(actor);return new VerifiedSystemScope(original.boundSystem(),original.authenticatedServiceRef(),original.actorRef(),original.sub(),Set.of("EQP_READ","DLQ_READ","AI_LOG_READ","AI_TRACE_READ","AI_CODE_READ","AI_DOC_READ"),original.pdg(),original.issuedAt(),original.expiresAt(),original.requestId());}
    private WorkflowSourceFixture.Server server(){return context.getBean(WorkflowSourceFixture.Server.class);}
    private tools.jackson.databind.JsonNode payload(String job){return json.readTree(jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:job").param("job",job).query(String.class).single());}
    private tools.jackson.databind.JsonNode git(String job){return java.util.stream.StreamSupport.stream(payload(job).path("toolSummary").spliterator(),false).filter(stage->stage.path("stepId").asString().equals("git")).findFirst().orElseThrow();}
    @Test void actualTempoVersionsResolveTheirOwnSiblingCommitsAndUseTheSameLineageToSearchHistoricalRefs() throws Exception {
        var server=server();server.logText="EQP-001 EQUIPMENT_ERROR";server.traceVersions=List.of("123","124","123");var accepted=incident("verified-lineages",Set.of());await(()->state(accepted.jobId()).equals("SUCCEEDED"));
        String first=WorkflowSourceFixture.Server.shortSha("123")+"2".repeat(33),second=WorkflowSourceFixture.Server.shortSha("124")+"2".repeat(33);
        assertThat(server.pipelines).containsExactly("123","124");assertThat(server.lineageTraces).hasValue(2);assertThat(server.commits).hasValue(2);assertThat(server.codeRefs).containsExactly(first,first,second).doesNotContain("main");assertThat(git(accepted.jobId()).path("status").asString()).isEqualTo("SUCCESS");
        String raw=jdbc().sql("SELECT payload::text FROM gw.tool_execution WHERE job_id=:job AND capability_id='tc.git.searchCode'").param("job",accepted.jobId()).query(String.class).single();assertThat(raw).contains("DEPLOYED_SOURCE_COMMIT",first,second,"span-0","span-1","span-2").doesNotContain("CONFIRMED");
        var deployments=java.util.stream.StreamSupport.stream(payload(accepted.jobId()).path("evidence").spliterator(),false).filter(value->!value.path("deploymentRef").isMissingNode()&&!value.path("deploymentRef").isNull()).toList();assertThat(deployments).hasSize(3);
        assertThat(deployments.stream().map(value->value.path("deploymentRef").path("ref").asString())).containsExactly("deployment://TC/pipelines/123/repos/nori-tc-gateway/commits/"+first,"deployment://TC/pipelines/123/repos/nori-tc-gateway/commits/"+first,"deployment://TC/pipelines/124/repos/nori-tc-gateway/commits/"+second);
    }
    @Test void unknownVersionsUseOnlyApprovedRefsAndCommit503NeverStartsFallbackCode() throws Exception {
        var server=server();server.logText="EQP-001 EQUIPMENT_ERROR";server.traceVersions=List.of("image-tag");var unknown=incident("unknown-version",Set.of());await(()->state(unknown.jobId()).equals("SUCCEEDED"));assertThat(server.pipelines).isEmpty();assertThat(server.codeRefs).containsExactly("main");assertThat(payload(unknown.jobId()).toString()).contains("CURRENT_APPROVED_REF","UNKNOWN","missingEvidence").doesNotContain("CONFIRMED");
        server.codeRefs.clear();server.traceVersions=List.of("123");server.commitStatus=503;var unavailable=incident("unavailable-lineage",Set.of());await(()->state(unavailable.jobId()).equals("PARTIAL"));assertThat(git(unavailable.jobId()).path("status").asString()).isEqualTo("SOURCE_UNAVAILABLE");assertThat(server.commits).hasValue(2);assertThat(server.codeRefs).isEmpty();
    }
    @Test void cacheHitKeepsSpecTraceMetadataForLineageAndStoresOnlyMaskedFreeText() throws Exception {
        // mask-v1 전화 규칙과 겹치는 합법 hex32 traceId·hex16 spanId·numeric pipeline version입니다.
        String prefix="aaaa01012345678"+"b".repeat(13),traceId=prefix+"0001",spanId="ab01012345678cde",version="1012345678";
        var sanitizer=new com.nori.ai.gateway.core.sanitize.ContentSanitizer();
        for(String value:List.of(traceId,spanId,version))assertThat(sanitizer.sanitize(value).text()).isNotEqualTo(value);
        var server=server();server.tracePrefix=prefix;server.spanIds=List.of(spanId);server.traceVersions=List.of(version);
        server.logText="EQP-001 EQUIPMENT_ERROR SENTINEL:cache-raw operator@example.com";
        var first=incidentIn("cache-mask-first",null);await(()->state(first.jobId()).equals("SUCCEEDED"));
        int traces=server.traces.get();var pipelines=List.copyOf(server.pipelines);var refs=List.copyOf(server.codeRefs);var deployments=deployments(first.jobId());
        String cached=jdbc().sql("SELECT string_agg(value::text, ' ') FROM gw.cache_entry WHERE conversation_id=:conversation").param("conversation",first.conversationId()).query(String.class).single();
        assertThat(cached).doesNotContain("SENTINEL:cache-raw","operator@example.com").contains(traceId,spanId,version);
        server.pipelines.clear();server.codeRefs.clear();
        var second=incidentIn("cache-mask-second",first.conversationId());await(()->state(second.jobId()).equals("SUCCEEDED"));
        assertThat(server.traces).hasValue(traces);
        assertThat(pipelines).containsExactly(version);assertThat(server.pipelines).isEqualTo(pipelines);
        assertThat(refs).isNotEmpty().allSatisfy(ref->assertThat(ref).matches("[0-9a-f]{40}"));assertThat(server.codeRefs).isEqualTo(refs);
        assertThat(deployments).isNotEmpty().allSatisfy(value->assertThat(value.getLast()).isEqualTo(traceId));
        assertThat(deployments(second.jobId())).isEqualTo(deployments);
    }
    private JobAdmissionService.Accepted incidentIn(String key,String conversation){
        hooks().arm("actor",key,new TestHookRegistry.Scenario(TaskType.INCIDENT_ANALYSIS,Set.of(),null,false));
        return inRequestSpan(()->context.getBean(JobAdmissionService.class).accept(scope("actor"),new JobAdmissionService.AdmissionRequest("/v1/query",conversation,"장애 조회",workflowFields(),key,registeredDlg("actor"))));
    }
    private List<List<String>> deployments(String job){
        return java.util.stream.StreamSupport.stream(payload(job).path("evidence").spliterator(),false).filter(value->!value.path("deploymentRef").isMissingNode()&&!value.path("deploymentRef").isNull())
            .map(value->List.of(value.path("deploymentRef").path("ref").asString(),value.path("traceId").asString())).toList();
    }
    @Test void currentDenialDuringLineagePreservesDeniedAndDoesNotUseAnApprovedRefFallback() throws Exception {
        var server=server();server.logText="EQP-001 EQUIPMENT_ERROR";server.traceVersions=List.of("123");server.afterHttpStop=path->{if(path.contains("/pipelines/")){String delegation=observations().workflows.getLast().delegation();context.getBean(MockDelegationRegistry.class).revoke(MockDelegationRegistry.hash(delegation));}};
        var accepted=incident("denied-lineage",Set.of());await(()->state(accepted.jobId()).equals("PARTIAL"));assertThat(git(accepted.jobId()).path("status").asString()).isEqualTo("DENIED");assertThat(server.pipelines).containsExactly("123");assertThat(server.lineageTraces).hasValue(0);assertThat(server.commits).hasValue(0);assertThat(server.codeRefs).isEmpty();
    }
}
