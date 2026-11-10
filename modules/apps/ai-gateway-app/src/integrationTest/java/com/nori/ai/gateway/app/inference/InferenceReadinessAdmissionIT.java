package com.nori.ai.gateway.app.inference;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.job.P09QueueSupport;
import com.nori.ai.gateway.integration.inference.InferenceReadinessService;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static com.nori.ai.gateway.app.inference.OllamaEndpointStub.*;
import static org.assertj.core.api.Assertions.*;

/**
 * readiness stub hook(라벨 LLM_STUB, 추론 서버 무접촉)과 실제 /api/ps stub 으로 AC-33·AC-44 구성 요소를 확인한다:
 * 비READY 접수 503 + Retry-After 60 + 상태 errorCode, 경보(600s 유예·즉시), 추론·모델 관리 요청 0, 대화 이력·k8s readiness 200, 디스패처 READY 게이트.
 */
@Tag("stage-F2") @Tag("label-MOCK_API") @Tag("label-LLM_STUB")
@ExtendWith(OutputCaptureExtension.class)
class InferenceReadinessAdmissionIT extends P09QueueSupport {
    private final OllamaEndpointStub stub = new OllamaEndpointStub();
    private String conversation;

    @Override protected Map<String,Object> extraApplicationProperties() {
        var properties = new HashMap<String,Object>(super.extraApplicationProperties());
        properties.putAll(Map.of("ai.gateway.inference.mode", "enabled", "ai.gateway.inference.baseUrl", stub.baseUrl(), "ai.gateway.inference.generationModelDigest", EXPECTED_DIGEST));
        return properties;
    }
    @BeforeEach void ready() throws Exception {
        await(() -> readiness().ready());
        var created = api("POST", "/v1/conversations", "{}");
        assertThat(created.statusCode()).isEqualTo(201);
        conversation = json.readTree(created.body()).get("id").asString();
    }
    @AfterEach void stopStub() { stub.close(); }

