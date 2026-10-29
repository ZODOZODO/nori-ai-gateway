package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.EvalException;
import java.util.*;

/** /api/ps·/api/tags 를 메모리에서만 읽어 허용 필드 요약을 만든다. ok=true 일 때만 추론 호출 증표(Verified)를 낸다. */
public final class Preflight {
    public static final int REQUIRED_CONTEXT = 4096;
    private static final String NAME = "[A-Za-z0-9._:/-]{1,128}", DIGEST = "(?:sha256:)?[0-9a-f]{64}";
    public record ModelSummary(String name, String digest, Integer context_length) {}
    public record Summary(List<ModelSummary> models, int residentCount, boolean ok, String reason) {}
    /** 통과한 preflight 에서만 만들어지는 증표. 생성자가 비공개라 다른 경로로 추론 호출을 열 수 없다. */
    public static final class Verified {
        private final String generationDigest;
        private Verified(String generationDigest) { this.generationDigest = generationDigest; }
        public String generationDigest() { return generationDigest; }
    }
    public record Result(Summary summary, Verified verified) {}

    public static Result run(OllamaChatClient client, String generationModel, String embeddingModel) {
        JsonNode ps, tags;
        try { ps = client.ps(); tags = client.tags(); }
        catch (EvalException e) { return new Result(new Summary(List.of(), 0, false, "PREFLIGHT_ENDPOINT_ERROR"), null); }
        List<ModelSummary> models = new ArrayList<>(); boolean malformed = !ps.path("models").isArray();
        for (JsonNode model : ps.path("models")) {
            String name = model.path("name").asText(""), digest = model.path("digest").asText("");
            if (!name.matches(NAME) || !digest.matches(DIGEST)) { malformed = true; continue; }
            // context_length 는 없으면 null 로 기록, 있으면 0 이상 int 정수여야 합니다(4096.9 를 4096 으로 자르지 않음).
            JsonNode context = model.path("context_length");
            if (!context.isMissingNode() && !context.isNull() && !OllamaChatClient.isCount(context)) { malformed = true; continue; }
            models.add(new ModelSummary(name, normalize(digest), OllamaChatClient.isCount(context) ? context.intValue() : null));
        }
        if (malformed) return new Result(new Summary(List.copyOf(models), models.size(), false, "PREFLIGHT_RESPONSE_INVALID"), null);
        ModelSummary generation = find(models, generationModel), embedding = find(models, embeddingModel);
        if (generation == null || embedding == null) return new Result(new Summary(List.copyOf(models), models.size(), false, "MODEL_NOT_RESIDENT"), null);
        // bge-m3 의 context_length 는 기록만 하고 4096 을 강제하지 않습니다.
        if (generation.context_length() == null || generation.context_length() != REQUIRED_CONTEXT) return new Result(new Summary(List.copyOf(models), models.size(), false, "CONTEXT_LENGTH_MISMATCH"), null);
        String tagged = null;
        for (JsonNode model : tags.path("models")) if (matches(model.path("name").asText(""), generationModel) && model.path("digest").asText("").matches(DIGEST)) tagged = normalize(model.path("digest").asText());
        if (tagged == null || !tagged.equals(generation.digest())) return new Result(new Summary(List.copyOf(models), models.size(), false, "MODEL_DIGEST_MISMATCH"), null);
        return new Result(new Summary(List.copyOf(models), models.size(), true, "OK"), new Verified(generation.digest()));
    }
    /** 측정 호출 직전 두 모델이 아직 상주하는지만 다시 확인합니다(상주하지 않으면 추론 호출 0 — 모델 로드 유발 방지). */
    public static void ensureResident(OllamaChatClient client, String generationModel, String embeddingModel, Verified verified) {
        JsonNode ps = client.ps(); boolean generation = false, embedding = false;
        for (JsonNode model : ps.path("models")) {
            String name = model.path("name").asText("");
            if (matches(name, generationModel) && normalize(model.path("digest").asText("")).equals(verified.generationDigest())) generation = true;
            if (matches(name, embeddingModel)) embedding = true;
        }
        if (!generation || !embedding) throw new EvalException(5, "MODEL_NOT_RESIDENT");
    }
    static boolean matches(String name, String model) { return name.equals(model) || (!model.contains(":") && name.equals(model + ":latest")); }
    private static ModelSummary find(List<ModelSummary> models, String model) { return models.stream().filter(m -> matches(m.name(), model)).findFirst().orElse(null); }
    static String normalize(String digest) { return digest.startsWith("sha256:") ? digest.substring(7) : digest; }
}
