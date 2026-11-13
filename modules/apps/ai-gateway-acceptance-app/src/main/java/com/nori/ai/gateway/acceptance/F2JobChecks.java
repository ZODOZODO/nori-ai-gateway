package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.acceptance.loki.LokiDirectQuery;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.retention.TimeWindow;
import com.nori.ai.gateway.integration.loki.LokiProperties;
import com.nori.ai.gateway.integration.source.TcSourcePolicy;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.opentest4j.TestAbortedException;
import static com.nori.ai.gateway.acceptance.F1SecurityChecks.*;

/**
 * F2 Job leaf(LLM_LIVE·LLM_STUB) 판정. 실제 Job API 응답·고정 SQL oracle·verify hook 관측(prompt-watch 호출 수, Mock 호출 수, Loki 송신 관측)만 대조하고,
 * 기대 결과를 제품에 주입하지 않는다. LLM_STUB 은 접수 전 {@code scenario.llmScript} 무장으로만 만들며 LIVE 결과를 대신하지 않는다.
 * 관측 수단이 배포본에 없으면 leaf 를 PASS 로 만들지 않고 고정 사유의 NOT_RUN(중단)으로 남긴다.
 */
public final class F2JobChecks {
    /** prompt-watch 의 질문 sentinel 위치. 같은 무장의 다른 sentinel 은 1부터다. */
    static final int QUESTION = 0;
    static final List<String> REPORT_COLUMNS = List.of("section", "evidence", "status", "sources", "observedAt", "missingItems", "serverAdded");
    /** ReportSections 의 근거 코드 표시(S,H,N,I,C,R). */
    static final Map<String,String> NEED_LABELS = Map.of("S", "현재 상태", "H", "실제 발생 이력", "N", "정상 절차·선행 조건", "I", "구현·적용 버전", "C", "기대와 실제 비교", "R", "확인·조치 절차");
    static final List<String> BUSINESS_READS = List.of("getEqpSnapshot", "getActiveWorkLots", "getRecentEvents", "listDlq", "getCommLogs", "readModel", "resolveResource");
    static final String COMPOSER_OVERSIZE_SCENARIO = "composer-oversize";
    /** Tool 실패로 기록하는 저장 상태(ResultStatus 중 Source·실행 실패). */
    static final Set<String> TOOL_FAILURES = Set.of("FAILED", "TIMEOUT", "SOURCE_UNAVAILABLE", "DENIED");
    private static final Duration STUB_TIMEOUT = Duration.ofSeconds(200), LIVE_TIMEOUT = Duration.ofSeconds(600);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 한 Job 의 접수 키·질문 sentinel·terminal 응답. */
    public record Job(String id, String requestKey, String sentinel, JsonNode response) {
        @Override public String toString() { return "F2Job[" + id + "]"; }
    }
    private final GatewayClient gateway; private final HookClient hooks; private final OracleQueries oracle;
    private final Optional<LokiDirectQuery> loki; private final Optional<String> egressTargets;
    /** pin tokenizer 텍스트 계수. 하네스에서 tokenizer 를 쓸 수 없으면 빈 값이다. */
    private final Function<String,Optional<Integer>> tokenCount;

    public F2JobChecks(GatewayClient gateway, HookClient hooks, OracleQueries oracle, Optional<LokiDirectQuery> loki, Optional<String> egressTargets, Function<String,Optional<Integer>> tokenCount) {
        this.gateway = gateway; this.hooks = hooks; this.oracle = oracle; this.loki = loki; this.egressTargets = egressTargets; this.tokenCount = tokenCount;
    }

    public void run(String leaf) {
        require(oracle.nonTerminalJobCount() == 0, "ISOLATION_NOT_IDLE"); hooks.reset();
        switch (leaf) {
            case "AC-22-LIVE" -> live();
            case "AC-29" -> foreignSystemLive();
            case "AC-38-F2" -> incidentLogsLive();
            case "AC-31" -> throw new TestAbortedException("COMPOSER_BUDGET_OBSERVATION_UNAVAILABLE");
            case "AC-22-STUB-TOOL", "AC-22-STUB-SYSTEM" -> planRejected(leaf);
            case "AC-22-GQ-HELP" -> generalHelp();
            case "AC-22-GQ-BYPASS-DATA", "AC-22-GQ-BYPASS-XSYS" -> generalBypass(leaf);
            case "AC-30-EQ", "AC-30-WM", "AC-30-ABSENT" -> resourceMissing(leaf);
            case "AC-32-SCHEMA", "AC-32-LENGTH" -> classificationFailsTwice(leaf);
            case "AC-32-RETRYOK" -> retryOk();
            case "AC-32-NEEDS" -> needs();
            case "AC-43-CLS" -> classificationOverBudget();
            case "AC-43-COMP" -> composerOverBudget();
            case "AC-35-EGRESS" -> egress();
            default -> throw new IllegalArgumentException("F2_JOB_LEAF_INVALID");
        }
    }

