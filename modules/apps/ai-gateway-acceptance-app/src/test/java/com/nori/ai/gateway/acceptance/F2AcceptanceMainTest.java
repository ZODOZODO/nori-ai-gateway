package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** F2 회차 경계: -OUTAGE 단계 입력·레코드 단계 표지, F2 rubric 대상 운반, F2-RUBRIC 대상 결속. */
class F2AcceptanceMainTest {
    static final byte[] SPEC = "# 승인 SPEC 바이트\n".getBytes(StandardCharsets.UTF_8);
    @TempDir Path directory;
    final AtomicInteger launches = new AtomicInteger();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private Map<String,String> env(Map<String,String> extra) throws IOException {
        Path gzip = directory.resolve("spec.md.gz");
        try (var stream = new GZIPOutputStream(Files.newOutputStream(gzip))) { stream.write(SPEC); }
        var base = new HashMap<>(Map.of("AIGW_PARENT_RUN_ID", "F2-20261113T000000Z-0a1b2c3d", "AIGW_SPEC_GZIP_FILE", gzip.toString(),
            "AIGW_EXPECTED_SPEC_SHA256", CanonicalJson.sha256(SPEC), "AIGW_BUILD_IDENTITY_JSON", "{\"appImageDigest\":\"sha256:" + "b".repeat(64) + "\"}"));
        base.putAll(extra); return base;
    }
    private static String otr(String leaf, String... tags) {
        var xml = new StringBuilder("<e:events xmlns:e=\"https://schemas.opentest4j.org/reporting/events/0.2.0\" xmlns:c=\"https://schemas.opentest4j.org/reporting/core/0.2.0\" xmlns:j=\"https://schemas.junit.org/open-test-reporting\">");
        xml.append("<e:started id=\"1\" name=\"t\" time=\"2026-11-13T00:00:00Z\"><c:metadata><c:tags><c:tag>").append(leaf).append("</c:tag>");
        for (String tag : tags) xml.append("<c:tag>").append(tag).append("</c:tag>");
        return xml.append("</c:tags></c:metadata><j:type>TEST</j:type></e:started><e:finished id=\"1\" time=\"2026-11-13T00:00:00Z\"><c:result status=\"SUCCESSFUL\"/></e:finished></e:events>").toString();
    }
    private int run(String suite, Map<String,String> extra, String xml, Runnable during) throws IOException {
        Path temporary = Files.createDirectories(directory.resolve("tmp"));
        return new AcceptanceMain(new AcceptanceConfig(env(extra), Map.of("aigw.acceptance.suite", suite)), new PrintStream(out, true, StandardCharsets.UTF_8), (name, otr) -> {
            launches.incrementAndGet(); during.run();
            try { Files.writeString(otr.resolve("junit-platform-events.xml"), xml); } catch (IOException e) { throw new UncheckedIOException(e); }
        }, temporary).runSuite();
    }
    private List<JsonNode> records() { return EvidenceBlockWriterTest.decode(out.toString(StandardCharsets.UTF_8)); }
    private static void assertSealed(JsonNode record) {
        ObjectNode copy = ((ObjectNode) record).deepCopy(); String expected = copy.remove("contentSha256").asText();
        assertThat(CanonicalJson.sha256(CanonicalJson.bytes(copy))).isEqualTo(expected);
    }

    @Test void outageSuiteWithoutAClosedPhaseIsAnInputErrorBeforeLaunch() throws Exception {
        assertThat(run("F2-OUTAGE", Map.of("AIGW_OUTAGE_PHASE", "RESTARTED"), otr("AC-44"), () -> {})).isEqualTo(2);
        assertThat(launches).hasValue(0);
        assertThat(records()).singleElement().satisfies(record -> { assertThat(record.path("reasonCode").asText()).isEqualTo("ACCEPTANCE_INPUT_INVALID"); assertThat(record.has("outagePhase")).isFalse(); });
    }

    @Test void outageRunStampsThePhaseOnEveryRecordSoTheGateCanMatchTheOutageComponent() throws Exception {
        assertThat(run("F2-OUTAGE", Map.of("AIGW_OUTAGE_PHASE", "STOPPED"), otr("AC-44", "suite-F2-OUTAGE", "stage-F2", "outage-STOPPED", "label-MOCK_API", "label-LLM_STUB"), () -> {})).isZero();
        var all = records();
        assertThat(all.stream().map(record -> record.path("status").asText() + record.path("kind").asText())).contains("STARTEDRUN", "LEAF", "COMPLETEDRUN");
        assertThat(all).allSatisfy(record -> { assertThat(record.path("outagePhase").asText()).isEqualTo("STOPPED"); assertThat(record.path("suite").asText()).isEqualTo("F2-OUTAGE"); assertSealed(record); });
        assertThat(all).filteredOn(record -> record.path("kind").asText().equals("LEAF")).singleElement().satisfies(leaf -> {
            assertThat(leaf.path("leafId").asText()).isEqualTo("AC-44"); assertThat(leaf.path("componentId").asText()).isEqualTo("ACC:F2-OUTAGE");
            assertThat(leaf.path("stage").asText()).isEqualTo("F2"); assertThat(leaf.path("result").asText()).isEqualTo("PASS");
            assertThat(leaf.path("labels").toString()).contains("MOCK_API", "LLM_STUB");
        });
        assertThat(all.getLast().has("rubricTargets")).isFalse();
    }

