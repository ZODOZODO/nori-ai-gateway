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
import static com.nori.ai.gateway.eval.TestSupport.error;

class EvidenceIngestGuardTest {
    @TempDir Path directory;
    TestSupport f;
    @BeforeEach void setup() throws Exception { f=new TestSupport(directory); }
    @Test void rejectsForbiddenNestedFieldsBadHashesUnverifiedSpecAndUnsafeReportMetadata() {
        ObjectNode leaf=f.leaf("F0","p","r","AC-38-F0",0,"PASS","2026-10-29T00:00:01Z");
        for(String key:List.of("answer","prompt","raw","token","jws","dlg","message","stackTrace")) {
            ObjectNode forbidden=leaf.deepCopy();forbidden.set("values",Json.MAPPER.valueToTree(Map.of(key,"SENTINEL")));EvidenceRecord.seal(forbidden);
            error(()->EvidenceRecord.validate(forbidden,f.catalog),2,"EVIDENCE_FORBIDDEN_FIELD");
        }
        ObjectNode bad=leaf.deepCopy();bad.put("result","FAIL");error(()->EvidenceRecord.validate(bad,f.catalog),5,"EVIDENCE_HASH_MISMATCH");
        for(String source:List.of("BASELINE_DECLARED","")){ObjectNode n=leaf.deepCopy();n.put("specShaSource",source);EvidenceRecord.seal(n);error(()->EvidenceRecord.validate(n,f.catalog),2,"SPEC_SHA_UNVERIFIED");}
        ObjectNode missing=leaf.deepCopy();missing.remove("specSha");EvidenceRecord.seal(missing);error(()->EvidenceRecord.validate(missing,f.catalog),2,"SPEC_SHA_UNVERIFIED");
        ObjectNode unsafe=leaf.deepCopy();unsafe.put("reasonCode","PRIVATE\n|TEXT|");EvidenceRecord.seal(unsafe);error(()->EvidenceRecord.validate(unsafe,f.catalog),2,"EVIDENCE_IDENTIFIER_INVALID");
        ObjectNode timestamp=leaf.deepCopy();timestamp.put("recordedAt","not-a-time");EvidenceRecord.seal(timestamp);error(()->EvidenceRecord.validate(timestamp,f.catalog),2,"EVIDENCE_TIMESTAMP_INVALID");
    }
    @Test void rejectsUnknownFieldsAndFreeTextValuesBeforeReportWriting() {
        ObjectNode leaf=f.leaf("F0","p","r","AC-38-F0",0,"PASS","2026-10-29T00:00:01Z");
        leaf.put("note","RAW_SENTINEL");EvidenceRecord.seal(leaf);error(()->EvidenceRecord.validate(leaf,f.catalog),2,"EVIDENCE_FIELD_INVALID");
        leaf.remove("note");leaf.put("reasonCode","raw response text");EvidenceRecord.seal(leaf);error(()->EvidenceRecord.validate(leaf,f.catalog),2,"EVIDENCE_IDENTIFIER_INVALID");
        EvidenceRepositoryFs repository=new EvidenceRepositoryFs(directory);error(()->new EvidenceIngestor(repository,f.catalog).ingest(directory.resolve("absent"),"unknown","F0","p",f.binding()),2,"EVIDENCE_SOURCE_INVALID");
    }
    @Test void acceptsSpecInputAbortedRunOnlyWithoutSpecBinding() {
        ObjectNode aborted=Json.MAPPER.valueToTree(Map.of("kind","RUN","status","ABORTED","stage","F1","runId","r","parentRunId","p","recordedAt","2026-10-29T00:00:01Z","reasonCode","SPEC_INPUT_MISSING"));EvidenceRecord.seal(aborted);
        assertThatCode(()->EvidenceRecord.validate(aborted,f.catalog)).doesNotThrowAnyException();
        aborted.put("kind","LEAF");aborted.put("leafId","AC-36");aborted.put("result","PASS");EvidenceRecord.seal(aborted);error(()->EvidenceRecord.validate(aborted,f.catalog),2,"SPEC_SHA_UNVERIFIED");
    }
    @Test void missingAcceptanceBlocksAbortParentWithoutRevivingEarlierSuitePassAndExplicitSuiteCanRecover() throws Exception {
        EvidenceRepositoryFs repository=new EvidenceRepositoryFs(directory);repository.store(f.parent("F1","p"));repository.store(f.run("F1","p","r1","F1","COMPLETED","2026-10-29T00:00:01Z"));repository.store(f.leaf("F1","p","r1","AC-36",0,"PASS","2026-10-29T00:00:01Z"));
        Path log=directory.resolve("acceptance.log");Files.writeString(log,"untrusted-line-only\n");Files.writeString(log.resolveSibling("acceptance.log.sha256"),Json.sha(Files.readAllBytes(log)));
        new EvidenceIngestor(repository,f.catalog).ingest(log,"acceptance","F1","p",f.binding());
        var evaluator=new GateEvaluator();var report=evaluator.evaluate("F1","p",repository.readAll("F1"),f.catalog,SpecBaseline.parseChain(f.baseline));
        assertThat(report.leaves().stream().filter(l->l.leafId().equals("AC-36")).findFirst().orElseThrow().result()).isEqualTo("NOT_RUN");
        List<JsonNode> records=new ArrayList<>(repository.readAll("F1"));records.add(f.run("F1","p","r2","F1","COMPLETED","2099-01-01T00:00:00Z"));records.add(f.leaf("F1","p","r2","AC-36",0,"PASS","2099-01-01T00:00:00Z"));
        assertThat(evaluator.evaluate("F1","p",records,f.catalog,SpecBaseline.parseChain(f.baseline)).leaves().stream().filter(l->l.leafId().equals("AC-36")).findFirst().orElseThrow().result()).isEqualTo("PASS");
        assertThat(repository.readAll("F1").stream().filter(n->n.path("status").asText().equals("ABORTED")).count()).isEqualTo(1);
    }
    @Test void malformedBlockHashCreatesOnlyAbortAndExplicitDifferentSuiteAbortDoesNotInvalidatePass() throws Exception {
        EvidenceRepositoryFs repository=new EvidenceRepositoryFs(directory);repository.store(f.parent("F1","p"));
        Path log=directory.resolve("acceptance.log");String payload=Base64.getEncoder().encodeToString("{}".getBytes());Files.writeString(log,"AIGW-EVIDENCE-BEGIN "+"0".repeat(64)+"\n"+payload+"\nAIGW-EVIDENCE-END\n");Files.writeString(log.resolveSibling("acceptance.log.sha256"),Json.sha(Files.readAllBytes(log)));
        assertThat(new EvidenceIngestor(repository,f.catalog).ingest(log,"acceptance","F1","p",f.binding())).isEqualTo(1);
        assertThat(repository.readAll("F1").stream().filter(n->n.path("kind").asText().equals("LEAF")).count()).isZero();
        var report=new GateEvaluator().evaluate("F1","p",List.of(f.parent("F1","p"),f.run("F1","p","r1","F1","COMPLETED","2026-10-29T00:00:01Z"),f.leaf("F1","p","r1","AC-36",0,"PASS","2026-10-29T00:00:01Z"),f.run("F1","p","r2","F1-RUBRIC","ABORTED","2026-10-29T00:00:02Z")),f.catalog,SpecBaseline.parseChain(f.baseline));
        assertThat(report.leaves().stream().filter(l->l.leafId().equals("AC-36")).findFirst().orElseThrow().result()).isEqualTo("PASS");
    }
    @Test void itReferencePreservesOriginalBytesAndRejectsDifferentGitIdentity() throws Exception {
        EvidenceRepositoryFs repository=new EvidenceRepositoryFs(directory);repository.store(f.parent("F4","p"));
        ObjectNode summary=f.record(Map.of("kind","RUN","status","COMPLETED","stage","F4","producer","JUNIT_OTR","runId","it1","taskPath",":apps:ai-gateway-app:integrationTest","otrSha256","a".repeat(64),"buildIdentity",f.identity("F4")));
        ObjectNode leaf=f.leaf("F4","raw-parent","it1","AC-18-WORKER-RUN",0,"PASS","2026-10-29T00:00:01Z");Path input=directory.resolve("it/leaf-results.json");Json.write(input,Map.of("summary",summary,"records",List.of(leaf)));byte[] before=Files.readAllBytes(input);
        EvidenceIngestor ingestor=new EvidenceIngestor(repository,f.catalog);assertThat(ingestor.ingest(input,"junit-otr","F4","p",f.binding())).isEqualTo(1);assertThat(Files.readAllBytes(input)).isEqualTo(before);
        var report=new GateEvaluator().evaluate("F4","p",repository.readAll("F4"),f.catalog,SpecBaseline.parseChain(f.baseline));assertThat(report.leaves().stream().filter(l->l.leafId().equals("AC-18-WORKER-RUN")).findFirst().orElseThrow().result()).isEqualTo("PASS");
        ((ObjectNode)summary.get("buildIdentity")).put("gitSha","f".repeat(40));EvidenceRecord.seal(summary);Json.write(input,Map.of("summary",summary,"records",List.of(leaf)));
        error(()->ingestor.ingest(input,"junit-otr","F4","p",f.binding()),3,"IDENTITY_MISMATCH");
    }
}