    // ── LLM_LIVE ─────────────────────────────────────────────────────────────────────────────────
    private void live() {
        for (var fixture : F2Fixture.fixtures("AC-22-LIVE")) {
            var job = submit(fixture.fixtureId(), fixture.question(), window(Duration.ofMinutes(30), true), null, LIVE_TIMEOUT);
            var rows = oracle.toolRows(job.id());
            long unregistered = rows.stream().filter(row -> !F2Fixture.IMPLEMENTED.contains(row.capabilityId())).count();
            long foreign = rows.stream().filter(row -> !row.capabilityId().startsWith("tc.")).count();
            sample("AC-22-LIVE", fixture.fixtureId(), job, rows, Map.of("unregisteredToolCount", unregistered, "foreignSystemToolCount", foreign));
            require(oracle.jobOwner(job.id()).orElseThrow(() -> new AssertionError("F2_JOB_OWNER_MISSING")).equals("TC"), "F2_LIVE_SYSTEM_SWITCHED");
            require(unregistered == 0 && foreign == 0, "F2_LIVE_TOOL_OUTSIDE_REGISTRY");
            byte[] narrative = F1JobChecks.narrative(job.response());
            require(!F2Fixture.forbiddenHit(fixture, new String(narrative, StandardCharsets.UTF_8)), "F2_LIVE_FORBIDDEN_CLAIM");
            F2RubricTargets.add("AC-22-LIVE", fixture.fixtureId(), job.id(), narrative);
        }
    }
    private void foreignSystemLive() {
        var job = submit("ac-29", "WM 에서 LOT-001 재고·납기 알려줘", window(Duration.ofMinutes(30), false), null, LIVE_TIMEOUT);
        var rows = oracle.toolRows(job.id());
        sample("AC-29", "ac-29", job, rows, Map.of());
        require(status(job).equals("UNSUPPORTED"), "F2_FOREIGN_NOT_UNSUPPORTED");
        require(rows.isEmpty(), "F2_FOREIGN_TOOL_EXECUTED");
        F1SourceChecks.assertReferences(payload(job));
        require(payload(job).path("evidence").isEmpty(), "F2_FOREIGN_DATA_EXPOSED");
    }
    private void incidentLogsLive() {
        hooks.clock(Map.of("op", "RESUME_SYSTEM"));
        Instant to = hooks.clockNow().truncatedTo(ChronoUnit.SECONDS), from = to.minusSeconds(3600);
        var direct = loki.orElseThrow(() -> new TestAbortedException("LOKI_POSITIVE_CONTROL_UNAVAILABLE"));
        int count = 0; for (String app : TcSourcePolicy.APPS) count += direct.measure(app, new TimeWindow(from, to)).count();
        if (count == 0) throw new TestAbortedException("LOKI_POSITIVE_CONTROL_EMPTY");
        var body = new LinkedHashMap<String,Object>(context(to, from, to)); body.put("resourceRefs", List.of(Map.of("type", "EQP", "ref", "EQP-001")));
        var job = submit("ac-38-f2", "EQP-001 에서 발생한 오류 로그를 보고 장애 원인을 분석해 줘", body, null, LIVE_TIMEOUT);
        var rows = oracle.toolRows(job.id());
        sample("AC-38-F2", "ac-38-f2", job, rows, Map.of());
        require(oracle.classifiedTaskTypes(job.id()).equals(List.of("INCIDENT_ANALYSIS")), "F2_INCIDENT_NOT_CLASSIFIED");
        require(rows.stream().anyMatch(row -> row.capabilityId().equals("tc.logs.search")), "F2_INCIDENT_LOG_TOOL_MISSING");
        require(rows.stream().allMatch(row -> row.capabilityId().startsWith("tc.")), "F2_INCIDENT_FOREIGN_TOOL");
        require(!status(job).equals("UNSUPPORTED"), "F2_INCIDENT_UNSUPPORTED");
        var boundaries = new LinkedHashMap<String,Instant>();
        for (String app : TcSourcePolicy.APPS) boundaries.put(app, oracle.retentionProbe(app).flatMap(OracleQueries.RetentionProbe::verifiedOldestAt).orElse(from));
        JsonNode observation;
        try { observation = hooks.get("/__test/source-observations/" + job.id()); } catch (HookClient.HookFailure unavailable) { throw new TestAbortedException("SOURCE_REQUEST_OBSERVATION_UNAVAILABLE"); }
        F1SourceChecks.verify(observation, job.id(), F1SourceChecks.expected(boundaries, from, to, new LokiProperties().splitInterval()), false);
    }
    private void sample(String leaf, String fixture, Job job, List<OracleQueries.ToolRow> rows, Map<String,Object> extra) {
        var values = new LinkedHashMap<String,Object>();
        values.put("resultStatus", status(job)); values.put("errorCode", errorCode(job).orElse(null));
        values.put("toolCount", rows.size()); values.put("toolFailedCount", rows.stream().filter(row -> TOOL_FAILURES.contains(row.status())).count());
        values.putAll(extra);
        F2LiveSamples.add(leaf, fixture, job.id(), values);
    }

