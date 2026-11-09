package com.nori.ai.gateway.acceptance;

import com.nori.ai.gateway.tcmock.testing.StubAssertionSigner;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 수용 하네스의 Gateway API 호출(N10). 단언 {@code iat} 는 직전 {@code GET /__test/clock} 의 Gateway now 이고,
 * Job 을 만드는 요청마다 새 {@code dlg} 를 Mock 에 먼저 등록한 뒤 접수한다. {@code dlg} 원문은 메모리에서만 쓴다.
 */
public final class GatewayClient {
    private final HttpClient http;
    private final URI base;
    private final String serviceToken;
    private final StubAssertionSigner signer;
    private final HookClient hooks;
    private final SecureRandom random = new SecureRandom();

    public GatewayClient(HttpClient http, URI base, String serviceToken, StubAssertionSigner signer, HookClient hooks) {
        this.http = http; this.base = base; this.serviceToken = serviceToken; this.signer = signer; this.hooks = hooks;
    }

    /** 접수 {@code Idempotency-Key} = fixture 요청 식별자의 결정론 파생 값. */
    public static String idempotencyKey(String fixtureRequestId) {
        return "fx-" + CanonicalJson.sha256(fixtureRequestId.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }
    /** CSPRNG base64url 32바이트. */
    public String newDlg() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    /** PREP 의 결정론 재현이 필요한 경우만: HMAC-SHA256(회차 비밀, fixtureRequestId) base64url. */
    public static String deterministicDlg(byte[] roundSecret, String fixtureRequestId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(roundSecret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(fixtureRequestId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException("HMAC 을 사용할 수 없습니다"); }
    }

    /** Job 생성 요청: Mock 위임 등록 → Gateway 시계로 서명 → 접수. 응답 상태와 본문을 돌려준다. */
    public HttpResponse<String> submit(String path, String fixtureRequestId, Map<String,Object> claims, String dlg, String jsonBody) {
        return submitObserved(path,fixtureRequestId,claims,dlg,jsonBody).response();
    }
    public record Submission(HttpResponse<String> response, Set<String> sentinels) {
        public Submission { sentinels=Set.copyOf(sentinels); }
        @Override public String toString(){return "Submission[보호됨]";}
    }
    public Submission submitObserved(String path, String fixtureRequestId, Map<String,Object> claims, String dlg, String jsonBody) {
        hooks.registerDelegation(String.valueOf(claims.get("sub")), dlg);
        var signed = new LinkedHashMap<>(claims); signed.put("dlg", dlg);
        String assertion=signer.sign(signed,hooks.clockNow());
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",assertion).header("Idempotency-Key", idempotencyKey(fixtureRequestId)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
        return new Submission(send(request, path),Set.of(dlg,serviceToken,assertion));
    }
    public HttpResponse<String> get(String path, Map<String,Object> claims) { return send(authorized(path, claims).GET().build(), path); }

    /** 인입 음성 시험은 헤더를 정확히 하나씩 깨뜨려야 하므로 자동 보정 없이 전송합니다. */
    public HttpResponse<String> request(String path, String assertion, String token, String body) {
        return request("POST", path, assertion, token, body);
    }
    public HttpResponse<String> request(String method, String path, String assertion, String token, String body) {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (assertion != null) request.header("X-Actor-Assertion", assertion);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request.method(method, method.equals("GET") ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), path);
    }

    private HttpRequest.Builder authorized(String path, Map<String,Object> claims) {
        Instant now = hooks.clockNow();
        return HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + serviceToken).header("X-Actor-Assertion", signer.sign(claims, now));
    }
    private HttpResponse<String> send(HttpRequest request, String path) {
        try { return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
        catch (IOException e) { throw new IllegalStateException("GATEWAY_CALL_FAILED:" + path); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("GATEWAY_CALL_FAILED:" + path); }
    }
}
