package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.eval.TestSupport.error;

class TestEvidenceVerifyTest {
    @TempDir Path directory;
    TestSupport f;ObjectNode summary;Path output;
    @BeforeEach void fixture() throws Exception {
        f=new TestSupport(directory);summary=f.record(Map.ofEntries(Map.entry("kind","RUN"),Map.entry("status","COMPLETED"),Map.entry("stage","F0"),Map.entry("runId","r"),Map.entry("executedTestCount",1),Map.entry("inputFingerprint","a".repeat(64)),Map.entry("executedClasses",List.of("probe.RealTest")),Map.entry("skippedTestCount",0)));
        output=directory.resolve("sub/build/aigw-evidence/test/leaf-results.json");save();Json.write(directory.resolve("sub/build/aigw-otr/test/run-binding.json"),Map.of("runId","r","specSha",f.binding().specSha(),"inputFingerprint","a".repeat(64)));Json.write(directory.resolve("build/aigw-fingerprint/_sub_test.json"),Map.of("inputFingerprint","a".repeat(64),"projectDir","sub"));
    }
    void save(){EvidenceRecord.seal(summary);Json.write(output,Map.of("summary",summary,"records",List.of()));}
    void verify(boolean docker,Map<String,List<String>> classes){new TestEvidenceVerifier().verify(directory,List.of(":sub:test"),docker,classes);}
    @Test void acceptsMatchingEvidenceWithoutReissuingOriginalBinding() throws Exception {byte[] before=Files.readAllBytes(output);verify(false,Map.of(":sub:test",List.of("probe.RealTest")));assertThat(Files.readAllBytes(output)).isEqualTo(before);}
    @Test void rejectsMissingSummaryAndZeroTests() throws Exception {Files.delete(output);error(()->verify(false,Map.of()),4,"NO_TESTS_EXECUTED");summary.put("executedTestCount",0);save();error(()->verify(false,Map.of()),4,"NO_TESTS_EXECUTED");}
    @Test void rejectsCurrentFingerprintMismatchAndRegisteredClassNotExecuted() {Json.write(directory.resolve("build/aigw-fingerprint/_sub_test.json"),Map.of("inputFingerprint","b".repeat(64),"projectDir","sub"));error(()->verify(false,Map.of()),4,"STALE_TEST_EVIDENCE");Json.write(directory.resolve("build/aigw-fingerprint/_sub_test.json"),Map.of("inputFingerprint","a".repeat(64),"projectDir","sub"));error(()->verify(false,Map.of(":sub:test",List.of("probe.MissingTest"))),4,"REQUIRED_CLASS_NOT_EXECUTED");}
    // settings 의 projectDir 매핑(:sub → modules/sub)처럼 물리 위치가 task 경로와 다르면, 루트 build 가 fingerprint 에 기록한 projectDir 로만 찾는다.
    @Test void findsEvidenceOnlyThroughRecordedProjectDir() throws Exception {
        Path fingerprint=directory.resolve("build/aigw-fingerprint/_sub_test.json");
        Files.createDirectories(directory.resolve("modules"));Files.move(directory.resolve("sub"),directory.resolve("modules/sub"));
        Json.write(fingerprint,Map.of("inputFingerprint","a".repeat(64),"projectDir","modules/sub"));
        verify(false,Map.of(":sub:test",List.of("probe.RealTest")));
        Json.write(fingerprint,Map.of("inputFingerprint","a".repeat(64)));
        error(()->verify(false,Map.of()),4,"TEST_PROJECT_DIR_INVALID");
        Json.write(fingerprint,Map.of("inputFingerprint","a".repeat(64),"projectDir","../modules/sub"));
        error(()->verify(false,Map.of()),4,"TEST_PROJECT_DIR_INVALID");
    }
    @Test void refusesSkippedDockerRequiredAndTamperedSummary(){summary.put("skippedTestCount",1);save();error(()->verify(true,Map.of()),4,"TESTS_SKIPPED");summary.put("skippedTestCount",0);save();summary.put("executedTestCount",2);Json.write(output,Map.of("summary",summary,"records",List.of()));error(()->verify(false,Map.of()),4,"STALE_TEST_EVIDENCE");}
}
