package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 레포의 실제 v3 사전등록 문서를 두 묶음으로 확인합니다(endpoint 호출 0, 측정 허가 아님).
 * - 과거 등록 18개(C5/C6: 진단 a/b/c·개발 회귀·정식 GATE, C7/C8: 후보 d 3개, C9/C10: 진단 e, C11/C12: 후보 f 3개, C13/C14: 후보 g 3개, C15/C16: 후보 h 3개): 등록 입력 바이트는 현재 작업 트리와 같고,
 *   harnessDigest 는 실제 git 의 등록 commit 트리로 다시 계산한 역사 신원과 같습니다. 등록 뒤 하네스 코드가 바뀐 등록은 현재 작업 트리 기준으로 stale 이며
 *   Prereg.verify 가 PREREG_INPUT_CHANGED 로 신규 측정을 거부해야 합니다(등록 문서는 고치지 않음). 후보 i 가 f0 하네스 소스(재시도 힌트)를 바꿨으므로 h 도 stale 입니다.
 *   각 등록의 투영 조립·audit 결속도 계속 검사하며, 과거 등록은 모두 retryHint 키가 없어 기존 고정 오류 코드 11종 재시도 메시지 그대로입니다.
 * - 후보 i 등록 3개(진단 i·개발 회귀 i·정식 GATE i): 현재 작업 트리와 로컬 불일치 0, PRESENCE_V1 조립 + 재시도 힌트 STEPS_DETAIL_V1 기준 audit 전수 결속, responseFormat=JSON_SCHEMA_V1.
 *   초안(UNCOMMITTED)이면 verify 가 PREREG_NOT_COMMITTED 로 거부하고, 등록 뒤(REGISTERED)면 실제 Prereg.verify 엄격 검증을 통과해야 합니다.
 *   g 는 f 의 계획 프롬프트만, h 는 g 의 계획 프롬프트와 계획 스키마 송신 키 순서만, i 는 g 의 계획 프롬프트와 계획 재시도 세부 코드만 바꾸므로
 *   A 외 호출(B·경계·usage·token) 메시지 바이트·audit 행·분류 스키마 바이트가 앞 후보와 같아야 합니다.
 * 등록 수명 상태는 초안(UNCOMMITTED, registeredCommit=null)과 등록 완료(REGISTERED, 40자리 hex SHA) 두 가지를 메타데이터 자체 일관성으로 검사합니다.
 */
