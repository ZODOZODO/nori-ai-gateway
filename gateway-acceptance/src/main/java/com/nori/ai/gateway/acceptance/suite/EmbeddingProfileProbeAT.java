package com.nori.ai.gateway.acceptance.suite;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.acceptance.AcceptanceMeasurements;
import com.nori.ai.gateway.acceptance.F1Runtime;
import com.nori.ai.gateway.acceptance.HookClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * suite {@code F3-PROBE} 전용 측정(Plan P19, N1). 배포 Gateway 의 {@code POST /__test/embedding-probe} 응답에서 {@code dimension}·{@code modelDigest} 만
 * {@code kind=MEASUREMENT}·{@code measurementId=f3-probe:embedding-profile} 레코드 값으로 남긴다. leaf 가 아니므로 leaf tag 가 없고 gate 판정에 쓰이지 않는다.
 */
@Tag("suite-F3-PROBE")
public final class EmbeddingProfileProbeAT {
    public static final String MEASUREMENT_ID = "f3-probe:embedding-profile";
    private static final Set<String> RESPONSE_FIELDS = Set.of("dimension", "modelDigest");

    @Test void embeddingProfile() {
        var config = F1Runtime.config();
        final HookClient hooks;
        try {
            URI base = URI.create(Objects.requireNonNull(config.env().get("AIGW_GATEWAY_BASE_URL")));
            if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null) throw new IllegalArgumentException();
            hooks = new HookClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), base, config.secret("AIGW_HOOK_TOKEN_FILE"));
        } catch (RuntimeException missing) { throw new TestAbortedException("VERIFY_CREDENTIALS_MISSING"); }
        AcceptanceMeasurements.add(MEASUREMENT_ID, measure(hooks));
    }

    /** hook 응답을 정제 값으로 바꾼다. 응답 필드가 정확히 두 개가 아니거나 형식이 다르면 측정 실패다(벡터·추가 필드 수용 0). */
    public static Map<String,Object> measure(HookClient hooks) {
        JsonNode body = hooks.post("/__test/embedding-probe", Map.of(), 200);
        var names = new HashSet<String>(); body.fieldNames().forEachRemaining(names::add);
        JsonNode dimension = body.path("dimension"), digest = body.path("modelDigest");
        if (!body.isObject() || !names.equals(RESPONSE_FIELDS) || !dimension.isIntegralNumber() || !dimension.canConvertToLong() || dimension.longValue() < 1
            || !(digest.isNull() || digest.isTextual() && digest.asText().matches("[0-9a-f]{64}"))) throw new AssertionError("EMBEDDING_PROBE_RESPONSE_INVALID");
        var values = new LinkedHashMap<String,Object>();
        values.put("dimension", dimension.longValue());
        values.put("modelDigest", digest.isNull() ? null : digest.asText());
        return values;
    }
}
