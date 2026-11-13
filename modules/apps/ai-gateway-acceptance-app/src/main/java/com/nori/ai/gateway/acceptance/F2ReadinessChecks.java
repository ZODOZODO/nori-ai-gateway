package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.opentest4j.TestAbortedException;
import static com.nori.ai.gateway.acceptance.F1SecurityChecks.*;

/**
 * AC-33-*·AC-44 판정(라벨 LLM_STUB). 수단은 Gateway 쪽 readiness stub hook 뿐이며 추론 서버를 재시작·중지·재로드하지 않는다(SPEC §26.1 임차 원칙).
 * 접수 503·Retry-After·errorCode 는 실제 HTTP 응답, Job 미생성은 고정 SQL, 경보는 Gateway 로그의 고정 코드 줄 수(Loki readback)로만 본다.
 * 끝나면 항상 {@code OFF} 로 되돌리고 READY 복귀를 확인한다.
 */
public final class F2ReadinessChecks {
    /** Gateway 로그에서 고정 경보 코드가 든 줄 수. readback 수단이 없으면 NOT_RUN 으로 중단한다. */
    @FunctionalInterface public interface AlertLog { int count(String code, Instant since); }
    static final Duration ALERT_WAIT = Duration.ofSeconds(90);
    private final GatewayClient gateway; private final HookClient hooks; private final OracleQueries oracle; private final AlertLog alerts;

    public F2ReadinessChecks(GatewayClient gateway, HookClient hooks, OracleQueries oracle, AlertLog alerts) {
        this.gateway = gateway; this.hooks = hooks; this.oracle = oracle; this.alerts = alerts;
    }