class F0V3RegistrationDraftTest {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    static final String V2_GOLDEN = "eval/golden/tc-f0-v2.jsonl", REGRESSION = "eval/f0/v3/golden-regression-v3.jsonl";
    /** 등록 대상·하네스 commit 과 registeredCommit 기록 commit 쌍(과거 사실 고정값): C5/C6 = v3 5문서, C7/C8 = 후보 d 3문서, C9/C10 = 진단 e, C11/C12 = 후보 f 3문서, C13/C14 = 후보 g 3문서, C15/C16 = 후보 h 3문서. */
    static final String C5 = "05c5facab7daa5d9d46bad2caa5ec26e13760e77", C6 = "01731891b58381ca9d873d2b931a2c482ffb8f5e",
        C7 = "4e1e866440e50799b4466e23ed344e0651de6081", C8 = "f2ffaeeacf28be86fcb557f6bf0a51642120cbfb",
        C9 = "1b5be44f8549db157d0da2c69925e3f7b901431a", C10 = "ca6f8a92cf56ee65086111b3c7d59ec65a5b522b",
        C11 = "8ebacc365c1610fdf11de8ea0e8bc2e2a05cfbbc", C12 = "48561f3eefaef7e230939da0169c2f3e1a9e5070",
        C13 = "1b1ded2566e911520e03cbf82606eba4e7f2b133", C14 = "87113b50a0a57f75c80c3e33a6faec1c3d39189d",
        C15 = "889e27b582dfbb4c4fac033d25a6d5cbc6c423a9", C16 = "372dbb4653ab807324ec7361554ea1c9d5f87f82";
    /** 과거 등록 1건의 고정 사실: 목적·등록 commit·PREREG commit·Context 투영. */
    record Historical(String purpose, String registeredCommit, String preregCommit, String projection) {}
    static final Map<String,Historical> HISTORICAL = new LinkedHashMap<>();
    /** 등록 commit 뒤 f0 하네스 소스가 바뀌지 않은 과거 등록. 현재는 없습니다(후보 i 가 재시도 힌트 소스를 바꿔 C15 하네스와도 다릅니다). */
    static final Set<String> HARNESS_UNCHANGED_SINCE_REGISTRATION = new LinkedHashSet<>();
    static final Map<String,String> CANDIDATE_I = new LinkedHashMap<>(), PURPOSE = new LinkedHashMap<>();
    static {
        for (String id : List.of("diag-a", "diag-b", "diag-c", "regression")) HISTORICAL.put("eval/f0/PREREG-v3-" + id + ".md", new Historical("DIAGNOSTIC", C5, C6, MessageAssembler.FULL));
        HISTORICAL.put("eval/f0/PREREG-v3-gate.md", new Historical("GATE", C5, C6, MessageAssembler.FULL));
        for (String id : List.of("diag-d", "regression-d")) HISTORICAL.put("eval/f0/PREREG-v3-" + id + ".md", new Historical("DIAGNOSTIC", C7, C8, MessageAssembler.EXTRACTION_V1));
        HISTORICAL.put("eval/f0/PREREG-v3-gate-d.md", new Historical("GATE", C7, C8, MessageAssembler.EXTRACTION_V1));
        HISTORICAL.put("eval/f0/PREREG-v3-diag-e.md", new Historical("DIAGNOSTIC", C9, C10, MessageAssembler.EXTRACTION_V1));
        for (String id : List.of("diag-f", "regression-f")) HISTORICAL.put("eval/f0/PREREG-v3-" + id + ".md", new Historical("DIAGNOSTIC", C11, C12, MessageAssembler.PRESENCE_V1));
        HISTORICAL.put("eval/f0/PREREG-v3-gate-f.md", new Historical("GATE", C11, C12, MessageAssembler.PRESENCE_V1));
        for (String id : List.of("diag-g", "regression-g")) HISTORICAL.put("eval/f0/PREREG-v3-" + id + ".md", new Historical("DIAGNOSTIC", C13, C14, MessageAssembler.PRESENCE_V1));
        HISTORICAL.put("eval/f0/PREREG-v3-gate-g.md", new Historical("GATE", C13, C14, MessageAssembler.PRESENCE_V1));
        for (String id : List.of("diag-h", "regression-h")) HISTORICAL.put("eval/f0/PREREG-v3-" + id + ".md", new Historical("DIAGNOSTIC", C15, C16, MessageAssembler.PRESENCE_V1));
        HISTORICAL.put("eval/f0/PREREG-v3-gate-h.md", new Historical("GATE", C15, C16, MessageAssembler.PRESENCE_V1));
        for (String id : List.of("diag-i", "regression-i")) CANDIDATE_I.put("eval/f0/PREREG-v3-" + id + ".md", "DIAGNOSTIC");
        CANDIDATE_I.put("eval/f0/PREREG-v3-gate-i.md", "GATE");
        HISTORICAL.forEach((file, h) -> PURPOSE.put(file, h.purpose())); PURPOSE.putAll(CANDIDATE_I);
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
    /** 실제 git status 로 등록 문서·입력·하네스 루트 아래 미커밋 경로를 셉니다(측정 산출물 디렉터리 results·chat-template 제외). */
    private static List<String> uncommittedRegistrationPaths(GitReader git, Prereg.Document d, String file) {
        List<String> args = new ArrayList<>(List.of("status", "--porcelain", "--untracked-files=all", "--", file)); args.addAll(d.inputs().keySet()); args.addAll(HarnessDigest.ROOTS);
        GitReader.Result status = git.run(ROOT, args);
        assertThat(status.exit()).as(file).isZero();
        return new String(status.stdout(), java.nio.charset.StandardCharsets.UTF_8).lines().filter(line -> !line.isBlank()).map(line -> line.substring(3).replace("\"", ""))
            .filter(path -> !path.startsWith("eval/f0/results/") && !path.startsWith("eval/f0/chat-template/")).toList();
    }
    private static List<String> inputMismatches(Prereg.Document d) {
        List<String> mismatches = new ArrayList<>(Prereg.localMismatches(ROOT, d)); mismatches.remove("harnessDigest"); return mismatches;
    }

    @Test void historicalRegistrationsKeepTheirRegisteredIdentityAndOnlyHarnessChangedOnesAreStale() {
        GitReader git = GitReader.process();
        assertThat(HISTORICAL.keySet()).containsAll(HARNESS_UNCHANGED_SINCE_REGISTRATION);
        for (var entry : HISTORICAL.entrySet()) {
            Prereg.Document d = doc(entry.getKey()); Historical h = entry.getValue();
            assertThat(d.block().path("schema").asText()).as(entry.getKey()).isEqualTo("f0-prereg-v3");
            assertThat(d.purpose()).as(entry.getKey()).isEqualTo(h.purpose());
            assertThat(d.block().path("registrationStatus").asText()).as(entry.getKey()).isEqualTo("REGISTERED"); assertThat(d.registeredCommit()).as(entry.getKey()).isEqualTo(h.registeredCommit());
            assertThat(d.repeats()).as(entry.getKey()).isEqualTo(h.purpose().equals("GATE") ? Prereg.GATE_REPEATS : 1);
            // C5 등록은 투영 키가 없어 기존 전체 Context 조립 그대로이고, d·e 등록은 EXTRACTION_V1, f 등록은 PRESENCE_V1 그대로입니다.
            assertThat(d.parameters().has("contextProjection")).as(entry.getKey()).isEqualTo(!h.projection().equals(MessageAssembler.FULL)); assertThat(d.contextProjection()).as(entry.getKey()).isEqualTo(h.projection());
            // 과거 등록은 재시도 힌트 키가 없어 기존 고정 오류 코드 재시도 그대로입니다(아래 audit 결속 시험이 재시도 메시지 바이트를 등록 audit 행과 대조).
            assertThat(d.parameters().has("retryHint")).as(entry.getKey()).isFalse(); assertThat(d.retryHint()).as(entry.getKey()).isEqualTo(ClassificationJudge.RETRY_HINT_NONE);
            // 등록 입력 바이트는 그대로이고, harnessDigest 는 등록 commit 트리에서 다시 계산한 값과 같습니다(역사 신원).
            assertThat(inputMismatches(d)).as(entry.getKey()).isEmpty();
            Prereg.State state = Prereg.verifyHistorical(ROOT, ROOT.resolve(entry.getKey()), git, d);
            assertThat(state.preregCommit()).as(entry.getKey()).isEqualTo(h.preregCommit());
            if (HARNESS_UNCHANGED_SINCE_REGISTRATION.contains(entry.getKey())) {
                // 등록 commit 하네스 = 현재 하네스이므로 로컬 불일치 0 입니다. verify 는 작업 트리 검사까지 하므로, 다음 후보 파일이 eval/f0 에
                // 미커밋으로 있으면 PREREG_WORKTREE_DIRTY 로 거부하고, 깨끗하면 실제 엄격 검증을 통과해야 합니다.
                assertThat(HarnessDigest.compute(ROOT, d)).as(entry.getKey()).isEqualTo(d.harnessDigest());
                assertThat(Prereg.localMismatches(ROOT, d)).as(entry.getKey()).isEmpty();
                if (uncommittedRegistrationPaths(git, d, entry.getKey()).isEmpty()) assertThatCode(() -> Prereg.verify(ROOT, ROOT.resolve(entry.getKey()), git)).as(entry.getKey()).doesNotThrowAnyException();
                else assertThatThrownBy(() -> Prereg.verify(ROOT, ROOT.resolve(entry.getKey()), git)).as(entry.getKey())
                    .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_WORKTREE_DIRTY"));
                continue;
            }
            // 현재 하네스는 등록 commit 과 다르므로 stale 이며 신규 측정 허가(verify)는 endpoint 호출 전에 거부됩니다.
            assertThat(HarnessDigest.compute(ROOT, d)).as(entry.getKey()).isNotEqualTo(d.harnessDigest());
            assertThat(Prereg.localMismatches(ROOT, d)).as(entry.getKey()).containsExactly("harnessDigest");
            assertThatThrownBy(() -> Prereg.verify(ROOT, ROOT.resolve(entry.getKey()), git)).as(entry.getKey())
                .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_INPUT_CHANGED"));
        }
        assertThat(doc("eval/f0/PREREG-v3-diag-a.md").responseFormat()).isEqualTo("NONE");
        for (String id : List.of("diag-b", "diag-c", "regression", "gate", "diag-d", "regression-d", "gate-d", "diag-e", "diag-f", "regression-f", "gate-f", "diag-g", "regression-g", "gate-g")) assertThat(doc("eval/f0/PREREG-v3-" + id + ".md").responseFormat()).isEqualTo(ResponseSchema.POLICY);
        for (String id : List.of("diag-h", "regression-h", "gate-h")) assertThat(doc("eval/f0/PREREG-v3-" + id + ".md").responseFormat()).isEqualTo(ResponseSchema.PLAN_ORDERED_POLICY);
    }

    @Test void candidateIDraftsMatchTheWorkingTreeAndBindProjectionRetryHintPurposeRepeatsAndCaptureDirs() {
        Set<String> captureDirs = new HashSet<>();
        for (String file : HISTORICAL.keySet()) captureDirs.add(doc(file).runtimeCaptureDir());
        for (var entry : CANDIDATE_I.entrySet()) {
            Prereg.Document d = doc(entry.getKey());
            assertThat(d.block().path("schema").asText()).as(entry.getKey()).isEqualTo("f0-prereg-v3");
            assertThat(d.purpose()).as(entry.getKey()).isEqualTo(entry.getValue());
            // 초안(UNCOMMITTED/null) 또는 등록 완료(REGISTERED/40자리 hex) 중 자체 일관된 상태만 허용합니다.
            assertThatCode(() -> requireConsistentLifecycle(d)).as(entry.getKey()).doesNotThrowAnyException();
            assertThat(d.repeats()).as(entry.getKey()).isEqualTo(entry.getValue().equals("GATE") ? Prereg.GATE_REPEATS : 1);
            assertThat(d.responseFormat()).as(entry.getKey()).isEqualTo(ResponseSchema.POLICY); assertThat(d.contextProjection()).as(entry.getKey()).isEqualTo(MessageAssembler.PRESENCE_V1);
            assertThat(d.retryHint()).as(entry.getKey()).isEqualTo(ClassificationJudge.STEPS_DETAIL_V1);
            assertThat(d.budget()).isEqualTo(Prereg.Budget.V2);
            assertThat(Prereg.localMismatches(ROOT, d)).as(entry.getKey()).isEmpty();
            assertThat(captureDirs.add(d.runtimeCaptureDir())).as(entry.getKey() + " 고유 capture 경로").isTrue();
            if (d.registeredCommit() == null) {
                // 미등록 초안은 측정·prereg-check 허가가 없습니다(endpoint 호출 전 거부).
                assertThatThrownBy(() -> Prereg.verify(ROOT, ROOT.resolve(entry.getKey()), GitReader.process())).as(entry.getKey())
                    .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("PREREG_NOT_COMMITTED"));
            } else {
                // 등록 완료 문서는 실제 Prereg.verify 가 현재 입력·digest·commit 을 엄격 검증하여 통과해야 합니다.
                assertThatCode(() -> Prereg.verify(ROOT, ROOT.resolve(entry.getKey()), GitReader.process())).as(entry.getKey()).doesNotThrowAnyException();
            }
        }
    }