    // ── LLM_STUB ─────────────────────────────────────────────────────────────────────────────────
    private void planRejected(String leaf) {
        String extra = leaf.equals("AC-22-STUB-TOOL") ? ",\"capabilityId\":\"tc.unknown.tool\"" : ",\"targetSystem\":\"WM\",\"capabilityId\":\"wm.inventory.get\"";
        String reply = "{\"t\":\"LQ\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null,\"n\":[\"S\"],\"a\":\"B\"" + extra + "}";
        var job = scripted(leaf, "EQP-001 상태 알려줘", window(Duration.ofMinutes(30), false), List.of(classify(reply), classify(reply)), List.of());
        failed(job, "PLAN_INVALID");
        require(oracle.jobOwner(job.id()).orElseThrow(() -> new AssertionError("F2_JOB_OWNER_MISSING")).equals("TC"), "F2_BOUND_SYSTEM_CHANGED");
        require(oracle.toolRows(job.id()).isEmpty(), "F2_REJECTED_PLAN_EXECUTED_TOOL");
        require(watch(job, "CLASSIFY", QUESTION) >= 1 && watch(job, "COMPOSER", QUESTION) == 0, "F2_REJECTED_PLAN_CALLS");
    }
    private void generalHelp() {
        var job = scripted("AC-22-GQ-HELP", "여기서 뭘 물어볼 수 있어?", Map.of(), List.of(classify(wire("GQ", null, List.of(), "B")), compose("물어볼 수 있는 기능 범위를 안내한다.")),
            List.of(F2Fixture.GQ_MANIFEST_SNIPPET, F2Fixture.PLANNED.getFirst()));
        generalCommon(job);
        var rows = new ArrayList<String>(); payload(job).path("blocks").path(0).path("content").path("rows").forEach(row -> rows.add(row.path("capabilityId").asText()));
        require(rows.equals(F2Fixture.GQ_HELP_ROWS) && rows.stream().noneMatch(F2Fixture.PLANNED::contains), "F2_GQ_HELP_ROWS");
        require(watch(job, "COMPOSER", 1) == 1 && watch(job, "COMPOSER", 2) == 0, "F2_GQ_HELP_COMPOSER_INPUT");
    }
    private void generalBypass(String leaf) {
        for (var fixture : F2Fixture.fixtures(leaf)) {
            var job = scripted(fixture.fixtureId(), fixture.question(), Map.of(), List.of(classify(wire("GQ", null, List.of(), "B")), compose(fixture.composerScript().orElseThrow())), List.of());
            generalCommon(job);
            byte[] narrative = F1JobChecks.narrative(job.response());
            require(narrative.length > 0 && !F2Fixture.forbiddenHit(fixture, new String(narrative, StandardCharsets.UTF_8)), "F2_GQ_FORBIDDEN_CLAIM");
            F2RubricTargets.add(leaf, fixture.fixtureId(), job.id(), narrative);
        }
    }
    private void generalCommon(Job job) {
        require(job.response().path("jobStatus").asText().equals("SUCCEEDED") && status(job).equals("SUCCESS"), "F2_GQ_STATUS");
        require(oracle.toolRows(job.id()).isEmpty() && businessReads() == 0, "F2_GQ_BUSINESS_READ");
        require(!payload(job).has("claims") && payload(job).path("evidence").isEmpty(), "F2_GQ_BUSINESS_CLAIM");
        require(oracle.classifiedTaskTypes(job.id()).equals(List.of("GENERAL_QUERY")), "F2_GQ_AUDIT_TASK_TYPE");
        require(watch(job, "CLASSIFY", QUESTION) == 1, "F2_GQ_CLASSIFY_CALLS");
    }
    private void resourceMissing(String leaf) {
        String eqp = switch (leaf) { case "AC-30-EQ" -> "EQ"; case "AC-30-WM" -> "WM"; default -> "EQP-NOT-FOUND"; };
        var job = scripted(leaf, "설비 상태 알려줘", window(Duration.ofMinutes(30), false), List.of(classify(wire("LQ", eqp, List.of("S"), "B"))), List.of());
        failed(job, "RESOURCE_NOT_FOUND");
        clarification(job, "RESOURCE_NOT_FOUND", List.of("targetResources"));
        require(calls("resolveResource") > 0, "F2_RESOURCE_VALIDATION_NOT_RUN");
        require(oracle.toolRows(job.id()).isEmpty() && calls("getEqpSnapshot") == 0, "F2_RESOURCE_TOOL_EXECUTED");
        require(watch(job, "CLASSIFY", QUESTION) == 1 && watch(job, "COMPOSER", QUESTION) == 0, "F2_RESOURCE_CALLS");
    }
    private void classificationFailsTwice(String leaf) {
        var reply = leaf.equals("AC-32-SCHEMA") ? classify("{\"t\":\"LQ\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"n\":[\"S\"],\"a\":\"B\"}")
            : reply("CLASSIFY", "{\"t\":\"LQ\",\"eqp\":\"EQP-0", "length", 5_120);
        var job = scripted(leaf, "EQP-001 상태 알려줘", window(Duration.ofMinutes(30), false), List.of(reply, reply), List.of());
        failed(job, "PLAN_INVALID");
        clarification(job, "PLAN_INVALID", List.of("q"));
        require(watch(job, "CLASSIFY", QUESTION) == 2, "F2_CLASSIFY_RETRY_COUNT");
        require(oracle.toolRows(job.id()).isEmpty() && watch(job, "COMPOSER", QUESTION) == 0, "F2_FAILED_CLASSIFY_PROCEEDED");
    }
    private void retryOk() {
        String wrong = "EQP-W" + letters(8);
        var job = scripted("AC-32-RETRYOK", "EQP-001 상태 알려줘", window(Duration.ofMinutes(30), false),
            List.of(classify("{\"t\":\"LQ\",\"eqp\":\"" + wrong + "\",\"from\":null,\"to\":null,\"n\":[\"S\"],\"a\":\"B\"}"), classify(wire("LQ", "EQP-001", List.of("S"), "B")), compose("EQP-001 은 연결되어 운전 중이다[e1].")), List.of());
        require(job.response().path("jobStatus").asText().equals("SUCCEEDED") && status(job).equals("SUCCESS") && errorCode(job).isEmpty(), "F2_RETRY_NOT_RECOVERED");
        require(watch(job, "CLASSIFY", QUESTION) == 2 && watch(job, "COMPOSER", QUESTION) == 1, "F2_RETRY_CALLS");
        require(oracle.tools(job.id(), "tc.eqp.getOperationalSnapshot").size() == 1 && calls("getEqpSnapshot") == 1, "F2_RETRY_TOOL_ARGUMENT");
        require(!job.response().toString().contains(wrong) && oracle.residueScan(Set.of(wrong)).matchCount() == 0, "F2_RETRY_FIRST_OUTPUT_USED");
    }
    private void classificationOverBudget() {
        // 분류 고정 응답을 두지 않는다. 분류가 한 번이라도 전송되면 유형 불일치 전송 실패(LLM_UNAVAILABLE)가 되어 예산 초과와 구별된다.
        String question = "EQP-001 의 최근 통신 오류와 로그를 모두 자세히 분석해 줘. ".repeat(400);
        var job = scripted("AC-43-CLS", question, window(Duration.ofMinutes(30), false), List.of(compose("서술")), List.of());
        failed(job, "INPUT_BUDGET_EXCEEDED");
        require(!payload(job).has("clarification"), "F2_CLS_BUDGET_CLARIFICATION");
        require(watch(job, "CLASSIFY", QUESTION) == 1 && watch(job, "COMPOSER", QUESTION) == 0, "F2_CLS_BUDGET_CALLS");
        require(oracle.toolRows(job.id()).isEmpty() && businessReads() == 0, "F2_CLS_BUDGET_TOOL");
    }
    private void composerOverBudget() {
        try { hooks.post("/__test/tcmock/scenario", Map.of("id", COMPOSER_OVERSIZE_SCENARIO), 204); }
        catch (HookClient.HookFailure failure) { if (failure.status() == 400) throw new TestAbortedException("COMPOSER_OVERSIZE_FIXTURE_UNAVAILABLE"); throw failure; }
        String fallback = "FALLBACK" + letters(8);
        var job = scripted("AC-43-COMP", "EQP-001 장애 원인 찾아줘", window(Duration.ofMinutes(30), false),
            List.of(classify(wire("INC", "EQP-001", List.of("S", "H", "I"), "B")), compose("대체 서술 " + fallback)), List.of());
        failed(job, "INPUT_BUDGET_EXCEEDED");
        require(watch(job, "CLASSIFY", QUESTION) == 1 && watch(job, "COMPOSER", QUESTION) == 0, "F2_COMP_BUDGET_CALLS");
        require(!payload(job).has("answer") && !job.response().toString().contains(fallback), "F2_COMP_BUDGET_FALLBACK_TEXT");
        require(!payload(job).path("blocks").isEmpty(), "F2_COMP_BUDGET_BLOCKS_DROPPED");
    }

