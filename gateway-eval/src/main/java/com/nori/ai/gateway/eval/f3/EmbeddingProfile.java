package com.nori.ai.gateway.eval.f3;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.eval.Args;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.report.EvidenceRecord;
import com.nori.ai.gateway.eval.report.LeafCatalog;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;

/**
 * f3 embedding-profile: 직접 HTTP 호출 없이, 검증된 정제 MEASUREMENT 레코드(f3-probe:embedding-profile·FILE_HASHED·contentSha256 일치)에서
 * dimension·modelDigest 만 읽어 profile id(bge-m3@&lt;digest&gt;/&lt;dim&gt;/cosine)를 만들고 형식만 검사한다. 측정 자체는 P22 몫.
 */
public final class EmbeddingProfile {
    public static final String MEASUREMENT_ID = "f3-probe:embedding-profile";
    private EmbeddingProfile() {}
    public static void execute(String[] arguments, Path root, PrintStream out) {
        if (arguments.length < 2 || !arguments[1].equals("embedding-profile")) throw new EvalException(2, "UNKNOWN_COMMAND");
        Args args = new Args(arguments, 2, Set.of());
        args.only("--evidence", "--out");
        Path evidence = root.resolve(args.required("--evidence")).normalize(), target = root.resolve(args.required("--out")).normalize();
        Map<String,Object> profile = build(Json.read(evidence), LeafCatalog.load(null));
        Json.write(target, profile);
        out.println("F3_EMBEDDING_PROFILE " + profile.get("profileId"));
    }
    static Map<String,Object> build(JsonNode record, LeafCatalog catalog) {
        try { EvidenceRecord.validate(record, catalog); }
        catch (EvalException e) { throw new EvalException(2, e.getMessage().equals("EVIDENCE_HASH_MISMATCH") ? "EVIDENCE_HASH_MISMATCH" : "MEASUREMENT_INVALID"); }
        if (!record.path("kind").asText().equals("MEASUREMENT") || !record.path("measurementId").asText().equals(MEASUREMENT_ID) || !record.path("specShaSource").asText().equals("FILE_HASHED") || !record.path("stage").asText().equals("F3")) throw new EvalException(2, "MEASUREMENT_INVALID");
        JsonNode values = record.path("values"), dimension = values.path("dimension");
        String digest = values.path("modelDigest").asText("");
        // Plan P02: 정수 차원 > 0 만 검사(임의 상한 없음). long 으로 손실 없이 표현되지 않는 값(overflow)은 거부합니다.
        if (!dimension.isIntegralNumber() || !dimension.canConvertToLong() || dimension.longValue() < 1) throw new EvalException(2, "PROFILE_DIMENSION_INVALID");
        if (!digest.matches("(?:sha256:)?[0-9a-f]{64}")) throw new EvalException(2, "PROFILE_DIGEST_INVALID");
        String normalized = digest.startsWith("sha256:") ? digest.substring(7) : digest;
        Map<String,Object> profile = new TreeMap<>();
        profile.put("profileId", "bge-m3@" + normalized + "/" + dimension.longValue() + "/cosine"); profile.put("model", "bge-m3"); profile.put("modelDigest", normalized);
        profile.put("dimension", dimension.longValue()); profile.put("metric", "cosine"); profile.put("sourceContentSha256", record.path("contentSha256").asText()); profile.put("sourceRunId", record.path("runId").asText());
        return profile;
    }
}
