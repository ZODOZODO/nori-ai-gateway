package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.PromptTokenCounting;
import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.tcmock.MockScenarioStore;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * SPEC §14.5·§12.3 LLM 호출 시도 감사(LLM_CALL, AC-31 판정 원천)를 실제 Job 경로(JobWorker → ClassificationService·ComposerService → 일반 감사 writer → PG)로 확인한다.
 * 시도마다 1행(재시도 포함), 토큰·버전·축소 단계만 저장, 질문·응답 원문 0, 요청·trace 식별자는 같은 Job 의 접수 감사에서 파생, 예산 assert 로 막힌 시도는 sent=false.
 * 축소 1~4 뒤에도 Composer 입력이 상한을 넘으면 Composer 호출 시도가 없어 감사도 없다(AC-43-COMP fixture {@code composer-oversize}).
 */
class LlmCallAuditIT extends LlmStubSupport {
    private List<Map<String,Object>> rows(String job) {
        return jdbc().sql("SELECT token_usage::text AS tokens, versions::text AS versions, reduction::text AS reduction, status, error_code, timings::text AS timings, request_id, trace_id, tool_id, task_type, row_to_json(a)::text AS row"
            + " FROM gw.audit_event a WHERE job_id=:job AND event_type='LLM_CALL' ORDER BY id").param("job", job).query().listOfRows();
    }