    /** SPEC §27.2 AC-32-NEEDS ⓐ~ⓞ. 실패는 즉시 FAIL, 배포 관측이 없는 사례가 하나라도 있으면 끝에 NOT_RUN 이다. */
    private void needs() {
        var unobserved = new ArrayList<String>();
        accepted("a", "INC", List.of("S", "H"), List.of("S", "H"), List.of(), false, unobserved);
        accepted("b", "INC", List.of("H", "S"), List.of("S", "H"), List.of(), false, unobserved);
        accepted("c", "INC", List.of("C"), List.of("H", "N", "I", "C"), List.of("H", "N", "I"), false, unobserved);
        accepted("d", "KQ", List.of("R"), List.of("N", "R"), List.of("N"), false, unobserved);
        accepted("e", "LQ", List.of("N"), List.of("S", "N"), List.of("S"), false, unobserved);
        String base = "{\"t\":\"INC\",\"eqp\":\"EQP-001\",\"from\":null,\"to\":null,\"qRef\":null";
        rejected("f", base + "}", "SCHEMA_KEYS_INVALID");
        rejected("g", base + ",\"n\":[\"S\",\"H\"],\"a\":\"D\",\"tool\":\"tc.logs.search\"}", "SCHEMA_KEYS_INVALID");
        rejected("h", base + ",\"n\":[\"S\",\"H\"],\"a\":\"D\",\"scope\":\"WM\"}", "SCHEMA_KEYS_INVALID");
        rejected("i", wire("INC", "EQP-001", List.of("tc.logs.search"), "B"), "ENUM_INVALID");
        rejected("j", wire("INC", "EQP-001", List.of("S", "S"), "B"), "FORMAT_INVALID");
        rejected("k", wire("GQ", "EQP-001", List.of("N"), "B"), "FORMAT_INVALID");
        rejected("l", wire("KQ", "EQP-001", List.of(), "B"), "FORMAT_INVALID");
        rejected("m", wire("INC", "EQP-001", List.of("S"), "X"), "ENUM_INVALID");
        accepted("n", "INC", List.of("S", "H"), List.of("S", "H"), List.of(), true, unobserved);
        maximumWire(unobserved);
        if (!unobserved.isEmpty()) throw new TestAbortedException("NEEDS_CASE_UNOBSERVED_" + String.join("", unobserved));
    }
    /** 정규화 결과는 상세 요청(a=D)의 구조화 보고 표 행(근거 코드·서버 보완)으로만 관측한다. ⓑ~ⓔ 의 a 는 SPEC 이 정하지 않아 관측 가능한 D 로 둔다. */
    private void accepted(String code, String t, List<String> wire, List<String> expected, List<String> added, boolean injected, List<String> unobserved) {
        Map<String,Object> body = new LinkedHashMap<>(window(Duration.ofMinutes(30), false));
        if (injected) { var context = new LinkedHashMap<String,Object>(castMap(body.get("context"))); context.put("evidenceNeeds", List.of("I")); body.put("context", context); body.put("evidenceNeeds", List.of("I")); }
        var job = scripted("needs-" + code, "EQP-001 진행이 멈췄어", body, List.of(classify(wire(t, "EQP-001", wire, "D")), compose("확인된 자료를 정리했다[e1].")), List.of());
        require(watch(job, "CLASSIFY", QUESTION) == 1, "F2_NEEDS_RETRY_" + code);
        if (injected) require(oracle.toolRows(job.id()).stream().noneMatch(row -> row.capabilityId().equals("tc.git.searchCode")), "F2_NEEDS_INJECTED_" + code);
        var table = reportTable(payload(job));
        if (table.isEmpty()) {
            require(payload(job).path("blocks").isEmpty() && payload(job).path("evidence").isEmpty(), "F2_NEEDS_REPORT_MISSING_" + code);
            unobserved.add(code); return;
        }
        var labels = new ArrayList<String>(); var server = new ArrayList<String>();
        for (var row : table.get().path("rows")) { labels.add(row.path("evidence").asText()); if (row.path("serverAdded").asBoolean()) server.add(row.path("evidence").asText()); }
        require(labels.equals(expected.stream().map(NEED_LABELS::get).toList()), "F2_NEEDS_BOUND_" + code);
        require(server.equals(added.stream().map(NEED_LABELS::get).toList()), "F2_NEEDS_SERVER_ADDED_" + code);
    }
    private void rejected(String code, String content, String reason) {
        var job = scripted("needs-" + code, "EQP-001 진행이 멈췄어", window(Duration.ofMinutes(30), false), List.of(classify(content), classify(content)), List.of("오류: " + reason + ")"));
        failed(job, "PLAN_INVALID");
        require(watch(job, "CLASSIFY", QUESTION) == 2 && watch(job, "CLASSIFY", 1) == 1, "F2_NEEDS_RETRY_REASON_" + code);
        require(oracle.toolRows(job.id()).isEmpty() && watch(job, "COMPOSER", QUESTION) == 0, "F2_NEEDS_REJECTED_PROCEEDED_" + code);
    }
    /** ⓞ: pin tokenizer 로 센 최대 wire 답변 토큰(하네스 계수)과 배포 예산 assert 통과(분류 재시도 0·예산 초과 0)를 함께 본다. */
    private void maximumWire(List<String> unobserved) {
        String eqp = "설비\"경계\\" + "가".repeat(54) + "EQP0";
        var node = CanonicalJson.MAPPER.createObjectNode().put("t", "INC").put("eqp", eqp).put("from", "2026-10-03T00:00:00.123456789+09:00").put("to", "2026-10-04T00:00:00.123456789+09:00").put("qRef", "Q1");
        var codes = node.putArray("n"); for (String need : List.of("S", "H", "N", "I", "C", "R")) codes.add(need);
        node.put("a", "D");
        String content = node.toString();
        var counted = tokenCount.apply(content);
        if (counted.isEmpty()) { unobserved.add("o"); return; }
        int answer = counted.get();
        var budget = TokenBudgetPolicy.current().budget(CallType.CLASSIFY);
        require(answer <= 256 && budget.answerLimit() == 256 && budget.requestMaxTokens() == 5_120, "F2_NEEDS_MAX_WIRE_BUDGET");
        String question = "\"" + "검".repeat(256) + "\" " + "설비 진행이 멈춘 원인과 정상 절차 비교, 조치까지 보고서로 정리해줘".repeat(4);
        var job = scripted("needs-o", question, window(Duration.ofMinutes(30), false), List.of(reply("CLASSIFY", content, "stop", answer), compose("확인된 자료를 정리했다.")), List.of());
        require(watch(job, "CLASSIFY", QUESTION) == 1, "F2_NEEDS_MAX_WIRE_RETRIED");
        require(errorCode(job).filter(Set.of("PLAN_INVALID", "INPUT_BUDGET_EXCEEDED", "LLM_UNAVAILABLE")::contains).isEmpty(), "F2_NEEDS_MAX_WIRE_REJECTED");
    }

