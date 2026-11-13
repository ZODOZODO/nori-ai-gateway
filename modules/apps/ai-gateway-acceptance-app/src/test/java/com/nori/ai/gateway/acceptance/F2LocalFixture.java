package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import com.sun.net.httpserver.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * F2 판정기 loopback 대역. 제품 동작을 흉내 내지 않고, 시험이 정한 Job 결과·prompt-watch 행·Tool 행·Mock 호출 수·readiness 상태를 그대로 돌려준다.
 * 판정기가 실제 HTTP·hook·고정 SQL 경로로만 관측하는지, 그리고 무장 본문(llmScript·promptWatch)을 어떻게 보내는지를 기록한다. 운영 증거 저장소에 쓰지 않는다.
 */
final class F2LocalFixture implements AutoCloseable {
    /** 한 Job 의 시험 지정 결과. */
    static final class Outcome {
        String jobStatus = "SUCCEEDED", status = "SUCCESS", errorCode = null; Map<String,Object> payload = new LinkedHashMap<>(Map.of("blocks", List.of()));
        final List<Map<String,Object>> watch = new ArrayList<>(); final List<Object[]> tools = new ArrayList<>(); final Map<String,Object> calls = new TreeMap<>();
        List<String> taskTypes = List.of(); String owner = "TC";
        Outcome watch(String type, int index, int count) { watch.add(Map.of("callType", type, "sentinelIndex", index, "matchCount", count)); return this; }
        Outcome failed(String code) { jobStatus = "FAILED"; status = "FAILED"; errorCode = code; return this; }
        Outcome clarification(String reason, List<String> fields) { payload = new LinkedHashMap<>(Map.of("clarification", Map.of("reason", reason, "fields", fields))); return this; }
        Outcome tool(String capability, String status) { tools.add(new Object[]{capability, status}); return this; }
    }
    final HttpServer server; final GatewayClient gateway; final HookClient hooks; final OracleQueries oracle; final Instant now = Instant.parse("2026-11-13T00:00:00Z");
    /** 무장 시나리오(무장 없는 LIVE Job 은 null)와 질문으로 결과를 정한다. */
    Function<Map.Entry<JsonNode,String>,Outcome> responder = entry -> new Outcome();
    final Map<String,JsonNode> armed = new LinkedHashMap<>(); final List<String> questions = new ArrayList<>(); final List<String> clockOps = new ArrayList<>();
    final Map<String,Outcome> outcomes = new HashMap<>(); final Map<String,String> keyToJob = new HashMap<>(); final Map<String,Boolean> egress = new LinkedHashMap<>();
    final Set<String> scenarios = new HashSet<>(Set.of("default")); String readiness = "READY", restoredState = "READY"; boolean retryAfter = true; long jobs;
    Outcome last;

