package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.*;
import com.nori.ai.gateway.eval.spec.SpecBaseline;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GateEvaluatorTest {
    @TempDir Path directory;
    TestSupport f;
    @BeforeEach void setup() throws Exception { f = new TestSupport(directory); }
    GateEvaluator.GateReport evaluate(String stage, String parent, JsonNode... records) { return new GateEvaluator().evaluate(stage, parent, List.of(records), f.catalog, SpecBaseline.parseChain(f.baseline)); }
    String result(GateEvaluator.GateReport report, String leaf) { return report.leaves().stream().filter(l -> l.leafId().equals(leaf)).findFirst().orElseThrow().result(); }
    @Test void requiresAllComponentsAndPropagatesFailWithoutCreatingAbsentRecords() {
        ObjectNode p = f.parent("F0","p"), a = f.leaf("F0","p","r","AC-37-CMP",0,"PASS","2026-10-29T00:00:01Z"), b = f.leaf("F0","p","r","AC-37-CMP",1,"PASS","2026-10-29T00:00:02Z");
        assertThat(result(evaluate("F0","p",p,a),"AC-37-CMP")).isEqualTo("NOT_RUN");
        assertThat(result(evaluate("F0","p",p,a,b),"AC-37-CMP")).isEqualTo("PASS");
        b.put("result","FAIL"); EvidenceRecord.seal(b); var failed = evaluate("F0","p",p,a,b);
        assertThat(failed.stageParents().get("AC-37")).isEqualTo("FAIL"); assertThat(failed.allParents().get("AC-06")).isEqualTo("NOT_RUN");
        assertThat(List.of(p,a,b)).hasSize(3);
    }
    @Test void completesCurrentF0WithoutRequiringFutureLeaves() {
        List<JsonNode> records = new ArrayList<>(); records.add(f.parent("F0","p"));
        for (JsonNode leaf : f.catalog.required("F0")) for (int i=0;i<f.catalog.components("F0",leaf.path("id").asText()).size();i++) records.add(f.leaf("F0","p","r",leaf.path("id").asText(),i,"PASS","2026-10-29T00:00:01Z"));
        var report = new GateEvaluator().evaluate("F0","p",records,f.catalog,SpecBaseline.parseChain(f.baseline));
        assertThat(report.result()).isEqualTo("PASS"); assertThat(report.allParents().get("AC-38")).isEqualTo("NOT_RUN"); assertThat(report.stageParents().get("AC-38")).isEqualTo("PASS");
    }
    @Test void selectsLastAttemptAndNeverRevivesPreviousPassAfterAbortOrStartedOnly() {
        ObjectNode p = f.parent("F1","p"), done = f.run("F1","p","r1","F1","COMPLETED","2026-10-29T00:00:01Z"), pass = f.leaf("F1","p","r1","AC-36",0,"PASS","2026-10-29T00:00:01Z");
        ObjectNode failRun = f.run("F1","p","r2","F1","COMPLETED","2026-10-29T00:00:02Z"), fail = f.leaf("F1","p","r2","AC-36",0,"FAIL","2026-10-29T00:00:02Z");
        assertThat(result(evaluate("F1","p",p,done,pass,failRun,fail),"AC-36")).isEqualTo("FAIL");
        for (String status : List.of("ABORTED","STARTED")) { failRun.put("status",status); EvidenceRecord.seal(failRun); assertThat(result(evaluate("F1","p",p,done,pass,failRun,fail),"AC-36")).isEqualTo("NOT_RUN"); }
    }
    @Test void comparesInstantsRatherThanVariableWidthFractionStrings() {
        ObjectNode p=f.parent("F1","p"), first=f.run("F1","p","r1","F1","COMPLETED","2026-10-29T00:00:00.100Z"), last=f.run("F1","p","r2","F1","ABORTED","2026-10-29T00:00:00.100001Z"), pass=f.leaf("F1","p","r1","AC-36",0,"PASS","2026-10-29T00:00:00.100Z");
        assertThat(result(evaluate("F1","p",p,first,last,pass),"AC-36")).isEqualTo("NOT_RUN");
    }
    @Test void separatesParentsLabelsAndNonAuthorityProducer() {
        ObjectNode p=f.parent("F0","p2"), a=f.leaf("F0","p1","r1","AC-37-CMP",0,"PASS","2026-10-29T00:00:01Z"), b=f.leaf("F0","p2","r2","AC-37-CMP",1,"PASS","2026-10-29T00:00:02Z");
        assertThat(result(evaluate("F0","p2",p,a,b),"AC-37-CMP")).isEqualTo("NOT_RUN");
        ObjectNode stage=f.parent("F1","p"), done=f.run("F1","p","r","F1","COMPLETED","2026-10-29T00:00:01Z"), pass=f.leaf("F1","p","r","AC-36",0,"PASS","2026-10-29T00:00:01Z");
        ObjectNode irrelevant=pass.deepCopy(); irrelevant.put("result","FAIL"); irrelevant.put("recordedAt","2026-10-29T00:00:02Z"); irrelevant.set("labels",Json.MAPPER.valueToTree(List.of("TC_LIVE"))); EvidenceRecord.seal(irrelevant);
        assertThat(result(evaluate("F1","p",stage,done,pass,irrelevant),"AC-36")).isEqualTo("PASS");
        ObjectNode wrong=f.leaf("F3","p","r","AC-23-ING-ACL",0,"PASS","2026-10-29T00:00:01Z"); wrong.put("producer","JUNIT_OTR"); wrong.put("componentId","IT"); EvidenceRecord.seal(wrong);
        assertThat(result(evaluate("F3","p",f.parent("F3","p"),wrong),"AC-23-ING-ACL")).isEqualTo("NOT_RUN");
    }
    @Test void requiresSameActualImagesButAllowsSuiteConfigurationDifferences() {
        ObjectNode parent=f.parent("F3","p"), done=f.run("F3","p","r","F3-ACL","COMPLETED","2026-10-29T00:00:01Z"), leaf=f.leaf("F3","p","r","AC-23-ING-ACL",0,"PASS","2026-10-29T00:00:01Z");
        ((ObjectNode)leaf.get("buildIdentity")).put("renderedConfigSha256","f".repeat(64)); EvidenceRecord.seal(leaf);
        assertThat(result(evaluate("F3","p",parent,done,leaf),"AC-23-ING-ACL")).isEqualTo("PASS");
        ((ObjectNode)leaf.get("buildIdentity")).put("appImageDigest","sha256:"+"e".repeat(64)); EvidenceRecord.seal(leaf);
        assertThat(result(evaluate("F3","p",parent,done,leaf),"AC-23-ING-ACL")).isEqualTo("NOT_RUN");
        ((ObjectNode)parent.get("buildIdentity")).remove("acceptanceImageDigest"); EvidenceRecord.seal(parent);
        assertThat(result(evaluate("F3","p",parent,done,leaf),"AC-23-ING-ACL")).isEqualTo("NOT_RUN");
    }
    @Test void rejectsStaleSpecAndAllowsExplicitNewJudgement() throws Exception {
        ObjectNode p=f.parent("F0","p"), a=f.leaf("F0","p","r","AC-38-F0",0,"PASS","2026-10-29T00:00:01Z"); String old=f.binding().specSha();
        Files.writeString(f.spec,"new-approved"); String current=Json.sha(Files.readAllBytes(f.spec)); f.baselineChain(List.of(f.entry(1,old,null,"ALL",true),f.entry(2,current,old,List.of("AC-38-F0"),false)));
        assertThat(result(evaluate("F0","p",p,a),"AC-38-F0")).isEqualTo("NOT_RUN");
        a.put("measurementSpecSha",old); a.put("rawRunId","r"); a.put("rawResultsSha256","0".repeat(64)); a.put("specSha",current); EvidenceRecord.seal(a);
        assertThat(result(evaluate("F0","p",p,a),"AC-38-F0")).isEqualTo("PASS");
        a.put("specSha","f".repeat(64)); EvidenceRecord.seal(a); assertThat(result(evaluate("F0","p",p,a),"AC-38-F0")).isEqualTo("NOT_RUN");
    }
    @Test void requiresRubricForLatestAutomaticAnswerRatherThanAnyOldCompletedTarget() {
        ObjectNode p=f.parent("F1","p"), first=f.run("F1","p","r1","F1","COMPLETED","2026-10-29T00:00:01Z"), next=f.run("F1","p","r2","F1","COMPLETED","2026-10-29T00:00:03Z");
        first.set("rubricTargets",Json.MAPPER.valueToTree(List.of(Map.of("leafId","AC-46-EMPTY","fixtureId","fixture","answerSha256","a".repeat(64))))); EvidenceRecord.seal(first);
        next.set("rubricTargets",Json.MAPPER.valueToTree(List.of(Map.of("leafId","AC-46-EMPTY","fixtureId","fixture","answerSha256","b".repeat(64))))); EvidenceRecord.seal(next);
        ObjectNode acc=f.leaf("F1","p","r2","AC-46-EMPTY",0,"PASS","2026-10-29T00:00:03Z"), rubric=f.leaf("F1","p","rubric","AC-46-EMPTY",1,"PASS","2026-10-29T00:00:02Z");
        rubric.put("fixtureId","fixture"); rubric.put("answerSha256","a".repeat(64)); EvidenceRecord.seal(rubric);
        assertThat(result(evaluate("F1","p",p,first,next,acc,rubric),"AC-46-EMPTY")).isEqualTo("NOT_RUN");
        rubric.put("answerSha256","b".repeat(64)); EvidenceRecord.seal(rubric);
        assertThat(result(evaluate("F1","p",p,first,next,acc,rubric),"AC-46-EMPTY")).isEqualTo("PASS");
    }
    @Test void measurementRecordsCannotSatisfyLeafAndSharedChildrenPropagateToBothParents() {
        ObjectNode p=f.parent("F4","p"), done=f.run("F4","p","r","F4","COMPLETED","2026-10-29T00:00:01Z"), leaf=f.leaf("F4","p","r","AC-03-RES",0,"FAIL","2026-10-29T00:00:01Z");
        var report=evaluate("F4","p",p,done,leaf); assertThat(report.stageParents().get("AC-03")).isEqualTo("FAIL"); assertThat(report.stageParents().get("AC-24")).isEqualTo("FAIL");
        ObjectNode measurement=f.record(Map.of("kind","MEASUREMENT","measurementId","f4:job-latency-samples","stage","F4","parentRunId","p","runId","m","result","PASS"));
        assertThat(result(evaluate("F4","p",p,measurement),"AC-24-PERF")).isEqualTo("NOT_RUN");
    }
}
