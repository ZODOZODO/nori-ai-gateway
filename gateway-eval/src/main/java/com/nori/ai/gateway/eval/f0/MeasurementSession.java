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

    private MeasurementSession(OllamaChatClient client, Preflight.Verified verified, Prereg.State prereg, Runtime runtime, ObjectNode binding, String template) {
        this.client = client; this.verified = verified; this.prereg = prereg; this.runtime = runtime; this.binding = binding; this.template = template;
    }
    public static MeasurementSession start(Context context, String command, String endpoint, String runId, Path bindingPath, Consumer<Runtime> beforeBinding) {
        Prereg.State prereg = Prereg.verify(context.root(), context.root().resolve(Prereg.DEFAULT_PATH), context.git());
        SpecBinding spec = new SpecGuard(context.env()).check(null, null, context.root().resolve("docs/gates/spec-baseline.md"));
        if (Files.exists(bindingPath)) throw new EvalException(2, "RUN_BINDING_EXISTS");
        OllamaChatClient client = new OllamaChatClient(endpoint, prereg.document().requestTimeout());
        Preflight.Result preflight = Preflight.run(client, prereg.document().generationModel(), prereg.document().embeddingModel());
        if (!preflight.summary().ok()) throw new EvalException(preflight.summary().reason().equals("MODEL_DIGEST_MISMATCH") ? 6 : 4, preflight.summary().reason());
        String template = client.showTemplate(prereg.document().generationModel());
        Runtime runtime = new Runtime(preflight.verified().generationDigest(), Json.sha(template.getBytes(StandardCharsets.UTF_8)), tokenizerSha(context.root()));
        if (beforeBinding != null) beforeBinding.accept(runtime);
        ObjectNode binding = Json.MAPPER.createObjectNode();
        binding.put("schema", "f0-run-binding-v1"); binding.put("runId", runId); binding.put("command", command); binding.put("startedAt", context.clock().instant().toString());
        binding.put("specSha", spec.specSha()); binding.put("specShaSource", spec.specShaSource()); binding.put("specInputOrigin", spec.specInputOrigin());
        binding.put("harnessDigest", prereg.document().harnessDigest());
        binding.set("runtimeManifest", Json.MAPPER.valueToTree(runtime.manifest())); binding.put("runtimeManifestSha256", runtime.sha256());
        binding.set("parameterManifest", prereg.document().parameters()); binding.put("parameterManifestSha256", prereg.document().parameterManifestSha256());
        binding.put("registeredCommit", prereg.document().registeredCommit()); binding.put("preregCommit", prereg.preregCommit()); binding.put("preregCommitTime", prereg.preregCommitTime().toString());
        writeNew(bindingPath, Json.canonical(binding));
        return new MeasurementSession(client, preflight.verified(), prereg, runtime, binding, template);
    }
    /** pin 파일의 SHA 와 실제 tokenizer.json 바이트가 같을 때만 런타임 manifest 에 넣습니다(대체 tokenizer 경로 0). */
    static String tokenizerSha(Path root) {
        Path pin = root.resolve(TOKENIZER_PIN), file = root.resolve(TOKENIZER_FILE);
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
    public ObjectNode binding() { return binding.deepCopy(); }
    /** chat-template 캡처 전용 — 같은 세션이 읽은 template 필드. 다른 명령은 SHA 만 씁니다. */
    String template() { return template; }
    public void ensureResident() { Preflight.ensureResident(client, prereg.document().generationModel(), prereg.document().embeddingModel(), verified); }
}