    @Test void everyClassificationAndComposerAttemptLeavesOneTextFreeAuditRowWithTokensVersionsAndReduction() throws Exception {
        String wrong = "EQP-WRONGAUDIT";
        var job = admitScripted("llm-audit", "AUDITQZ EQP-001 상태 알려줘", windowOnly(),
            List.of(classify("{\"t\":\"LQ\",\"eqp\":\"" + wrong + "\",\"from\":null,\"to\":null,\"n\":[\"S\"],\"a\":\"B\"}"), classify(wire("LQ", "EQP-001", null, null, null)), compose("COMPOSEDQZ 연결 중이다[e1].")),
            Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("SUCCEEDED");
        var rows = rows(job.jobId());
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> json.readTree((String) row.get("tokens")).path("callType").asString()).containsExactly("CLASSIFY", "CLASSIFY", "COMPOSER");
        assertThat(rows).extracting(row -> row.get("status")).containsExactly("FAILED", "SUCCESS", "SUCCESS");
        assertThat(rows.getFirst().get("error_code")).isEqualTo("PLAN_INVALID");
        var accepted = jdbc().sql("SELECT request_id, trace_id FROM gw.audit_event WHERE job_id=:job AND event_type='JOB_ACCEPTED'").param("job", job.jobId()).query().singleRow();
        for (var row : rows) {
            var tokens = json.readTree((String) row.get("tokens"));
            assertThat(tokens.path("sent").asBoolean()).isTrue();
            int input = tokens.path("inputTokens").asInt();
            // 고정 응답 usage.promptTokens 를 비우면 ScriptedChatTransport 가 같은 Gateway 계수를 돌려준다(서버 잘림 0 의 양성 대조).
            assertThat(tokens.path("promptTokens").asInt()).isEqualTo(input).isPositive();
            assertThat(tokens.path("maxTokens").asInt()).isEqualTo(tokens.path("callType").asString().equals("CLASSIFY") ? 5_120 : 5_248);
            assertThat(json.readTree((String) row.get("versions")).path("model").asString()).isEqualTo("qwen3:4b");
            assertThat(json.readTree((String) row.get("timings")).path("latencyMs").isIntegralNumber()).isTrue();
            assertThat(row.get("request_id")).isEqualTo(accepted.get("request_id")); assertThat(row.get("trace_id")).isEqualTo(accepted.get("trace_id"));
            assertThat(row.get("tool_id")).isNull(); assertThat(row.get("task_type")).isNull();
            assertThat((String) row.get("row")).doesNotContain("AUDITQZ", wrong, "COMPOSEDQZ", "상태 알려줘", "연결 중이다");
        }
        assertThat(json.readTree((String) rows.get(0).get("versions")).path("prompt").asString()).isEqualTo("classify-v3");
        assertThat(json.readTree((String) rows.get(2).get("versions")).path("prompt").asString()).isEqualTo("compose-v1");
        assertThat(json.readTree((String) rows.get(0).get("reduction"))).isEmpty();
        assertThat(json.readTree((String) rows.get(2).get("reduction")).isArray()).isTrue();
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY) + scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).as("행 수 = 실제 전송 수").isEqualTo(rows.size());
    }

    @Test void aClassificationBlockedByThePreCallAssertIsAuditedAsNotSentWithItsInputCount() throws Exception {
        String question = "EQP-001 의 최근 통신 오류와 로그를 모두 자세히 분석해 줘. ".repeat(400);
        var job = admitScripted("llm-audit-budget", question, windowOnly(), List.of(compose("서술")), Optional.empty(), null);
        assertThat(terminal(job)).isEqualTo("FAILED");
        var row = rows(job.jobId());
        assertThat(row).singleElement().satisfies(value -> {
            var tokens = json.readTree((String) value.get("tokens"));
            assertThat(tokens.path("callType").asString()).isEqualTo("CLASSIFY"); assertThat(tokens.path("sent").asBoolean()).isFalse();
            assertThat(tokens.path("inputTokens").asInt()).isGreaterThan(3_072); assertThat(tokens.path("promptTokens").isNull()).isTrue();
            assertThat(value.get("status")).isEqualTo("FAILED"); assertThat(value.get("error_code")).isEqualTo("INPUT_BUDGET_EXCEEDED");
        });
        assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).isZero();
    }

    @Test void theComposerOversizeScenarioStillExceedsTheComposerLimitAfterReductionWhileClassificationIsSentWithinItsLimit() throws Exception {
        var store = context.getBean(MockScenarioStore.class);
        store.select("composer-oversize");
        try {
            var job = admitScripted("llm-audit-oversize", "EQP-001 상태와 적용 모델 정의 알려줘", windowOnly(),
                List.of(classify(wire("LQ", "EQP-001", null, null, null, List.of("S", "N"), "B")), compose("대체 서술 FALLBACKQZ")), Optional.empty(), null);
            assertThat(terminal(job)).isEqualTo("FAILED");
            assertThat(result(job.jobId()).get("error_code")).isEqualTo("INPUT_BUDGET_EXCEEDED");
            assertThat(scripts().scriptedCalls(job.jobId(), CallType.CLASSIFY)).isEqualTo(1);
            assertThat(scripts().scriptedCalls(job.jobId(), CallType.COMPOSER)).isZero();
            assertThat(payload(job.jobId()).toString()).doesNotContain("FALLBACKQZ");
            assertThat(rows(job.jobId())).singleElement().satisfies(row -> {
                var tokens = json.readTree((String) row.get("tokens"));
                assertThat(tokens.path("callType").asString()).isEqualTo("CLASSIFY"); assertThat(tokens.path("sent").asBoolean()).isTrue();
                assertThat(tokens.path("inputTokens").asInt()).isPositive().isLessThanOrEqualTo(3_072); assertThat(row.get("status")).isEqualTo("SUCCESS");
            });
            // 축소 4단계(outputSchema 필수 필드만) 뒤의 모델 조회 결과 하나만으로도 Composer 입력 상한을 넘는다.
            var stored = json.readTree(jdbc().sql("SELECT payload::text FROM gw.tool_execution WHERE job_id=:id AND capability_id='tc.model.read'").param("id", job.jobId()).query(String.class).single());
            var schema = context.getBean(CapabilityRegistry.class).find("tc.model.read").orElseThrow().outputSchema();
            var required = SchemaContracts.JSON.createArrayNode();
            for (var block : stored.path("result").path("blocks")) required.add(ComposerInputs.requiredOnly(block.path("content"), schema));
            assertThat(required.toString()).contains("STEP-320");
            assertThat(context.getBean(PromptTokenCounting.class).count(List.of(new ChatMessage(ChatMessage.Role.USER, required.toString())))).isGreaterThan(2_816);
        } finally { store.select("default"); }
    }
}
