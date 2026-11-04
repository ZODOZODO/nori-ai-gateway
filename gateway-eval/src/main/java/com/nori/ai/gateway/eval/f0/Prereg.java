package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * PREREG.md 의 기계 판독 블록과 사전등록 검사.
 * 등록 commit(registeredCommit — 골든셋·fixture·프롬프트·분석 코드가 들어간 commit)은 PREREG 문서에 적고, PREREG 문서 자신은 그 뒤 별도 commit 으로 올린다.
 * PREREG 는 자기 해시를 담지 않는다(자기 해시 고리 0). 측정 전 아래가 모두 성립하지 않으면 측정 명령은 시작하지 않는다.
 */
public final class Prereg {
    public static final String DEFAULT_PATH = "eval/f0/PREREG.md";
    public static final List<String> REQUIRED_INPUTS = List.of("eval/golden/tc-f0-v1.jsonl", "eval/golden/tc-registry-fixture-v1.json", "eval/f0/prompts/classify-v1.txt", "eval/f0/prompts/plan-v1.txt",
        "eval/f0/arg-limits-v1.json", "eval/f0/output-boundary-cases.jsonl", "eval/f0/usage-probe-input-v1.jsonl", "eval/f0/token-golden-input-v1.jsonl", "eval/f0/chat-renderers-v1.json");
    public static final Map<String,String> LEGACY_ROLES = Map.ofEntries(
        Map.entry("golden", REQUIRED_INPUTS.get(0)), Map.entry("fixture", REQUIRED_INPUTS.get(1)),
        Map.entry("classifyPrompt", REQUIRED_INPUTS.get(2)), Map.entry("planPrompt", REQUIRED_INPUTS.get(3)),
        Map.entry("argLimits", REQUIRED_INPUTS.get(4)), Map.entry("boundaryCases", REQUIRED_INPUTS.get(5)),
        Map.entry("usageMessages", REQUIRED_INPUTS.get(6)), Map.entry("tokenMessages", REQUIRED_INPUTS.get(7)), Map.entry("chatRenderers", REQUIRED_INPUTS.get(8)));
    /** v2·v3(Jetson) 등록의 지원 파일. v4 는 RuntimeProfile.INFER_V4.supportInputs() 입니다. */
    public static final List<String> SUPPORT_INPUTS = RuntimeProfile.JETSON.supportInputs();
    /** v2는 v1 역할에 호출 전 입력 계수 audit 역할을 더합니다. */
    public static final Set<String> V2_ROLES = Collections.unmodifiableSet(new TreeSet<>(Set.of("golden", "fixture", "classifyPrompt", "planPrompt", "argLimits", "boundaryCases", "usageMessages", "tokenMessages", "chatRenderers", "inputAudit")));
    public static final int CONTEXT_TOKENS = 4096;
    /**
     * 등록 schema 가 고정하는 추론 런타임. v1~v3 = Jetson 등록(값 불변 — 과거 등록 재검증 보존), v4 = 2026-11-04 교체 추론 서버(SPEC D44·D51).
     * v4 는 생성 모델 digest 를 등록값으로 고정합니다(공유·무인증 서버에서 같은 태그가 다른 digest 로 바뀔 수 있음, SPEC §14.6).
     */
    public record RuntimeProfile(String generationModel, int contextTokens, String tokenizerFile, String tokenizerPin, String templateStem) {
        public static final RuntimeProfile JETSON = new RuntimeProfile(F0RunnerCli.GENERATION_MODEL, CONTEXT_TOKENS, MeasurementSession.TOKENIZER_FILE, MeasurementSession.TOKENIZER_PIN, "qwen3-4b-instruct");
        public static final RuntimeProfile INFER_V4 = new RuntimeProfile("qwen3:4b", 8192, "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json", "eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256", "qwen3-4b");
        /** versioned 등록이 inputs 에 반드시 담는 지원 파일(tokenizer 쌍만 profile 별). */
        public List<String> supportInputs() { return List.of(tokenizerFile, tokenizerPin, "eval/py/count_tokens_qref_v2.py", "eval/py/count_input_audit_v2.py", "eval/py/requirements.txt"); }
    }
    /**
     * 사전등록 하나가 쓰는 유일한 토큰 예산. 요청 max_tokens·판정 상한·경계 판정·보고가 모두 이 값을 씁니다.
     * v1은 원 SPEC 값(입력 1024/2048·출력 128/160)을 보존하고 호출 전 입력 audit 없이 동작합니다.
     * V4_DIAGNOSTIC 은 사고 상한 T 확정 전 진단 규칙(SPEC §14.5): 요청 max_tokens = context 8192 − 입력 상한, outputLimit = 답변(wire) 상한 192(사고 토큰 제외).
     */
    public record Budget(int classifyInput, int planInput, int classifyOutput, int planOutput, int outputLimit) {
        public static final Budget V1 = new Budget(1024, 2048, 128, 160, 128), V2 = new Budget(3072, 3072, 192, 256, 192),
            V4_DIAGNOSTIC = new Budget(3072, 3072, RuntimeProfile.INFER_V4.contextTokens() - 3072, RuntimeProfile.INFER_V4.contextTokens() - 3072, 192);
        public int output(MessageAssembler.Kind kind) { return kind == MessageAssembler.Kind.PLAN ? planOutput : classifyOutput; }
        public int input(MessageAssembler.Kind kind) { return kind == MessageAssembler.Kind.PLAN ? planInput : classifyInput; }
    }
    /** v3 등록 목적. DIAGNOSTIC 회차는 진단 보고서만 만들며 F0 leaf 증거(게이트 입력)를 만들 수 없습니다. */
    public static final Set<String> PURPOSES = Set.of("DIAGNOSTIC", "GATE");
    /** v3 GATE 등록의 고정 회차 수(SPEC §14.5 정식 R=5). */
    public static final int GATE_REPEATS = 5;
    /** v3 서버 출력 제약 선택. NONE = v1/v2 와 같은 요청 body. PLAN_ORDERED_POLICY 는 새 등록에서만 쓰는 opt-in 값입니다. */
    public static final Set<String> RESPONSE_FORMATS = Set.of("NONE", ResponseSchema.POLICY, ResponseSchema.PLAN_ORDERED_POLICY);
    /** v3 에서만 명시 opt-in 하는 LLM 입력 Context 투영. 키가 없으면 기존 전체 Context(FULL) 그대로이며 FULL 은 등록 값으로 쓰지 않습니다. */
    public static final Set<String> CONTEXT_PROJECTIONS = Set.of(MessageAssembler.EXTRACTION_V1, MessageAssembler.PRESENCE_V1);
    /** v3 에서만 명시 opt-in 하는 재시도 힌트. 키가 없으면 기존 재시도 메시지(NONE) 그대로이며 NONE 은 등록 값으로 쓰지 않습니다. */
    public static final Set<String> RETRY_HINTS = Set.of(ClassificationJudge.STEPS_DETAIL_V1);
    /** v4 진단 등록이 허용하는 temperature(SPEC §16 F0: 정식 0, 진단에 0.6 비교 arm). */
    public static final Set<Double> V4_DIAGNOSTIC_TEMPERATURES = Set.of(0.0, 0.6);
    public record Document(JsonNode block, String registeredCommit, Map<String,String> inputs, String harnessDigest, JsonNode parameters) {
        /** qRef wire 계약(v2·v3·v4 공통). */
        public boolean versioned() { return Set.of("f0-prereg-v2", "f0-prereg-v3", "f0-prereg-v4").contains(block.path("schema").asText()); }
        /** 2026-11-04 교체 추론 서버 등록(v1~v3 = Jetson 등록). */
        public boolean v4() { return block.path("schema").asText().equals("f0-prereg-v4"); }
        public String purpose() { return Set.of("f0-prereg-v3", "f0-prereg-v4").contains(block.path("schema").asText()) ? block.path("purpose").asText() : "GATE"; }
        public boolean diagnostic() { return purpose().equals("DIAGNOSTIC"); }
        public String responseFormat() { return parameters.path("responseFormat").asText("NONE"); }
        public String contextProjection() { return parameters.path("contextProjection").asText(MessageAssembler.FULL); }
        public String retryHint() { return parameters.path("retryHint").asText(ClassificationJudge.RETRY_HINT_NONE); }
        public Budget budget() { return v4() ? Budget.V4_DIAGNOSTIC : versioned() ? Budget.V2 : Budget.V1; }
        public RuntimeProfile profile() { return v4() ? RuntimeProfile.INFER_V4 : RuntimeProfile.JETSON; }
        public int contextTokens() { return profile().contextTokens(); }
        /** v4 등록 생성 모델 digest(64 hex). v1~v3 은 등록하지 않으므로 null. */
        public String generationModelDigest() { return v4() ? parameters.path("generationModelDigest").asText() : null; }
        /** v4 요청 body temperature(등록값 그대로). v1~v3 은 기존 고정 0 경로를 쓰므로 호출하지 않습니다. */
        public JsonNode temperature() { return parameters.path("temperature"); }
        /** v4 등록 답변(사고 제외) 상한 — 초과·계수 불가는 그 시도 실패(SPEC §14.5 "답변 상한 초과 = 실패"). v1~v3 은 null(답변·사고를 나누지 않음). */
        public Integer answerLimit(MessageAssembler.Kind kind) { return v4() ? parameters.path("answerLimitTokens").path(kind == MessageAssembler.Kind.PLAN ? "plan" : "classify").intValue() : null; }
        public String input(String role) { return versioned() ? block.path("inputRoles").path(role).asText() : LEGACY_ROLES.get(role); }
        public String runtimeCaptureDir() { return versioned() ? block.path("runtimeCaptureDir").asText() : "eval/f0/chat-template"; }
        public int repeats() { return parameters.path("repeats").asInt(); }
        public String generationModel() { return parameters.path("generationModel").asText(); }
        public String embeddingModel() { return parameters.path("embeddingModel").asText(); }
        public java.time.Duration requestTimeout() { return java.time.Duration.ofMillis(parameters.path("requestTimeoutMs").asLong()); }
        public String parameterManifestSha256() { return Json.sha(Json.canonical(parameters)); }
    }
    public record State(Document document, String preregCommit, Instant preregCommitTime) {}
    private Prereg() {}

