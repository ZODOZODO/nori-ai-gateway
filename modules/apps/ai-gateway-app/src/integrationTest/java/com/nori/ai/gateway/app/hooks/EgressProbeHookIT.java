package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.app.job.P09QueueSupport;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

class EgressProbeHookIT extends P09QueueSupport {
    private ServerSocket listening;
    private int closedPort;

    @Override protected Map<String,Object> extraApplicationProperties() {
        var properties = new HashMap<>(super.extraApplicationProperties());
        try {
            listening = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            try (var probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { closedPort = probe.getLocalPort(); }
            Files.writeString(securityDirectory.resolve("egress-targets.json"), json.writeValueAsString(List.of(
                Map.of("id", "allowed-local", "host", "127.0.0.1", "port", listening.getLocalPort(), "expect", "ALLOWED"),
                Map.of("id", "blocked-local", "host", "127.0.0.1", "port", closedPort, "expect", "BLOCKED"))));
        } catch (Exception failure) { throw new IllegalStateException("egress 대상 준비 실패"); }
        properties.put("ai.gateway.test-hooks.egress-targets-file", securityDirectory.resolve("egress-targets.json").toString());
        return properties;
    }
    @AfterEach void closeListener() throws Exception { if (listening != null) listening.close(); }

    @Test void probesOnlyListedTargetsBehindTheHookTokenAndReturnsNoAddress() throws Exception {
        var allowed = hook("/__test/egress-probe", "POST", "{\"targetId\":\"allowed-local\"}", hookToken);
        assertThat(allowed.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(allowed.body());
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("targetId", "connected", "errorClass");
        assertThat(body.get("targetId").asString()).isEqualTo("allowed-local");
        assertThat(body.get("connected").asBoolean()).isTrue();
        assertThat(body.get("errorClass").isNull()).isTrue();

        var blocked = json.readTree(hook("/__test/egress-probe", "POST", "{\"targetId\":\"blocked-local\"}", hookToken).body());
        assertThat(blocked.get("connected").asBoolean()).isFalse();
        assertThat(blocked.get("errorClass").asString()).isEqualTo("REFUSED");

        // 본문의 호스트·포트는 무시하고 목록의 고정 대상만 연결한다.
        var injected = hook("/__test/egress-probe", "POST", "{\"targetId\":\"blocked-local\",\"host\":\"127.0.0.1\",\"port\":" + listening.getLocalPort() + "}", hookToken);
        assertThat(injected.statusCode()).isEqualTo(200);
        assertThat(json.readTree(injected.body()).get("connected").asBoolean()).isFalse();
        assertThat(injected.body()).doesNotContain("127.0.0.1", String.valueOf(closedPort), String.valueOf(listening.getLocalPort()));

        assertThat(hook("/__test/egress-probe", "POST", "{\"targetId\":\"unknown\"}", hookToken).statusCode()).isEqualTo(404);
        assertThat(hook("/__test/egress-probe", "POST", "{\"host\":\"127.0.0.1\",\"port\":" + listening.getLocalPort() + "}", hookToken).statusCode()).isEqualTo(404);
        assertThat(hook("/__test/egress-probe", "POST", "{\"targetId\":\"allowed-local\"}", null).statusCode()).isEqualTo(404);
        assertThat(hook("/__test/egress-probe", "POST", "{\"targetId\":\"allowed-local\"}", "wrong").statusCode()).isEqualTo(404);
    }
}
