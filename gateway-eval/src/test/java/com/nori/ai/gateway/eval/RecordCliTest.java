package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.report.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class RecordCliTest {
    @TempDir Path directory;
    @Test void recordsTypedAllowlistedUserRunWithFileHashedIdentity() throws Exception {
        TestSupport f=new TestSupport(directory);new EvidenceRepositoryFs(directory).store(f.parent("F2","p"));
        assertThat(f.cli("record","user-run","--stage","F2","--leaf","AC-35-FW","--run-id","r","--parent-run-id","p","--field","tcpConnectFailed=true","--field","proxyReceived=0","--field","sourceHostClass=LAN")).isZero();
        var record=new EvidenceRepositoryFs(directory).readAll("F2").stream().filter(n->n.path("kind").asText().equals("LEAF")).findFirst().orElseThrow();
        assertThat(record.path("result").asText()).isEqualTo("PASS");assertThat(record.path("specShaSource").asText()).isEqualTo("FILE_HASHED");assertThat(record.path("contentSha256").asText()).hasSize(64);EvidenceRecord.validate(record,f.catalog);
    }
    @Test void rejectsUnknownFieldsMultilineOversizedAndRemovedCompatibilityCommand() throws Exception {
        TestSupport f=new TestSupport(directory);new EvidenceRepositoryFs(directory).store(f.parent("F2","p"));
        for(String field:new String[]{"answer=PRIVATE_SENTINEL","tcpConnectFailed=true\nPRIVATE_SENTINEL","sourceHostClass="+"x".repeat(129)}) {
            assertThat(f.cli("record","user-run","--stage","F2","--leaf","AC-35-FW","--run-id","r","--parent-run-id","p","--field",field)).isEqualTo(2);
        }
        assertThat(f.cli("record","identity-compat","--stage","F2","--parent-run-id","p")).isEqualTo(2);
        try(var files=Files.walk(directory.resolve("evidence"))){for(Path file:files.filter(Files::isRegularFile).toList())assertThat(Files.readString(file)).doesNotContain("PRIVATE_SENTINEL");}
    }
    @Test void futureExecutionCommandsNeverReturnSuccess() throws Exception {
        TestSupport f=new TestSupport(directory);for(String command:new String[]{"f0","f3","ci-evidence"})assertThat(f.cli(command,"report")).isEqualTo(2);
    }
}
