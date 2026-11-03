package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * f0 probe-g08: A 경로 G08 steps 공백 원인 확정 시험(DIAGNOSTIC 사전등록 전용, F0 게이트 입력 생성 0).
 * 기준선 = 등록 golden 의 G08·등록 fixture·등록 계획 프롬프트·등록 Context 투영으로 조립한 첫 시도 메시지이며, manifest baseline.messagesSha256 와 같아야 한다.
 * 시험은 manifest 순서(IDS)대로 1회씩 첫 시도만 보낸다(재시도 0). 각 시험은 기준선에서 요소 하나만 바꾼다.
 * - E0: /api/generate raw = 등록 렌더러로 렌더한 기준 메시지, format = 키 정렬 계획 스키마(chat 경로가 서버에 보내는 순서), logprobs + top_logprobs.
 * - E-ORD: E0 에서 format properties 선언 순서만 t, eqp, from, to, qRef, steps.
 * - 나머지: chat 경로(등록 응답 형식)에서 질문·계획 프롬프트·fixture 중 하나에 manifest edit 를 정확히 한 번 적용. 프롬프트·fixture 변형은 별도 파일이며
 *   그 바이트는 기준 파일에 edit 를 적용한 바이트와 같아야 한다.
 * 결과에는 응답 원문·API 응답 원문을 쓰지 않는다. SHA·고정 분류·토큰 수·지연과 E0·E-ORD 의 두 위치(steps 배열 첫 문자·t 값 첫 문자) 후보 확률만 남기며,
 * 후보 토큰 문자열은 그 위치의 판별 후보 집합(`]`·`"` / INC·UNS)에 드는 것만 남긴다.
 */
public final class G08Probe {
    public static final String SCHEMA = "f0-probe-g08-v1", GOLDEN_ID = "G08", RESULT_FILE = "probe-g08-results.json";
    public static final String GENERATE_RAW = "GENERATE_RAW", CHAT = "CHAT", SORTED = "SORTED", T_FIRST = "T_FIRST";
    public static final int TOP_LOGPROBS = 5;
    /** 사전등록 순서. E0·E-ORD 만 generate 이고 나머지는 chat 입니다. */
    public static final List<String> IDS = List.of("E0", "E-ORD", "E1", "E2", "E3", "E5", "E6", "N1");
    static final Pattern STEPS_OPEN = Pattern.compile("\"steps\"\\s*:\\s*\\["), T_OPEN = Pattern.compile("\"t\"\\s*:\\s*\"");
    static final Map<Character,String> STEPS_CLASSES = Map.of(']', "CLOSE_BRACKET", '"', "QUOTE"), T_CLASSES = Map.of('I', "INC", 'U', "UNS");
    private static final Set<String> MANIFEST_KEYS = Set.of("schema", "notice", "goldenId", "baseline", "experiments");

    /** 기준선 입력 역할(등록 inputRoles 의 golden·fixture·planPrompt 와 등록 contextProjection). */
    public record Roles(Path golden, Path fixture, Path planPrompt, String projection) {}
    /** 송신 단위 하나. question 은 그 시험이 보낸 질문(진단 계산용, 결과 파일에 쓰지 않음)입니다. */
    public record Experiment(String id, String call, String schemaPropertyOrder, String editTarget, String question, QRefContext references, List<Map<String,String>> messages) {
        @Override public String toString() { return "Experiment[" + id + "]"; }
    }
    /** inputFiles = manifest edit 가 참조한 변형 파일(레포 루트 기준 상대 경로). 측정 명령은 모두 등록 inputs 에 있어야 합니다. */
    public record Plan(String baselineMessagesSha256, String baselineOutputSha256, GoldenSet.Question question, RegistryFixture fixture, List<Experiment> experiments, List<String> inputFiles) {
        /** 사전 입력 계수 대상(위치 id = probe/&lt;시험 id&gt;). */
        public List<InputAudit.Planned> planned(Prereg.Budget budget) {
            List<InputAudit.Planned> planned = new ArrayList<>();
            for (Experiment e : experiments) planned.add(new InputAudit.Planned("probe/" + e.id(), e.messages(), budget.planInput(), budget.planOutput()));
            return planned;
        }
    }
    private G08Probe() {}

