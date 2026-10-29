package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.eval.TestSupport.error;

class PerfReporterTest {
    @TempDir Path directory;
    TestSupport f;
    @BeforeEach void setup() throws Exception {f=new TestSupport(directory);Files.createDirectories(directory.resolve("docs/gates/f3"));Files.writeString(directory.resolve("docs/gates/approval.md"),"fixture 승인");}
    Path criteria(String leaf,Map<String,Object> extra) throws Exception {
        Map<String,Object> fields=new LinkedHashMap<>();fields.put("baselineSeq",1);fields.put("approved",true);fields.put("approvalRef","docs/gates/approval.md");fields.put("specSha",f.binding().specSha());fields.put("leafId",leaf);fields.putAll(extra);
        Path path=directory.resolve("docs/gates/f3/perf-proposal.md");Files.writeString(path,"# 승인 기준\n\n```json\n"+Json.canonicalString(Json.MAPPER.valueToTree(fields))+"\n```\n");return path;
    }
    ObjectNode measurement(String stage,String parent,String id,List<Map<String,Object>> samples,Map<String,Object> extra){Map<String,Object> values=new LinkedHashMap<>();values.put("kind","MEASUREMENT");values.put("measurementId",id);values.put("stage",stage);values.put("parentRunId",parent);values.put("runId",UUID.randomUUID().toString());values.put("samples",samples);values.put("buildIdentity",f.identity(stage));values.putAll(extra);return f.record(values);}
    Path sample(ObjectNode record,String name){Path file=directory.resolve("samples/"+name+".json");Json.write(file,record);return file;}
    ObjectNode report(String stage,String leaf,List<Path> files,Path criteria){return new PerfReporter().report(stage,leaf,"p",files,criteria,directory.resolve("out"),directory,f.binding(),f.catalog,f.parent(stage,"p"));}
    Map<String,Object> limits(){return Map.of("chunkCount",100L,"indexingMs",100L,"dbBytes",100L,"indexBytes",100L,"memoryBytes",100L);}
    List<Map<String,Object>> f3samples(){return List.of(Map.of("path","VECTOR","round",1,"latencyMs",10),Map.of("path","KEYWORD","round",1,"latencyMs",20));}
    @Test void computesNearestRankOnlyFromMatchingParentRawSamplesAndIndexMeasurement() throws Exception {
        assertThat(PerfReporter.percentile(List.of(1L,2L,3L,4L,5L),95)).isEqualTo(5);assertThat(PerfReporter.percentile(List.of(1L,2L,3L,4L),50)).isEqualTo(2);
        Path criteria=criteria("AC-23-PERF",Map.of("thresholds",Map.of("VECTOR",Map.of("minSamples",1,"p95MaxMs",30),"KEYWORD",Map.of("minSamples",1,"p95MaxMs",30)),"resourceMax",limits()));
        Path search=sample(measurement("F3","p","f3:search-latency-samples",f3samples(),Map.of()),"search");Path index=sample(measurement("F3","p","f3:index-resources",List.of(),Map.of("resources",limits())),"index");Path other=sample(measurement("F3","other-parent","f3:search-latency-samples",List.of(Map.of("path","VECTOR","round",1,"latencyMs",9999)),Map.of()),"other");
        ObjectNode result=report("F3","AC-23-PERF",List.of(directory.resolve("samples")),criteria);assertThat(result.path("result").asText()).isEqualTo("PASS");assertThat(result.path("metrics").path("VECTOR").path("p95Ms").asInt()).isEqualTo(10);assertThat(result.path("parentRunId").asText()).isEqualTo("p");
        assertThat(search).exists();assertThat(index).exists();assertThat(other).exists();
    }
    @Test void absentCriteriaProducesNoRecordAndMissingResourceEvidenceNeverPasses() throws Exception {
        Path search=sample(measurement("F3","p","f3:search-latency-samples",f3samples(),Map.of()),"search");assertThat(report("F3","AC-23-PERF",List.of(search),directory.resolve("absent.md"))).isNull();
        Path criteria=criteria("AC-23-PERF",Map.of("thresholds",Map.of("VECTOR",Map.of("minSamples",1,"p95MaxMs",30),"KEYWORD",Map.of("minSamples",1,"p95MaxMs",30)),"resourceMax",limits()));
        assertThat(report("F3","AC-23-PERF",List.of(search),criteria).path("result").asText()).isEqualTo("NOT_RUN");
        Path index=sample(measurement("F3","p","f3:index-resources",List.of(),Map.of("resources",Map.of("chunkCount",101,"indexingMs",1,"dbBytes",1,"indexBytes",1,"memoryBytes",1))),"index");
        assertThat(report("F3","AC-23-PERF",List.of(search,index),criteria).path("result").asText()).isEqualTo("FAIL");
    }
    @Test void rejectsHistogramAverageAndNonIntegerRawSampleInputs() throws Exception {
        Path criteria=criteria("AC-23-PERF",Map.of("thresholds",Map.of("VECTOR",Map.of("minSamples",1,"p95MaxMs",30)),"resourceMax",limits()));
        for(Map<String,Object> extra:List.of(Map.<String,Object>of("average",10),Map.<String,Object>of("histogram",List.of(1,2)))) {
            Path file=sample(measurement("F3","p","f3:search-latency-samples",f3samples(),extra),"invalid");error(()->report("F3","AC-23-PERF",List.of(file),criteria),2,"PERF_RAW_SAMPLES_REQUIRED");
        }
        Path file=sample(measurement("F3","p","f3:search-latency-samples",List.of(Map.of("path","VECTOR","round",1,"latencyMs",1.5)),Map.of()),"fraction");error(()->report("F3","AC-23-PERF",List.of(file),criteria),2,"PERF_RAW_SAMPLES_REQUIRED");
    }
    List<Map<String,Object>> f4samples(boolean candidate){List<Map<String,Object>> samples=new ArrayList<>();for(String variant:candidate?List.of("A","B"):List.of("B"))for(String path:List.of("UNSUPPORTED","SIMPLE","INCIDENT"))for(int i=1;i<=20;i++)samples.add(Map.of("variant",variant,"path",path,"round",i,"fixtureId","fixture-"+i,"queueWaitMs",5,"latencyMs",variant.equals("A")?10:100,"terminalStatus",i==20?"FAILED":"SUCCEEDED"));return samples;}
    Map<String,Object> population(boolean candidate){return Map.of("acceptedCount",candidate?120:60,"preStartTerminalCount",0,"rejectedCount",0,"terminalCounts",Map.of("SUCCEEDED",candidate?114:57,"FAILED",candidate?6:3,"PARTIAL",0,"CANCELLED",0,"TIMED_OUT",0));}
    Map<String,Object> f4thresholds(boolean candidate){Map<String,Object> thresholds=new LinkedHashMap<>();for(String variant:candidate?List.of("A","B"):List.of("B"))for(String route:List.of("UNSUPPORTED","SIMPLE","INCIDENT"))thresholds.put(variant+":"+route,Map.of("minSamples",20,"p95MaxMs",50));return thresholds;}
    @Test void keepsCandidateAndBaselinePopulationsSeparateAndDoesNotFailBaselineBudgetOverrun() throws Exception {
        Path criteria=criteria("AC-24-PERF",Map.of("thresholds",f4thresholds(true),"candidateA",true,"f0AccuracyQualified",true));Path file=sample(measurement("F4","p","f4:job-latency-samples",f4samples(true),population(true)),"jobs");ObjectNode result=report("F4","AC-24-PERF",List.of(file),criteria);
        assertThat(result.path("result").asText()).isEqualTo("PASS");assertThat(result.path("decision").asText()).isEqualTo("PROMOTE_A");assertThat(result.path("metrics").path("A:SIMPLE").path("p95Ms").asInt()).isEqualTo(10);assertThat(result.path("metrics").path("B:SIMPLE").path("p95Ms").asInt()).isEqualTo(100);
        ObjectNode changed=Json.read(file).deepCopy();changed.put("preStartTerminalCount",1);EvidenceRecord.seal(changed);Json.write(file,changed);assertThat(report("F4","AC-24-PERF",List.of(file),criteria).path("result").asText()).isEqualTo("NOT_RUN");
    }
    @Test void loadRequiresBackfillResourcesZeroErrorsAndFixedQueryRankEquality() throws Exception {
        Map<String,Object> maxima=Map.of("backfillMaxTxMs",100,"lockWaitMs",100,"poolWaitMs",100,"cpuMillis",100,"ioBytes",100);
        Path criteria=criteria("AC-23-LOAD",Map.of("thresholds",f4thresholds(false),"resourceMax",maxima));Map<String,Object> extra=new LinkedHashMap<>(population(false));extra.putAll(Map.of("backfillActive",true,"resources",maxima,"queueTimeoutCount",0,"workerInterruptedCount",0,"fixedQueryExpectedSha256","a".repeat(64),"fixedQueryActualSha256","a".repeat(64)));
        Path file=sample(measurement("F4","p","f4:job-latency-samples",f4samples(false),extra),"load");
        assertThat(report("F4","AC-23-LOAD",List.of(file),criteria).path("result").asText()).isEqualTo("FAIL");
        JsonNode base=Json.parse(Files.readString(criteria).split("```json\n",2)[1].split("```",2)[0].getBytes(java.nio.charset.StandardCharsets.UTF_8));ObjectNode updated=base.deepCopy();for(JsonNode threshold:updated.path("thresholds"))((ObjectNode)threshold).put("p95MaxMs",150);Json.write(criteria,updated);
        assertThat(report("F4","AC-23-LOAD",List.of(file),criteria).path("result").asText()).isEqualTo("PASS");
        ObjectNode missing=Json.read(file).deepCopy();missing.remove("fixedQueryActualSha256");EvidenceRecord.seal(missing);Json.write(file,missing);assertThat(report("F4","AC-23-LOAD",List.of(file),criteria).path("result").asText()).isEqualTo("NOT_RUN");
        missing.put("fixedQueryActualSha256","b".repeat(64));EvidenceRecord.seal(missing);Json.write(file,missing);assertThat(report("F4","AC-23-LOAD",List.of(file),criteria).path("result").asText()).isEqualTo("FAIL");
    }
}
