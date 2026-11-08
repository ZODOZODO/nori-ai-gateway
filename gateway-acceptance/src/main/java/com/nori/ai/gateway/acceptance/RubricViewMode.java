package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * {@code AcceptanceMain --mode rubric-view} 입력 검사. SPEC 입력과 rubric 대상 레코드(같은 부모 회차의 RUN COMPLETED)를 확인한다.
 * 대상 Job 서술을 다시 읽으려면 그 fixture 의 actor 단언 claims 가 필요하며, 이는 rubric suite fixture 를 정의하는 phase(P16 F1-RUBRIC)가
 * 공급한다. 공급 전에는 viewer 를 띄우지 않고 RUN ABORTED({@code RUBRIC_FIXTURE_CLAIMS_UNDEFINED})로 끝낸다(RUBRIC 0 = NOT_RUN).
 */
final class RubricViewMode {
    private RubricViewMode() {}

    static int run(AcceptanceConfig config, PrintStream out) {
        String suite = config.suite().orElse(null);
        String stage = suite == null ? null : AcceptanceConfig.stageOf(suite).orElseThrow();
        String runId = UUID.randomUUID().toString(), parent = config.parentRunId().orElse(null);
        JsonNode identity = config.buildIdentity().orElse(null);
        String specSha;
        try { specSha = SpecInputVerifier.verify(config.specGzipFile().orElse(null), config.expectedSpecSha256().orElse(null)); }
        catch (SpecInputVerifier.Failure failure) {
            EvidenceBlockWriter.write(out, List.of(EvidenceBlockWriter.record(new EvidenceBlockWriter.Context(stage, suite, runId, parent, null, identity),
                Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", failure.code()))));
            return 3;
        }
        var context = new EvidenceBlockWriter.Context(stage, suite, runId, parent, specSha, identity);
        if (suite == null || !suite.endsWith("-RUBRIC") || parent == null || targets(config.env().get("AIGW_RUBRIC_TARGETS_FILE"), parent).isEmpty()) {
            EvidenceBlockWriter.write(out, List.of(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "RUBRIC_TARGETS_INVALID"))));
            return 2;
        }
        EvidenceBlockWriter.write(out, List.of(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "RUBRIC_FIXTURE_CLAIMS_UNDEFINED"))));
        return 2;
    }

    /** 대상 레코드의 {@code parentRunId} 가 이번 회차와 다르면 빈 값(exit 2). */
    static Optional<List<RubricViewer.Target>> targets(String file, String parent) {
        if (file == null) return Optional.empty();
        try {
            JsonNode record = CanonicalJson.MAPPER.readTree(Files.readAllBytes(Path.of(file)));
            if (!"RUN".equals(record.path("kind").asText()) || !"COMPLETED".equals(record.path("status").asText()) || !parent.equals(record.path("parentRunId").asText())
                || !record.path("rubricTargets").isArray()) return Optional.empty();
            var targets = new ArrayList<RubricViewer.Target>();
            for (JsonNode target : record.path("rubricTargets")) {
                if (!target.path("answerSha256").asText().matches("[0-9a-f]{64}")) return Optional.empty();
                targets.add(new RubricViewer.Target(target.path("leafId").asText(), target.path("fixtureId").asText(), target.path("jobId").asText(), target.path("answerSha256").asText()));
            }
            return Optional.of(List.copyOf(targets));
        } catch (IOException | RuntimeException e) { return Optional.empty(); }
    }
}