    public static Plan load(Path root, Roles roles, Path manifestPath) {
        JsonNode manifest = Json.read(manifestPath), baseline = manifest.path("baseline"), experiments = manifest.path("experiments");
        if (!GoldenSet.keys(manifest).equals(MANIFEST_KEYS) || !manifest.path("schema").asText().equals(SCHEMA) || !manifest.path("goldenId").asText().equals(GOLDEN_ID)
            || !GoldenSet.keys(baseline).equals(Set.of("messagesSha256", "outputSha256")) || !baseline.path("messagesSha256").asText().matches("[0-9a-f]{64}") || !baseline.path("outputSha256").asText().matches("[0-9a-f]{64}")
            || !experiments.isArray() || experiments.size() != IDS.size()) throw invalid();
        RegistryFixture fixture = RegistryFixture.load(roles.fixture());
        GoldenSet.Question q = GoldenSet.load(roles.golden(), fixture).byId(GOLDEN_ID);
        byte[] promptBytes = Json.bytes(roles.planPrompt());
        MessageAssembler base = new MessageAssembler(promptBytes, fixture, roles.projection());
        if (base.kind() != MessageAssembler.Kind.PLAN) throw new EvalException(2, "PROMPT_INVALID");
        QRefContext baseReferences = QRefContext.extract(q.question());
        List<Map<String,String>> baseMessages = base.messages(q.question(), q.context(), baseReferences);
        if (!InputAudit.messagesSha256(baseMessages).equals(baseline.path("messagesSha256").asText())) throw new EvalException(2, "PROBE_BASELINE_MISMATCH");
        List<Experiment> loaded = new ArrayList<>(); List<String> files = new ArrayList<>();
        for (int i = 0; i < IDS.size(); i++) {
            JsonNode row = experiments.get(i); String id = IDS.get(i);
            if (!row.path("id").asText().equals(id)) throw invalid();
            if (i < 2) {
                String order = i == 0 ? SORTED : T_FIRST;
                if (!GoldenSet.keys(row).equals(Set.of("id", "call", "schemaPropertyOrder")) || !row.path("call").asText().equals(GENERATE_RAW) || !row.path("schemaPropertyOrder").asText().equals(order)) throw invalid();
                loaded.add(new Experiment(id, GENERATE_RAW, order, null, q.question(), baseReferences, baseMessages));
                continue;
            }
            JsonNode edit = row.path("edit"); String target = edit.path("target").asText(), find = edit.path("find").asText(), replace = edit.path("replace").asText();
            boolean fileTarget = Set.of("planPrompt", "fixture").contains(target);
            if (!GoldenSet.keys(row).equals(Set.of("id", "call", "edit")) || !row.path("call").asText().equals(CHAT) || !(fileTarget || target.equals("question"))
                || !edit.path("find").isTextual() || !edit.path("replace").isTextual() || !GoldenSet.keys(edit).equals(fileTarget ? Set.of("target", "find", "replace", "file") : Set.of("target", "find", "replace"))) throw invalid();
            List<Map<String,String>> messages; String question = q.question(); QRefContext references = baseReferences;
            if (target.equals("question")) {
                question = replaceOnce(q.question(), find, replace); references = QRefContext.extract(question);
                messages = base.messages(question, q.context(), references);
            } else {
                String file = edit.path("file").asText(); Path path = variantPath(root, file); files.add(file);
                byte[] variant = Json.bytes(path), source = target.equals("planPrompt") ? promptBytes : fixture.bytes();
                // 변형 파일은 기준 파일에 edit 를 한 번 적용한 바이트와 같아야 합니다(손 편집으로 다른 곳이 바뀐 변형 거부).
                if (!Arrays.equals(variant, replaceOnce(new String(source, StandardCharsets.UTF_8), find, replace).getBytes(StandardCharsets.UTF_8))) throw invalid();
                MessageAssembler assembler = target.equals("planPrompt") ? new MessageAssembler(variant, fixture, roles.projection()) : new MessageAssembler(promptBytes, RegistryFixture.load(path), roles.projection());
                messages = assembler.messages(q.question(), q.context(), references);
            }
            // 질문 변형은 user 메시지만, 프롬프트·fixture 변형은 system 메시지만 다릅니다(요소 하나).
            boolean systemChanged = !messages.get(0).equals(baseMessages.get(0)), userChanged = !messages.get(1).equals(baseMessages.get(1));
            if (messages.size() != 2 || systemChanged != fileTarget || userChanged == fileTarget) throw invalid();
            loaded.add(new Experiment(id, CHAT, null, target, question, references, messages));
        }
        return new Plan(baseline.path("messagesSha256").asText(), baseline.path("outputSha256").asText(), q, fixture, List.copyOf(loaded), List.copyOf(files));
    }
    /** find 가 정확히 한 번 나와야 합니다(0회·여러 회면 어느 요소를 바꾸는지 모호하므로 거부). */
    static String replaceOnce(String text, String find, String replace) {
        int at = text.indexOf(find);
        if (find.isEmpty() || at < 0 || at != text.lastIndexOf(find)) throw invalid();
        return text.substring(0, at) + replace + text.substring(at + find.length());
    }
    private static Path variantPath(Path root, String file) {
        Path base = root.toAbsolutePath().normalize(), path = base.resolve(file).normalize();
        if (!file.matches("[A-Za-z0-9_./-]+") || file.contains("..") || !path.startsWith(base)) throw invalid();
        return path;
    }
    private static EvalException invalid() { return new EvalException(2, "PROBE_INPUT_INVALID"); }

