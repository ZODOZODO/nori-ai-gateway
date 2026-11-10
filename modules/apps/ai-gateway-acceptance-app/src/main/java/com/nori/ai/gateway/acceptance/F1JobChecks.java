package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.loki.*;
import com.nori.ai.gateway.acceptance.loki.LokiDirectQuery;
import com.nori.ai.gateway.tcmock.verify.ResidueScanner;
import java.time.*;
import java.util.*;
import org.opentest4j.TestAbortedException;
import static com.nori.ai.gateway.acceptance.F1SecurityChecks.*;

/** F1은 실제 Job API와 저장된 실행 관측을 대조합니다. 테스트 하네스가 기대 결과를 제품에 주입하지 않습니다. */
public final class F1JobChecks {
    public static final Instant MOCK_NOW=Instant.parse("2026-11-06T00:00:00Z");
    private final GatewayClient gateway;private final HookClient hooks;private final OracleQueries oracle;
    private final Optional<LokiDirectQuery> loki;private final F1SecurityChecks.ResidualCheck residual;
    private Set<String> submittedSentinels=Set.of();
    public F1JobChecks(GatewayClient gateway,HookClient hooks,OracleQueries oracle,Optional<LokiDirectQuery> loki,F1SecurityChecks.ResidualCheck residual){this.gateway=gateway;this.hooks=hooks;this.oracle=oracle;this.loki=loki;this.residual=residual;}
    public void run(String leaf) {
        require(oracle.nonTerminalJobCount()==0,"ISOLATION_NOT_IDLE");hooks.reset();
        try {
            if(leaf.startsWith("AC-17-")&&!leaf.equals("AC-17-VERSION")){availability(leaf);return;}
            if(leaf.startsWith("AC-46-")||leaf.equals("AC-41")){hooks.clock(Map.of("op","FREEZE_AT","at",MOCK_NOW.toString()));}
            switch(leaf) {
                case "AC-17-VERSION" -> version();
                case "AC-21-TOOL" -> owner();
                case "AC-21-SOURCE" -> source();
                case "AC-41" -> residue();
                case "AC-46-EMPTY","AC-46-HIT","AC-46-TRIM" -> completeness(leaf);
                case "AC-36","AC-45","ISO-15-QUERY","ISO-15-WINDOW" -> logs(leaf);
                default -> throw new IllegalArgumentException("F1_JOB_LEAF_INVALID");
            }
        } finally {if(oracle.nonTerminalJobCount()==0)hooks.clock(Map.of("op","RESUME_SYSTEM"));}
    }
    private void scenario(String id){hooks.post("/__test/tcmock/scenario",Map.of("id",id),204);require(hooks.get("/__test/tcmock/scenario").path("id").asText().equals(id),"F1_SCENARIO_NOT_SELECTED");}
    private JsonNode submit(String fixture,String task,Instant from,Instant to,Map<String,Object> seam,String dlg) {
        String request=fixture+"-"+UUID.randomUUID();String key=GatewayClient.idempotencyKey(request);
        var scenario=new LinkedHashMap<String,Object>();scenario.put("taskType",task);if(!seam.isEmpty())scenario.put("seam",seam);
        hooks.arm("f1-acceptance",key,CanonicalJson.MAPPER.valueToTree(scenario));
        var body=Map.of("query","설비 상태와 통신 로그를 조회해 주세요","context",Map.of("timezone","UTC","referenceTime",hooks.clockNow().toString(),"timeWindow",Map.of("from",from.toString(),"to",to.toString())),"resourceRefs",List.of(Map.of("type","EQP","ref","EQP-001")));
        var submission=gateway.submitObserved("/v1/query",request,F1Fixture.claims(),dlg,json(body));submittedSentinels=submission.sentinels();var admitted=submission.response();require(admitted.statusCode()==202,"F1_JOB_NOT_ACCEPTED");
        String id=parse(admitted.body()).path("jobId").asText();require(id.matches("[A-Za-z0-9_:-]{1,160}"),"F1_JOB_ID_INVALID");
        long deadline=System.nanoTime()+Duration.ofSeconds(200).toNanos();
        do {
            var response=gateway.get("/v1/analyses/"+id,F1Fixture.claims());require(response.statusCode()==200,"F1_JOB_NOT_READABLE");var result=parse(response.body());
            if(!Set.of("QUEUED","RUNNING").contains(result.path("jobStatus").asText())){require(result.hasNonNull("result"),"F1_RESULT_MISSING");require(oracle.resultRowCount(id)==1,"F1_RESULT_NOT_UNIQUE");return result;}
            try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("F1_WAIT_INTERRUPTED");}
        }while(System.nanoTime()<deadline);throw new AssertionError("F1_JOB_TIMEOUT");
    }
    private JsonNode submit(String fixture,String task,Map<String,Object> seam){Instant now=hooks.clockNow();return submit(fixture,task,now.minusSeconds(1800),now.minusNanos(1),seam,gateway.newDlg());}
    private static String job(JsonNode response){return response.path("jobId").asText();}
    private static JsonNode payload(JsonNode response){return response.path("result").path("payload");}
    private OracleQueries.ToolObservation only(JsonNode response,String capability){var records=oracle.tools(job(response),capability);require(records.size()==1,"F1_TOOL_EXECUTION_COUNT");return records.getFirst();}
    private long calls(String operation){return hooks.get("/__test/tcmock/calls").path(operation).asLong();}
    private void availability(String leaf){String kind=switch(leaf){case "AC-17-P"->"PLANNED";case "AC-17-U"->"UNSUPPORTED";case "AC-17-D"->"DISABLED";case "AC-17-PHASE"->"PHASE_INACTIVE";default->throw new IllegalArgumentException("F1_AVAILABILITY_INVALID");};
        var response=submit(leaf,"LIVE_QUERY",Map.of("kind",kind,"capabilityId","tc.eqp.getOperationalSnapshot"));
        require(response.path("jobStatus").asText().equals("SUCCEEDED")&&response.path("result").path("status").asText().equals("UNSUPPORTED"),"F1_AVAILABILITY_STATUS");
        int sixth=0;for(var trace:payload(response).path("validationTrace"))if(trace.path("step").asInt()==6){sixth++;require(!trace.path("passed").asBoolean()&&trace.path("reason").asText().equals(kind),"F1_AVAILABILITY_REASON");}
        require(sixth==1&&calls("getEqpSnapshot")==0&&calls("getCommLogs")==0,"F1_AVAILABILITY_BOUNDARY");require(oracle.tools(job(response),"tc.eqp.getOperationalSnapshot").isEmpty(),"F1_AVAILABILITY_EXECUTED");
    }
    private void version(){scenario("contract-v2");var response=submit("version","LIVE_QUERY",Map.of());var tool=only(response,"tc.eqp.getOperationalSnapshot");
        require(tool.status().equals("FAILED")&&tool.errorCode().equals("CONTRACT_INVALID"),"F1_VERSION_STATUS");require(tool.blockCount()+tool.evidenceCount()+tool.claimCount()+tool.citationCount()+tool.composerInputCount()==0,"F1_VERSION_EXPOSED_RESULT");require(calls("getEqpSnapshot")==1,"F1_VERSION_NOT_EXECUTED");}
    private void owner(){var response=submit("owner","INCIDENT_ANALYSIS",Map.of("kind","DISPATCH_OWNER_OVERRIDE","capabilityId","tc.commLogs.search","ownerSystem","WM"));require(calls("getEqpSnapshot")==1&&calls("getCommLogs")==0,"F1_DISPATCH_FIXTURE_INVALID");require(only(response,"tc.eqp.getOperationalSnapshot").status().equals("SUCCESS"),"F1_FIRST_TOOL_NOT_SUCCESS");require(oracle.ownerBlockCount(job(response))==1&&oracle.tools(job(response),"tc.commLogs.search").isEmpty(),"F1_DISPATCH_OWNER_BOUNDARY");}
    private void source(){var response=submit("source","LIVE_QUERY",Map.of("kind","SOURCE_ALLOWLIST_REF_OVERRIDE","capabilityId","tc.eqp.getOperationalSnapshot","ownerSystem","WM"));require(oracle.sourceBlockCount(job(response))==1&&calls("getEqpSnapshot")==0,"F1_SOURCE_OWNER_BOUNDARY");}
    private void residue(){String dlg=gateway.newDlg();var response=submit("residue","LIVE_QUERY",MOCK_NOW.minusSeconds(60),MOCK_NOW,Map.of(),dlg);require(response.path("jobStatus").asText().equals("SUCCEEDED"),"F1_RESIDUE_POSITIVE_JOB");var sentinels=submittedSentinels;require(oracle.jobDelegationNonNull()==0&&oracle.residueScan(sentinels).matchCount()==0,"F1_DB_RESIDUE");require(ResidueScanner.scan(Map.of("response",response.toString()),sentinels).matchCount()==0,"F1_RESPONSE_RESIDUE");residual.verify(sentinels);}
    private void completeness(String leaf){scenario(leaf.equals("AC-46-EMPTY")?"empty":leaf.equals("AC-46-TRIM")?"comm-trim":"default");var response=submit(leaf,"INCIDENT_ANALYSIS",MOCK_NOW.minusSeconds(1800).plusNanos(1),MOCK_NOW.minusNanos(1),Map.of(),gateway.newDlg());var tool=only(response,"tc.commLogs.search");
        require(tool.sources().size()==2,"F1_COMM_SOURCE_COUNT");require(!response.path("result").path("errorCode").asText().equals("LLM_UNAVAILABLE"),"F1_DISABLED_COMPOSER_ERROR");
        for(var value:tool.sources())require(value.warnings().contains("RETENTION_UNVERIFIED"),"F1_COMM_WARNING_MISSING");
        if(leaf.equals("AC-46-TRIM")){var value=tool.sources().getFirst();require(value.count()>=value.maximum()&&value.oldest().isPresent()&&value.applied()&&value.deletionPathsExcluded(),"F1_TRIM_FIXTURE_INVALID");
            Instant from=MOCK_NOW.minusSeconds(1800).plusNanos(1),to=value.oldest().orElseThrow();require(value.unverified().equals(List.of(new OracleQueries.Range(from,to)))&&value.missing().isEmpty()&&!value.status().equals("OUT_OF_RETENTION"),"F1_TRIM_RANGE_INVALID");
        }else{String status=leaf.equals("AC-46-EMPTY")?"EMPTY":"SUCCESS";require(tool.status().equals(status),"F1_COMM_TOOL_STATUS");for(var value:tool.sources())require(value.status().equals(status)&&(status.equals("EMPTY")?value.itemCount()==0:value.itemCount()>0),"F1_COMM_OBSERVATION_INVALID");
            require(response.path("jobStatus").asText().equals("SUCCEEDED")&&response.path("result").path("status").asText().equals("SUCCESS"),"F1_COMM_AGGREGATION");byte[] narrative=narrative(response);require(narrative.length==0,"F1_NARRATIVE_PRESENT");F1RubricTargets.add(leaf,leaf.equals("AC-46-EMPTY")?"f1-retention-empty":"f1-retention-hit",job(response),narrative);}
    }
    public static byte[] narrative(JsonNode response){var payload=payload(response);if(payload.hasNonNull("answer")&&!payload.path("answer").asText().isEmpty())return payload.path("answer").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8);for(var block:payload.path("blocks"))if(block.path("type").asText().equals("text"))return block.path("content").path("text").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8);return new byte[0];}
    private void logs(String leaf){if(leaf.equals("ISO-15-WINDOW"))throw new TestAbortedException("MAX_WINDOW_UNDEFINED");hooks.clock(Map.of("op","RESUME_SYSTEM"));Instant now=hooks.clockNow();Instant from=now.minusSeconds(3600),to=now;var capturedBoundaries=new LinkedHashMap<String,Instant>();
        if(!leaf.equals("ISO-15-QUERY")){var boundaries=new ArrayList<Instant>();for(String app:LokiSourcePolicy.APPS){var probe=oracle.retentionProbe(app);if(probe.isEmpty()||probe.get().verifiedOldestAt().isEmpty())throw new TestAbortedException("MAX_WINDOW_UNDEFINED");boundaries.add(probe.get().verifiedOldestAt().orElseThrow());capturedBoundaries.put(app,probe.get().verifiedOldestAt().orElseThrow());}
            Instant boundary=Collections.min(boundaries);from=leaf.equals("AC-36")?boundary.minusSeconds(60):leaf.equals("AC-45")?boundary.minusSeconds(1):boundary;to=leaf.equals("AC-36")?boundary:now;}
        if(leaf.startsWith("ISO-15-")){var direct=loki.orElseThrow(()->new TestAbortedException("LOKI_POSITIVE_CONTROL_UNAVAILABLE"));int tc=0,wm=0;var window=new TimeWindow(from,to);for(String app:LokiSourcePolicy.APPS)tc+=direct.measure(app,window).count();for(String app:List.of("wm-ui-backend","wm-ui-front"))wm+=direct.measure(app,window).count();if(tc==0||wm==0)throw new TestAbortedException("LOKI_POSITIVE_CONTROL_EMPTY");}
        for(String app:LokiSourcePolicy.APPS)if(!capturedBoundaries.containsKey(app))capturedBoundaries.put(app,oracle.retentionProbe(app).flatMap(OracleQueries.RetentionProbe::verifiedOldestAt).orElse(from));
        var response=submit(leaf,"INCIDENT_ANALYSIS",from,to,Map.of(),gateway.newDlg());var tool=only(response,"tc.logs.search");
        if(leaf.equals("AC-36"))require(tool.status().equals("OUT_OF_RETENTION")&&tool.itemCount()==0,"F1_RETENTION_OUTSIDE");
        else if(leaf.equals("AC-45")){require(tool.status().equals("PARTIAL"),"F1_RETENTION_CROSSING");Instant boundary=Collections.max(capturedBoundaries.values());Instant end=boundary.isBefore(to)?boundary:to;require(tool.missing().equals(List.of(new OracleQueries.Range(from,end))),"F1_RETENTION_MISSING_RANGE");}
        else{require(Set.of("SUCCESS","EMPTY","PARTIAL").contains(tool.status()),"F1_LOG_TOOL_STATUS");require(LokiSourcePolicy.APPS.containsAll(tool.apps()),"F1_WM_LOG_LEAK");}
        // 실제 Gateway 송신의 건수/창/selector 관측 없이 결과만으로 그 사실을 추정하지 않습니다.
        JsonNode observation;try{observation=hooks.get("/__test/source-observations/"+job(response));}catch(HookClient.HookFailure unavailable){throw new TestAbortedException("SOURCE_REQUEST_OBSERVATION_UNAVAILABLE");}
        F1SourceChecks.verify(observation,job(response),F1SourceChecks.expected(capturedBoundaries,from,to,new LokiProperties().splitInterval()),leaf.equals("AC-36"));
        if(leaf.startsWith("ISO-15-"))F1SourceChecks.assertReferences(payload(response));
    }
}