    private InferenceReadinessService readiness() { return context.getBean(InferenceReadinessService.class); }
    private String stubState(String kind) throws Exception {
        var response = hook("/__test/readiness-stub", "POST", "{\"kind\":\"" + kind + "\"}", hookToken);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("state").asString();
    }
    private HttpResponse<String> api(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + serviceToken)
            .header("X-Actor-Assertion", signer.sign(claims("actor", null), clock().instant())).header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
    private void assertRejected(String key, String errorCode) throws Exception {
        var response = postQuery("actor", key, "새 질의");
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Retry-After")).contains("60");
        assertThat(json.readTree(response.body()).get("error").asString()).isEqualTo(errorCode);
    }
    private void assertHistoryAndProbesStayAvailable() throws Exception {
        assertThat(api("GET", "/v1/conversations/" + conversation, null).statusCode()).as("대화 이력 조회").isEqualTo(200);
        assertThat(HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/actuator/health/readiness")).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode())
            .as("k8s readiness 는 추론 상태와 분리").isEqualTo(200);
    }
    private double alert(String state) { return context.getBean(MeterRegistry.class).get("aigw.inference.alert").tag("state", state).gauge().value(); }
    private void assertNoInferenceOrManagementRequests() {
        assertThat(stub.received()).extracting(Received::path).as("추론 요청·모델 로드/pull/재시작 요청 0").containsOnly("/api/ps");
        assertThat(count("job")).as("대체 문장·Job 생성 0").isZero();
    }

    @Test @Tag("AC-33-LOADING") void loadingRejectsNewQueriesWithRetryAfterAndKeepsHistoryAndK8sReadiness() throws Exception {
        assertThat(stubState("MODEL_LOADING")).isEqualTo("MODEL_LOADING");
        assertRejected("loading", "MODEL_LOADING");
        assertHistoryAndProbesStayAvailable();
        assertThat(alert("MODEL_LOADING")).as("유예 안").isZero();
        assertNoInferenceOrManagementRequests();
    }

    @Test @Tag("AC-33-PERSIST") void loadingBeyondTheGraceRaisesOnePersistentAlertUntilReadyWithoutRecoveryOrInference() throws Exception {
        stubState("MODEL_LOADING");
        clock().advance(600);
        stubState("MODEL_LOADING");
        assertThat(alert("MODEL_LOADING")).as("정확히 600s").isZero();
        clock().advance(1);
        stubState("MODEL_LOADING");
        assertThat(alert("MODEL_LOADING")).isEqualTo(1.0);
        clock().advance(3600);
        stubState("MODEL_LOADING");
        assertThat(alert("MODEL_LOADING")).as("READY 전 해제 0").isEqualTo(1.0);
        assertRejected("persist", "MODEL_LOADING");
        assertNoInferenceOrManagementRequests();
        assertThat(stubState("OFF")).isEqualTo("READY");
        assertThat(alert("MODEL_LOADING")).isZero();
    }

    @Test @Tag("AC-33-CTX") void contextMismatchRejectsAndAlertsImmediately() throws Exception {
        assertThat(stubState("MODEL_CONTEXT_MISMATCH")).isEqualTo("MODEL_CONTEXT_MISMATCH");
        assertThat(alert("MODEL_CONTEXT_MISMATCH")).as("즉시 경보").isEqualTo(1.0);
        assertRejected("context", "MODEL_CONTEXT_MISMATCH");
        assertHistoryAndProbesStayAvailable();
        assertNoInferenceOrManagementRequests();
    }

    @Test @Tag("AC-33-DIGEST") void digestMismatchRejectsWithItsOwnCodeAlertsImmediatelyAndNeverLoadsOrPullsAModel(CapturedOutput output) throws Exception {
        assertThat(stubState("MODEL_DIGEST_MISMATCH")).isEqualTo("MODEL_DIGEST_MISMATCH");
        assertThat(alert("MODEL_DIGEST_MISMATCH")).as("600s 유예 없이 즉시 경보").isEqualTo(1.0);
        assertThat(alert("MODEL_CONTEXT_MISMATCH")).isZero();
        assertThat(output.getAll()).as("즉시 경보 코드가 허용 목록 stdout 에 원문으로 남는다").contains("\"message\":\"INFERENCE_ALERT_RAISED_MODEL_DIGEST_MISMATCH\"");
        assertRejected("digest", "MODEL_DIGEST_MISMATCH");
        assertHistoryAndProbesStayAvailable();
        clock().advance(3600);
        assertThat(stubState("MODEL_DIGEST_MISMATCH")).isEqualTo("MODEL_DIGEST_MISMATCH");
        assertThat(alert("MODEL_DIGEST_MISMATCH")).as("저절로 해제되지 않음").isEqualTo(1.0);
        assertNoInferenceOrManagementRequests();
    }

    @Test @Tag("AC-44") void noResponseIsInferenceUnavailableAndRejectsWithoutFallbackText() throws Exception {
        assertThat(stubState("INFERENCE_UNAVAILABLE")).isEqualTo("INFERENCE_UNAVAILABLE");
        assertThat(readiness().snapshot().reason()).isEqualTo(OllamaReadinessProbe.Reason.NO_RESPONSE);
        assertRejected("stopped", "INFERENCE_UNAVAILABLE");
        assertHistoryAndProbesStayAvailable();
        assertNoInferenceOrManagementRequests();
    }

    @Test void realEndpointEvaluationOrderDecidesTheAdmissionCode() throws Exception {
        stub.respond(request -> new Json(200, ps(generation(4096, OTHER_DIGEST), embedding(8192))));
        assertThat(readiness().refresh().state()).as("ctx·digest 동시 불일치").isEqualTo(OllamaReadinessProbe.State.MODEL_CONTEXT_MISMATCH);
        assertRejected("order-context", "MODEL_CONTEXT_MISMATCH");
        stub.respond(request -> new Json(200, ps(generation(8192, OTHER_DIGEST))));
        assertThat(readiness().refresh().state()).as("미상주 + digest 불일치").isEqualTo(OllamaReadinessProbe.State.MODEL_LOADING);
        assertRejected("order-loading", "MODEL_LOADING");
        stub.respond(request -> new Json(200, ps("{\"name\":\"qwen3:4b\",\"context_length\":8192}", embedding(8192))));
        var missingDigest = readiness().refresh();
        assertThat(missingDigest.state()).isEqualTo(OllamaReadinessProbe.State.INFERENCE_UNAVAILABLE);
        assertThat(missingDigest.reason()).isEqualTo(OllamaReadinessProbe.Reason.ENDPOINT_ERROR);
        assertRejected("order-digest", "INFERENCE_UNAVAILABLE");
        stub.respond(OllamaEndpointStub::healthy);
        assertThat(readiness().refresh().state()).isEqualTo(OllamaReadinessProbe.State.READY);
        assertNoInferenceOrManagementRequests();
    }

    @Test void dispatcherMovesAQueuedJobToRunningOnlyWhileReady() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        var job = admit("actor", "dispatch", Set.of(PausePointRegistry.Point.BEFORE_CLASSIFICATION));
        assertThat(state(job.jobId())).isEqualTo("QUEUED");
        stubState("MODEL_LOADING");
        pauses().release(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        Thread.sleep(1500);
        assertThat(state(job.jobId())).as("비READY 디스패치 0").isEqualTo("QUEUED");
        assertThat(jdbc().sql("SELECT count(*) FROM gw.job_event WHERE job_id=:job AND type='job.started'").param("job", job.jobId()).query(Integer.class).single()).isZero();
        stubState("OFF");
        await(() -> state(job.jobId()).equals("RUNNING"));
        pauses().release(PausePointRegistry.Point.BEFORE_CLASSIFICATION, job.jobId());
        await(() -> !Set.of("QUEUED", "RUNNING").contains(state(job.jobId())));
    }
}