    F2LocalFixture() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0); server.createContext("/", this::handle); server.start();
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort()); var http = HttpClient.newHttpClient();
        hooks = new HookClient(http, base, "f2-hook"); gateway = new GatewayClient(http, base, "f2-local-service-token", StubAssertionSigner.ephemeral("test-eph-f2-local"), hooks);
        oracle = new OracleQueries(this::connect);
    }
    F2JobChecks checks(Optional<String> egressTargets, Optional<Integer> tokens) { return new F2JobChecks(gateway, hooks, oracle, Optional.empty(), egressTargets, text -> tokens); }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod(); byte[] input = exchange.getRequestBody().readAllBytes();
            JsonNode body = input.length == 0 ? null : CanonicalJson.MAPPER.readTree(input); Object result = Map.of(); int status = 200; var headers = exchange.getResponseHeaders();
            if (path.equals("/__test/clock")) { if (method.equals("POST")) clockOps.add(body.toString()); result = Map.of("now", now.toString()); }
            else if (path.equals("/__test/reset") || path.equals("/__test/tcmock/delegations")) status = 204;
            else if (path.equals("/__test/arm")) { armed.put(body.path("requestKey").asText(), body.path("scenario")); status = 204; }
            else if (path.equals("/__test/tcmock/calls")) result = last == null ? Map.of() : last.calls;
            else if (path.equals("/__test/tcmock/scenario")) status = scenarios.contains(body.path("id").asText()) ? 204 : 400;
            else if (path.startsWith("/__test/prompt-watch/")) { var job = keyToJob.get(path.substring(path.lastIndexOf('/') + 1)); var rows = job == null ? null : outcomes.get(job).watch; if (rows == null || rows.isEmpty()) status = 404; else result = rows; }
            else if (path.equals("/__test/readiness-stub")) { String kind = body.path("kind").asText(); readiness = kind.equals("OFF") ? restoredState : kind; result = Map.of("state", readiness); }
            else if (path.equals("/__test/egress-probe")) { String id = body.path("targetId").asText(); result = new LinkedHashMap<>(Map.of("targetId", id, "connected", egress.getOrDefault(id, false))); }
            else if (path.equals("/v1/conversations") && method.equals("POST")) { status = 201; result = Map.of("id", "22222222-2222-2222-2222-222222222222"); }
            else if (path.startsWith("/v1/conversations/")) result = Map.of("messages", List.of());
            else if (path.equals("/v1/query")) {
                if (!readiness.equals("READY")) { status = 503; if (retryAfter) headers.set("Retry-After", "60"); result = Map.of("error", readiness); }
                else {
                    String key = exchange.getRequestHeaders().getFirst("Idempotency-Key"), question = body.path("query").asText(); questions.add(question);
                    String job = "job-" + (++jobs); last = responder.apply(Map.entry(armed.getOrDefault(key, CanonicalJson.MAPPER.nullNode()), question));
                    outcomes.put(job, last); keyToJob.put(key, job); status = 202; result = Map.of("jobId", job);
                }
            } else if (path.startsWith("/v1/analyses/")) {
                String job = path.substring(path.lastIndexOf('/') + 1); var outcome = outcomes.get(job);
                var value = new LinkedHashMap<String,Object>(); value.put("status", outcome.status); value.put("errorCode", outcome.errorCode); value.put("payload", outcome.payload);
                result = Map.of("jobId", job, "jobStatus", outcome.jobStatus, "result", value);
            }
            byte[] bytes = CanonicalJson.MAPPER.writeValueAsBytes(result); exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length); if (status != 204) exchange.getResponseBody().write(bytes);
        }
    }
    private Connection connect(String database) throws SQLException {
        if (!database.equals(OracleQueries.DATABASE)) throw new SQLException("database", "3D000");
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
            if (!m.getName().equals("prepareStatement")) return null;
            String sql = (String) a[0]; var args = new ArrayList<Object>();
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (sp, sm, sa) -> {
                if (sm.getName().equals("setObject")) { args.add(sa[1]); return null; }
                if (!sm.getName().equals("executeQuery")) return null;
                List<Object[]> rows;
                var outcome = args.isEmpty() ? null : outcomes.get(args.getFirst().toString());
                if (sql.startsWith("SELECT capability_id, status")) rows = outcome == null ? List.of() : outcome.tools;
                else if (sql.startsWith("SELECT owner_system")) rows = List.<Object[]>of(new Object[]{outcome.owner});
                else if (sql.startsWith("SELECT task_type")) rows = outcome.taskTypes.stream().map(type -> new Object[]{type}).toList();
                else if (sql.startsWith("SELECT payload")) rows = outcome == null ? List.of() : outcome.tools.stream().filter(row -> row[0].equals(args.get(1))).map(row -> new Object[]{F1SecurityChecks.json(Map.of("status", row[1], "result", Map.of("blocks", List.of())))}).toList();
                else if (sql.contains("row_to_json") || sql.contains("verified_oldest_at")) rows = List.of();
                else rows = List.<Object[]>of(new Object[]{sql.contains("status IN") ? 0L : sql.equals("SELECT count(*) FROM gw.job") ? jobs : 1L});
                var iterator = rows.iterator();
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, new InvocationHandler() { Object[] row;
                    public Object invoke(Object rp, Method rm, Object[] ra) { return switch (rm.getName()) {
                        case "next" -> { boolean next = iterator.hasNext(); if (next) row = iterator.next(); yield next; }
                        case "getString" -> row[(int) ra[0] - 1] == null ? null : row[(int) ra[0] - 1].toString();
                        case "getLong" -> ((Number) row[(int) ra[0] - 1]).longValue(); case "getInt" -> ((Number) row[(int) ra[0] - 1]).intValue(); default -> null; }; } });
            });
        });
    }
    @Override public void close() { server.stop(0); F2RubricTargets.clear(); F2LiveSamples.clear(); }
}
