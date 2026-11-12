package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.budget.ChatMessage;
import com.nori.ai.gateway.core.budget.TokenBudgetPolicy;
import com.nori.ai.gateway.core.job.JobRequestInput;
import com.nori.ai.gateway.core.registry.ImplementationPhase;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.nori.ai.gateway.core.llm.LlmTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 제품 분류 메시지 조립이 F0 v2 등록 하네스(classify-v2·tc-registry-fixture-v2·tc-f0-v2·경계 케이스)와 같은 바이트를 만드는지
 * 등록 입력 audit {@code eval/f0/v2/input-audit-v2.json} 의 messagesSha256 로 대조한다. 제품 코드는 gateway-eval 을 의존하지 않는다.
 */
class ClassificationF0ContractRegressionTest {
    private static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    /** F0 ClassificationJudge.RETRY_ERROR_CODES(분류 재시도 요지 전체). */
    private static final List<String> RETRY_CODES = List.of("FINISH_LENGTH", "FINISH_REASON_INVALID", "USAGE_EXCEEDS_LIMIT", "JSON_PARSE_FAILED", "SCHEMA_KEYS_INVALID",
        "ENUM_INVALID", "QREF_INVALID", "TYPE_INVALID", "FORMAT_INVALID", "ARG_LENGTH_EXCEEDED", "STEPS_INVALID");

    private static JsonNode json(Path path) throws Exception { return SchemaContracts.JSON.readTree(Files.readString(path, StandardCharsets.UTF_8)); }
    private static List<JsonNode> jsonl(Path path) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) if (!line.isBlank()) rows.add(SchemaContracts.JSON.readTree(line));
        return rows;
    }

    private static List<String> strings(JsonNode array) { List<String> values = new ArrayList<>(); array.forEach(value -> values.add(value.asString())); return values; }

    private static String registry(JsonNode fixture) {
        List<ClassificationRegistryText.SystemArea> systems = new ArrayList<>();
        for (JsonNode system : fixture.get("systems")) systems.add(new ClassificationRegistryText.SystemArea(system.get("system").asString(),
            strings(system.get("aliases")), strings(system.get("ownedAreas"))));
        List<ClassificationRegistryText.CapabilityLine> lines = new ArrayList<>();
        for (JsonNode capability : fixture.get("capabilities")) lines.add(new ClassificationRegistryText.CapabilityLine(capability.get("capabilityId").asString(),
            capability.get("status").asString().equals("IMPLEMENTED"), ImplementationPhase.valueOf(capability.get("phase").asString())));
        return ClassificationRegistryText.render(OwnerSystem.valueOf(fixture.get("boundSystem").asString()), systems, lines);
    }

    private static JobRequestInput input(String question, JsonNode context) {
        Map<String,Object> contextFields = new LinkedHashMap<>();
        for (String key : List.of("timezone", "referenceTime", "timeWindow")) contextFields.put(key, SchemaContracts.JSON.convertValue(context.get(key), Object.class));
        Map<String,Object> fields = new HashMap<>();
        fields.put("context", contextFields);
        List<Map<String,Object>> refs = new ArrayList<>();
        // F0 golden 의 선택 리소스 키는 {type,id} 이고 제품 API·Context 계약은 {type,ref} 다(아래 sha 에서 키 이름만 되돌려 대조).
        for (JsonNode ref : context.get("selectedResourceRefs")) refs.add(Map.of("type", ref.get("type").asString(), "ref", ref.get(ref.has("id") ? "id" : "ref").asString()));
        fields.put("resourceRefs", refs);
        return new JobRequestInput("message", 1, question, fields);
    }

    private static String sha(List<ChatMessage> messages) throws Exception {
        var array = SchemaContracts.JSON.createArrayNode();
        for (ChatMessage message : messages) array.addObject().put("role", message.role().name().toLowerCase(java.util.Locale.ROOT)).put("content", message.content().replace("{\"ref\":", "{\"id\":"));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ClassificationService.canonical(array).getBytes(StandardCharsets.UTF_8)));
    }

    @Test void productAssemblyMatchesEveryRegisteredF0V2ClassifyFirstAndRetryMessage() throws Exception {
        String prompt = Files.readString(ROOT.resolve("eval/f0/v2/prompts/classify-v2.txt"), StandardCharsets.UTF_8);
        var service = new ClassificationService(new ScriptedClient(), TokenBudgetPolicy.current(), CODEPOINTS, expiry(new MutableClock()), LlmCallObserver.NONE,
            prompt, registry(json(ROOT.resolve("eval/golden/tc-registry-fixture-v2.json"))));
        Map<String,String> audit = new HashMap<>();
        for (JsonNode row : json(ROOT.resolve("eval/f0/v2/input-audit-v2.json")).get("rows")) audit.put(row.get("id").asString(), row.get("messagesSha256").asString());
        int compared = 0;
        for (JsonNode golden : jsonl(ROOT.resolve("eval/golden/tc-f0-v2.jsonl"))) {
            String id = golden.get("id").asString(), question = golden.get("question").asString();
            var input = input(question, golden.get("context"));
            var messages = service.messages(question, ClassificationService.modelContext(input), QRefContext.extract(question));
            assertThat(sha(messages)).as(id).isEqualTo(audit.get("B/" + id + "/first"));
            compared++;
            if (id.equals("G01")) for (String code : RETRY_CODES) {
                assertThat(sha(ClassificationService.retry(messages, code))).as(code).isEqualTo(audit.get("B/G01/retry/" + code));
                compared++;
            }
        }
        for (JsonNode boundary : jsonl(ROOT.resolve("eval/f0/v2/output-boundary-cases.jsonl"))) {
            String question = boundary.get("question").asString();
            var input = input(question, boundary.get("context"));
            var references = QRefContext.extract(question);
            assertThat(sha(service.messages(question, ClassificationService.modelContext(input), references))).as(boundary.get("caseId").asString())
                .isEqualTo(audit.get("boundary/" + boundary.get("caseId").asString() + "/first"));
            // 경계 케이스의 기대 resolved q 는 같은 바인딩으로 원문 그대로 복원된다.
            JsonNode expected = boundary.get("expectedOutput").get("qRef");
            assertThat(references.resolve(expected)).as("resolved q").isEqualTo(boundary.get("expectedResolvedOutput").get("q").isNull() ? null : boundary.get("expectedResolvedOutput").get("q").asString());
            compared++;
        }
        assertThat(compared).isGreaterThanOrEqualTo(30);
    }
}
