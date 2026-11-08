package com.nori.ai.gateway.app.safety;

import com.nori.ai.gateway.tcmock.MockTcBusinessReadAdapter;
import com.nori.ai.gateway.tcmock.MockTcSourceAuthorizationAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code prod} 프로필에서 검증 전용 구성이 하나라도 보이면 빈 생성 전에 기동을 거부한다(Plan §4.5·P15).
 * 거부 메시지에는 위반 코드만 담고 설정 값·파일 경로는 담지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class ProductionSafetyGuard {
    @Bean static BeanFactoryPostProcessor productionSafetyPolicy(Environment environment) {
        return beans -> {
            List<String> violations = violations(environment, beans);
            if (!violations.isEmpty()) throw new IllegalStateException("운영 프로필 안전 검사 실패: " + String.join(",", violations));
        };
    }

    static List<String> violations(Environment environment, ConfigurableListableBeanFactory beans) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) return List.of();
        Binder binder = Binder.get(environment);
        var found = new ArrayList<String>();
        if (binder.bind("ai.gateway.test-hooks.enabled", Boolean.class).orElse(false)) found.add("TEST_HOOKS_ENABLED");
        // F1 의 TC 어댑터는 Mock 뿐이므로 Mock 빈 정의가 있으면 tcAdapter=mock 이다(HTTP 어댑터는 F5a).
        if (beans.getBeanNamesForType(MockTcBusinessReadAdapter.class, true, false).length > 0
            || beans.getBeanNamesForType(MockTcSourceAuthorizationAdapter.class, true, false).length > 0) found.add("TC_ADAPTER_MOCK");
        testKeyViolation(binder).ifPresent(found::add);
        if ("DISABLED".equalsIgnoreCase(binder.bind("ai.gateway.inference.mode", String.class).orElse(""))) found.add("INFERENCE_DISABLED");
        if (binder.bind("ai.gateway.ingest.fixture-catalog.enabled", Boolean.class).orElse(false)) found.add("FIXTURE_CATALOG_ENABLED");
        if (binder.bind("ai.gateway.recovery.restore-mode", Boolean.class).orElse(false)) found.add("RESTORE_MODE");
        String hookFile = binder.bind("ai.gateway.test-hooks.token-sha256-file", String.class).orElse(null);
        if (hookFile != null) found.add(!hookFile.isBlank() && Files.exists(Path.of(hookFile.strip())) ? "HOOK_TOKEN_FILE_PRESENT" : "HOOK_TOKEN_FILE_CONFIGURED");
        // Map 바인딩은 대시 포함 이름의 환경 변수 형태(AI_GATEWAY_TEST_HOOKS_CLOCK_MODE)를 찾지 못하므로 앱이 읽는 키를 직접 조회한다.
        if (Stream.of("mode", "initial").anyMatch(key -> binder.bind("ai.gateway.test-hooks.clock." + key, String.class).isBound())) found.add("TEST_CLOCK_CONFIGURED");
        if (binder.bind("ai.gateway.tcmock.state-dir", String.class).isBound()) found.add("MOCK_STATE_DIR_CONFIGURED");
        if ("mask-v1r".equals(binder.bind("ai.gateway.sanitize.verify-rule-set", String.class).orElse(""))) found.add("VERIFY_SANITIZE_RULE_SET");
        return List.copyOf(found);
    }

    /** actor JWKS 에 시험 서명 키(kid 접두 {@code test-})가 있으면 위반이다. 판정할 수 없는 파일도 운영에서는 허용하지 않는다. */
    private static Optional<String> testKeyViolation(Binder binder) {
        String file = binder.bind("ai.gateway.security.actor-jwks-file", String.class).orElse(null);
        if (file == null) return Optional.empty();
        try {
            JsonNode keys = JsonMapper.builder().build().readTree(Files.readString(Path.of(file))).path("keys");
            if (!keys.isArray()) return Optional.of("ACTOR_JWKS_UNREADABLE");
            for (JsonNode key : keys) if (key.path("kid").asString("").startsWith("test-")) return Optional.of("TEST_SIGNER_KEY_TRUSTED");
            return Optional.empty();
        } catch (RuntimeException | java.io.IOException failure) { return Optional.of("ACTOR_JWKS_UNREADABLE"); }
    }
}
