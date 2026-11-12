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
 * 고정 입력(F0 v2 골든 질문·경계 케이스의 질문과 Context)에 대한 제품 분류 wire v3 메시지 조립 결과를 core 시험 자원의
 * 분류 wire v3 메시지 golden(messagesSha256)과 대조한다(Plan §16.3 R0 "v2 결속 WIP 시험 처리"). v2 바이트 대조는 gateway-eval
 * F0V4RegistrationDraftTest 의 F0 이력 회귀에만 남기며, 제품 코드는 gateway-eval 을 의존하지 않는다.
 * 재시도 요지 코드는 F0 ClassificationJudge.RETRY_ERROR_CODES 와 공통인 코드만 쓴다(Path A 전용 STEPS_INVALID 제외, 새 코드 0).
 */
class ClassificationV3MessageContractTest {
    private static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    private static final Path PROMPT = ROOT.resolve("modules/apps/ai-gateway-app/src/main/resources/prompts/classify-v3.txt");
    private static final Path GOLDEN = ROOT.resolve("modules/ai-gateway-core/src/test/resources/llm/classify-wire-v3-messages.json");
    /** F0 ClassificationJudge.RETRY_ERROR_CODES 중 분류 기본 경로가 내는 코드(같은 이름·같은 순서). */
    static final List<String> RETRY_CODES = List.of("FINISH_LENGTH", "FINISH_REASON_INVALID", "USAGE_EXCEEDS_LIMIT", "JSON_PARSE_FAILED", "SCHEMA_KEYS_INVALID",
        "ENUM_INVALID", "QREF_INVALID", "TYPE_INVALID", "FORMAT_INVALID", "ARG_LENGTH_EXCEEDED");

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
        for (JsonNode ref : context.get("selectedResourceRefs")) refs.add(Map.of("type", ref.get("type").asString(), "ref", ref.get(ref.has("id") ? "id" : "ref").asString()));
        fields.put("resourceRefs", refs);
        return new JobRequestInput("message", 1, question, fields);
    }

    static String sha(List<ChatMessage> messages) throws Exception {
        var array = SchemaContracts.JSON.createArrayNode();
        for (ChatMessage message : messages) array.addObject().put("role", message.role().name().toLowerCase(java.util.Locale.ROOT)).put("content", message.content());
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ClassificationService.canonical(array).getBytes(StandardCharsets.UTF_8)));
    }

    /** 고정 입력 전체의 실제 조립 결과(id → messagesSha256). */
    static Map<String,String> assembled() throws Exception {
        var service = new ClassificationService(new ScriptedClient(), TokenBudgetPolicy.current(), CODEPOINTS, expiry(new MutableClock()), LlmCallObserver.NONE,
            Files.readString(PROMPT, StandardCharsets.UTF_8), registry(json(ROOT.resolve("eval/golden/tc-registry-fixture-v2.json"))));
        Map<String,String> actual = new LinkedHashMap<>();
        for (JsonNode golden : jsonl(ROOT.resolve("eval/golden/tc-f0-v2.jsonl"))) {
            String id = golden.get("id").asString(), question = golden.get("question").asString();
            var messages = service.messages(question, ClassificationService.modelContext(input(question, golden.get("context"))), QRefContext.extract(question));
            actual.put("golden/" + id + "/first", sha(messages));
            if (id.equals("G01")) for (String code : RETRY_CODES) actual.put("golden/G01/retry/" + code, sha(ClassificationService.retry(messages, code)));
        }
        for (JsonNode boundary : jsonl(ROOT.resolve("eval/f0/v2/output-boundary-cases.jsonl"))) {
            String question = boundary.get("question").asString();
            actual.put("boundary/" + boundary.get("caseId").asString() + "/first",
                sha(service.messages(question, ClassificationService.modelContext(input(question, boundary.get("context"))), QRefContext.extract(question))));
        }
        return actual;
    }

    @Test void productAssemblyMatchesTheRegisteredWireV3MessageGoldenForEveryFixedInputAndRetryCode() throws Exception {
        // core 시험 classpath 는 앱 계약 자원(manifests·workflows)만 포함하므로 golden 은 저장소 경로로 읽는다.
        JsonNode golden = json(GOLDEN);
        assertThat(golden.get("promptSha256").asString()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(PROMPT))));
        Map<String,String> expected = new LinkedHashMap<>();
        golden.get("rows").forEach(row -> expected.put(row.get("id").asString(), row.get("messagesSha256").asString()));
        var actual = assembled();
        assertThat(actual).hasSizeGreaterThanOrEqualTo(40).containsExactlyEntriesOf(expected);
    }

    @Test void retryCodesAreExactlyTheSharedF0CodesAndRetryCarriesOnlyTheFixedSummary() throws Exception {
        assertThat(RETRY_CODES).doesNotContain("STEPS_INVALID").doesNotHaveDuplicates();
        var original = List.of(new ChatMessage(ChatMessage.Role.SYSTEM, "s"), new ChatMessage(ChatMessage.Role.USER, "u"));
        for (String code : RETRY_CODES)
            assertThat(ClassificationService.retry(original, code).getLast().content()).isEqualTo("직전 출력이 무효다(오류: " + code + "). 출력 스키마에 맞는 JSON 객체 한 개만 다시 출력한다.");
    }
}
