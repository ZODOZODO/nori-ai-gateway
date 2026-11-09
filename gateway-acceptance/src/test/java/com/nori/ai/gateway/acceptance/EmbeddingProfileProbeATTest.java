package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.acceptance.suite.EmbeddingProfileProbeAT;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.assertj.core.api.Assertions.*;

/** F3-PROBE 측정: MEASUREMENT 레코드 형태(측정 ID·dimension·modelDigest·벡터 0·leaf 0)와 suite 선택 경계를 확인한다. */
class EmbeddingProfileProbeATTest {
    static final String DIGEST = "3".repeat(64);
    @TempDir Path directory;
    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile String reply;

    @BeforeEach void hook() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + exchange.getRequestHeaders().getFirst("X-AIGW-Hook-Token"));
                byte[] body = reply.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); AcceptanceMeasurements.clear(); }
    private HookClient hooks() { return new HookClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "hook-token"); }

    @Test void measureKeepsOnlyDimensionAndModelDigestFromTheHook() {
        reply = "{\"dimension\":1024,\"modelDigest\":\"" + DIGEST + "\"}";
        assertThat(EmbeddingProfileProbeAT.measure(hooks())).containsExactly(Map.entry("dimension", 1024L), Map.entry("modelDigest", DIGEST));
        assertThat(requests).containsExactly("POST /__test/embedding-probe hook-token");
        reply = "{\"dimension\":3,\"modelDigest\":null}";
        assertThat(EmbeddingProfileProbeAT.measure(hooks())).containsEntry("dimension", 3L).containsEntry("modelDigest", null);
    }

    @Test void vectorsExtraFieldsOrInvalidValuesAreRejected() {
        for (String invalid : List.of("{\"dimension\":3,\"modelDigest\":\"" + DIGEST + "\",\"vector\":[0.1,0.2,0.3]}", "{\"dimension\":0,\"modelDigest\":null}",
                "{\"dimension\":\"3\",\"modelDigest\":null}", "{\"dimension\":3,\"modelDigest\":\"not-a-digest\"}", "{\"dimension\":3}", "[]")) {
            reply = invalid;
            assertThatThrownBy(() -> EmbeddingProfileProbeAT.measure(hooks())).as(invalid).isInstanceOf(AssertionError.class).hasMessage("EMBEDDING_PROBE_RESPONSE_INVALID");
        }
    }

    @Test void f3ProbeRunEmitsOnlyAMeasurementRecordWithoutLeafOrVector() throws Exception {
        byte[] spec = "# 승인 SPEC 바이트\n".getBytes(StandardCharsets.UTF_8);
        Path gzip = directory.resolve("spec.md.gz");
        try (var stream = new GZIPOutputStream(Files.newOutputStream(gzip))) { stream.write(spec); }
        var env = Map.of("AIGW_PARENT_RUN_ID", "F3-20261109T000000Z-0a1b2c3d", "AIGW_SPEC_GZIP_FILE", gzip.toString(), "AIGW_EXPECTED_SPEC_SHA256", CanonicalJson.sha256(spec),
            "AIGW_BUILD_IDENTITY_JSON", "{\"appImageDigest\":\"sha256:" + "b".repeat(64) + "\"}");
        var out = new ByteArrayOutputStream();
        reply = "{\"dimension\":1024,\"modelDigest\":\"" + DIGEST + "\"}";
        int exit = new AcceptanceMain(new AcceptanceConfig(env, Map.of("aigw.acceptance.suite", "F3-PROBE")), new PrintStream(out, true, StandardCharsets.UTF_8), (suite, otr) -> {
            assertThat(suite).isEqualTo("F3-PROBE");
            AcceptanceMeasurements.add(EmbeddingProfileProbeAT.MEASUREMENT_ID, EmbeddingProfileProbeAT.measure(hooks()));
            String xml = "<e:events xmlns:e=\"https://schemas.opentest4j.org/reporting/events/0.2.0\" xmlns:c=\"https://schemas.opentest4j.org/reporting/core/0.2.0\""
                + " xmlns:j=\"https://schemas.junit.org/open-test-reporting\"><e:started id=\"1\" name=\"embeddingProfile\" time=\"2026-11-09T00:00:00Z\"><c:metadata><c:tags>"
                + "<c:tag>suite-F3-PROBE</c:tag></c:tags></c:metadata><j:type>TEST</j:type></e:started><e:finished id=\"1\" time=\"2026-11-09T00:00:00Z\"><c:result status=\"SUCCESSFUL\"/></e:finished></e:events>";
            try { Files.writeString(otr.resolve("junit-platform-events.xml"), xml); } catch (IOException e) { throw new UncheckedIOException(e); }
        }, Files.createDirectories(directory.resolve("tmp"))).runSuite();
        assertThat(exit).isZero();
        List<JsonNode> records = EvidenceBlockWriterTest.decode(out.toString(StandardCharsets.UTF_8));
        assertThat(records).noneMatch(record -> record.path("kind").asText().equals("LEAF") || record.has("leafId"));
        assertThat(records).filteredOn(record -> record.path("kind").asText().equals("MEASUREMENT")).singleElement().satisfies(measurement -> {
            assertThat(measurement.path("measurementId").asText()).isEqualTo("f3-probe:embedding-profile");
            assertThat(measurement.path("stage").asText()).isEqualTo("F3");
            assertThat(measurement.path("suite").asText()).isEqualTo("F3-PROBE");
            assertThat(measurement.path("specShaSource").asText()).isEqualTo("FILE_HASHED");
            var names = new TreeSet<String>(); measurement.path("values").fieldNames().forEachRemaining(names::add);
            assertThat(names).containsExactly("dimension", "modelDigest");
            assertThat(measurement.path("values").path("dimension").asLong()).isEqualTo(1024);
            assertThat(measurement.path("values").path("modelDigest").asText()).isEqualTo(DIGEST);
        });
        assertThat(records.getLast().path("status").asText()).isEqualTo("COMPLETED");
        assertThat(AcceptanceMeasurements.snapshot()).as("회차 뒤 비움").isEmpty();
    }

    @Test void onlyTheF3ProbeSuiteSelectsTheProbeAndItCarriesNoLeafTag() {
        for (String suite : List.of("F1", "F2", "F2-OUTAGE", "F3", "F4"))
            assertThat(discovered(suite)).as(suite).noneMatch(test -> test.getUniqueId().contains("EmbeddingProfileProbeAT"));
        var probe = discovered("F3-PROBE");
        assertThat(probe).singleElement().satisfies(test -> {
            assertThat(test.getUniqueId()).contains("EmbeddingProfileProbeAT");
            assertThat(test.getTags()).extracting(tag -> tag.getName()).containsExactly("suite-F3-PROBE").noneMatch(tag -> tag.matches("(AC|ISO)-[0-9]{2}.*"));
        });
    }
    private static List<org.junit.platform.launcher.TestIdentifier> discovered(String suite) {
        var plan = LauncherFactory.create().discover(LauncherDiscoveryRequestBuilder.request().selectors(selectPackage(AcceptanceMain.SUITE_PACKAGE))
            .filters(TagFilter.includeTags("suite-" + suite)).build());
        return plan.getRoots().stream().flatMap(root -> plan.getDescendants(root).stream()).filter(org.junit.platform.launcher.TestIdentifier::isTest).toList();
    }
}
