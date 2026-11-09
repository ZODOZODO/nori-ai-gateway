package com.nori.ai.gateway.acceptance;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class F1RubricBindingTest {
    @TempDir Path directory;
    @Test void authenticatedFixtureReadRejectsWrongReturnedJobAndUnknownClaims()throws Exception {try(var fixture=new F1LocalFixture()) {fixture.authReason=null;var target=new RubricViewer.Target("AC-46-HIT","f1-retention-hit","job-one",CanonicalJson.sha256(new byte[0]));assertThat(RubricViewMode.answer(fixture.gateway,target)).hasValueSatisfying(value->assertThat(value).isEmpty());fixture.returnedJob="job-other";assertThat(RubricViewMode.answer(fixture.gateway,target)).isEmpty();assertThat(RubricViewMode.answer(fixture.gateway,new RubricViewer.Target("AC-41","invented","job-one",target.answerSha256()))).isEmpty();}}
    @Test void sameParentCannotRebindDifferentSpecBuildOrStageAndPathsAreRejected()throws Exception {
        byte[] spec="# local spec\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);Path gzip=directory.resolve("spec.gz");try(var output=new GZIPOutputStream(Files.newOutputStream(gzip))){output.write(spec);}String sha=CanonicalJson.sha256(spec);var identity=CanonicalJson.MAPPER.readTree("{\"appImageDigest\":\"sha256:"+"a".repeat(64)+"\"}");
        var context=new EvidenceBlockWriter.Context("F1","F1","run","parent",sha,identity);var targets=List.of(new RubricViewer.Target("AC-46-EMPTY","f1-retention-empty","job-empty",CanonicalJson.sha256(new byte[0])),new RubricViewer.Target("AC-46-HIT","f1-retention-hit","job-hit",CanonicalJson.sha256(new byte[0])));
        var original=EvidenceBlockWriter.record(context,Map.of("kind","RUN","status","COMPLETED","rubricTargets",targets));Path file=directory.resolve("targets.json");
        var env=Map.of("AIGW_PARENT_RUN_ID","parent","AIGW_SPEC_GZIP_FILE",gzip.toString(),"AIGW_EXPECTED_SPEC_SHA256",sha,"AIGW_BUILD_IDENTITY_JSON",identity.toString(),"AIGW_RUBRIC_TARGETS_FILE",file.toString());
        for(String changed:List.of("specSha","stage","buildIdentity")){var invalid=original.deepCopy();if(changed.equals("buildIdentity"))invalid.set(changed,CanonicalJson.MAPPER.readTree("{}"));else invalid.put(changed,"other");Files.writeString(file,CanonicalJson.seal(invalid).toString());var output=new ByteArrayOutputStream();assertThat(RubricViewMode.run(new AcceptanceConfig(env,Map.of("aigw.acceptance.suite","F1-RUBRIC")),new PrintStream(output))).isEqualTo(2);assertThat(EvidenceBlockWriterTest.decode(output.toString()).getLast().path("reasonCode").asText()).isEqualTo("RUBRIC_TARGETS_INVALID");}
        var malformed=original.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)malformed.path("rubricTargets").get(0)).put("jobId","../other?secret");Files.writeString(file,CanonicalJson.seal(malformed).toString());assertThat(RubricViewMode.targets(file.toString(),"parent")).isEmpty();
    }
}
