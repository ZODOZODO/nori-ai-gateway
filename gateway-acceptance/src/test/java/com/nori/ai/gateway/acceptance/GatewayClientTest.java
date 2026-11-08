package com.nori.ai.gateway.acceptance;

import com.nimbusds.jose.JWSObject;
import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/** stub Gateway 로 N10 호출 순서·서명 시각·dlg 비노출을 확인한다. */
class GatewayClientTest {
    static final String CLOCK = "2026-01-01T00:10:00Z";
    final List<String> calls = new CopyOnWriteArrayList<>();
    final Map<String,String> headers = new HashMap<>();
    HttpServer server;
    GatewayClient client;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
                calls.add(key + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (key.startsWith("POST /v1/")) for (String name : List.of("Authorization", "X-Actor-Assertion", "Idempotency-Key")) headers.put(name, exchange.getRequestHeaders().getFirst(name));
                byte[] body = key.equals("GET /__test/clock") ? ("{\"now\":\"" + CLOCK + "\",\"mode\":\"FROZEN\"}").getBytes(StandardCharsets.UTF_8) : new byte[0];
                int status = key.equals("POST /__test/tcmock/delegations") ? 204 : key.startsWith("POST /v1/") ? 202 : 200;
                exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
                if (body.length > 0) exchange.getResponseBody().write(body);
            }
        });
        server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var http = HttpClient.newHttpClient();
        client = new GatewayClient(http, base, "service-token-value", StubAssertionSigner.ephemeral("test-eph-gatewayclient"), new HookClient(http, base, "hook-token-value"));
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void registersDelegationBeforeAdmissionAndSignsWithGatewayClock() throws Exception {
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out, originalErr = System.err;
        String dlg;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8)); System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
            dlg = client.newDlg();
            var response = client.submit("/v1/query", "fixture-req-1", Map.of("iss", "tc-ui-backend", "aud", "ai-gateway", "sys", "TC", "sub", "acceptance-actor"), dlg, "{\"query\":\"q\"}");
            assertThat(response.statusCode()).isEqualTo(202);
        } finally { System.setOut(originalOut); System.setErr(originalErr); }
        int delegation = indexOf("POST /__test/tcmock/delegations"), admission = indexOf("POST /v1/query");
        assertThat(delegation).isNotNegative().isLessThan(admission);
        assertThat(calls.get(delegation)).contains("\"sub\":\"acceptance-actor\"").contains(dlg);
        var payload = JWSObject.parse(headers.get("X-Actor-Assertion")).getPayload().toJSONObject();
        assertThat(new BigDecimal(payload.get("iat").toString()).longValue()).isEqualTo(java.time.Instant.parse(CLOCK).getEpochSecond());
        assertThat(payload.get("dlg")).isEqualTo(dlg);
        assertThat(headers.get("Idempotency-Key")).isEqualTo(GatewayClient.idempotencyKey("fixture-req-1")).doesNotContain("fixture-req-1");
        assertThat(headers.get("Authorization")).isEqualTo("Bearer service-token-value");
        // dlg 원문은 요청 본문·단언 밖(stdout·stderr·증거 블록)에 나타나지 않는다.
        assertThat(stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8)).doesNotContain(dlg);
        var block = new ByteArrayOutputStream();
        EvidenceBlockWriter.write(new PrintStream(block, true, StandardCharsets.UTF_8), List.of(EvidenceBlockWriter.record(EvidenceBlockWriterTest.CONTEXT,
            Map.of("kind", "RUN", "status", "COMPLETED", "executedTestCount", 1))));
        assertThat(block.toString(StandardCharsets.UTF_8)).doesNotContain(dlg);
        assertThat(dlg).matches("[A-Za-z0-9_-]{43}");
        assertThat(GatewayClient.deterministicDlg(new byte[32], "fixture-req-1")).isEqualTo(GatewayClient.deterministicDlg(new byte[32], "fixture-req-1"))
            .isNotEqualTo(GatewayClient.deterministicDlg(new byte[32], "fixture-req-2"));
    }

    @Test void hookFailureMessageCarriesNoToken() {
        server.removeContext("/");
        server.createContext("/", exchange -> { try (exchange) { exchange.sendResponseHeaders(409, -1); } });
        assertThatThrownBy(() -> new HookClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "hook-token-value").reset())
            .hasMessage("HOOK_FAILED:/__test/reset:409").hasMessageNotContaining("hook-token-value");
    }
    private int indexOf(String prefix) { for (int i = 0; i < calls.size(); i++) if (calls.get(i).startsWith(prefix)) return i; return -1; }
}
