package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 수용 Job 입력. 값은 차트가 넣은 env·system property·Secret 파일에서만 읽고 로그·예외 메시지에 값을 담지 않는다.
 * env 이름은 차트 {@code templates/tests/acceptance-job.yaml} 과 같은 정본이다.
 */
public record AcceptanceConfig(Map<String,String> env, Map<String,String> properties) {
    public AcceptanceConfig { env = Map.copyOf(env); properties = Map.copyOf(properties); }

    /** suite 의 ingest 단계(§11.9) = 접두 단계(F1-RUBRIC→F1, F2-OUTAGE→F2, F3-PROBE→F3, F4-RESTORE-PREP→F4). */
    public static Optional<String> stageOf(String suite) {
        if (suite == null || !suite.matches("F[1-4](-[A-Z]+(-[A-Z]+)?)?")) return Optional.empty();
        return Optional.of(suite.substring(0, 2));
    }

    public Optional<String> suite() { return Optional.ofNullable(properties.get("aigw.acceptance.suite")).filter(s -> stageOf(s).isPresent()); }
    public Optional<String> parentRunId() { return Optional.ofNullable(env.get("AIGW_PARENT_RUN_ID")).filter(s -> s.matches("[A-Za-z0-9_.:-]{1,128}")); }
    /** 경로 문자열이 경로로 해석되지 않으면 부재와 같다(SPEC_INPUT_MISSING — 검증 전 예외 0). */
    public Optional<Path> specGzipFile() {
        try { return Optional.ofNullable(env.get("AIGW_SPEC_GZIP_FILE")).map(Path::of); }
        catch (java.nio.file.InvalidPathException e) { return Optional.empty(); }
    }
    public Optional<String> expectedSpecSha256() { return Optional.ofNullable(env.get("AIGW_EXPECTED_SPEC_SHA256")); }
    public Optional<String> gatewayBaseUrl() { return Optional.ofNullable(env.get("AIGW_GATEWAY_BASE_URL")); }

    /** {@code image-digests.json} 의 digest·빌드 신원과 렌더 SHA(비밀 아님). 형식 위반이면 빈 값이다. */
    public Optional<JsonNode> buildIdentity() {
        String value = env.get("AIGW_BUILD_IDENTITY_JSON");
        if (value == null) return Optional.empty();
        try { JsonNode node = CanonicalJson.MAPPER.readTree(value); return node.isObject() ? Optional.of(node) : Optional.empty(); }
        catch (IOException e) { return Optional.empty(); }
    }

    /** Secret 파일 한 줄 값. 부재·빈 값은 그 이름만 담은 오류다. */
    public String secret(String envName) {
        String file = env.get(envName);
        if (file == null) throw new IllegalStateException("ACCEPTANCE_SECRET_MISSING:" + envName);
        try {
            String value = Files.readString(Path.of(file), StandardCharsets.UTF_8).strip();
            if (value.isEmpty()) throw new IllegalStateException("ACCEPTANCE_SECRET_MISSING:" + envName);
            return value;
        } catch (IOException e) { throw new IllegalStateException("ACCEPTANCE_SECRET_MISSING:" + envName); }
    }
}