    public void run(String leaf) {
        require(oracle.nonTerminalJobCount() == 0, "ISOLATION_NOT_IDLE"); hooks.reset();
        require(stub("OFF").equals("READY"), "F2_READINESS_NOT_READY_BEFORE");
        Throwable primary = null;
        try {
            switch (leaf) {
                case "AC-33-LOADING" -> unavailable("MODEL_LOADING");
                case "AC-44" -> unavailable("INFERENCE_UNAVAILABLE");
                case "AC-33-CTX" -> immediate("MODEL_CONTEXT_MISMATCH", false);
                case "AC-33-DIGEST" -> immediate("MODEL_DIGEST_MISMATCH", true);
                case "AC-33-PERSIST" -> persistent();
                default -> throw new IllegalArgumentException("F2_READINESS_LEAF_INVALID");
            }
        } catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            String restored = stub("OFF");
            if (oracle.nonTerminalJobCount() == 0) hooks.clock(Map.of("op", "RESUME_SYSTEM"));
            if (primary == null) require(restored.equals("READY"), "F2_READINESS_NOT_RESTORED");
        }
    }

    /** AC-33-LOADING·AC-44: 상태 → 새 질의 503 + Retry-After + 같은 errorCode, Job 0, 대화 이력 200. */
    private void unavailable(String kind) {
        String conversation = conversation();
        require(stub(kind).equals(kind), "F2_READINESS_STATE_" + kind);
        rejected(kind, true);
        require(gateway.get("/v1/conversations/" + conversation, F2Fixture.claims()).statusCode() == 200, "F2_HISTORY_UNAVAILABLE");
    }
    /** AC-33-CTX·AC-33-DIGEST: 즉시 경보 1건 + 503(+DIGEST 는 Retry-After) + Job 0. DIGEST 는 ctx 경보를 올리지 않는다. */
    private void immediate(String kind, boolean digest) {
        Instant since = Instant.now().minusSeconds(1);
        require(stub(kind).equals(kind), "F2_READINESS_STATE_" + kind);
        require(awaitAlert("INFERENCE_ALERT_RAISED_" + kind, since) == 1, "F2_IMMEDIATE_ALERT_" + kind);
        rejected(kind, digest);
        if (digest) require(alerts.count("INFERENCE_ALERT_RAISED_MODEL_CONTEXT_MISMATCH", since) == 0, "F2_DIGEST_RAISED_CONTEXT_ALERT");
    }
    /** AC-33-PERSIST: 제어 시계로 600s 를 넘긴 MODEL_LOADING 은 지속 경보 1건, READY 전 해제 0, 503 유지, Job 0. */
    private void persistent() {
        hooks.clock(Map.of("op", "FREEZE_AT", "at", hooks.clockNow().toString()));
        Instant since = Instant.now().minusSeconds(1);
        require(stub("MODEL_LOADING").equals("MODEL_LOADING"), "F2_READINESS_STATE_MODEL_LOADING");
        hooks.clock(Map.of("op", "ADVANCE", "seconds", 601));
        require(stub("MODEL_LOADING").equals("MODEL_LOADING"), "F2_READINESS_STATE_MODEL_LOADING");
        require(awaitAlert("INFERENCE_ALERT_RAISED_MODEL_LOADING", since) == 1, "F2_PERSIST_ALERT");
        hooks.clock(Map.of("op", "ADVANCE", "seconds", 3600));
        require(stub("MODEL_LOADING").equals("MODEL_LOADING"), "F2_READINESS_STATE_MODEL_LOADING");
        rejected("MODEL_LOADING", true);
        require(alerts.count("INFERENCE_ALERT_RAISED_MODEL_LOADING", since) == 1 && alerts.count("INFERENCE_ALERT_CLEARED", since) == 0, "F2_PERSIST_ALERT_CLEARED_BEFORE_READY");
    }

    private void rejected(String code, boolean retryAfter) {
        long before = oracle.jobCount();
        String request = "readiness-" + UUID.randomUUID();
        var body = Map.of("query", "EQP-001 상태 알려줘");
        HttpResponse<String> response = gateway.submitObserved("/v1/query", request, F2Fixture.claims(), gateway.newDlg(), json(body)).response();
        require(response.statusCode() == 503, "F2_ADMISSION_NOT_503_" + code);
        JsonNode error = parse(response.body());
        require(error.path("error").asText().equals(code), "F2_ADMISSION_CODE_" + code);
        if (retryAfter) require(response.headers().firstValue("Retry-After").filter(value -> value.matches("[1-9][0-9]{0,4}")).isPresent(), "F2_ADMISSION_RETRY_AFTER_" + code);
        require(oracle.jobCount() == before, "F2_ADMISSION_CREATED_JOB_" + code);
    }
    private String conversation() {
        var created = gateway.post("/v1/conversations", F2Fixture.claims(), "{}");
        require(created.statusCode() == 201, "F2_CONVERSATION_CREATE");
        String id = parse(created.body()).path("id").asText(); require(id.matches("[a-f0-9-]{36}"), "F2_CONVERSATION_ID");
        return id;
    }
    private String stub(String kind) {
        var state = hooks.post("/__test/readiness-stub", Map.of("kind", kind), 200).path("state").asText();
        require(Set.of("READY", "MODEL_LOADING", "MODEL_CONTEXT_MISMATCH", "MODEL_DIGEST_MISMATCH", "INFERENCE_UNAVAILABLE").contains(state), "F2_READINESS_STATE_INVALID");
        return state;
    }
    /** 로그 수집 지연을 기다려 1건 이상이 보이면 그 건수를 돌려준다. 기한 안에 0 이면 0 이다. */
    private int awaitAlert(String code, Instant since) {
        long deadline = System.nanoTime() + ALERT_WAIT.toNanos();
        do {
            int count = alerts.count(code, since);
            if (count > 0) return count;
            try { Thread.sleep(2_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("F2_WAIT_INTERRUPTED"); }
        } while (System.nanoTime() < deadline);
        return 0;
    }
    /** 배포 입력에 Loki readback 이 없을 때의 경보 관측 수단. */
    public static AlertLog unavailableAlerts() { return (code, since) -> { throw new TestAbortedException("LOKI_READBACK_MISSING"); }; }
}
