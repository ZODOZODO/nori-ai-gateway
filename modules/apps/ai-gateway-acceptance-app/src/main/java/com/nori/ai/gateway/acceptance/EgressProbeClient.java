package com.nori.ai.gateway.acceptance;

import java.util.Map;

/** Gateway Pod 안 egress probe hook({@code POST /__test/egress-probe {targetId}}) 호출. 주소는 보내지 않는다. */
public final class EgressProbeClient {
    public record Outcome(String targetId, boolean connected, String errorClass) {}
    private final HookClient hooks;
    public EgressProbeClient(HookClient hooks) { this.hooks = hooks; }

    public Outcome probe(String targetId) {
        if (targetId == null || !targetId.matches("[A-Za-z0-9_.-]{1,64}")) throw new IllegalArgumentException("TARGET_ID_INVALID");
        var body = hooks.post("/__test/egress-probe", Map.of("targetId", targetId), 200);
        return new Outcome(body.path("targetId").asText(), body.path("connected").asBoolean(), body.path("errorClass").isNull() ? null : body.path("errorClass").asText());
    }
}
