package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

/**
 * 레포의 실제 v4 사전등록 6문서(P03 v4 ⑥ — 진단 4: 프롬프트 arm V2·I × temperature 0·0.6, 개발 회귀 2: V2·I × 0)를 확인합니다(endpoint 호출 0, 측정 허가 아님).
 * 등록 수명 상태는 초안(UNCOMMITTED/null — verify 가 PREREG_NOT_COMMITTED)과 등록 완료(REGISTERED/40자리 hex — 실제 Prereg.verify 통과) 두 가지만 허용합니다.
 */
class F0V4RegistrationDraftTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    static final String DIGEST = "359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7", TEMPLATE = "2d54db2b9bb29ce7db54fea63a891f5859603813c555b1f88b5e0994652897f9";
    static final String V4_REGISTRY = "eval/f0/v4/chat-renderers-v4.json", GOLDEN = "eval/golden/tc-f0-v2.jsonl", REGRESSION = "eval/f0/v3/golden-regression-v3.jsonl";
    record Arm(String classify, String plan, String responseFormat, String projection, String retryHint) {}
    static final Arm V2 = new Arm("eval/f0/v2/prompts/classify-v2.txt", "eval/f0/v2/prompts/plan-v2.txt", "NONE", MessageAssembler.FULL, ClassificationJudge.RETRY_HINT_NONE);
    static final Arm I = new Arm("eval/f0/v3/prompts/classify-v3-f.txt", "eval/f0/v3/prompts/plan-v3-i.txt", ResponseSchema.POLICY, MessageAssembler.PRESENCE_V1, ClassificationJudge.STEPS_DETAIL_V1);
    record Registration(Arm arm, double temperature, String golden, String audit) {}
    static final Map<String,Registration> DOCS = new LinkedHashMap<>();
    static {
        DOCS.put("eval/f0/PREREG-v4-diag-v2-t0.md", new Registration(V2, 0, GOLDEN, "eval/f0/v4/input-audit-v4-v2.json"));
        DOCS.put("eval/f0/PREREG-v4-diag-i-t0.md", new Registration(I, 0, GOLDEN, "eval/f0/v4/input-audit-v4-i.json"));
        DOCS.put("eval/f0/PREREG-v4-diag-v2-t06.md", new Registration(V2, 0.6, GOLDEN, "eval/f0/v4/input-audit-v4-v2.json"));
        DOCS.put("eval/f0/PREREG-v4-diag-i-t06.md", new Registration(I, 0.6, GOLDEN, "eval/f0/v4/input-audit-v4-i.json"));
        DOCS.put("eval/f0/PREREG-v4-regression-v2.md", new Registration(V2, 0, REGRESSION, "eval/f0/v4/input-audit-v4-regression-v2.json"));
        DOCS.put("eval/f0/PREREG-v4-regression-i.md", new Registration(I, 0, REGRESSION, "eval/f0/v4/input-audit-v4-regression-i.json"));
    }
    static Prereg.Document doc(String file) { return Prereg.parse(ROOT.resolve(file)); }

    @Test void draftsBindArmTemperatureRuntimeAndBudgetAndMatchTheWorkingTree() throws Exception {
        Set<String> captureDirs = new HashSet<>();
        try (Stream<Path> files = Files.list(ROOT.resolve("eval/f0"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("PREREG-v3-.*\\.md")).toList()) captureDirs.add(Prereg.parse(file).runtimeCaptureDir());
        }
        for (var entry : DOCS.entrySet()) {
            Prereg.Document d = doc(entry.getKey()); Registration r = entry.getValue(); String file = entry.getKey();
            assertThat(d.block().path("schema").asText()).as(file).isEqualTo("f0-prereg-v4"); assertThat(d.v4()).isTrue(); assertThat(d.diagnostic()).as(file).isTrue();
            String status = d.block().path("registrationStatus").asText();
            assertThat(status.equals("UNCOMMITTED") ? d.block().path("registeredCommit").isNull() : status.equals("REGISTERED") && d.registeredCommit() != null).as(file + " 수명 상태").isTrue();
            assertThat(d.repeats()).as(file).isEqualTo(1);
            assertThat(d.profile()).isEqualTo(Prereg.RuntimeProfile.INFER_V4); assertThat(d.budget()).isEqualTo(Prereg.Budget.V4_DIAGNOSTIC);
            assertThat(d.generationModel()).isEqualTo("qwen3:4b"); assertThat(d.generationModelDigest()).isEqualTo(DIGEST); assertThat(d.contextTokens()).isEqualTo(8192);
            assertThat(d.answerLimit(MessageAssembler.Kind.CLASSIFY)).isEqualTo(192); assertThat(d.answerLimit(MessageAssembler.Kind.PLAN)).isEqualTo(256);
            assertThat(d.requestTimeout()).isEqualTo(java.time.Duration.ofMinutes(10));
            assertThat(d.temperature().asDouble()).as(file).isEqualTo(r.temperature());
            assertThat(d.responseFormat()).as(file).isEqualTo(r.arm().responseFormat()); assertThat(d.contextProjection()).as(file).isEqualTo(r.arm().projection()); assertThat(d.retryHint()).as(file).isEqualTo(r.arm().retryHint());
            // V2 arm 은 투영·재시도 힌트 키 자체가 없습니다(PREREG-v2 와 같은 요청 구성).
            assertThat(d.parameters().has("contextProjection")).as(file).isEqualTo(r.arm() == I); assertThat(d.parameters().has("retryHint")).as(file).isEqualTo(r.arm() == I);
            Map<String,String> roles = Map.of("golden", r.golden(), "classifyPrompt", r.arm().classify(), "planPrompt", r.arm().plan(), "chatRenderers", V4_REGISTRY, "inputAudit", r.audit(),
                "fixture", "eval/golden/tc-registry-fixture-v2.json", "boundaryCases", "eval/f0/v2/output-boundary-cases.jsonl", "usageMessages", "eval/f0/v2/usage-probe-input-v2.jsonl", "tokenMessages", "eval/f0/v2/token-golden-input-v2.jsonl");
            roles.forEach((role, path) -> assertThat(d.input(role)).as(file + " " + role).isEqualTo(path));
            assertThat(d.inputs().keySet()).as(file).contains("eval/py/count_input_audit_v4.py").doesNotContain("eval/py/count_input_audit_v2.py", MeasurementSession.TOKENIZER_FILE);
            assertThat(Prereg.localMismatches(ROOT, d)).as(file).isEmpty();
            assertThat(captureDirs.add(d.runtimeCaptureDir())).as(file + " 고유 capture 경로").isTrue();
            if (d.registeredCommit() == null) assertThatThrownBy(() -> Prereg.verify(ROOT, ROOT.resolve(file), GitReader.process())).as(file)
                .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_NOT_COMMITTED"));
            else assertThatCode(() -> Prereg.verify(ROOT, ROOT.resolve(file), GitReader.process())).as(file).doesNotThrowAnyException();
        }
    }

    @Test void temperaturePairsDifferOnlyInTemperature() {
        for (String arm : List.of("v2", "i")) {
            Prereg.Document cold = doc("eval/f0/PREREG-v4-diag-" + arm + "-t0.md"), warm = doc("eval/f0/PREREG-v4-diag-" + arm + "-t06.md");
            assertThat(warm.inputs()).isEqualTo(cold.inputs()); assertThat(warm.block().path("inputRoles")).isEqualTo(cold.block().path("inputRoles")); assertThat(warm.harnessDigest()).isEqualTo(cold.harnessDigest());
            ObjectNode a = cold.parameters().deepCopy(), b = warm.parameters().deepCopy(); a.remove("temperature"); b.remove("temperature");
            assertThat(b).isEqualTo(a);
            assertThat(cold.temperature().asDouble()).isZero(); assertThat(warm.temperature().asDouble()).isEqualTo(0.6);
        }
    }

    static List<InputAudit.Planned> planned(Prereg.Document d) {
        java.util.function.Function<String,Path> role = r -> ROOT.resolve(d.input(r));
        RegistryFixture fixture = RegistryFixture.load(role.apply("fixture")); GoldenSet golden = GoldenSet.load(role.apply("golden"), fixture);
        MessageAssembler classify = MessageAssembler.load(role.apply("classifyPrompt"), fixture, d.contextProjection()), plan = MessageAssembler.load(role.apply("planPrompt"), fixture, d.contextProjection());
        List<InputAudit.Planned> planned = new ArrayList<>(InputAudit.run(golden, plan, d.budget(), "A", d.retryHint()));
        planned.addAll(InputAudit.run(golden, classify, d.budget(), "B", d.retryHint())); planned.addAll(InputAudit.boundary(BoundaryCases.load(role.apply("boundaryCases")), classify, d.budget()));
        for (String use : List.of("usage", "token"))
            planned.addAll(InputAudit.probes(use, F0RunnerCli.messages(role.apply(use + "Messages"), role.apply("fixture"), role.apply("classifyPrompt"), role.apply("golden"), true, d.contextProjection()), d.contextTokens()));
        return planned;
    }

    @Test void eachV4AuditCoversEveryPlannedCallWithinTheV4ProfileAndRecordsTheV4RuntimeIdentity() {
        ChatRenderers.Renderer renderer = ChatRenderers.forTemplate(ROOT.resolve(V4_REGISTRY), TEMPLATE);
        for (String file : DOCS.keySet()) {
            Prereg.Document d = doc(file);
            List<InputAudit.Planned> planned = planned(d);
            InputAudit audit = InputAudit.load(ROOT.resolve(d.input("inputAudit")), d.contextTokens());
            assertThatCode(() -> audit.requireAll(planned)).as(file).doesNotThrowAnyException();
            JsonNode table = Json.read(ROOT.resolve(d.input("inputAudit")));
            assertThat(table.path("tokenizerSha256").asText()).isEqualTo(d.inputs().get(Prereg.RuntimeProfile.INFER_V4.tokenizerFile()));
            assertThat(table.path("templateSha256").asText()).isEqualTo(TEMPLATE); assertThat(table.path("renderer").asText()).isEqualTo(ChatRenderers.OLLAMA_GO_QWEN3_THINKING);
            assertThat(table.path("tool").asText()).isEqualTo("count_input_audit_v4.py"); assertThat(table.path("overBudgetIds")).as(file).isEmpty();
            assertThat(table.path("rows")).as(file).hasSize(planned.size());
            for (int i = 0; i < planned.size(); i++) {
                InputAudit.Planned call = planned.get(i);
                assertThat(table.path("rows").get(i).path("id").asText()).as(file).isEqualTo(call.id());
                assertThat(table.path("rows").get(i).path("messagesSha256").asText()).as(file).isEqualTo(InputAudit.messagesSha256(call.messages()));
                boolean probe = call.id().startsWith("usage/") || call.id().startsWith("token/");
                assertThat(call.maxTokens()).as(call.id()).isEqualTo(probe ? 1 : 5120); assertThat(call.inputLimit()).as(call.id()).isEqualTo(probe ? 8191 : 3072);
                // 모든 호출(다중 턴 TG07 포함)이 검토된 v4 렌더러 범위 안입니다.
                assertThatCode(() -> renderer.render(call.messages())).as(file + " " + call.id()).doesNotThrowAnyException();
            }
            // 같은 Java 조립 목록이 Jetson 예산(context 4096)에서는 v4 요청 max_tokens 5120 을 담지 못합니다(v4 표를 v1~v3 profile 로 쓰면 fail-closed).
            InputAudit jetson = InputAudit.load(ROOT.resolve(d.input("inputAudit")));
            assertThatThrownBy(() -> jetson.requireAll(planned)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("INPUT_BUDGET_EXCEEDED"));
        }
    }

    @Test void regressionAddsOnlyTheEightQuestionsAndKeepsEveryDiagnosticRowOfTheSameArm() {
        for (String arm : List.of("v2", "i")) {
            Map<String,JsonNode> diag = rows("eval/f0/v4/input-audit-v4-" + arm + ".json"), regression = rows("eval/f0/v4/input-audit-v4-regression-" + arm + ".json");
            assertThat(regression.keySet()).containsAll(diag.keySet());
            diag.forEach((id, row) -> assertThat(regression.get(id)).as(arm + " " + id).isEqualTo(row));
            Set<String> added = new TreeSet<>(regression.keySet()); added.removeAll(diag.keySet());
            assertThat(added).isNotEmpty().allSatisfy(id -> assertThat(id).matches("[AB]/R0[1-8]/.*"));
        }
    }
    private static Map<String,JsonNode> rows(String file) {
        Map<String,JsonNode> rows = new LinkedHashMap<>(); Json.read(ROOT.resolve(file)).path("rows").forEach(r -> rows.put(r.path("id").asText(), r)); return rows;
    }
}
