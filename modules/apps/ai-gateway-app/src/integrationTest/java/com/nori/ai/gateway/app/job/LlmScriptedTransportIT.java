package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.app.inference.OllamaEndpointStub;
import com.nori.ai.gateway.core.budget.CallType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/**
 * N10 ScriptedChatTransport: 무장 Job 만 고정 응답, 결속 없는 동시 Job 은 로컬 LIVE endpoint 대역. 고정 응답 경로에서도 Client 의 예산 assert·잘림 감지가 앞뒤에 있다.
 * PromptWatchHook 은 sentinel 건수만 반환한다.
 */
class LlmScriptedTransportIT extends LlmStubSupport {
    @Override protected Map<String,Object> extraApplicationProperties() {
        var properties = new HashMap<String,Object>(super.extraApplicationProperties());
        properties.put("ai.gateway.capacity.max-running-jobs", 2);
        return properties;
    }

    @Test void onlyTheArmedJobIsScriptedWhileAnUnboundConcurrentJobUsesTheLiveEndpoint() throws Exception {
        var chats = new AtomicInteger();
        stub.respond(request -> request.path().equals("/v1/chat/completions")
            ? (chats.getAndIncrement() == 0 ? chat(wire("GQ", null, null, null, null), 100_000) : chat("라이브 서술이다.", 100_000))
            : OllamaEndpointStub.healthy(request));
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        String body = "{\"actorSub\":\"actor\",\"requestKey\":\"armed-http\",\"scenario\":{\"taskType\":\"GENERAL_QUERY\",\"llmScript\":["
            + "{\"callType\":\"CLASSIFY\",\"content\":" + json.writeValueAsString(wire("GQ", null, null, null, null)) + ",\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":30}},"
            + "{\"callType\":\"COMPOSER\",\"content\":\"스크립트 서술이다.\",\"finishReason\":\"stop\",\"usage\":{\"completionTokens\":10}}],"
            + "\"promptWatch\":{\"sentinels\":[\"ARMEDQZ\",\"LIVEQZ\"]}}}";
        assertThat(hook("/__test/arm", "POST", body, hookToken).statusCode()).isEqualTo(204);
        var armed = admitUnarmed("armed-http", "ARMEDQZ 무엇을 물을 수 있나", Map.of(), null);
        var live = admitUnarmed("live-unbound", "LIVEQZ 무엇을 물을 수 있나", Map.of(), null);
        pauses().release(PausePointRegistry.Point.DISPATCH_HOLD, "*");
        assertThat(terminal(armed)).isEqualTo("SUCCEEDED");
        assertThat(terminal(live)).isEqualTo("SUCCEEDED");
        assertThat(payload(armed.jobId()).path("answer").asString()).isEqualTo("스크립트 서술이다.");
        assertThat(payload(live.jobId()).path("answer").asString()).isEqualTo("라이브 서술이다.");
        assertThat(scripts().scriptedCalls(armed.jobId(), CallType.CLASSIFY) + scripts().scriptedCalls(armed.jobId(), CallType.COMPOSER)).isEqualTo(2);
        assertThat(scripts().scriptedCalls(live.jobId(), CallType.CLASSIFY) + scripts().scriptedCalls(live.jobId(), CallType.COMPOSER)).isZero();
        assertThat(chats).as("LIVE endpoint 수신 = 결속 없는 Job 의 분류 1 + Composer 1").hasValue(2);
        assertThat(stub.received()).filteredOn(request -> request.path().equals("/v1/chat/completions")).allSatisfy(request -> assertThat(request.body()).contains("LIVEQZ").doesNotContain("ARMEDQZ"));
        var watch = hook("/__test/prompt-watch/armed-http", "GET", null, hookToken);
        assertThat(watch.statusCode()).isEqualTo(200);
        var rows = json.readTree(watch.body());
        assertThat(rows).hasSize(4).allSatisfy(row -> assertThat(new TreeSet<>(row.propertyNames())).containsExactly("callType", "matchCount", "sentinelIndex"));
        assertThat(watch.body()).doesNotContain("무엇을", "ARMEDQZ", "LIVEQZ", "boundSystem");
        assertThat(scripts().promptWatch(armed.jobId()).orElseThrow()).contains(new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(CallType.CLASSIFY, 0, 1),
            new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(CallType.CLASSIFY, 1, 0), new com.nori.ai.gateway.app.hooks.LlmScriptRegistry.WatchRow(CallType.COMPOSER, 0, 1));
        assertThat(hook("/__test/prompt-watch/armed-http", "GET", null, null).statusCode()).as("hook 토큰 없음").isEqualTo(404);
        assertThat(hook("/__test/prompt-watch/live-unbound", "GET", null, hookToken).statusCode()).as("무장 없는 Job").isEqualTo(404);
    }

    @Test void composerInputOverTheInputLimitIsRejectedBeforeTheScriptedTransport() throws Exception {
        var server = context.getBean(WorkflowSourceFixture.Server.class);
        server.traceVersions = List.of("image-tag"); server.spanName = "설비추적구간이름".repeat(700);
        var job = admitScripted("scripted-budget", "EQP-001 장애 원인 찾아줘", workflowFields(), List.of(classify(wire("INC", "EQP-001", null, null, null)), compose("서술")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("FAILED");
        assertThat(result(job.jobId())).containsEntry("error_code", "INPUT_BUDGET_EXCEEDED");
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isZero();
        assertThat(stub.count("/v1/chat/completions")).isZero();
    }

    @Test @Tag("AC-31") void stubUsageEqualsTheGatewayCountAndAShorterUsageIsDetectedAsTruncationBehindTheScript() throws Exception {
        var counted = admitScripted("usage-count", "EQP-001 상태 알려줘", windowOnly(), List.of(classify(wire("LQ", "EQP-001", null, null, null)), compose("상태를 확인했다[e1].")), Optional.empty(), null);
        assertThat(terminal(counted)).isEqualTo("SUCCEEDED");
        assertThat(payload(counted.jobId()).path("answer").asString()).isEqualTo("상태를 확인했다[e1].");
        var truncated = admitScripted("usage-short", "EQP-001 상태 알려줘", windowOnly(),
            List.of(classify(wire("LQ", "EQP-001", null, null, null)), new TestHookRegistry.LlmReply(CallType.COMPOSER, "상태를 확인했다[e1].", "stop", 1, 10)), Optional.empty(), null);
        assertThat(terminal(truncated)).isEqualTo("PARTIAL");
        assertThat(result(truncated.jobId())).containsEntry("error_code", "LLM_UNAVAILABLE");
        assertThat(scripts().scriptedCalls(truncated.jobId(), CallType.COMPOSER)).as("잘림 감지는 재시도하지 않는 호출 실패").isOne();
    }

    @Test void anIncidentWithOnlyOptionalStepOmittedTracesAggregatesAsSuccess() throws Exception {
        perms = Set.of("EQP_READ", "COMM_LOG_READ", "DLQ_READ", "AI_LOG_READ", "AI_TRACE_READ");
        var job = admitScripted("omitted-only", "EQP-001 장애 원인 찾아줘", workflowFields(), List.of(classify(wire("INC", "EQP-001", null, null, null)), compose("장애 근거를 확인했다[e1].")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("SUCCEEDED");
        var trace = new ArrayList<String>();
        payload(job.jobId()).path("trace").forEach(value -> trace.add(value.path("outcome").asString()));
        assertThat(trace).isNotEmpty().containsOnly("OPTIONAL_STEP_OMITTED");
        assertThat(result(job.jobId())).containsEntry("status", "SUCCESS").containsEntry("error_code", null);
    }
}
