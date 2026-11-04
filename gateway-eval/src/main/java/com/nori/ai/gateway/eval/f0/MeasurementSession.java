package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.SpecBinding;
import com.nori.ai.gateway.eval.spec.SpecGuard;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;

/**
 * 측정 명령 공통 시작 순서: PREREG 커밋 검사 → SPEC 실제 바이트 검사 → preflight(실패 exit 4, digest 불일치 exit 6) → 런타임 manifest
 * → run-binding.json 신규 작성(이미 있으면 덮어쓰지 않고 exit 2). 이 순서가 끝나기 전에는 추론 호출을 보내지 않는다.
 */
public final class MeasurementSession {
    public static final String TOKENIZER_PIN = "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json.sha256";
    public static final String TOKENIZER_FILE = "eval/f0/tokenizer/qwen3-4b-instruct-2507.tokenizer.json";
    public record Runtime(String modelDigest, String templateSha256, String tokenizerSha256) {
        public Map<String,String> manifest() { return new TreeMap<>(Map.of("modelDigest", modelDigest, "templateSha256", templateSha256, "tokenizerSha256", tokenizerSha256)); }
        public String sha256() { return Json.sha(Json.canonical(Json.MAPPER.valueToTree(manifest()))); }
    }
    public record Context(Path root, Map<String,String> env, GitReader git, Clock clock) {}
    private final OllamaChatClient client;
    private final Preflight.Verified verified;
    private final Prereg.State prereg;
    private final Runtime runtime;
    private final ObjectNode binding;
    private final String template;
    private final InputAudit audit;
    private final Clock clock;
    /** 응답과 그 호출의 사전 입력 계수(v1 은 audit 없음 = null). 실제 usage.prompt_tokens 와 따로 기록합니다. */
    public record Sent(OllamaChatClient.ChatResult result, Integer auditInputTokens) {}