    public static Document parse(Path path) {
        return parseBytes(Json.bytes(path));
    }
    public static Document parseBytes(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        int start = text.indexOf("```json\n"), end = start < 0 ? -1 : text.indexOf("\n```", start + 8);
        if (start < 0 || end < 0 || text.indexOf("```json\n", end) >= 0) throw new EvalException(2, "PREREG_INVALID");
        JsonNode block = Json.parse(text.substring(start + 8, end + 1).getBytes(StandardCharsets.UTF_8));
        if (!Set.of("f0-prereg-v1", "f0-prereg-v2", "f0-prereg-v3", "f0-prereg-v4").contains(block.path("schema").asText())) throw new EvalException(2, "PREREG_INVALID");
        Map<String,String> inputs = new LinkedHashMap<>();
        for (JsonNode input : block.path("inputs")) {
            String file = input.path("path").asText(""), sha = input.path("sha256").asText("");
            if (!sha.matches("[0-9a-f]{64}") || !file.matches("[A-Za-z0-9_./-]+") || file.contains("..") || file.startsWith("/") || !Path.of(file).normalize().toString().replace('\\', '/').equals(file) || inputs.put(file, sha) != null) throw new EvalException(2, "PREREG_INVALID");
        }
        // v4 는 v3 의 선언 규칙(목적·출력 제약·Context 투영·재시도 힌트)을 그대로 쓰고 런타임 profile·예산만 다릅니다(아래 "v3" 는 v3·v4).
        boolean v4 = block.path("schema").asText().equals("f0-prereg-v4"), v3 = v4 || block.path("schema").asText().equals("f0-prereg-v3"), versioned = v3 || block.path("schema").asText().equals("f0-prereg-v2");
        RuntimeProfile profile = v4 ? RuntimeProfile.INFER_V4 : RuntimeProfile.JETSON;
        // 목적·출력 제약은 v3 에서만 선언하며 v1/v2 에 같은 키가 있으면 기존 등록과 다른 요청으로 오해될 수 있어 거부합니다.
        JsonNode responseFormat = block.path("parameters").path("responseFormat");
        if (v3 ? !PURPOSES.contains(block.path("purpose").asText()) || !responseFormat.isTextual() || !RESPONSE_FORMATS.contains(responseFormat.asText())
               : block.has("purpose") || !responseFormat.isMissingNode()) throw new EvalException(2, "PREREG_INVALID");
        // Context 투영도 v3 opt-in 전용입니다. v1/v2 의 같은 키·v3 의 미정의 값·비문자열은 기존 전체 Context 로 조용히 떨어지지 않게 거부합니다.
        JsonNode projection = block.path("parameters").path("contextProjection");
        if (!projection.isMissingNode() && (!v3 || !projection.isTextual() || !CONTEXT_PROJECTIONS.contains(projection.asText()))) throw new EvalException(2, "PREREG_INVALID");
        // 재시도 힌트도 v3 opt-in 전용입니다. v1/v2 의 같은 키·미정의 값·비문자열은 기존 재시도 메시지로 조용히 떨어지지 않게 거부합니다.
        JsonNode retryHint = block.path("parameters").path("retryHint");
        if (!retryHint.isMissingNode() && (!v3 || !retryHint.isTextual() || !RETRY_HINTS.contains(retryHint.asText()))) throw new EvalException(2, "PREREG_INVALID");
        Map<String,String> roles = new LinkedHashMap<>(LEGACY_ROLES);
        if (versioned) {
            JsonNode mapping = block.path("inputRoles");
            if (!block.path("runtimeCaptureDir").asText().matches("eval/f0/chat-template/[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) throw new EvalException(2, "PREREG_INVALID");
            if (!GoldenSet.keys(mapping).equals(V2_ROLES) || !inputs.keySet().containsAll(profile.supportInputs())) throw new EvalException(2, "PREREG_INVALID");
            if (inputs.keySet().stream().anyMatch(file -> file.matches(".*(?:^|/)PREREG[^/]*\\.md") || file.startsWith("eval/f0/results/") || file.startsWith("eval/f0/chat-template/"))) throw new EvalException(2, "PREREG_INVALID");
            roles.clear();
            for (String role : V2_ROLES) {
                String file = mapping.path(role).asText("");
                if (!inputs.containsKey(file) || !(file.startsWith("eval/f0/") || file.startsWith("eval/golden/")) || file.matches(".*(?:^|/)PREREG[^/]*\\.md") || file.startsWith("eval/f0/results/") || file.startsWith("eval/f0/chat-template/")) throw new EvalException(2, "PREREG_INVALID");
                roles.put(role, file);
            }
            if (new HashSet<>(roles.values()).size() != roles.size()) throw new EvalException(2, "PREREG_INVALID");
        }
        if ((!versioned && !inputs.keySet().containsAll(REQUIRED_INPUTS)) || !block.path("harnessDigest").asText("").matches("[0-9a-f]{64}")) throw new EvalException(2, "PREREG_INVALID");
        JsonNode p = block.path("parameters");
        if (versioned && !p.path("judgeRulesVersion").asText().equals("f0-judge-v2")) throw new EvalException(2, "PREREG_INVALID");
        Budget budget = v4 ? Budget.V4_DIAGNOSTIC : versioned ? Budget.V2 : Budget.V1;
        int context = profile.contextTokens();
        // 문자열·소수 표기("192"·192.0)나 profile과 다른 값은 요청 max_tokens·판정으로 흘러가기 전에 거부합니다.
        boolean budgetValid = exactInt(p.path("maxTokens").path("classify"), budget.classifyOutput()) && exactInt(p.path("maxTokens").path("plan"), budget.planOutput())
            && exactInt(p.path("outputLimitTokens"), budget.outputLimit()) && (v4 || budget.outputLimit() == budget.classifyOutput());
        if (versioned) budgetValid &= exactInt(p.path("inputLimitTokens").path("classify"), budget.classifyInput()) && exactInt(p.path("inputLimitTokens").path("plan"), budget.planInput())
            && exactInt(p.path("contextTokens"), context) && budget.classifyInput() + budget.classifyOutput() <= context && budget.planInput() + budget.planOutput() <= context;
        // v4 요청 max_tokens = 답변 상한 + 사고 상한이므로 답변(wire) 상한을 따로 등록합니다(SPEC §14.5 표, 사고 토큰 제외).
        if (v4) budgetValid &= exactInt(p.path("answerLimitTokens").path("classify"), Budget.V2.classifyOutput()) && exactInt(p.path("answerLimitTokens").path("plan"), Budget.V2.planOutput());
        else budgetValid &= !p.has("answerLimitTokens");
        if (!budgetValid || !p.path("repeats").isInt() || p.path("repeats").asInt() < 1 || !temperatureValid(p.path("temperature"), v4) || p.path("requestTimeoutMs").asLong() < 1 || !p.path("generationModel").asText().equals(profile.generationModel()) || !p.path("embeddingModel").asText().equals(F0RunnerCli.EMBEDDING_MODEL)
            || !p.path("promptSha256").path("classify").asText().equals(inputs.get(roles.get("classifyPrompt"))) || !p.path("promptSha256").path("plan").asText().equals(inputs.get(roles.get("planPrompt")))) throw new EvalException(2, "PREREG_INVALID");
        // 생성 모델 digest 는 v4 에서만 등록하며 v1~v3 에 같은 키가 있으면 기존 등록과 다른 검사로 오해될 수 있어 거부합니다.
        if (v4 ? !p.path("generationModelDigest").isTextual() || !p.path("generationModelDigest").asText().matches("[0-9a-f]{64}") : p.has("generationModelDigest")) throw new EvalException(2, "PREREG_INVALID");
        // v4 정식(GATE) 등록은 사고 상한 T 확정(G-F0-TARGET) 전에는 만들 수 없습니다(SPEC §14.5 "확정 전 정식 측정 0").
        if (v4 && !block.path("purpose").asText().equals("DIAGNOSTIC")) throw new EvalException(2, "PREREG_INVALID");
        // v3 정식(GATE) 등록은 최종 프로토콜 R=5 만 허용합니다. v1/v2 등록과 DIAGNOSTIC 회차 수는 기존 규칙 그대로입니다.
        if (v3 && block.path("purpose").asText().equals("GATE") && p.path("repeats").asInt() != GATE_REPEATS) throw new EvalException(2, "PREREG_INVALID");
        JsonNode commit = block.path("registeredCommit");
        String registered = commit.isNull() || commit.isMissingNode() ? null : commit.asText();
        if (registered != null && !registered.matches("[0-9a-f]{40}")) throw new EvalException(2, "PREREG_INVALID");
        return new Document(block, registered, Collections.unmodifiableMap(inputs), block.path("harnessDigest").asText(), p.deepCopy());
    }
    private static boolean exactInt(JsonNode node, int expected) { return node.isInt() && node.intValue() == expected; }
    /** v1~v3 = 0 만, v4 진단 = 0 또는 0.6. */
    private static boolean temperatureValid(JsonNode node, boolean v4) {
        return node.isNumber() && (v4 ? V4_DIAGNOSTIC_TEMPERATURES.contains(node.asDouble()) : node.asDouble() == 0);
    }
    /** 현재 작업 트리 바이트가 등록값과 같은지(커밋 여부와 무관한 로컬 검사). */
    public static List<String> localMismatches(Path root, Document document) {
        List<String> mismatches = new ArrayList<>();
        document.inputs().forEach((file, sha) -> { Path p = root.resolve(file); if (!Files.isRegularFile(p) || !Json.sha(Json.bytes(p)).equals(sha)) mismatches.add(file); });
        if (!HarnessDigest.compute(root, document).equals(document.harnessDigest())) mismatches.add("harnessDigest");
        return mismatches;
    }
    /** 측정 전 필수 검사: 등록 commit 존재·PREREG 별도 commit·순서·등록 바이트 일치·작업 트리 clean. 하나라도 아니면 exit 2. */
    public static State verify(Path root, Path preregPath, GitReader git) {
        Document document = parse(preregPath);
        if (document.registeredCommit() == null) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        if (!localMismatches(root, document).isEmpty()) throw new EvalException(2, "PREREG_INPUT_CHANGED");
        String relative = relativePath(root, preregPath);
        List<String> statusArgs = new ArrayList<>(List.of("status", "--porcelain", "--untracked-files=all", "--", relative)); statusArgs.addAll(document.inputs().keySet()); statusArgs.addAll(HarnessDigest.ROOTS);
        GitReader.Result status = git.run(root, statusArgs);
        if (status.exit() != 0) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        for (String line : text(status).split("\n")) {
            if (line.isBlank()) continue;
            String file = line.length() > 3 ? line.substring(3).replace("\"", "") : line;
            if (!file.startsWith("eval/f0/results/") && !file.startsWith("eval/f0/chat-template/")) throw new EvalException(2, "PREREG_WORKTREE_DIRTY");
        }
        return verifyHistorical(root, preregPath, git, document);
    }
    /** 과거 등록의 정합성만 확인하며 현재 하네스의 측정·재판정 허가는 부여하지 않습니다. */
    static State verifyHistorical(Path root, Path preregPath, GitReader git, Document document) {
        if (document.registeredCommit() == null) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        GitReader.Result inside = git.run(root, List.of("rev-parse", "--is-inside-work-tree"));
        if (inside.exit() != 0 || !text(inside).equals("true")) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        String relative = relativePath(root, preregPath);
        if (document.inputs().containsKey(relative)) throw new EvalException(2, "PREREG_INVALID");
        GitReader.Result log = git.run(root, List.of("log", "-1", "--format=%H %ct", "--", relative));
        String[] head = text(log).split(" ");
        if (log.exit() != 0 || head.length != 2 || !head[0].matches("[0-9a-f]{40}")) throw new EvalException(2, "PREREG_NOT_COMMITTED");
        String preregCommit = head[0];
        if (preregCommit.equals(document.registeredCommit()) || git.run(root, List.of("merge-base", "--is-ancestor", document.registeredCommit(), preregCommit)).exit() != 0) throw new EvalException(2, "PREREG_COMMIT_ORDER_INVALID");
        for (var input : document.inputs().entrySet()) {
            GitReader.Result blob = git.run(root, List.of("cat-file", "blob", document.registeredCommit() + ":" + input.getKey()));
            if (blob.exit() != 0 || !Json.sha(blob.stdout()).equals(input.getValue())) throw new EvalException(2, "PREREG_REGISTERED_COMMIT_MISMATCH");
        }
        if (!HarnessDigest.computeAtCommit(git, root, document.registeredCommit(), document).equals(document.harnessDigest())) throw new EvalException(2, "PREREG_REGISTERED_COMMIT_MISMATCH");
        Instant committed;
        try { committed = Instant.ofEpochSecond(Long.parseLong(head[1])); } catch (NumberFormatException e) { throw new EvalException(2, "PREREG_NOT_COMMITTED"); }
        return new State(document, preregCommit, committed);
    }
    public static String relativePath(Path root, Path path) {
        Path base = root.toAbsolutePath().normalize(), selected = path.toAbsolutePath().normalize();
        if (!selected.startsWith(base) || selected.equals(base)) throw new EvalException(2, "ARGUMENT_NOT_REGISTERED");
        return base.relativize(selected).toString().replace('\\', '/');
    }
    private static String text(GitReader.Result result) { return new String(result.stdout(), StandardCharsets.UTF_8).strip(); }
}
