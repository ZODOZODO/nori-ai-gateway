package com.nori.ai.gateway.app.hooks;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pod 안 egress 확인용 hook(Plan §4.5 H-2·M-5). 대상은 차트가 렌더한 고정 목록 파일의 id 로만 고르며,
 * 요청에서 호스트·포트·URL 을 받지 않고 TCP connect 1회만 한다(본문 송신·HTTP 요청 0).
 */
@RestController
@RequestMapping("/__test")
@Profile("verify")
@ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "true")
// 차트는 verify 렌더마다 목록 파일을 마운트한다. 목록이 없는 실행에는 hook 자체가 없다(404).
@ConditionalOnProperty(name = "ai.gateway.test-hooks.egress-targets-file")
public final class EgressProbeHook {
    static final int CONNECT_TIMEOUT_MILLIS = 3000;
    private static final Set<String> TARGET_FIELDS = Set.of("id", "host", "port", "expect");
    record Target(String host, int port) {}
    private final Map<String,Target> targets;

    public EgressProbeHook(Environment environment) {
        this.targets = load(environment.getRequiredProperty("ai.gateway.test-hooks.egress-targets-file"));
    }

    @PostMapping("/egress-probe")
    public ResponseEntity<Map<String,Object>> probe(@RequestBody JsonNode body) {
        // 본문의 다른 필드(호스트 등)는 대상 선택에 쓰지 않는다.
        JsonNode id = body.path("targetId");
        Target target = id.isString() ? targets.get(id.asString()) : null;
        if (target == null) return ResponseEntity.notFound().build();
        String errorClass = null;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host(), target.port()), CONNECT_TIMEOUT_MILLIS);
        } catch (IOException failure) { errorClass = classify(failure); }
        var response = new LinkedHashMap<String,Object>();
        response.put("targetId", id.asString()); response.put("connected", errorClass == null); response.put("errorClass", errorClass);
        return ResponseEntity.ok(response);
    }

    static String classify(IOException failure) {
        if (failure instanceof SocketTimeoutException) return "TIMEOUT";
        if (failure instanceof UnknownHostException) return "UNRESOLVED";
        if (failure instanceof NoRouteToHostException) return "NO_ROUTE";
        if (failure instanceof ConnectException) return "REFUSED";
        return "IO_ERROR";
    }

    /** 목록 파일 = runconfig {@code egressProbe.targets} 의 JSON 배열 {@code [{id, host, port, expect}]}. 형식 위반이면 기동을 거부한다. */
    static Map<String,Target> load(String file) {
        try {
            JsonNode root = JsonMapper.builder().build().readTree(Files.readString(Path.of(file)));
            if (!root.isArray() || root.isEmpty()) throw new IllegalArgumentException();
            var loaded = new LinkedHashMap<String,Target>();
            for (JsonNode node : root) {
                if (!node.isObject() || !TARGET_FIELDS.equals(new HashSet<>(node.propertyNames()))) throw new IllegalArgumentException();
                String id = node.get("id").asString(), host = node.get("host").asString(), expect = node.get("expect").asString();
                JsonNode port = node.get("port");
                if (!id.matches("[A-Za-z0-9_.-]{1,64}") || host.isBlank() || !port.isIntegralNumber() || port.asInt() < 1 || port.asInt() > 65535
                    || !Set.of("ALLOWED", "BLOCKED").contains(expect) || loaded.putIfAbsent(id, new Target(host, port.asInt())) != null) throw new IllegalArgumentException();
            }
            return Map.copyOf(loaded);
        } catch (RuntimeException | IOException failure) { throw new IllegalStateException("egress probe 대상 목록이 잘못되었습니다"); }
    }
}
