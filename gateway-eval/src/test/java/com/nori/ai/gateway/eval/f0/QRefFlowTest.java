package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QRefFlowTest {
    @TempDir Path directory;
    @Test void allV2BoundaryRequestsResolveWireReferencesBeforeExactOracleAndKeepResultsPrivate() throws Exception {
        F0TestRoot f = new F0TestRoot(directory); Prereg.Document doc = F0VersionedInputsTest.versioned(f);
        RegistryFixture fixture = RegistryFixture.load(f.root.resolve(doc.input("fixture")));
        BoundaryCases.Result generated = BoundaryCases.generate(ArgLimits.parse(F0BoundaryCasesV2Test.limits()), fixture);
        f.write(doc.input("boundaryCases"), F0TestRoot.cases(generated));
        ObjectNode block = doc.block().deepCopy();
        // 바뀐 경계 메시지도 호출 전 계수 표에 있어야 송신됩니다.
        F0VersionedInputsTest.writeAudit(f, block.path("inputRoles"), id -> 10);
        for (JsonNode input : block.path("inputs")) if (Set.of(doc.input("boundaryCases"), doc.input("inputAudit")).contains(input.path("path").asText())) ((ObjectNode)input).put("sha256", Json.sha(Json.bytes(f.root.resolve(input.path("path").asText()))));
        F0VersionedInputsTest.writePrereg(f, block);
        block.put("harnessDigest", HarnessDigest.compute(f.root, Prereg.parse(f.root.resolve(F0VersionedInputsTest.PREREG))));
        F0VersionedInputsTest.writePrereg(f, block);
        Map<String,JsonNode> byQuestion = new HashMap<>(); generated.rows().forEach(row -> byQuestion.put(row.path("question").asText(), row));
        try (OllamaStub stub = new OllamaStub()) {
            stub.chat = body -> {
                String question = OllamaStub.question(body); JsonNode row = byQuestion.get(question);
                JsonNode user = Json.parse(body.path("messages").path(1).path("content").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertThat(user.path("question").asText()).isEqualTo(question);
                assertThat(user.path("qRefCatalog")).isEqualTo(QRefContext.extract(question).catalog());
                assertThat(user.path("qRefCatalog").toString()).doesNotContain("value");
                return OllamaStub.chatResponse(row.path("expectedJson").asText(), "stop", 300, 80);
            };
            assertThat(F0VersionedInputsTest.execute(f, "boundary-gen", "--endpoint", stub.endpoint(), "--cases", doc.input("boundaryCases"), "--fixture", doc.input("fixture"), "--prompt", doc.input("classifyPrompt"), "--repeats", "2", "--run-id", F0VersionedInputsTest.RUN, "--out", "eval/f0/results/" + F0VersionedInputsTest.RUN + "/boundary")).isZero();
        }
        JsonNode result = f.json("eval/f0/results/" + F0VersionedInputsTest.RUN + "/boundary/boundary-results.json");
        assertThat(result.path("cases")).hasSize(9).allSatisfy(row -> {
            assertThat(row.path("passRounds").asInt()).isEqualTo(2);
            assertThat(row.path("attempts")).allSatisfy(attempt -> assertThat(attempt.path("diagnostics").path("argumentMatches").path("q").asBoolean()).isTrue());
        });
        String output = f.allOutput("eval/f0/results");
        assertThat(output).doesNotContain("경로C:", "\\u0001", "😀", "expectedResolvedOutput", "qRefCatalog");
    }
}