    @Test void lifecycleCheckAcceptsDraftAndRegisteredMetadataWithIdenticalPinnedInputsAndRejectsMismatches() throws Exception {
        // 고정 합성 SHA — 실측 등록 commit 이 아니며 메모리 안 검증에만 씁니다.
        String syntheticSha = "0123456789abcdef0123456789abcdef01234567";
        for (String file : PURPOSE.keySet()) {
            Prereg.Document actual = doc(file);
            Prereg.Document draft = Prereg.parseBytes(withLifecycle(file, "\"registrationStatus\": \"UNCOMMITTED\",\n  ", "\"registeredCommit\": null"));
            Prereg.Document registered = Prereg.parseBytes(withLifecycle(file, "\"registrationStatus\": \"REGISTERED\",\n  ", "\"registeredCommit\": \"" + syntheticSha + "\""));
            assertThatCode(() -> requireConsistentLifecycle(actual)).as(file).doesNotThrowAnyException();
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
        Prereg.Document d = doc("eval/f0/PREREG-v3-diag-d.md"), gateD = doc("eval/f0/PREREG-v3-gate-d.md"), regressionD = doc("eval/f0/PREREG-v3-regression-d.md");
        Prereg.Document f = doc("eval/f0/PREREG-v3-diag-f.md"), gateF = doc("eval/f0/PREREG-v3-gate-f.md"), regressionF = doc("eval/f0/PREREG-v3-regression-f.md");
        Prereg.Document g = doc("eval/f0/PREREG-v3-diag-g.md"), gateG = doc("eval/f0/PREREG-v3-gate-g.md"), regressionG = doc("eval/f0/PREREG-v3-regression-g.md");
        Prereg.Document h = doc("eval/f0/PREREG-v3-diag-h.md"), gateH = doc("eval/f0/PREREG-v3-gate-h.md"), regressionH = doc("eval/f0/PREREG-v3-regression-h.md");
        Prereg.Document i = doc("eval/f0/PREREG-v3-diag-i.md"), gateI = doc("eval/f0/PREREG-v3-gate-i.md"), regressionI = doc("eval/f0/PREREG-v3-regression-i.md");
        for (String id : List.of("diag-a", "diag-b", "diag-c", "gate", "diag-d", "gate-d", "diag-e", "diag-f", "gate-f", "diag-g", "gate-g", "diag-h", "gate-h", "diag-i", "gate-i")) {
            Prereg.Document x = doc("eval/f0/PREREG-v3-" + id + ".md");
            assertThat(x.input("golden")).isEqualTo(V2_GOLDEN); assertThat(x.input("boundaryCases")).isEqualTo("eval/f0/v2/output-boundary-cases.jsonl");
        }
        // 정식 후보는 같은 후보의 진단 등록과 같은 입력·예산·스키마·투영·재시도 힌트이며 목적·회차만 다릅니다(c·d·f·g·h·i 각각).
        for (Prereg.Document[] pair : List.of(new Prereg.Document[]{c, gate}, new Prereg.Document[]{d, gateD}, new Prereg.Document[]{f, gateF}, new Prereg.Document[]{g, gateG}, new Prereg.Document[]{h, gateH}, new Prereg.Document[]{i, gateI})) {
            assertThat(pair[1].inputs()).isEqualTo(pair[0].inputs()); assertThat(pair[1].block().path("inputRoles")).isEqualTo(pair[0].block().path("inputRoles"));
            ObjectNode gateParameters = (ObjectNode)pair[1].parameters().deepCopy(); gateParameters.put("repeats", 1);
            assertThat(gateParameters).isEqualTo(pair[0].parameters());
        }
        // 회귀는 golden·audit 만 다르고 나머지 입력·파라미터는 같은 후보의 진단과 같습니다.
        for (Object[] row : List.of(new Object[]{c, regression, "eval/f0/v3/input-audit-v3-c.json", "eval/f0/v3/input-audit-v3-regression.json"},
                new Object[]{d, regressionD, "eval/f0/v3/input-audit-v3-d.json", "eval/f0/v3/input-audit-v3-regression-d.json"},
                new Object[]{f, regressionF, "eval/f0/v3/input-audit-v3-f.json", "eval/f0/v3/input-audit-v3-regression-f.json"},
                new Object[]{g, regressionG, "eval/f0/v3/input-audit-v3-g.json", "eval/f0/v3/input-audit-v3-regression-g.json"},
                new Object[]{h, regressionH, "eval/f0/v3/input-audit-v3-h.json", "eval/f0/v3/input-audit-v3-regression-h.json"},
                new Object[]{i, regressionI, "eval/f0/v3/input-audit-v3-i.json", "eval/f0/v3/input-audit-v3-regression-i.json"})) {
            Prereg.Document diag = (Prereg.Document)row[0], reg = (Prereg.Document)row[1];
            assertThat(diag.input("inputAudit")).isEqualTo(row[2]); assertThat(reg.input("inputAudit")).isEqualTo(row[3]); assertThat(reg.input("golden")).isEqualTo(REGRESSION);
            Map<String,String> rest = new TreeMap<>(reg.inputs()); rest.remove(REGRESSION); rest.remove((String)row[3]);
            Map<String,String> diagRest = new TreeMap<>(diag.inputs()); diagRest.remove(V2_GOLDEN); diagRest.remove((String)row[2]);
            assertThat(rest).isEqualTo(diagRest); assertThat(reg.parameters()).isEqualTo(diag.parameters());
        }
        // 후보 d 는 c 대비 프롬프트 2개·audit·투영 키만 다르고 골든 22·회귀 30·fixture·경계·메시지 입력·지원 입력은 등록 바이트 그대로 참조합니다.
        Map<String,String> cShared = new TreeMap<>(c.inputs()), dShared = new TreeMap<>(d.inputs());
        for (String role : List.of("classifyPrompt", "planPrompt", "inputAudit")) { cShared.remove(c.input(role)); dShared.remove(d.input(role)); }
        assertThat(dShared).isEqualTo(cShared);
        assertThat(regressionD.inputs().get(REGRESSION)).isEqualTo(regression.inputs().get(REGRESSION));
        ObjectNode dParameters = (ObjectNode)d.parameters().deepCopy(); dParameters.remove("contextProjection"); dParameters.remove("promptSha256");
        ObjectNode cParameters = (ObjectNode)c.parameters().deepCopy(); cParameters.remove("promptSha256");
        assertThat(dParameters).isEqualTo(cParameters);
        // 후보 f 는 d 대비 프롬프트 2개·audit·투영 값만 다르고 나머지 입력·파라미터는 같습니다.
        Map<String,String> fShared = new TreeMap<>(f.inputs()), dSharedF = new TreeMap<>(d.inputs());
        for (String role : List.of("classifyPrompt", "planPrompt", "inputAudit")) { fShared.remove(f.input(role)); dSharedF.remove(d.input(role)); }
        assertThat(fShared).isEqualTo(dSharedF);
        assertThat(regressionF.inputs().get(REGRESSION)).isEqualTo(regression.inputs().get(REGRESSION));
        assertThat(f.contextProjection()).isEqualTo(MessageAssembler.PRESENCE_V1);
        ObjectNode fParameters = (ObjectNode)f.parameters().deepCopy(); fParameters.remove("contextProjection"); fParameters.remove("promptSha256");
        assertThat(fParameters).isEqualTo(dParameters);
        // 후보 g 는 f 대비 계획 프롬프트·audit 만 다르고 분류 프롬프트(B·경계·usage·token 입력)·투영·나머지 입력·파라미터는 같습니다.
        for (Prereg.Document[] pair : List.of(new Prereg.Document[]{f, g}, new Prereg.Document[]{regressionF, regressionG})) {
            assertThat(pair[1].input("classifyPrompt")).isEqualTo(pair[0].input("classifyPrompt")); assertThat(pair[1].input("planPrompt")).isNotEqualTo(pair[0].input("planPrompt"));
            Map<String,String> before = new TreeMap<>(pair[0].inputs()), after = new TreeMap<>(pair[1].inputs());
            for (String role : List.of("planPrompt", "inputAudit")) { before.remove(pair[0].input(role)); after.remove(pair[1].input(role)); }
            assertThat(after).isEqualTo(before);
            ObjectNode beforeParameters = (ObjectNode)pair[0].parameters().deepCopy(), afterParameters = (ObjectNode)pair[1].parameters().deepCopy();
            assertThat(afterParameters.path("promptSha256").path("classify")).isEqualTo(beforeParameters.path("promptSha256").path("classify"));
            assertThat(afterParameters.path("promptSha256").path("plan")).isNotEqualTo(beforeParameters.path("promptSha256").path("plan"));
            ((ObjectNode)beforeParameters.path("promptSha256")).remove("plan"); ((ObjectNode)afterParameters.path("promptSha256")).remove("plan");
            assertThat(afterParameters).isEqualTo(beforeParameters);
        }
        // 후보 h 는 g 대비 계획 프롬프트·audit·responseFormat(JSON_SCHEMA_V1 → JSON_SCHEMA_PLAN_ORDERED_V1)만 다르고 분류 프롬프트·투영·나머지 입력·파라미터는 같습니다.
        for (Prereg.Document[] pair : List.of(new Prereg.Document[]{g, h}, new Prereg.Document[]{regressionG, regressionH})) {
            assertThat(pair[1].input("classifyPrompt")).isEqualTo(pair[0].input("classifyPrompt")); assertThat(pair[1].input("planPrompt")).isNotEqualTo(pair[0].input("planPrompt"));
            Map<String,String> before = new TreeMap<>(pair[0].inputs()), after = new TreeMap<>(pair[1].inputs());
            for (String role : List.of("planPrompt", "inputAudit")) { before.remove(pair[0].input(role)); after.remove(pair[1].input(role)); }
            assertThat(after).isEqualTo(before);
            assertThat(pair[0].responseFormat()).isEqualTo(ResponseSchema.POLICY); assertThat(pair[1].responseFormat()).isEqualTo(ResponseSchema.PLAN_ORDERED_POLICY);
            ObjectNode beforeParameters = (ObjectNode)pair[0].parameters().deepCopy(), afterParameters = (ObjectNode)pair[1].parameters().deepCopy();
            assertThat(afterParameters.path("promptSha256").path("classify")).isEqualTo(beforeParameters.path("promptSha256").path("classify"));
            assertThat(afterParameters.path("promptSha256").path("plan")).isNotEqualTo(beforeParameters.path("promptSha256").path("plan"));
            ((ObjectNode)beforeParameters.path("promptSha256")).remove("plan"); ((ObjectNode)afterParameters.path("promptSha256")).remove("plan");
            beforeParameters.remove("responseFormat"); afterParameters.remove("responseFormat");
            assertThat(afterParameters).isEqualTo(beforeParameters);
        }
        // 후보 i 는 g 대비 계획 프롬프트·audit·retryHint(키 없음 → STEPS_DETAIL_V1)만 다르고 분류 프롬프트·투영·응답 형식(JSON_SCHEMA_V1)·나머지 입력·파라미터는 같습니다.
        for (Prereg.Document[] pair : List.of(new Prereg.Document[]{g, i}, new Prereg.Document[]{regressionG, regressionI})) {
            assertThat(pair[1].input("classifyPrompt")).isEqualTo(pair[0].input("classifyPrompt")); assertThat(pair[1].input("planPrompt")).isNotEqualTo(pair[0].input("planPrompt"));
            Map<String,String> before = new TreeMap<>(pair[0].inputs()), after = new TreeMap<>(pair[1].inputs());
            for (String role : List.of("planPrompt", "inputAudit")) { before.remove(pair[0].input(role)); after.remove(pair[1].input(role)); }
            assertThat(after).isEqualTo(before);
            assertThat(pair[1].responseFormat()).isEqualTo(pair[0].responseFormat()).isEqualTo(ResponseSchema.POLICY);
            assertThat(pair[0].parameters().has("retryHint")).isFalse(); assertThat(pair[1].retryHint()).isEqualTo(ClassificationJudge.STEPS_DETAIL_V1);
            ObjectNode beforeParameters = (ObjectNode)pair[0].parameters().deepCopy(), afterParameters = (ObjectNode)pair[1].parameters().deepCopy();
            assertThat(afterParameters.path("promptSha256").path("classify")).isEqualTo(beforeParameters.path("promptSha256").path("classify"));
            assertThat(afterParameters.path("promptSha256").path("plan")).isNotEqualTo(beforeParameters.path("promptSha256").path("plan"));
            ((ObjectNode)beforeParameters.path("promptSha256")).remove("plan"); ((ObjectNode)afterParameters.path("promptSha256")).remove("plan");
            afterParameters.remove("retryHint");
            assertThat(afterParameters).isEqualTo(beforeParameters);
        }
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

    /** 계획(A) 문항당 호출 수 = 첫 시도 1 + 등록 재시도 힌트의 계획 재시도 코드 수(NONE 11, STEPS_DETAIL_V1 14). */
    static int planCallsPerQuestion(Prereg.Document d) { return 1 + ClassificationJudge.retryCodes(MessageAssembler.Kind.PLAN, d.retryHint()).size(); }
    /** 등록이 선언한 투영·재시도 힌트로 실제 하네스 호출 목록(A/B·경계·usage·token, 첫 시도 + 고정 오류 코드 재시도)을 만듭니다. */
    static List<InputAudit.Planned> planned(Prereg.Document d, String projection) {
        java.util.function.Function<String,Path> role = r -> ROOT.resolve(d.input(r));
        RegistryFixture fixture = RegistryFixture.load(role.apply("fixture")); GoldenSet golden = GoldenSet.load(role.apply("golden"), fixture);
        MessageAssembler classify = MessageAssembler.load(role.apply("classifyPrompt"), fixture, projection), plan = MessageAssembler.load(role.apply("planPrompt"), fixture, projection);
        List<InputAudit.Planned> planned = new ArrayList<>(InputAudit.run(golden, plan, d.budget(), "A", d.retryHint()));
        planned.addAll(InputAudit.run(golden, classify, d.budget(), "B", d.retryHint())); planned.addAll(InputAudit.boundary(BoundaryCases.load(role.apply("boundaryCases")), classify, d.budget()));
        for (String use : List.of("usage", "token")) planned.addAll(InputAudit.probes(use, F0RunnerCli.messages(role.apply(use + "Messages"), role.apply("fixture"), role.apply("classifyPrompt"), role.apply("golden"), true, projection)));
        assertThat(planned).hasSize((planCallsPerQuestion(d) + 12) * golden.questions().size() + 12 * 9 + 2 + 13);
        return planned;
    }

    @Test void eachAuditCoversEveryPlannedCallOfItsRegistrationWithinTheRegisteredProfile() {
        for (String file : PURPOSE.keySet()) {
            Prereg.Document d = doc(file);
            List<InputAudit.Planned> planned = planned(d, d.contextProjection());
            InputAudit audit = InputAudit.load(ROOT.resolve(d.input("inputAudit")));
            assertThatCode(() -> audit.requireAll(planned)).as(file).doesNotThrowAnyException();
            JsonNode table = Json.read(ROOT.resolve(d.input("inputAudit")));
            assertThat(table.path("tokenizerSha256").asText()).isEqualTo(d.inputs().get(MeasurementSession.TOKENIZER_FILE));
            assertThat(table.path("overBudgetIds")).isEmpty(); assertThat(table.path("rows")).hasSize(planned.size());
            // 표의 행 순서·위치 id·메시지 SHA 가 현재 Java 조립 목록과 정확히 같습니다(다른 투영 조립으로 만든 표 아님).
            for (int i = 0; i < planned.size(); i++) {
                assertThat(table.path("rows").get(i).path("id").asText()).as(file).isEqualTo(planned.get(i).id());
                assertThat(table.path("rows").get(i).path("messagesSha256").asText()).as(file).isEqualTo(InputAudit.messagesSha256(planned.get(i).messages()));
            }
            // 반대 투영의 골든·경계 메시지는 이 표에 없습니다(정책 혼용 시 호출 전 INPUT_AUDIT_MISSING). Context 가 없는 고정 probe 메시지만 공통입니다.
            // PRESENCE_V1 은 값 없는 Context 에서 FULL 과 같은 바이트이므로 모든 Context 메시지가 달라지는 EXTRACTION_V1 과 대조합니다.
            String other = d.contextProjection().equals(MessageAssembler.EXTRACTION_V1) ? MessageAssembler.FULL : MessageAssembler.EXTRACTION_V1;
            int crossed = 0;
            for (InputAudit.Planned call : planned(d, other)) {
                if (call.messages().stream().noneMatch(m -> m.get("content").startsWith("{\"context\":"))) continue;
                crossed++;
                assertThatThrownBy(() -> audit.require(call.messages(), call.inputLimit(), call.maxTokens())).as(file + " " + call.id())
                    .isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("INPUT_AUDIT_MISSING"));
            }
            // 조립 메시지(첫 시도 + 재시도) = A 골든 × (1 + 계획 재시도 코드 수) + B 골든 × 12 + 경계 9 × 12 + usage 2 + token 조립 2.
            assertThat(crossed).as(file).isEqualTo((planCallsPerQuestion(d) + 12) * GoldenSet.load(ROOT.resolve(d.input("golden")), RegistryFixture.load(ROOT.resolve(d.input("fixture")))).questions().size() + 12 * 9 + 2 + 2);
        }
    }

    @Test void candidateGChangesOnlyPlanRequestsAndKeepsEveryOtherMessageAndAuditRowOfCandidateF() {
        for (String[] pair : List.of(new String[]{"eval/f0/PREREG-v3-diag-f.md", "eval/f0/PREREG-v3-diag-g.md"}, new String[]{"eval/f0/PREREG-v3-regression-f.md", "eval/f0/PREREG-v3-regression-g.md"})) {
            Prereg.Document f = doc(pair[0]), g = doc(pair[1]);
            List<InputAudit.Planned> before = planned(f, f.contextProjection()), after = planned(g, g.contextProjection());
            JsonNode beforeRows = Json.read(ROOT.resolve(f.input("inputAudit"))).path("rows"), afterRows = Json.read(ROOT.resolve(g.input("inputAudit"))).path("rows");
            assertThat(after).hasSameSizeAs(before); assertThat(afterRows.size()).isEqualTo(beforeRows.size());
            int planCalls = 0;
            for (int i = 0; i < before.size(); i++) {
                String id = before.get(i).id();
                assertThat(after.get(i).id()).as(id).isEqualTo(id); assertThat(afterRows.get(i).path("id").asText()).as(id).isEqualTo(id);
                String beforeSha = InputAudit.messagesSha256(before.get(i).messages()), afterSha = InputAudit.messagesSha256(after.get(i).messages());
                if (id.startsWith("A/")) {
                    // 계획 요청은 system 프롬프트만 바뀌고 user·재시도 메시지는 같습니다.
                    planCalls++;
                    assertThat(afterSha).as(id).isNotEqualTo(beforeSha);
                    assertThat(after.get(i).messages().subList(1, after.get(i).messages().size())).as(id).isEqualTo(before.get(i).messages().subList(1, before.get(i).messages().size()));
                    continue;
                }
                // B·경계·usage·token 은 같은 분류 프롬프트·투영이므로 송신 메시지·audit 행(SHA·계수)이 f 와 바이트까지 같습니다.
                assertThat(afterSha).as(id).isEqualTo(beforeSha);
                assertThat(afterRows.get(i).path("messagesSha256").asText()).as(id).isEqualTo(beforeRows.get(i).path("messagesSha256").asText());
                assertThat(afterRows.get(i).path("inputTokens").asInt()).as(id).isEqualTo(beforeRows.get(i).path("inputTokens").asInt());
            }
            assertThat(planCalls).as(pair[1]).isEqualTo(12 * GoldenSet.load(ROOT.resolve(g.input("golden")), RegistryFixture.load(ROOT.resolve(g.input("fixture")))).questions().size());
        }
    }

    @Test void candidateHChangesOnlyPlanPromptAndPlanSchemaWireOrderAndKeepsEveryOtherRequestOfCandidateG() {
        for (String[] pair : List.of(new String[]{"eval/f0/PREREG-v3-diag-g.md", "eval/f0/PREREG-v3-diag-h.md"}, new String[]{"eval/f0/PREREG-v3-regression-g.md", "eval/f0/PREREG-v3-regression-h.md"})) {
            Prereg.Document g = doc(pair[0]), h = doc(pair[1]);
            List<InputAudit.Planned> before = planned(g, g.contextProjection()), after = planned(h, h.contextProjection());
            JsonNode beforeRows = Json.read(ROOT.resolve(g.input("inputAudit"))).path("rows"), afterRows = Json.read(ROOT.resolve(h.input("inputAudit"))).path("rows");
            assertThat(after).hasSameSizeAs(before); assertThat(afterRows.size()).isEqualTo(beforeRows.size());
            int planCalls = 0;
            for (int i = 0; i < before.size(); i++) {
                String id = before.get(i).id();
                assertThat(after.get(i).id()).as(id).isEqualTo(id); assertThat(afterRows.get(i).path("id").asText()).as(id).isEqualTo(id);
                String beforeSha = InputAudit.messagesSha256(before.get(i).messages()), afterSha = InputAudit.messagesSha256(after.get(i).messages());
                if (id.startsWith("A/")) {
                    planCalls++;
                    assertThat(afterSha).as(id).isNotEqualTo(beforeSha);
                    assertThat(after.get(i).messages().subList(1, after.get(i).messages().size())).as(id).isEqualTo(before.get(i).messages().subList(1, before.get(i).messages().size()));
                    continue;
                }
                // B·경계·usage·token 은 같은 분류 프롬프트·투영이므로 송신 메시지·audit 행(SHA·계수)이 g 와 바이트까지 같습니다.
                assertThat(afterSha).as(id).isEqualTo(beforeSha);
                assertThat(afterRows.get(i).path("messagesSha256").asText()).as(id).isEqualTo(beforeRows.get(i).path("messagesSha256").asText());
                assertThat(afterRows.get(i).path("inputTokens").asInt()).as(id).isEqualTo(beforeRows.get(i).path("inputTokens").asInt());
            }
            RegistryFixture fixture = RegistryFixture.load(ROOT.resolve(h.input("fixture")));
            List<String> questions = new ArrayList<>(); GoldenSet.load(ROOT.resolve(h.input("golden")), fixture).questions().forEach(q -> questions.add(q.question()));
            assertThat(planCalls).as(pair[1]).isEqualTo(12 * questions.size());
            // 응답 형식: 분류 스키마(B·경계)는 g 와 송신 바이트가 같고, 계획 스키마(A)는 내용이 같고 송신 순서만 t 가 steps 앞입니다.
            BoundaryCases.load(ROOT.resolve(h.input("boundaryCases"))).forEach(c -> questions.add(c.path("question").asText()));
            for (String question : questions) {
                QRefContext refs = QRefContext.extract(question);
                ResponseSchema.Format gClassify = ResponseSchema.forPolicy(g.responseFormat(), MessageAssembler.Kind.CLASSIFY, refs), hClassify = ResponseSchema.forPolicy(h.responseFormat(), MessageAssembler.Kind.CLASSIFY, refs);
                assertThat(hClassify.bytes()).as(question).isEqualTo(gClassify.bytes()); assertThat(hClassify.sha256()).isEqualTo(gClassify.sha256());
                ResponseSchema.Format gPlan = ResponseSchema.forPolicy(g.responseFormat(), MessageAssembler.Kind.PLAN, refs), hPlan = ResponseSchema.forPolicy(h.responseFormat(), MessageAssembler.Kind.PLAN, refs);
                assertThat(Json.parse(hPlan.bytes())).isEqualTo(Json.parse(gPlan.bytes())); assertThat(hPlan.bytes()).isNotEqualTo(gPlan.bytes());
                List<String> order = new ArrayList<>(); Json.parse(hPlan.bytes()).path("json_schema").path("schema").path("properties").fieldNames().forEachRemaining(order::add);
                assertThat(order).containsExactly("t", "eqp", "from", "to", "qRef", "steps");
            }
        }
    }

    @Test void candidateIChangesOnlyPlanPromptAndAddsPlanDetailRetriesAndKeepsEveryOtherRequestOfCandidateG() {
        for (String[] pair : List.of(new String[]{"eval/f0/PREREG-v3-diag-g.md", "eval/f0/PREREG-v3-diag-i.md"}, new String[]{"eval/f0/PREREG-v3-regression-g.md", "eval/f0/PREREG-v3-regression-i.md"})) {
            Prereg.Document g = doc(pair[0]), i = doc(pair[1]);
            List<InputAudit.Planned> before = planned(g, g.contextProjection()), after = planned(i, i.contextProjection());
            JsonNode beforeRows = Json.read(ROOT.resolve(g.input("inputAudit"))).path("rows"), afterRows = Json.read(ROOT.resolve(i.input("inputAudit"))).path("rows");
            int questions = GoldenSet.load(ROOT.resolve(i.input("golden")), RegistryFixture.load(ROOT.resolve(i.input("fixture")))).questions().size();
            assertThat(after).hasSize(before.size() + ClassificationJudge.STEPS_DETAIL_CODES.size() * questions); assertThat(afterRows.size()).isEqualTo(after.size());
            Map<String,Integer> beforeIndex = new HashMap<>(); for (int k = 0; k < before.size(); k++) beforeIndex.put(before.get(k).id(), k);
            Map<String,List<Map<String,String>>> afterFirst = new HashMap<>();
            List<String> keptOrder = new ArrayList<>();
            int planCalls = 0, detailCalls = 0, otherCalls = 0;
            for (int k = 0; k < after.size(); k++) {
                InputAudit.Planned call = after.get(k); String id = call.id();
                assertThat(afterRows.get(k).path("id").asText()).as(id).isEqualTo(id);
                assertThat(afterRows.get(k).path("messagesSha256").asText()).as(id).isEqualTo(InputAudit.messagesSha256(call.messages()));
                if (id.endsWith("/first")) afterFirst.put(id.substring(0, id.length() - "/first".length()), call.messages());
                Integer b = beforeIndex.get(id);
                if (b == null) {
                    // 새 행은 계획 재시도 세부 코드뿐이며, 같은 문항의 i 첫 시도 메시지에 그 코드 한 줄을 덧붙인 메시지입니다.
                    detailCalls++;
                    String code = id.substring(id.lastIndexOf('/') + 1), question = id.substring(0, id.indexOf("/retry/"));
                    assertThat(id).startsWith("A/"); assertThat(ClassificationJudge.STEPS_DETAIL_CODES).contains(code);
                    assertThat(call.messages()).as(id).isEqualTo(MessageAssembler.retry(afterFirst.get(question), code));
                    continue;
                }
                keptOrder.add(id);
                String beforeSha = InputAudit.messagesSha256(before.get(b).messages()), afterSha = InputAudit.messagesSha256(call.messages());
                if (id.startsWith("A/")) {
                    // 기존 계획 행(첫 시도·고정 오류 코드 11종 재시도)은 system 프롬프트만 바뀌고 user·재시도 메시지는 같습니다.
                    planCalls++;
                    assertThat(afterSha).as(id).isNotEqualTo(beforeSha);
                    assertThat(call.messages().subList(1, call.messages().size())).as(id).isEqualTo(before.get(b).messages().subList(1, before.get(b).messages().size()));
                    continue;
                }
                // B·경계·usage·token 은 같은 분류 프롬프트·투영이며 분류 재시도 코드 집합도 같으므로 송신 메시지·audit 행(SHA·계수)이 g 와 바이트까지 같습니다.
                otherCalls++;
                assertThat(afterSha).as(id).isEqualTo(beforeSha);
                assertThat(afterRows.get(k).path("messagesSha256").asText()).as(id).isEqualTo(beforeRows.get(b).path("messagesSha256").asText());
                assertThat(afterRows.get(k).path("inputTokens").asInt()).as(id).isEqualTo(beforeRows.get(b).path("inputTokens").asInt());
            }
            assertThat(keptOrder).as("기존 행 순서 유지").containsExactlyElementsOf(before.stream().map(InputAudit.Planned::id).toList());
            assertThat(planCalls).as(pair[1]).isEqualTo(12 * questions); assertThat(detailCalls).as(pair[1]).isEqualTo(3 * questions); assertThat(otherCalls).isEqualTo(before.size() - 12 * questions);
        }
    }
}