    private MeasurementSession(OllamaChatClient client, Preflight.Verified verified, Prereg.State prereg, Runtime runtime, ObjectNode binding, String template, InputAudit audit, Clock clock) {
        this.client = client; this.verified = verified; this.prereg = prereg; this.runtime = runtime; this.binding = binding; this.template = template; this.audit = audit; this.clock = clock;
    }
    public static MeasurementSession start(Context context, String command, String endpoint, String runId, Path bindingPath, Consumer<Runtime> beforeBinding) {
        return start(context, context.root().resolve(Prereg.DEFAULT_PATH), command, endpoint, runId, bindingPath, beforeBinding);
    }
    public static MeasurementSession start(Context context, Path preregPath, String command, String endpoint, String runId, Path bindingPath, Consumer<Runtime> beforeBinding) {
        Prereg.State prereg = Prereg.verify(context.root(), preregPath, context.git());
        SpecBinding spec = new SpecGuard(context.env()).check(null, null, context.root().resolve("docs/gates/spec-baseline.md"));
        if (Files.exists(bindingPath)) throw new EvalException(2, "RUN_BINDING_EXISTS");
        OllamaChatClient client = new OllamaChatClient(endpoint, prereg.document().requestTimeout());
        Preflight.Result preflight = Preflight.run(client, prereg.document().generationModel(), prereg.document().embeddingModel(), prereg.document().contextTokens(), prereg.document().generationModelDigest());
        if (!preflight.summary().ok()) throw new EvalException(preflight.summary().reason().equals("MODEL_DIGEST_MISMATCH") ? 6 : 4, preflight.summary().reason());
        String template = client.showTemplate(prereg.document().generationModel());
        Runtime runtime = new Runtime(preflight.verified().generationDigest(), Json.sha(template.getBytes(StandardCharsets.UTF_8)), tokenizerSha(context.root(), prereg.document().profile()));
        InputAudit audit = null;
        if (prereg.document().versioned()) { audit = InputAudit.load(context.root().resolve(prereg.document().input("inputAudit")), prereg.document().contextTokens()); audit.requireRuntime(runtime); }
        if (beforeBinding != null) beforeBinding.accept(runtime);
        ObjectNode binding = Json.MAPPER.createObjectNode();
        binding.put("schema", "f0-run-binding-v1"); binding.put("runId", runId); binding.put("command", command); binding.put("startedAt", context.clock().instant().toString());
        binding.put("specSha", spec.specSha()); binding.put("specShaSource", spec.specShaSource()); binding.put("specInputOrigin", spec.specInputOrigin());
        binding.put("preregPath", Prereg.relativePath(context.root(), preregPath)); binding.put("preregSha256", Json.sha(Json.bytes(preregPath)));
        binding.put("harnessDigest", prereg.document().harnessDigest());
        binding.set("runtimeManifest", Json.MAPPER.valueToTree(runtime.manifest())); binding.put("runtimeManifestSha256", runtime.sha256());
        binding.set("parameterManifest", prereg.document().parameters()); binding.put("parameterManifestSha256", prereg.document().parameterManifestSha256());
        binding.put("registeredCommit", prereg.document().registeredCommit()); binding.put("preregCommit", prereg.preregCommit()); binding.put("preregCommitTime", prereg.preregCommitTime().toString());
        writeNew(bindingPath, Json.canonical(binding));
        return new MeasurementSession(client, preflight.verified(), prereg, runtime, binding, template, audit, context.clock());
    }
    /** v1~v3(Jetson) 등록 tokenizer. */
    static String tokenizerSha(Path root) { return tokenizerSha(root, Prereg.RuntimeProfile.JETSON); }
    /** pin 파일의 SHA 와 실제 tokenizer.json 바이트가 같을 때만 런타임 manifest 에 넣습니다(대체 tokenizer 경로 0). 파일 쌍은 등록 profile 의 것입니다. */
    static String tokenizerSha(Path root, Prereg.RuntimeProfile profile) {
        Path pin = root.resolve(profile.tokenizerPin()), file = root.resolve(profile.tokenizerFile());
        if (!Files.isRegularFile(pin) || !Files.isRegularFile(file)) throw new EvalException(2, "TOKENIZER_PIN_MISSING");
        String[] parts = new String(Json.bytes(pin), StandardCharsets.UTF_8).strip().split("\\s+");
        if (parts.length == 0 || !parts[0].matches("[0-9a-f]{64}")) throw new EvalException(2, "TOKENIZER_PIN_MISSING");
        if (!Json.sha(Json.bytes(file)).equals(parts[0])) throw new EvalException(2, "TOKENIZER_SHA_MISMATCH");
        return parts[0];
    }
    public static void writeNew(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) { throw new EvalException(2, "RUN_BINDING_EXISTS"); }
        catch (IOException e) { throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
    }
    public OllamaChatClient client() { return client; }
    public Preflight.Verified verified() { return verified; }
    public Prereg.State prereg() { return prereg; }
    public Runtime runtime() { return runtime; }
    /** 시도별 송신·수신 시각(UTC) 기록용 — 공유 서버 겹침 판정(SharedServerOverlap). */
    public Clock clock() { return clock; }
    public ObjectNode binding() { return binding.deepCopy(); }
    /** chat-template 캡처 전용 — 같은 세션이 읽은 template 필드. 다른 명령은 SHA 만 씁니다. */
    String template() { return template; }
    /** v2 는 사전 계수 표에 있고 예산 안인 메시지만 보냅니다(재시도 포함, 매 호출 직전). */
    public Integer audited(List<Map<String,String>> messages, int inputLimit, int maxTokens) { return audit == null ? null : audit.require(messages, inputLimit, maxTokens); }
    public Sent chat(List<Map<String,String>> messages, int inputLimit, int maxTokens) { return chat(messages, inputLimit, maxTokens, null); }
    /** 분류·계획 호출. responseFormat 은 등록 parameters.responseFormat 이 NONE 이 아닐 때만 요청별로 만든 값입니다. v4 는 등록 temperature 를 보냅니다(v1~v3 은 기존 고정 0 body). */
    public Sent chat(List<Map<String,String>> messages, int inputLimit, int maxTokens, ResponseSchema.Format responseFormat) {
        Integer counted = audited(messages, inputLimit, maxTokens);
        Prereg.Document document = prereg.document();
        OllamaChatClient.ChatResult result = document.v4() ? client.chat(verified, document.generationModel(), messages, maxTokens, responseFormat, document.temperature())
            : client.chat(verified, document.generationModel(), messages, maxTokens, responseFormat);
        return new Sent(result, counted);
    }
    public void ensureResident() { Preflight.ensureResident(client, prereg.document().generationModel(), prereg.document().embeddingModel(), verified); }
}
