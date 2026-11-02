package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 레포의 실제 v3 사전등록 문서 5개(진단 a/b/c·개발 회귀·정식 GATE)가 현재 작업 트리와 로컬 일치하고, 목적·회차·입력·예산·audit 이
 * 실제 하네스 호출 목록에 결속돼 있는지 확인합니다. 등록 수명 상태는 초안(UNCOMMITTED, registeredCommit=null)과
 * 등록 완료(REGISTERED, 40자리 hex SHA) 두 가지를 메타데이터 자체 일관성으로만 검사합니다.
 * 실제 commit 존재·순서·바이트 일치는 Prereg.verify 의 책임이며 이 시험은 측정 허가가 아닙니다(endpoint 호출 0).
 */
class F0V3RegistrationDraftTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    static final String V2_GOLDEN = "eval/golden/tc-f0-v2.jsonl", REGRESSION = "eval/f0/v3/golden-regression-v3.jsonl";
    static final Map<String,String> PURPOSE = new LinkedHashMap<>();
    static {
        for (String id : List.of("diag-a", "diag-b", "diag-c", "regression")) PURPOSE.put("eval/f0/PREREG-v3-" + id + ".md", "DIAGNOSTIC");
        PURPOSE.put("eval/f0/PREREG-v3-gate.md", "GATE");
    }
    static Prereg.Document doc(String file) { return Prereg.parse(ROOT.resolve(file)); }

    /** 등록 수명 메타데이터 일관성: UNCOMMITTED 는 명시적 null, REGISTERED 는 40자리 hex SHA 만 허용합니다(그 밖 상태·조합은 거부). */
    private static void requireConsistentLifecycle(Prereg.Document d) {
        JsonNode status = d.block().path("registrationStatus"), commit = d.block().path("registeredCommit");
        boolean consistent = switch (status.isTextual() ? status.asText() : "") {
            case "UNCOMMITTED" -> commit.isNull() && d.registeredCommit() == null;
            case "REGISTERED" -> commit.isTextual() && d.registeredCommit() != null && d.registeredCommit().matches("[0-9a-f]{40}");
            default -> false;
        };
        if (!consistent) throw new IllegalStateException("REGISTRATION_LIFECYCLE_INCONSISTENT: " + status + " / " + commit);
    }

    /** 실제 문서 바이트에서 등록 수명 메타데이터 두 줄만 바꾼 메모리 사본(시험 전용 — 파일·증거로 기록하지 않습니다). */
    private static byte[] withLifecycle(String file, String statusLine, String commitLine) throws Exception {
        String text = Files.readString(ROOT.resolve(file), java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Pattern status = java.util.regex.Pattern.compile("\"registrationStatus\": \"[A-Z]+\",\\s*"), commit = java.util.regex.Pattern.compile("\"registeredCommit\": (?:null|\"[0-9a-f]{40}\")");
        assertThat(status.matcher(text).results().count()).as(file).isEqualTo(1); assertThat(commit.matcher(text).results().count()).as(file).isEqualTo(1);
        text = status.matcher(text).replaceFirst(java.util.regex.Matcher.quoteReplacement(statusLine));
        return commit.matcher(text).replaceFirst(java.util.regex.Matcher.quoteReplacement(commitLine)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test void draftsMatchTheWorkingTreeAndBindPurposeRepeatsAndResponseFormat() {
        Set<String> captureDirs = new HashSet<>();
        for (var entry : PURPOSE.entrySet()) {
            Prereg.Document d = doc(entry.getKey());
            assertThat(d.block().path("schema").asText()).as(entry.getKey()).isEqualTo("f0-prereg-v3");
            assertThat(d.purpose()).as(entry.getKey()).isEqualTo(entry.getValue());
            assertThatCode(() -> requireConsistentLifecycle(d)).as(entry.getKey()).doesNotThrowAnyException();
            assertThat(d.repeats()).as(entry.getKey()).isEqualTo(entry.getValue().equals("GATE") ? Prereg.GATE_REPEATS : 1);
            assertThat(Prereg.localMismatches(ROOT, d)).as(entry.getKey()).isEmpty();
            assertThat(captureDirs.add(d.runtimeCaptureDir())).as(entry.getKey()).isTrue();
        }
        assertThat(doc("eval/f0/PREREG-v3-diag-a.md").responseFormat()).isEqualTo("NONE");
        for (String id : List.of("diag-b", "diag-c", "regression", "gate")) assertThat(doc("eval/f0/PREREG-v3-" + id + ".md").responseFormat()).isEqualTo(ResponseSchema.POLICY);
    }

    @Test void lifecycleCheckAcceptsDraftAndRegisteredMetadataWithIdenticalPinnedInputsAndRejectsMismatches() throws Exception {
        // 고정 합성 SHA — 실측 등록 commit 이 아니며 메모리 안 검증에만 씁니다.
        String syntheticSha = "0123456789abcdef0123456789abcdef01234567";
        for (String file : PURPOSE.keySet()) {
            Prereg.Document actual = doc(file);
            Prereg.Document draft = Prereg.parseBytes(withLifecycle(file, "\"registrationStatus\": \"UNCOMMITTED\",\n  ", "\"registeredCommit\": null"));
            Prereg.Document registered = Prereg.parseBytes(withLifecycle(file, "\"registrationStatus\": \"REGISTERED\",\n  ", "\"registeredCommit\": \"" + syntheticSha + "\""));
            assertThatCode(() -> requireConsistentLifecycle(draft)).as(file).doesNotThrowAnyException();
            assertThatCode(() -> requireConsistentLifecycle(registered)).as(file).doesNotThrowAnyException();
            assertThat(draft.registeredCommit()).as(file).isNull(); assertThat(registered.registeredCommit()).as(file).isEqualTo(syntheticSha);
            // 수명 메타데이터만 바뀌고 고정 입력·하네스·파라미터·목적과 로컬 일치 결과는 동일합니다.
            for (Prereg.Document d : List.of(draft, registered)) {
                assertThat(d.inputs()).as(file).isEqualTo(actual.inputs()); assertThat(d.harnessDigest()).as(file).isEqualTo(actual.harnessDigest());
                assertThat(d.parameters()).as(file).isEqualTo(actual.parameters()); assertThat(d.purpose()).as(file).isEqualTo(actual.purpose());
                assertThat(d.block().path("inputRoles")).as(file).isEqualTo(actual.block().path("inputRoles"));
                assertThat(Prereg.localMismatches(ROOT, d)).as(file).isEqualTo(Prereg.localMismatches(ROOT, actual));
            }
            // 상태와 SHA 불일치·미정의 상태·상태 누락은 거부합니다.
            for (byte[] bad : List.of(
                    withLifecycle(file, "\"registrationStatus\": \"UNCOMMITTED\",\n  ", "\"registeredCommit\": \"" + syntheticSha + "\""),
                    withLifecycle(file, "\"registrationStatus\": \"REGISTERED\",\n  ", "\"registeredCommit\": null"),
                    withLifecycle(file, "\"registrationStatus\": \"DRAFT\",\n  ", "\"registeredCommit\": null"),
                    withLifecycle(file, "\"registrationStatus\": \"DRAFT\",\n  ", "\"registeredCommit\": \"" + syntheticSha + "\""),
                    withLifecycle(file, "", "\"registeredCommit\": null"),
                    withLifecycle(file, "", "\"registeredCommit\": \"" + syntheticSha + "\""))) {
                Prereg.Document d = Prereg.parseBytes(bad);
                assertThatThrownBy(() -> requireConsistentLifecycle(d)).as(file).isInstanceOf(IllegalStateException.class);
            }
        }
    }

    @Test void comparisonAndGateKeepTheOriginalDenominatorAndOnlyTheRegressionAddsQuestions() throws Exception {
        Prereg.Document c = doc("eval/f0/PREREG-v3-diag-c.md"), gate = doc("eval/f0/PREREG-v3-gate.md"), regression = doc("eval/f0/PREREG-v3-regression.md");
        for (String id : List.of("diag-a", "diag-b", "diag-c", "gate")) {
            Prereg.Document d = doc("eval/f0/PREREG-v3-" + id + ".md");
            assertThat(d.input("golden")).isEqualTo(V2_GOLDEN); assertThat(d.input("boundaryCases")).isEqualTo("eval/f0/v2/output-boundary-cases.jsonl");
        }
        // 정식 후보는 c 와 같은 입력·예산·스키마이며 목적·회차만 다릅니다.
        assertThat(gate.inputs()).isEqualTo(c.inputs()); assertThat(gate.block().path("inputRoles")).isEqualTo(c.block().path("inputRoles"));
        ObjectNode gateParameters = (ObjectNode)gate.parameters().deepCopy(); gateParameters.put("repeats", 1);
        assertThat(gateParameters).isEqualTo(c.parameters());
        // 회귀는 golden·audit 만 다르고 나머지 입력은 c 와 같습니다.
        Map<String,String> rest = new TreeMap<>(regression.inputs()); rest.remove(REGRESSION); rest.remove("eval/f0/v3/input-audit-v3-regression.json");
        Map<String,String> cRest = new TreeMap<>(c.inputs()); cRest.remove(V2_GOLDEN); cRest.remove("eval/f0/v3/input-audit-v3-c.json");
        assertThat(rest).isEqualTo(cRest); assertThat(regression.parameters()).isEqualTo(c.parameters());
        // 회귀 골든 30 = 원래 22행 바이트 그대로 + 신규 8행(AC38 정확히 1, fixture 에 있는 eqp 만).
        byte[] original = Files.readAllBytes(ROOT.resolve(V2_GOLDEN)), extended = Files.readAllBytes(ROOT.resolve(REGRESSION));
        assertThat(Arrays.copyOf(extended, original.length)).isEqualTo(original);
        RegistryFixture fixture = RegistryFixture.load(ROOT.resolve(regression.input("fixture")));
        GoldenSet golden = GoldenSet.load(ROOT.resolve(REGRESSION), fixture), base = GoldenSet.load(ROOT.resolve(V2_GOLDEN), fixture);
        assertThat(golden.questions()).hasSize(30); assertThat(base.questions()).hasSize(22);
        assertThat(golden.questions().subList(0, 22)).isEqualTo(base.questions());
        // v2 등록 입력(보호 대상)은 바이트 불변입니다.
        Prereg.Document v2 = Prereg.parse(ROOT.resolve("eval/f0/PREREG-v2.md"));
        v2.inputs().forEach((file, sha) -> assertThat(Json.sha(Json.bytes(ROOT.resolve(file)))).as(file).isEqualTo(sha));
    }

    @Test void eachAuditCoversEveryPlannedCallOfItsRegistrationWithinTheRegisteredProfile() {
        for (String file : PURPOSE.keySet()) {
            Prereg.Document d = doc(file);
            java.util.function.Function<String,Path> role = r -> ROOT.resolve(d.input(r));
            RegistryFixture fixture = RegistryFixture.load(role.apply("fixture")); GoldenSet golden = GoldenSet.load(role.apply("golden"), fixture);
            MessageAssembler classify = MessageAssembler.load(role.apply("classifyPrompt"), fixture), plan = MessageAssembler.load(role.apply("planPrompt"), fixture);
            List<InputAudit.Planned> planned = new ArrayList<>(InputAudit.run(golden, plan, d.budget(), "A"));
            planned.addAll(InputAudit.run(golden, classify, d.budget(), "B")); planned.addAll(InputAudit.boundary(BoundaryCases.load(role.apply("boundaryCases")), classify, d.budget()));
            for (String use : List.of("usage", "token")) planned.addAll(InputAudit.probes(use, F0RunnerCli.messages(role.apply(use + "Messages"), role.apply("fixture"), role.apply("classifyPrompt"), role.apply("golden"), true)));
            assertThat(planned).as(file).hasSize(12 * 2 * golden.questions().size() + 12 * 9 + 2 + 13);
            assertThatCode(() -> InputAudit.load(role.apply("inputAudit")).requireAll(planned)).as(file).doesNotThrowAnyException();
            JsonNode audit = Json.read(role.apply("inputAudit"));
            assertThat(audit.path("tokenizerSha256").asText()).isEqualTo(d.inputs().get(MeasurementSession.TOKENIZER_FILE));
            assertThat(audit.path("overBudgetIds")).isEmpty(); assertThat(audit.path("rows")).hasSize(planned.size());
        }
    }
}