    /** 등록 순서대로 각 1회 송신하고 정제 필드만 씁니다. 측정 중 endpoint 오류(timeout 제외)는 지금까지의 행을 complete=false 로 보존한 뒤 exit 5. */
    public static void run(MeasurementSession session, Plan plan, ChatRenderers.Renderer renderer, String runId, Path out) {
        Prereg.Document document = session.prereg().document();
        if (!document.responseFormat().equals(ResponseSchema.POLICY)) throw new EvalException(2, "PREREG_INVALID");
        Prereg.Budget budget = document.budget(); int maxTokens = budget.planOutput(), inputLimit = budget.planInput();
        GoldenSet.Question q = plan.question(); String timezone = q.context().path("timezone").asText();
        ObjectNode expected = Json.MAPPER.createObjectNode(); expected.put("t", q.expected().t());
        for (String field : GoldenSet.ARG_FIELDS) expected.put(field, q.expected().field(field));
        ObjectNode result = F0Measurements.header(runId, 1);
        result.put("goldenId", GOLDEN_ID); result.put("baselineMessagesSha256", plan.baselineMessagesSha256()); result.put("baselineOutputSha256", plan.baselineOutputSha256());
        result.put("maxTokens", maxTokens); result.put("inputLimitTokens", inputLimit); result.put("topLogprobs", TOP_LOGPROBS);
        ArrayNode rows = result.putArray("experiments"); Path file = out.resolve(RESULT_FILE);
        try {
            for (Experiment e : plan.experiments()) {
                session.ensureResident();
                ObjectNode row = rows.addObject();
                row.put("id", e.id()); row.put("call", e.call()); row.put("schemaPropertyOrder", e.schemaPropertyOrder()); row.put("editTarget", e.editTarget());
                row.put("messagesSha256", InputAudit.messagesSha256(e.messages()));
                row.put("auditInputTokens", session.audited(e.messages(), inputLimit, maxTokens));
                long start = System.nanoTime();
                try {
                    if (e.call().equals(GENERATE_RAW)) {
                        ObjectNode schema = (ObjectNode)ResponseSchema.responseFormat(MessageAssembler.Kind.PLAN, e.references()).path("json_schema").path("schema");
                        Set<List<String>> order = e.schemaPropertyOrder().equals(T_FIRST) ? Set.of(List.of("properties")) : Set.of();
                        row.put("formatSha256", Json.sha(ResponseSchema.canonicalKeepingOrder(schema, order)));
                        OllamaChatClient.GenerateResult generated = session.client().generateRawStructured(session.verified(), document.generationModel(), renderer.render(e.messages()), schema, order, maxTokens, TOP_LOGPROBS);
                        record(row, generated.asChatResult(), elapsed(start), plan, e, expected, maxTokens, timezone);
                        row.set("logprobs", logprobSummary(generated));
                    } else {
                        ResponseSchema.Format format = ResponseSchema.forPolicy(document.responseFormat(), MessageAssembler.Kind.PLAN, e.references());
                        row.put("formatSha256", format.sha256());
                        record(row, session.chat(e.messages(), inputLimit, maxTokens, format).result(), elapsed(start), plan, e, expected, maxTokens, timezone);
                    }
                } catch (EvalException timeout) {
                    if (!timeout.getMessage().equals("ENDPOINT_TIMEOUT")) throw timeout;
                    row.put("status", "TIMEOUT"); row.put("latencyMs", elapsed(start));
                }
                Json.write(file, result);
            }
        } catch (EvalException e) {
            if (e.exitCode() != 5) throw e;
            result.put("abortReason", e.getMessage()); Json.write(file, result); throw e;
        }
        ObjectNode reproduction = result.putObject("baselineReproduction");
        for (JsonNode row : rows) if (Set.of("E0", "N1").contains(row.path("id").asText())) reproduction.put(row.path("id").asText(), row.path("sameAsBaselineOutput").asBoolean(false));
        result.put("complete", true); Json.write(file, result);
    }
    private static void record(ObjectNode row, OllamaChatClient.ChatResult r, long latencyMs, Plan plan, Experiment e, JsonNode expected, int maxTokens, String timezone) {
        String sha = Json.sha(r.content().getBytes(StandardCharsets.UTF_8));
        row.put("status", "OK"); row.put("finishReason", ClassificationJudge.sanitize(r.finishReason()));
        row.put("promptTokens", r.promptTokens()); row.put("completionTokens", r.completionTokens()); row.put("latencyMs", latencyMs);
        row.put("outputSha256", sha); row.put("sameAsBaselineOutput", sha.equals(plan.baselineOutputSha256()));
        ClassificationJudge.Verdict verdict = ClassificationJudge.judge(r, maxTokens, MessageAssembler.Kind.PLAN, true, timezone, e.references());
        row.putObject("firstAttemptVerdict").put("valid", verdict.valid()).put("errorCode", verdict.errorCode()).put("stepsDetail", verdict.stepsDetail());
        ObjectNode diagnostics = AttemptDiagnostics.diagnose(r, maxTokens, MessageAssembler.Kind.PLAN, e.references(), expected, e.question(), plan.question().context(), timezone, plan.fixture());
        row.put("t", diagnostics.path("t").path("value").asText()); row.put("toolConsistency", diagnostics.path("steps").path("toolConsistency").asText());
        row.put("stepsIncludeLogsSearch", logsSearch(r.content())); row.set("attemptDiagnostics", diagnostics);
    }
    /** steps 배열에 tc.logs.search 문자열이 있는지(YES·NO), 객체·배열이 아니면 NOT_EVALUABLE. 원문은 메모리에서만 읽습니다. */
    static String logsSearch(String content) {
        JsonNode node;
        try { node = Json.MAPPER.readTree(content); } catch (IOException | RuntimeException e) { return "NOT_EVALUABLE"; }
        if (node == null || !node.isObject() || !node.path("steps").isArray()) return "NOT_EVALUABLE";
        for (JsonNode step : node.path("steps")) if (step.isTextual() && step.textValue().equals("tc.logs.search")) return "YES";
        return "NO";
    }
    /** logprobs 필드가 없으면 ABSENT. 있으면 선택 토큰을 이어 붙인 생성 텍스트에서 두 판별 위치의 후보 확률을 냅니다. */
    static ObjectNode logprobSummary(OllamaChatClient.GenerateResult generated) {
        ObjectNode out = Json.MAPPER.createObjectNode();
        if (generated.logprobs() == null) { out.put("status", "ABSENT"); return out; }
        StringBuilder text = new StringBuilder(); generated.logprobs().forEach(p -> text.append(p.chosen().token()));
        out.put("status", "PRESENT"); out.put("positions", generated.logprobs().size()); out.put("tokensMatchContent", text.toString().equals(generated.content()));
        out.set("stepsFirst", position(text.toString(), generated.logprobs(), STEPS_OPEN, true, STEPS_CLASSES));
        out.set("tValue", position(text.toString(), generated.logprobs(), T_OPEN, false, T_CLASSES));
        return out;
    }
    /**
     * pattern 이 처음 끝나는 곳(anchor, skipSpace 면 그 뒤 공백을 건너뛴 곳)을 덮는 생성 토큰의 top_logprobs 를 분류합니다.
     * 후보 c 의 분류 = 앞 토큰들 + c 가 anchor 까지 실제 생성 텍스트와 같고 그 뒤 첫 문자(skipSpace 면 첫 비공백 문자)가 classes 의 키일 때 그 이름.
     * classes 밖(OTHER·anchor 전에서 갈라짐 DIVERGED·문자가 아직 없음 INCOMPLETE)은 개수·확률 합만 남기고 토큰 문자열은 남기지 않습니다.
     */
    static ObjectNode position(String text, List<OllamaChatClient.Position> positions, Pattern pattern, boolean skipSpace, Map<Character,String> classes) {
        ObjectNode out = Json.MAPPER.createObjectNode();
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) { out.put("status", "NOT_FOUND"); return out; }
        int anchor = matcher.end(), target = skipSpace ? skipSpaces(text, anchor) : anchor, offset = 0, index = -1;
        for (int i = 0; i < positions.size(); i++) {
            int length = positions.get(i).chosen().token().length();
            if (target >= offset && target < offset + length) { index = i; break; }
            offset += length;
        }
        if (index < 0) { out.put("status", "NOT_FOUND"); return out; }
        String prefix = text.substring(0, offset), required = text.substring(0, anchor);
        OllamaChatClient.Position position = positions.get(index);
        out.put("status", "FOUND"); out.put("tokenIndex", index);
        String chosen = classOf(prefix + position.chosen().token(), required, skipSpace, classes);
        out.put("chosenClass", chosen); out.put("chosenProbability", Math.exp(position.chosen().logprob()));
        ObjectNode byClass = out.putObject("classes");
        for (String name : new TreeSet<>(classes.values())) { ObjectNode c = byClass.putObject(name); c.put("probability", 0.0); c.putArray("tokens"); }
        double otherProbability = 0; int otherCount = 0;
        for (OllamaChatClient.TokenLogprob candidate : position.top()) {
            String name = classOf(prefix + candidate.token(), required, skipSpace, classes); double probability = Math.exp(candidate.logprob());
            if (!classes.containsValue(name)) { otherProbability += probability; otherCount++; continue; }
            ObjectNode c = (ObjectNode)byClass.get(name);
            c.put("probability", c.path("probability").asDouble() + probability);
            ((ArrayNode)c.get("tokens")).addObject().put("token", candidate.token()).put("logprob", candidate.logprob()).put("probability", probability);
        }
        out.put("topCount", position.top().size()); out.put("otherCount", otherCount); out.put("otherProbability", otherProbability);
        return out;
    }
    static String classOf(String candidate, String required, boolean skipSpace, Map<Character,String> classes) {
        if (!candidate.regionMatches(0, required, 0, Math.min(candidate.length(), required.length()))) return "DIVERGED";
        int at = candidate.length() <= required.length() ? candidate.length() : skipSpace ? skipSpaces(candidate, required.length()) : required.length();
        if (at >= candidate.length()) return "INCOMPLETE";
        return classes.getOrDefault(candidate.charAt(at), "OTHER");
    }
    private static int skipSpaces(String text, int from) {
        int at = from;
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        return at;
    }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
