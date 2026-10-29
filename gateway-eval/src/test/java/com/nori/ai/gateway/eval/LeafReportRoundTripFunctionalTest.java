package com.nori.ai.gateway.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LeafReportRoundTripFunctionalTest {
    @TempDir Path directory;
    @Test void extractsActualGradleJunitOutputAndNeverCopiesFailureText() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);
        try(var input=getClass().getResourceAsStream("/fixtures/otr-probe/src/test/java/probe/ProbeTest.java")){assertThat(input).isNotNull();fixture.write("sub/src/test/java/probe/ProbeTest.java",new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        var result=fixture.run(true,":sub:test");assertThat(result.getOutput()).contains("6 tests completed");assertThat(result.task(":sub:testLeafEvidence")).isNotNull();
        Path output=directory.resolve("sub/build/aigw-evidence/test/leaf-results.json");JsonNode evidence=Json.read(output);Map<String,String> results=new HashMap<>();evidence.path("records").forEach(n->results.put(n.path("leafId").asText(),n.path("result").asText()));
        assertThat(results).containsEntry("AC-24-JOB","PASS").containsEntry("AC-24-AUTHZ","FAIL").containsEntry("AC-24-LOT-ACTIVE","NOT_RUN").containsEntry("AC-24-LOT-MULTI","NOT_RUN").containsEntry("AC-24-LOT-ENDED","FAIL").doesNotContainKey("AC-24-LOT-UNKNOWN");
        assertThat(evidence.path("summary").path("executedTestCount").asInt()).isEqualTo(5);assertThat(evidence.path("summary").path("skipped").asInt()).isEqualTo(1);assertThat(evidence.path("summary").path("aborted").asInt()).isEqualTo(1);
        assertThat(Files.readString(output)).doesNotContain("OTR_SENTINEL","AssertionFailedError","stackTrace","ProbeTest.java:");
        evidence.path("records").forEach(n->{assertThat(n.path("specSha").asText()).isEqualTo(fixture.fixture.binding().specSha());assertThat(n.path("specShaSource").asText()).isEqualTo("FILE_HASHED");});
        fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        fixture.write("sub/src/test/java/probe/ProbeTest.java","package probe; import org.junit.jupiter.api.*; class ProbeTest { @Test @Tag(\"AC-99-UNKNOWN\") void unknown(){Assertions.assertTrue(true);} }\n");
        assertThat(fixture.run(true,":sub:test").getOutput()).contains("UNKNOWN_LEAF_TAG");assertThat(output).doesNotExist();
    }
    @Test void separatesSameLeafByLabelSetInActualOtr() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);
        fixture.write("sub/src/test/java/probe/LabelsTest.java","package probe; import org.junit.jupiter.api.*; @Tag(\"stage-F4\") class LabelsTest { @Test @Tag(\"AC-24-JOB\") @Tag(\"label-MOCK_API\") void mock(){Assertions.assertEquals(1,1);} @Test @Tag(\"AC-24-JOB\") @Tag(\"label-TC_LIVE\") void live(){Assertions.fail(\"LABEL_SENTINEL\");} }\n");
        fixture.run(true,":sub:test");JsonNode result=Json.read(directory.resolve("sub/build/aigw-evidence/test/leaf-results.json"));assertThat(result.path("records").size()).isEqualTo(2);
        Map<String,String> labels=new HashMap<>();result.path("records").forEach(n->labels.put(n.path("labels").get(0).asText(),n.path("result").asText()));assertThat(labels).containsEntry("MOCK_API","PASS").containsEntry("TC_LIVE","FAIL");fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
    }
    @Test void missingAndTamperedSpecFailAndDeleteOldSanitizedOutput() throws Exception {
        TestKitSupport fixture=new TestKitSupport(directory);fixture.passingTest();fixture.run(false,":sub:test");fixture.run(false,":gateway-eval:run",fixture.verifyArgs(":sub:test"));
        Path output=directory.resolve("sub/build/aigw-evidence/test/leaf-results.json");fixture.env.remove("AIGW_SPEC_FILE");assertThat(fixture.run(true,":sub:testLeafEvidence").getOutput()).contains("SPEC_INPUT_MISSING");assertThat(output).doesNotExist();
        fixture.env.put("AIGW_SPEC_FILE",fixture.fixture.spec.toString());Files.writeString(fixture.fixture.spec,"tampered");assertThat(fixture.run(true,":sub:testLeafEvidence").getOutput()).contains("SPEC_BASELINE_MISMATCH");assertThat(output).doesNotExist();
    }
}