    @Test void f2RunCarriesTheCollectedRubricTargetsAndClearsThemAfterward() throws Exception {
        byte[] narrative = "근거 범위에서 원인 후보를 정리했다[e1].".getBytes(StandardCharsets.UTF_8);
        assertThat(run("F2", Map.of(), otr("AC-22-LIVE", "suite-F2", "stage-F2", "label-MOCK_API", "label-LLM_LIVE"), () -> F2RubricTargets.add("AC-22-LIVE", "f2-live-incident-comm", "job-live-1", narrative))).isZero();
        var completed = records().getLast();
        assertThat(completed.has("outagePhase")).isFalse();
        assertThat(completed.path("rubricTargets")).singleElement().satisfies(target -> {
            assertThat(target.path("leafId").asText()).isEqualTo("AC-22-LIVE"); assertThat(target.path("fixtureId").asText()).isEqualTo("f2-live-incident-comm");
            assertThat(target.path("answerSha256").asText()).isEqualTo(CanonicalJson.sha256(narrative));
        });
        assertThat(completed.toString()).doesNotContain("원인 후보");
        assertThat(F2RubricTargets.snapshot()).isEmpty();
    }

    @Test void f2RubricTargetsMustCoverEveryFreeTextLeafWithPreregisteredFixturesOnly() {
        String sha = "a".repeat(64);
        var complete = new ArrayList<RubricViewer.Target>();
        for (String leaf : F2Fixture.FREE_TEXT_LEAVES) for (var fixture : F2Fixture.fixtures(leaf)) complete.add(new RubricViewer.Target(leaf, fixture.fixtureId(), "job-" + fixture.fixtureId(), sha));
        assertThat(RubricViewMode.fixturesBound("F2-RUBRIC", complete)).isTrue();
        assertThat(RubricViewMode.fixturesBound("F2-RUBRIC", complete.stream().filter(target -> !target.leafId().equals("AC-22-GQ-BYPASS-XSYS")).toList())).as("세 leaf 미포함").isFalse();
        var duplicated = new ArrayList<>(complete); duplicated.add(complete.getFirst());
        assertThat(RubricViewMode.fixturesBound("F2-RUBRIC", duplicated)).isFalse();
        var invented = new ArrayList<>(complete); invented.add(new RubricViewer.Target("AC-22-LIVE", "f2-invented", "job-x", sha));
        assertThat(RubricViewMode.fixturesBound("F2-RUBRIC", invented)).isFalse();
        assertThat(RubricViewMode.fixturesBound("F1-RUBRIC", complete)).isFalse();
        assertThat(RubricViewMode.fixturesBound("F4-RUBRIC", complete)).isFalse();
    }

    @Test void f2RubricViewRejectsTargetsOfAnotherStageAndReadsTerminalLiveJobsWithTheOriginalActor() throws Exception {
        var identity = CanonicalJson.MAPPER.readTree("{\"appImageDigest\":\"sha256:" + "b".repeat(64) + "\"}");
        String specSha = CanonicalJson.sha256(SPEC);
        var context = new EvidenceBlockWriter.Context("F1", "F1", "run", "F2-20261113T000000Z-0a1b2c3d", specSha, identity);
        var source = EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "COMPLETED", "rubricTargets", List.of(new RubricViewer.Target("AC-22-LIVE", "f2-live-incident-comm", "job-1", "a".repeat(64)))));
        Path file = directory.resolve("targets.json"); Files.writeString(file, source.toString());
        var output = new ByteArrayOutputStream();
        assertThat(RubricViewMode.run(new AcceptanceConfig(env(Map.of("AIGW_RUBRIC_TARGETS_FILE", file.toString())), Map.of("aigw.acceptance.suite", "F2-RUBRIC")), new PrintStream(output))).isEqualTo(2);
        assertThat(EvidenceBlockWriterTest.decode(output.toString()).getLast().path("reasonCode").asText()).isEqualTo("RUBRIC_TARGETS_INVALID");
        try (var fixture = new F2LocalFixture()) {
            fixture.responder = entry -> { var outcome = new F2LocalFixture.Outcome(); outcome.jobStatus = "PARTIAL"; outcome.payload = new LinkedHashMap<>(Map.of("blocks", List.of())); return outcome; };
            var job = fixture.gateway.submitObserved("/v1/query", "live-" + UUID.randomUUID(), F2Fixture.claims(), fixture.gateway.newDlg(), "{\"query\":\"q\"}").response();
            String id = CanonicalJson.MAPPER.readTree(job.body()).path("jobId").asText();
            var target = new RubricViewer.Target("AC-22-LIVE", "f2-live-incident-comm", id, CanonicalJson.sha256(new byte[0]));
            assertThat(RubricViewMode.answer(fixture.gateway, target)).hasValueSatisfying(value -> assertThat(value).isEmpty());
            assertThat(RubricViewMode.answer(fixture.gateway, new RubricViewer.Target("AC-22-LIVE", "f2-invented", id, target.answerSha256()))).isEmpty();
        }
    }
}
