package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.budget.CallType;
import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.job.CancellationToken;
import com.nori.ai.gateway.core.job.JobRequestInput;
import com.nori.ai.gateway.core.llm.ClassificationRegistryText;
import com.nori.ai.gateway.core.llm.ClassificationService;
import com.nori.ai.gateway.core.llm.LlmCallObserver;
import com.nori.ai.gateway.core.port.InferenceClient;
import com.nori.ai.gateway.core.registry.ManifestLoader;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter;
import com.nori.ai.gateway.integration.tokenizer.PromptTokenCounter;
import com.nori.ai.gateway.integration.tokenizer.QwenChatTemplateRenderer;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/**
 * SPEC §27.2 AC-32-NEEDS ⓞ: eqp 64 CODEPOINT(JSON escape 포함)·from/to 각 35자·qRef·n 6코드·a 를 모두 채운 최대 분류 wire v3 를 pin tokenizer 로 계수하고,
 * 운영 classify-v3 프롬프트·Registry 참고 정보로 조립한 실제 분류 입력과 함께 예산(답변 256·요청 5,120·입력+요청 ≤ 8192)을 확인한다(Plan §16.3 R5).
 */
class ClassificationWireV3TokenBudgetTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

    private static String resource(String path) throws Exception {
        try (var input = ClassificationWireV3TokenBudgetTest.class.getClassLoader().getResourceAsStream(path)) { assertThat(input).as(path).isNotNull(); return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private static String registryReference() throws Exception {
        var systems = new ArrayList<ClassificationRegistryText.SystemArea>();
        for (var system : SchemaContracts.JSON.readTree(resource("prompts/classify-registry-systems-v2.json")).get("systems")) {
            List<String> aliases = new ArrayList<>(), areas = new ArrayList<>();
            system.get("aliases").forEach(value -> aliases.add(value.asString())); system.get("ownedAreas").forEach(value -> areas.add(value.asString()));
            systems.add(new ClassificationRegistryText.SystemArea(system.get("system").asString(), aliases, areas));
        }
        var lines = ClassificationRegistryText.lines(OwnerSystem.TC, new ManifestLoader().loadClasspath("manifests/tc/index.json"), phase -> true);
        return ClassificationRegistryText.render(OwnerSystem.TC, systems, lines);
    }

    @Test void maximumWireV3FitsTheAnswerLimitAndTheAssembledInputFitsTheContextWithTheRequestMaxTokens() throws Exception {
        String eqp = "설비\"경계\\" + "가".repeat(54) + "EQP0";
        assertThat(eqp.codePointCount(0, eqp.length())).isEqualTo(64);
        String time = "2026-10-03T00:00:00.123456789+09:00";
        assertThat(time.length()).isEqualTo(35);
        var wire = SchemaContracts.JSON.createObjectNode().put("t", "INC").put("eqp", eqp).put("from", time).put("to", "2026-10-04T00:00:00.123456789+09:00").put("qRef", "Q1");
        var needs = wire.putArray("n"); for (EvidenceNeed need : EvidenceNeed.values()) needs.add(need.name());
        wire.put("a", "D");
        String content = wire.toString();
        assertThat(content).contains("\\\"", "\\\\");
        var policy = TokenBudgetPolicy.current();
        var budget = policy.budget(CallType.CLASSIFY);
        String question = "\"" + "검".repeat(256) + "\" " + "설비 진행이 멈춘 원인과 정상 절차 비교, 조치까지 보고서로 정리해줘".repeat(4);
        List<List<ChatMessage>> sent = new ArrayList<>();
        try (var tokenizer = new HfTokenizerCounter()) {
            int answer = tokenizer.countText(content);
            assertThat(answer).as("ⓞ 답변 토큰").isLessThanOrEqualTo(256);
            assertThat(budget.answerLimit()).isEqualTo(256);
            assertThat(budget.requestMaxTokens()).as("ⓞ 요청 max_tokens").isEqualTo(5_120);
            InferenceClient client = new InferenceClient() {
                @Override public ChatOutcome chat(CallType type, List<ChatMessage> messages, int maxTokens) {
                    sent.add(messages); assertThat(maxTokens).isEqualTo(5_120);
                    return new Completed(content, "stop", 100, answer, Duration.ofMillis(1), 100);
                }
                @Override public List<float[]> embed(List<String> inputs) { throw new UnsupportedOperationException(); }
            };
            var service = new ClassificationService(client, policy, tokenizer::countText, new ExpiryCheckpoint(new ConversationExpiryGuard(), Clock.fixed(NOW, ZoneOffset.UTC)),
                LlmCallObserver.NONE, attempt -> {}, resource("prompts/classify-v3.txt"), registryReference());
            var context = Map.<String,Object>of("context", Map.of("timezone", "+09:00", "referenceTime", "2026-10-20T10:00:00+09:00"));
            var result = service.classify(new ClassificationService.Request(new com.nori.ai.gateway.core.scope.VerifiedSystemScope(OwnerSystem.TC, "svc", new com.nori.ai.gateway.core.scope.ActorRef("actor"), "actor",
                java.util.Set.of("EQP_READ"), "a".repeat(64), NOW.minusSeconds(1), NOW.plusSeconds(60), "job-1"), "conversation", "job-1", NOW.plusSeconds(600), new CancellationToken(), new JobRequestInput("message", 1, question, context)));
            assertThat(result).as("최대 wire 는 절삭 없이 통과").isInstanceOf(ClassificationService.Classified.class);
            var classified = ((ClassificationService.Classified) result).classification();
            assertThat(classified.eqp()).isEqualTo(eqp);
            assertThat(classified.evidenceNeeds()).containsExactly(EvidenceNeed.values());
            int input = new PromptTokenCounter(new QwenChatTemplateRenderer(), tokenizer).count(sent.getFirst());
            assertThat(input).as("분류 입력 상한").isLessThanOrEqualTo(budget.inputLimit());
            assertThat(input + budget.requestMaxTokens()).as("ⓞ 입력 + max_tokens").isLessThanOrEqualTo(TokenBudgetPolicy.CONTEXT_TOKENS);
        }
    }
}
