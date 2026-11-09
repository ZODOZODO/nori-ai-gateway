package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.report.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RecordCliTest {
    @TempDir Path directory;
    private static final String[] RUBRIC = {"--field","reviewerRef=reviewer-1","--field","reviewedAt=2026-11-09T00:00:00Z","--field","verdict=PASS","--field","answerSha256="+"a".repeat(64),
        "--field","fixtureId=fx-1","--field","jobId=job-1","--field","rubricVersion=v1"};
    private int rubric(TestSupport f, String... fields) {
        String[] head={"record","rubric","--stage","F2","--leaf","AC-22-LIVE","--run-id","r","--parent-run-id","p"};
        String[] args=java.util.Arrays.copyOf(head,head.length+fields.length);System.arraycopy(fields,0,args,head.length,fields.length);return f.cli(args);
    }
    @Test void recordsTypedAllowlistedRubricWithFileHashedIdentity() throws Exception {
        TestSupport f=new TestSupport(directory);new EvidenceRepositoryFs(directory).store(f.parent("F2","p"));
        assertThat(rubric(f,RUBRIC)).isZero();
        var record=new EvidenceRepositoryFs(directory).readAll("F2").stream().filter(n->n.path("kind").asText().equals("LEAF")).findFirst().orElseThrow();
        assertThat(record.path("result").asText()).isEqualTo("PASS");assertThat(record.path("componentId").asText()).isEqualTo("RB:F2-RUBRIC");
        assertThat(record.path("specShaSource").asText()).isEqualTo("FILE_HASHED");assertThat(record.path("contentSha256").asText()).hasSize(64);EvidenceRecord.validate(record,f.catalog);
    }
    @Test void rejectsUnknownFieldsMultilineOversizedAndRemovedCompatibilityCommand() throws Exception {
        TestSupport f=new TestSupport(directory);new EvidenceRepositoryFs(directory).store(f.parent("F2","p"));
        for(String field:new String[]{"answer=PRIVATE_SENTINEL","reviewerRef=reviewer-1\nPRIVATE_SENTINEL","reviewerRef="+"x".repeat(129)}) {
            String[] fields=RUBRIC.clone();fields[1]=field;
            assertThat(rubric(f,fields)).isEqualTo(2);
        }
        assertThat(f.cli("record","identity-compat","--stage","F2","--parent-run-id","p")).isEqualTo(2);
        try(var files=Files.walk(directory.resolve("evidence"))){for(Path file:files.filter(Files::isRegularFile).toList())assertThat(Files.readString(file)).doesNotContain("PRIVATE_SENTINEL");}
    }
    @Test void userRunHasNoAllowlistedLeafAfterTenantPrinciple() throws Exception {
        // SPEC §26.1 임차 원칙: 서버 측 계층 leaf 3개는 catalog 에서 빠졌고, 남은 AC-33-LOADING·AC-24-RESTORE 의 권위는 readiness stub 수용·복원 수용(§12.4)이라 user-run schema 항목이 없다.
        TestSupport f=new TestSupport(directory);EvidenceRepositoryFs repository=new EvidenceRepositoryFs(directory);
        repository.store(f.parent("F2","p"));repository.store(f.parent("F4","p"));ManualRecorder recorder=new ManualRecorder(f.catalog,repository);
        for(String retired:new String[]{"AC-35-TOKEN","AC-35-FW","AC-35-LOCAL"}) TestSupport.error(()->recorder.record("user-run","F2",retired,"r","p",List.of(),f.binding()),4,"UNKNOWN_LEAF_TAG");
        TestSupport.error(()->recorder.record("user-run","F2","AC-33-LOADING","r","p",List.of("restartAt=2026-11-09T00:00:00Z","readinessTransitions=[\"READY\"]"),f.binding()),2,"USER_RUN_LEAF_FORBIDDEN");
        TestSupport.error(()->recorder.record("user-run","F4","AC-24-RESTORE","r","p",List.of("startedAt=2026-11-09T00:00:00Z","readyAt=2026-11-09T00:01:00Z"),f.binding()),2,"USER_RUN_LEAF_FORBIDDEN");
        assertThat(repository.readAll("F2").stream().filter(n->n.path("kind").asText().equals("LEAF"))).isEmpty();
        assertThat(repository.readAll("F4").stream().filter(n->n.path("kind").asText().equals("LEAF"))).isEmpty();
    }
    @Test void futureExecutionCommandsNeverReturnSuccess() throws Exception {
        TestSupport f=new TestSupport(directory);for(String command:new String[]{"f0","f3","ci-evidence"})assertThat(f.cli(command,"report")).isEqualTo(2);
    }
}
