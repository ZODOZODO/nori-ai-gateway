package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 수동 rubric 열람 viewer(N16, §4.4). pod 의 {@code 127.0.0.1} 에만 bind 하고, 열람 요청마다 서술을 메모리로만 받아
 * 대상 {@code answerSha256} 과 같을 때만 HTML escape 해 {@code Cache-Control: no-store} 로 돌려준다. 판정 제출은 RUBRIC 레코드만 낸다.
 * 서술 원문은 stdout·로그·파일로 내보내지 않는다.
 */
public final class RubricViewer implements AutoCloseable {
    public record Target(String leafId, String fixtureId, String jobId, String answerSha256) {}
    /** 대상 Job 의 서술 바이트(기존 인증 {@code GET /v1/analyses/{jobId}}). 가져올 수 없으면 빈 값. */
    @FunctionalInterface public interface AnswerSource { Optional<byte[]> narrative(Target target); }

    private final HttpServer server;
    private final List<Target> targets;
    private final AnswerSource answers;
    private final Consumer<ObjectNode> verdicts;
    private final EvidenceBlockWriter.Context context;
    private final String reviewerRef, rubricVersion;
    private final Map<Integer,String> state = new ConcurrentHashMap<>();
    /** 이 viewer 에서 answerSha256 이 일치한 서술을 실제로 보여 준 대상. 판정은 이 집합의 대상에만 받는다. */
    private final Set<Integer> viewed = ConcurrentHashMap.newKeySet();

    private RubricViewer(HttpServer server, List<Target> targets, AnswerSource answers, Consumer<ObjectNode> verdicts,
                         EvidenceBlockWriter.Context context, String reviewerRef, String rubricVersion) {
        this.server = server; this.targets = List.copyOf(targets); this.answers = answers; this.verdicts = verdicts;
        this.context = context; this.reviewerRef = reviewerRef; this.rubricVersion = rubricVersion;
    }

    public static RubricViewer start(int port, List<Target> targets, AnswerSource answers, Consumer<ObjectNode> verdicts,
                                     EvidenceBlockWriter.Context context, String reviewerRef, String rubricVersion) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        var viewer = new RubricViewer(server, targets, answers, verdicts, context, reviewerRef, rubricVersion);
        server.createContext("/", viewer::handle);
        server.start();
        return viewer;
    }
    public InetSocketAddress address() { return server.getAddress(); }
    /** 모든 대상이 판정됐거나 열람 불가로 끝났으면 참. */
    public boolean finished() { return state.size() == targets.size(); }
    @Override public void close() { server.stop(0); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") && exchange.getRequestMethod().equals("GET")) { respond(exchange, 200, index()); return; }
            var match = java.util.regex.Pattern.compile("/target/(\\d{1,4})(/verdict)?").matcher(path);
            if (!match.matches()) { respond(exchange, 404, ""); return; }
            int index = Integer.parseInt(match.group(1));
            if (index >= targets.size()) { respond(exchange, 404, ""); return; }
            Target target = targets.get(index);
            if (match.group(2) == null && exchange.getRequestMethod().equals("GET")) {
                Optional<byte[]> narrative = state.containsKey(index) ? Optional.empty() : answers.narrative(target);
                if (narrative.isEmpty() || !CanonicalJson.sha256(narrative.get()).equals(target.answerSha256())) {
                    // 대상과 다른 서술은 보여주지 않고 판정도 받지 않는다(RUBRIC 없음 = NOT_RUN).
                    state.putIfAbsent(index, "UNAVAILABLE");
                    respond(exchange, 409, "<p>대상을 열람할 수 없습니다</p>");
                    return;
                }
                viewed.add(index);
                respond(exchange, 200, "<pre>" + escape(new String(narrative.get(), StandardCharsets.UTF_8)) + "</pre>"
                    + "<form method=\"post\" action=\"/target/" + index + "/verdict\"><button name=\"verdict\" value=\"PASS\">PASS</button>"
                    + "<button name=\"verdict\" value=\"FAIL\">FAIL</button></form>");
            } else if (match.group(2) != null && exchange.getRequestMethod().equals("POST")) {
                String body = new String(exchange.getRequestBody().readNBytes(64), StandardCharsets.UTF_8);
                String verdict = URLDecoder.decode(body.startsWith("verdict=") ? body.substring(8) : "", StandardCharsets.UTF_8);
                // 열람하지 않은(또는 해시가 다른) 대상에 대한 직접 POST 는 판정으로 받지 않는다.
                if (!viewed.contains(index) || !Set.of("PASS", "FAIL").contains(verdict) || state.putIfAbsent(index, verdict) != null) { respond(exchange, 409, ""); return; }
                var fields = new LinkedHashMap<String,Object>();
                fields.put("kind", "LEAF"); fields.put("producer", "RUBRIC"); fields.put("componentId", "RB:" + context.suite());
                fields.put("leafId", target.leafId()); fields.put("fixtureId", target.fixtureId()); fields.put("jobId", target.jobId());
                fields.put("answerSha256", target.answerSha256()); fields.put("reviewerRef", reviewerRef); fields.put("reviewedAt", Instant.now().toString());
                fields.put("verdict", verdict); fields.put("result", verdict); fields.put("rubricVersion", rubricVersion); fields.put("environment", "verify");
                verdicts.accept(EvidenceBlockWriter.record(context, fields));
                respond(exchange, 200, "<p>기록했습니다</p>");
            } else respond(exchange, 405, "");
        }
    }
    private String index() {
        var html = new StringBuilder("<ul>");
        for (int i = 0; i < targets.size(); i++) html.append("<li><a href=\"/target/").append(i).append("\">").append(escape(targets.get(i).leafId()))
            .append(" ").append(escape(targets.get(i).fixtureId())).append("</a> ").append(escape(state.getOrDefault(i, "PENDING"))).append("</li>");
        return html.append("</ul>").toString();
    }
    private static void respond(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    }
    static String escape(String value) {
        var out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) switch (c) {
            case '<' -> out.append("&lt;"); case '>' -> out.append("&gt;"); case '&' -> out.append("&amp;");
            case '"' -> out.append("&quot;"); case '\'' -> out.append("&#39;"); default -> out.append(c);
        }
        return out.toString();
    }
}
