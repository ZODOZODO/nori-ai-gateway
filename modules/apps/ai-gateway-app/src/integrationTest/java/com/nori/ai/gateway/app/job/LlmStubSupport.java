package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.LlmScriptRegistry;
import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.app.inference.OllamaEndpointStub;
import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.integration.inference.InferenceReadinessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * F2 LLM_STUB 통합 시험 공통(Plan P20). 추론 모드 enabled·verify hook 활성에서 실제 ClassificationService·ComposerService·SpringAiOpenAiInferenceClient
 * (예산 assert·잘림 감지) 경로를 쓰고, 무장 Job 은 ScriptedChatTransport 고정 응답, 결속 없는 Job 은 로컬 Ollama endpoint 대역으로 간다.
 * 실제 추론 서버·공유 서버 호출은 0이다.
 */
@Tag("stage-F2") @Tag("label-LLM_STUB")
abstract class LlmStubSupport extends P09QueueSupport {
    static final Set<String> PERMS = Set.of("EQP_READ","COMM_LOG_READ","MODEL_READ","DLQ_READ","AI_LOG_READ","AI_TRACE_READ","AI_CODE_READ","AI_DOC_READ");
    protected final OllamaEndpointStub stub = new OllamaEndpointStub();
    protected Set<String> perms = PERMS;

    @Override protected Map<String,Object> extraApplicationProperties() {
        var properties = new HashMap<String,Object>(super.extraApplicationProperties());
        properties.putAll(Map.of("ai.gateway.inference.mode", "enabled", "ai.gateway.inference.baseUrl", stub.baseUrl(),
            "ai.gateway.inference.generationModelDigest", OllamaEndpointStub.EXPECTED_DIGEST));
        return properties;
    }
    @Override protected Class<?>[] extraSources() { return new Class<?>[]{HarnessObservationsEndpoint.class, WorkflowSourceFixture.class}; }
    @Override protected com.nori.ai.gateway.core.scope.VerifiedSystemScope scope(String actor) {
        var original = super.scope(actor);
        return new com.nori.ai.gateway.core.scope.VerifiedSystemScope(original.boundSystem(), original.authenticatedServiceRef(), original.actorRef(), original.sub(), perms,
            original.pdg(), original.issuedAt(), original.expiresAt(), original.requestId());
    }
    @Override protected Map<String,Object> claims(String actor, String delegation) { var values = new HashMap<>(super.claims(actor, delegation)); values.put("perm", perms.stream().sorted().toList()); return values; }
    @BeforeEach void inferenceReady() throws Exception { await(() -> context.getBean(InferenceReadinessService.class).ready()); }
    @AfterEach void closeStub() { stub.close(); }

    protected LlmScriptRegistry scripts() { return context.getBean(LlmScriptRegistry.class); }
    protected static TestHookRegistry.LlmReply classify(String wire) { return new TestHookRegistry.LlmReply(CallType.CLASSIFY, wire, "stop", null, 40); }
    protected static TestHookRegistry.LlmReply compose(String text) { return new TestHookRegistry.LlmReply(CallType.COMPOSER, text, "stop", null, 30); }
    /**
     * 분류 wire v3 고정 fixture(SPEC §28.3): {@code n} 은 기존 고정 workflow 경로를 그대로 쓰는 근거 — KQ=[N], LQ=[S], INC=[S,H,I](선택 git 단계 포함),
     * GQ·UNS=[] — 이고 {@code a}=B 다. 다른 근거·표현 길이는 {@link #wire(String,String,String,String,String,java.util.List,String)} 로 명시한다.
     */
    protected static String wire(String t, String eqp, String from, String to, String qRef) {
        var needs = switch (t) { case "KQ" -> List.of("N"); case "LQ" -> List.of("S"); case "INC" -> List.of("S", "H", "I"); default -> List.<String>of(); };
        return wire(t, eqp, from, to, qRef, needs, "B");
    }
    protected static String wire(String t, String eqp, String from, String to, String qRef, List<String> n, String a) {
        var node = SchemaContracts.JSON.createObjectNode();
        node.put("t", t); node.put("eqp", eqp); node.put("from", from); node.put("to", to); node.put("qRef", qRef);
        var needs = node.putArray("n"); n.forEach(needs::add);
        node.put("a", a);
        return node.toString();
    }
    /** 질문 시간창(Context) 과 화면 선택 리소스 없는 요청 필드. 설비는 분류 출력으로만 들어온다. */
    protected Map<String,Object> windowOnly() {
        return Map.of("context", Map.of("timezone", "UTC", "referenceTime", NOW.toString(), "timeWindow", Map.of("from", "2026-11-05T23:00:00Z", "to", "2026-11-05T23:30:00Z")));
    }
    protected JobAdmissionService.Accepted admitScripted(String key, String question, Map<String,Object> fields, List<TestHookRegistry.LlmReply> script,
            Optional<TestHookRegistry.PromptWatch> watch, String conversation) {
        hooks().arm("actor", key, new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY, Set.of(), null, false, Optional.empty(), script, watch));
        return admitUnarmed(key, question, fields, conversation);
    }
    protected JobAdmissionService.Accepted admitUnarmed(String key, String question, Map<String,Object> fields, String conversation) {
        String delegation = registeredDlg("actor");
        return inRequestSpan(() -> context.getBean(JobAdmissionService.class).accept(scope("actor"), new JobAdmissionService.AdmissionRequest("/v1/query", conversation, question, fields, key, delegation)));
    }
    protected String terminal(JobAdmissionService.Accepted job) throws Exception {
        await(() -> !Set.of("QUEUED", "RUNNING").contains(state(job.jobId())));
        return state(job.jobId());
    }
    protected tools.jackson.databind.JsonNode payload(String job) { return json.readTree(jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:id").param("id", job).query(String.class).single()); }
    protected Map<String,Object> result(String job) { return jdbc().sql("SELECT status,error_code FROM gw.result WHERE job_id=:id").param("id", job).query().singleRow(); }
    protected int tools(String job) { return jdbc().sql("SELECT count(*) FROM gw.tool_execution WHERE job_id=:id").param("id", job).query(Integer.class).single(); }
    protected void assertClarification(String job, String reason, List<String> fields) {
        var value = payload(job);
        assertThat(value.properties().stream().map(Map.Entry::getKey)).containsExactly("clarification");
        assertThat(value.path("clarification").path("reason").asString()).isEqualTo(reason);
        var actual = new ArrayList<String>(); value.path("clarification").path("fields").forEach(field -> actual.add(field.asString()));
        assertThat(actual).containsExactlyElementsOf(fields);
    }
    /** 로컬 Ollama 대역의 chat 응답(OpenAI wire). usage.prompt_tokens 는 Gateway 계수 이상이어야 잘림으로 보지 않는다. */
    protected static OllamaEndpointStub.Json chat(String content, int promptTokens) {
        var message = SchemaContracts.JSON.createObjectNode().put("role", "assistant").put("content", content);
        var body = SchemaContracts.JSON.createObjectNode().put("id", "chatcmpl-stub").put("object", "chat.completion").put("created", 1).put("model", "qwen3:4b");
        body.putArray("choices").addObject().put("index", 0).put("finish_reason", "stop").set("message", message);
        body.putObject("usage").put("prompt_tokens", promptTokens).put("completion_tokens", 20).put("total_tokens", promptTokens + 20);
        return new OllamaEndpointStub.Json(200, body.toString());
    }
}
