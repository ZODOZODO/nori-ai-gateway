package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RubricViewerTest {
    static final String SENTINEL = "<script>alert('RUBRIC_NARRATIVE_SENTINEL')</script>";
    @TempDir Path directory;

    @Test void servesEscapedNarrativeOnLoopbackOnlyAndRecordsOnlyVerdict() throws Exception {
        byte[] narrative = ("서술 " + SENTINEL).getBytes(StandardCharsets.UTF_8);
        var target = new RubricViewer.Target("AC-02-MISSING", "fx-1", "job-1", CanonicalJson.sha256(narrative));
        var tampered = new RubricViewer.Target("AC-02-MISSING", "fx-2", "job-2", "0".repeat(64));
        List<ObjectNode> verdicts = new CopyOnWriteArrayList<>();
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out, originalErr = System.err;
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8)); System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        try (var viewer = RubricViewer.start(0, List.of(target, tampered), t -> Optional.of(narrative), verdicts::add,
                new EvidenceBlockWriter.Context("F4", "F4-RUBRIC", "run-r", "parent-1", "a".repeat(64), null), "reviewer-1", "rubric-v1")) {
            assertThat(viewer.address().getAddress().isLoopbackAddress()).isTrue();
            var http = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + viewer.address().getPort());
            var page = http.send(HttpRequest.newBuilder(base.resolve("/target/0")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertThat(page.body()).doesNotContain("<script>").contains("&lt;script&gt;");
            var mismatch = http.send(HttpRequest.newBuilder(base.resolve("/target/1")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(mismatch.statusCode()).isEqualTo(409); assertThat(mismatch.body()).doesNotContain("RUBRIC_NARRATIVE_SENTINEL");
            assertThat(http.send(HttpRequest.newBuilder(base.resolve("/target/1/verdict")).POST(HttpRequest.BodyPublishers.ofString("verdict=PASS")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(409);
            var posted = http.send(HttpRequest.newBuilder(base.resolve("/target/0/verdict")).POST(HttpRequest.BodyPublishers.ofString("verdict=PASS")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(posted.statusCode()).isEqualTo(200);
            assertThat(viewer.finished()).isTrue();
        } finally { System.setOut(originalOut); System.setErr(originalErr); }
        assertThat(verdicts).singleElement().satisfies(record -> {
            assertThat(record.path("producer").asText()).isEqualTo("RUBRIC");
            assertThat(record.path("answerSha256").asText()).isEqualTo(target.answerSha256());
            assertThat(record.path("verdict").asText()).isEqualTo("PASS");
            assertThat(record.path("reviewerRef").asText()).isEqualTo("reviewer-1");
            assertThat(record.toString()).doesNotContain("RUBRIC_NARRATIVE_SENTINEL");
            EvidenceBlockWriterTest.assertSealed(record);
        });
        assertThat(stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8)).doesNotContain("RUBRIC_NARRATIVE_SENTINEL");
        try (var files = Files.walk(directory)) { assertThat(files.filter(Files::isRegularFile)).isEmpty(); }
    }

    /** 서술을 열람하지 않은 대상에 직접 POST 한 판정은 받지 않는다. 열람 뒤에만 RUBRIC 레코드가 생긴다. */
    @Test void verdictRequiresPriorViewOfTheMatchingNarrative() throws Exception {
        byte[] narrative = "서술".getBytes(StandardCharsets.UTF_8);
        var target = new RubricViewer.Target("AC-02-MISSING", "fx-1", "job-1", CanonicalJson.sha256(narrative));
        List<ObjectNode> verdicts = new CopyOnWriteArrayList<>();
        try (var viewer = RubricViewer.start(0, List.of(target), t -> Optional.of(narrative), verdicts::add,
                new EvidenceBlockWriter.Context("F4", "F4-RUBRIC", "run-r", "parent-1", "a".repeat(64), null), "reviewer-1", "rubric-v1")) {
            var http = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + viewer.address().getPort());
            var direct = http.send(HttpRequest.newBuilder(base.resolve("/target/0/verdict")).POST(HttpRequest.BodyPublishers.ofString("verdict=FAIL")).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(direct.statusCode()).isEqualTo(409);
            assertThat(verdicts).isEmpty();
            assertThat(viewer.finished()).isFalse();
            assertThat(http.send(HttpRequest.newBuilder(base.resolve("/target/0")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            var posted = http.send(HttpRequest.newBuilder(base.resolve("/target/0/verdict")).POST(HttpRequest.BodyPublishers.ofString("verdict=PASS")).build(), HttpResponse.BodyHandlers.discarding());
            assertThat(posted.statusCode()).isEqualTo(200);
        }
        assertThat(verdicts).singleElement().satisfies(record -> assertThat(record.path("verdict").asText()).isEqualTo("PASS"));
    }

    @Test void targetsFromAnotherParentRunAreRejected() throws Exception {
        Path file = directory.resolve("targets.json");
        Files.writeString(file, "{\"kind\":\"RUN\",\"status\":\"COMPLETED\",\"parentRunId\":\"other\",\"rubricTargets\":[{\"leafId\":\"AC-02-MISSING\",\"fixtureId\":\"fx\",\"jobId\":\"j\",\"answerSha256\":\"" + "a".repeat(64) + "\"}]}");
        assertThat(RubricViewMode.targets(file.toString(), "parent-1")).isEmpty();
        assertThat(RubricViewMode.targets(file.toString(), "other")).hasValueSatisfying(list -> assertThat(list).hasSize(1));
    }
}
