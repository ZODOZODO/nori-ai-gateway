package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.math.BigInteger;
import java.util.*;
import org.opentest4j.TestAbortedException;
import static com.nori.ai.gateway.acceptance.F1SecurityChecks.require;

/** 실제 Job에 결속된 송신 관측을 소비합니다. 선언 파일·정적 selector 호출을 실행 증거로 사용하지 않습니다. */
public final class F1SourceChecks {
    private F1SourceChecks() {}
    public static void verify(JsonNode snapshot,String job,Set<String> expected,boolean zero) {
        require(snapshot.path("jobId").asText().equals(job),"F1_SOURCE_JOB_MISMATCH");
        if(!snapshot.path("status").asText().equals("RECORDED"))throw new TestAbortedException("SOURCE_REQUEST_OBSERVATION_UNAVAILABLE");
        JsonNode selected=null;for(var value:snapshot.path("observations"))if(value.path("capabilityId").asText().equals("tc.logs.search")){require(selected==null,"F1_SOURCE_DUPLICATE");selected=value;}
        if(selected==null)throw new TestAbortedException("SOURCE_REQUEST_OBSERVATION_UNAVAILABLE");
        require(selected.path("status").asText().equals("COMPLETE"),"F1_SOURCE_OBSERVATION_INCOMPLETE");require(selected.path("attempts").isArray()&&selected.path("count").asInt()==selected.path("attempts").size(),"F1_SOURCE_COUNT_INVALID");
        if(zero){require(selected.path("count").asInt()==0,"F1_SOURCE_CALLED_OUTSIDE_RETENTION");return;}
        var actual=new HashSet<String>();var lastOutcomes=new HashMap<String,String>();
        for(var attempt:selected.path("attempts")){String digest=attempt.path("digest").asText();require(digest.matches("[0-9a-f]{64}")&&attempt.path("state").asText().equals("ALLOWLIST"),"F1_SOURCE_SELECTOR_INVALID");String outcome=attempt.path("outcome").asText();require(Set.of("SUCCESS","FAILED").contains(outcome),"F1_SOURCE_ATTEMPT_INCOMPLETE");actual.add(digest);lastOutcomes.put(digest,outcome);}
        require(!expected.isEmpty()&&actual.equals(expected),"F1_SOURCE_WINDOW_DIGEST_MISMATCH");require(lastOutcomes.values().stream().allMatch("SUCCESS"::equals),"F1_SOURCE_LAST_ATTEMPT_FAILED");
    }
    public static Set<String> expected(Map<String,Instant> boundaries,Instant from,Instant to,Duration split) {
        var result=new HashSet<String>();Instant end=to;
        while(end.isAfter(from)){Instant start=end.minus(split);if(start.isBefore(from))start=from;for(var boundary:boundaries.entrySet()){Instant appStart=boundary.getValue().isAfter(start)?boundary.getValue():start;if(appStart.isBefore(end))result.add(digest(boundary.getKey(),appStart,end));}end=start;}
        return Set.copyOf(result);
    }
    public static String digest(String app,Instant from,Instant to){return CanonicalJson.sha256((app+"\ntc\n"+nanos(from)+"\n"+nanos(to)).getBytes(StandardCharsets.UTF_8));}
    private static String nanos(Instant time){return BigInteger.valueOf(time.getEpochSecond()).multiply(BigInteger.valueOf(1_000_000_000L)).add(BigInteger.valueOf(time.getNano())).toString();}
    public static void assertReferences(JsonNode node){
        if(node.isObject())node.fields().forEachRemaining(field->{String key=field.getKey();JsonNode value=field.getValue();if(key.equals("app")&&value.isTextual())require(com.nori.ai.gateway.integration.source.TcSourcePolicy.APPS.contains(value.asText()),"F1_REFERENCE_OUTSIDE_APP");if((key.equals("ownerSystem")||key.equals("systemId"))&&value.isTextual())require(value.asText().equals("TC"),"F1_REFERENCE_OUTSIDE_OWNER");if((key.equals("ref")||key.endsWith("Ref"))&&value.isTextual())require(!value.asText().contains("://WM/")&&!value.asText().contains("://HRM/"),"F1_REFERENCE_OUTSIDE_SOURCE");assertReferences(value);});else if(node.isArray())node.forEach(F1SourceChecks::assertReferences);
    }
}