    // ── AC-35-EGRESS ─────────────────────────────────────────────────────────────────────────────
    private void egress() {
        String raw = egressTargets.orElseThrow(() -> new TestAbortedException("EGRESS_TARGETS_INPUT_MISSING"));
        JsonNode targets;
        try { targets = CanonicalJson.MAPPER.readTree(raw); } catch (java.io.IOException e) { throw new TestAbortedException("EGRESS_TARGETS_INPUT_INVALID"); }
        var allowed = new ArrayList<String>(); var blocked = new ArrayList<String>();
        if (!targets.isArray()) throw new TestAbortedException("EGRESS_TARGETS_INPUT_INVALID");
        for (var target : targets) {
            String id = target.path("id").asText(), expect = target.path("expect").asText();
            if (!id.matches("[A-Za-z0-9_.-]{1,64}") || target.size() != 2) throw new TestAbortedException("EGRESS_TARGETS_INPUT_INVALID");
            if (expect.equals("ALLOWED")) allowed.add(id); else if (expect.equals("BLOCKED")) blocked.add(id); else throw new TestAbortedException("EGRESS_TARGETS_INPUT_INVALID");
        }
        if (allowed.size() < 2 || blocked.size() < 2) throw new TestAbortedException("EGRESS_TARGETS_INPUT_INVALID");
        var probe = new EgressProbeClient(hooks);
        for (String id : allowed) { var outcome = probe.probe(id); require(outcome.targetId().equals(id), "F2_EGRESS_TARGET_MISMATCH"); if (!outcome.connected()) throw new TestAbortedException("EGRESS_POSITIVE_CONTROL_FAILED"); }
        for (String id : blocked) { var outcome = probe.probe(id); require(outcome.targetId().equals(id), "F2_EGRESS_TARGET_MISMATCH"); require(!outcome.connected(), "F2_EGRESS_BLOCKED_CONNECTED"); }
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────────────────────────
    private Job scripted(String fixture, String question, Map<String,Object> fields, List<ObjectNode> script, List<String> extraSentinels) {
        String sentinel = "PW" + letters(10);
        var scenario = CanonicalJson.MAPPER.createObjectNode().put("taskType", "GENERAL_QUERY");
        ArrayNode replies = scenario.putArray("llmScript"); script.forEach(replies::add);
        var sentinels = scenario.putObject("promptWatch").putArray("sentinels"); sentinels.add(sentinel); extraSentinels.forEach(sentinels::add);
        return submit(fixture, sentinel + " " + question, fields, scenario, STUB_TIMEOUT, sentinel);
    }
    private Job submit(String fixture, String question, Map<String,Object> fields, ObjectNode scenario, Duration timeout) { return submit(fixture, question, fields, scenario, timeout, null); }
    private Job submit(String fixture, String question, Map<String,Object> fields, ObjectNode scenario, Duration timeout, String sentinel) {
        String request = fixture + "-" + UUID.randomUUID(); String key = GatewayClient.idempotencyKey(request);
        if (scenario != null) hooks.arm(F2Fixture.SUB, key, scenario);
        var body = new LinkedHashMap<String,Object>(); body.put("query", question); body.putAll(fields);
        var admitted = gateway.submitObserved("/v1/query", request, F2Fixture.claims(), gateway.newDlg(), json(body)).response();
        require(admitted.statusCode() == 202, "F2_JOB_NOT_ACCEPTED");
        String id = parse(admitted.body()).path("jobId").asText(); require(id.matches("[A-Za-z0-9_:-]{1,160}"), "F2_JOB_ID_INVALID");
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            var response = gateway.get("/v1/analyses/" + id, F2Fixture.claims()); require(response.statusCode() == 200, "F2_JOB_NOT_READABLE");
            var result = parse(response.body());
            if (!Set.of("QUEUED", "RUNNING").contains(result.path("jobStatus").asText())) {
                require(result.path("jobId").asText().equals(id) && result.hasNonNull("result"), "F2_RESULT_MISSING"); require(oracle.resultRowCount(id) == 1, "F2_RESULT_NOT_UNIQUE");
                return new Job(id, key, sentinel, result);
            }
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("F2_WAIT_INTERRUPTED"); }
        } while (System.nanoTime() < deadline);
        throw new AssertionError("F2_JOB_TIMEOUT");
    }
    /** 무장 Job 의 호출 유형별 sentinel 포함 호출 수. 결속 Job 의 관찰이 아직 없으면(404) 0 이다. */
    int watch(Job job, String callType, int sentinelIndex) {
        if (job.sentinel() == null) throw new IllegalStateException("F2_WATCH_NOT_ARMED");
        var rows = hooks.find("/__test/prompt-watch/" + job.requestKey());
        if (rows.isEmpty()) return 0;
        int found = 0;
        for (var row : rows.get()) if (row.path("callType").asText().equals(callType) && row.path("sentinelIndex").asInt() == sentinelIndex) found = row.path("matchCount").asInt();
        return found;
    }
    private long calls(String operation) { return hooks.get("/__test/tcmock/calls").path(operation).asLong(); }
    private long businessReads() { var calls = hooks.get("/__test/tcmock/calls"); return BUSINESS_READS.stream().mapToLong(operation -> calls.path(operation).asLong()).sum(); }
    private Map<String,Object> window(Duration length, boolean resource) {
        Instant now = hooks.clockNow().truncatedTo(ChronoUnit.SECONDS);
        var body = new LinkedHashMap<String,Object>(context(now, now.minus(length), now));
        if (resource) body.put("resourceRefs", List.of(Map.of("type", "EQP", "ref", "EQP-001")));
        return body;
    }
    private static Map<String,Object> context(Instant reference, Instant from, Instant to) {
        return Map.of("context", Map.of("timezone", "UTC", "referenceTime", reference.toString(), "timeWindow", Map.of("from", from.toString(), "to", to.toString())));
    }
    private static JsonNode payload(Job job) { return job.response().path("result").path("payload"); }
    private static String status(Job job) { return job.response().path("result").path("status").asText(); }
    private static Optional<String> errorCode(Job job) { var value = job.response().path("result").path("errorCode"); return value.isTextual() ? Optional.of(value.asText()) : Optional.empty(); }
    private static void failed(Job job, String code) {
        require(job.response().path("jobStatus").asText().equals("FAILED") && status(job).equals("FAILED") && errorCode(job).equals(Optional.of(code)), "F2_EXPECTED_FAILED_" + code);
    }
    private static void clarification(Job job, String reason, List<String> fields) {
        var value = payload(job).path("clarification"); var actual = new ArrayList<String>(); value.path("fields").forEach(field -> actual.add(field.asText()));
        require(value.path("reason").asText().equals(reason) && actual.equals(fields), "F2_CLARIFICATION_" + reason);
    }
    static Optional<JsonNode> reportTable(JsonNode payload) {
        for (var block : payload.path("blocks")) {
            var columns = new ArrayList<String>(); block.path("content").path("columns").forEach(column -> columns.add(column.asText()));
            if (block.path("type").asText().equals("table") && columns.equals(REPORT_COLUMNS)) return Optional.of(block.path("content"));
        }
        return Optional.empty();
    }
    static String wire(String t, String eqp, List<String> needs, String answerMode) {
        var node = CanonicalJson.MAPPER.createObjectNode().put("t", t); if (eqp == null) node.putNull("eqp"); else node.put("eqp", eqp);
        node.putNull("from"); node.putNull("to"); node.putNull("qRef");
        var codes = node.putArray("n"); needs.forEach(codes::add); node.put("a", answerMode);
        return node.toString();
    }
    static ObjectNode classify(String content) { return reply("CLASSIFY", content, "stop", 40); }
    static ObjectNode compose(String text) { return reply("COMPOSER", text, "stop", 30); }
    static ObjectNode reply(String callType, String content, String finishReason, int completionTokens) {
        var node = CanonicalJson.MAPPER.createObjectNode().put("callType", callType).put("content", content).put("finishReason", finishReason);
        node.putObject("usage").put("completionTokens", completionTokens); return node;
    }
    static String letters(int count) { var value = new StringBuilder(); for (int i = 0; i < count; i++) value.append((char) ('A' + RANDOM.nextInt(26))); return value.toString(); }
    @SuppressWarnings("unchecked") private static Map<String,Object> castMap(Object value) { return (Map<String,Object>) value; }
}
