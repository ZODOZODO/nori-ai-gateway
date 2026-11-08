package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Gateway verify hook 호출(§4.5). 토큰은 헤더로만 보내고 오류에는 경로·상태 코드만 담는다. */
public final class HookClient {
    public static final class HookFailure extends RuntimeException {
        HookFailure(String path, int status) { super("HOOK_FAILED:" + path + ":" + status); }
    }
    private final HttpClient http;
    private final URI base;
    private final String hookToken;

    public HookClient(HttpClient http, URI base, String hookToken) { this.http = http; this.base = base; this.hookToken = hookToken; }

    public Instant clockNow() { return Instant.parse(call("GET", "/__test/clock", null, 200).path("now").asText()); }
    public void reset() { call("POST", "/__test/reset", null, 204); }
    public void arm(String actorSub, String requestKey, JsonNode scenario) {
        call("POST", "/__test/arm", Map.of("actorSub", actorSub, "requestKey", requestKey, "scenario", scenario), 204);
    }
    /** Mock 위임 등록. 원문 {@code dlg} 는 요청 본문에만 있고 반환·기록하지 않는다. */
    public void registerDelegation(String sub, String dlg) { call("POST", "/__test/tcmock/delegations", Map.of("sub", sub, "dlg", dlg), 204); }
    public JsonNode clock(Map<String,Object> operation) { return call("POST", "/__test/clock", operation, 200); }
    public JsonNode post(String path, Map<String,Object> body, int expected) { return call("POST", path, body, expected); }

    private JsonNode call(String method, String path, Object body, int expected) {
        try {
            var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15)).header("X-AIGW-Hook-Token", hookToken)
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(CanonicalJson.MAPPER.writeValueAsBytes(body)));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != expected) throw new HookFailure(path, response.statusCode());
            return response.body().length == 0 ? CanonicalJson.MAPPER.nullNode() : CanonicalJson.MAPPER.readTree(response.body());
        } catch (IOException e) { throw new HookFailure(path, -1); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new HookFailure(path, -1); }
    }
}
