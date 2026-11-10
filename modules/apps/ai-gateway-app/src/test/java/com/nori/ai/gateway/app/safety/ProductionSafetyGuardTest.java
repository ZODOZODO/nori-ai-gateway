package com.nori.ai.gateway.app.safety;

import com.nori.ai.gateway.tcmock.MockTcBusinessReadAdapter;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionSafetyGuardTest {
    @TempDir Path directory;

    private MockEnvironment prod() throws Exception {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        Path jwks = directory.resolve("jwks.json");
        Files.writeString(jwks, "{\"keys\":[{\"kty\":\"EC\",\"kid\":\"prod-2026\",\"crv\":\"P-256\",\"x\":\"a\",\"y\":\"b\"}]}");
        environment.setProperty("ai.gateway.security.actor-jwks-file", jwks.toString());
        environment.setProperty("ai.gateway.inference.mode", "ENABLED");
        return environment;
    }
    private static List<String> violations(MockEnvironment environment) { return ProductionSafetyGuard.violations(environment, new DefaultListableBeanFactory()); }

    @Test void cleanProductionInputsHaveNoViolationAndVerifyProfileIsNotJudged() throws Exception {
        assertThat(violations(prod())).isEmpty();
        var verify = prod(); verify.setActiveProfiles("verify");
        verify.setProperty("ai.gateway.test-hooks.enabled", "true"); verify.setProperty("ai.gateway.recovery.restore-mode", "true");
        assertThat(violations(verify)).isEmpty();
    }

    @Test void eachVerifyOnlyInputIsRejectedSeparatelyInProduction() throws Exception {
        Path hookFile = Files.writeString(directory.resolve("hook-token-sha256"), "0".repeat(64));
        Map<String,Map.Entry<String,String>> cases = new LinkedHashMap<>();
        cases.put("TEST_HOOKS_ENABLED", Map.entry("ai.gateway.test-hooks.enabled", "true"));
        cases.put("FIXTURE_CATALOG_ENABLED", Map.entry("ai.gateway.ingest.fixture-catalog.enabled", "true"));
        cases.put("RESTORE_MODE", Map.entry("ai.gateway.recovery.restore-mode", "true"));
        cases.put("HOOK_TOKEN_FILE_PRESENT", Map.entry("ai.gateway.test-hooks.token-sha256-file", hookFile.toString()));
        cases.put("INFERENCE_DISABLED", Map.entry("ai.gateway.inference.mode", "DISABLED"));
        cases.put("TEST_CLOCK_CONFIGURED", Map.entry("ai.gateway.test-hooks.clock.initial", "2026-11-06T00:00:00Z"));
        cases.put("MOCK_STATE_DIR_CONFIGURED", Map.entry("ai.gateway.tcmock.state-dir", directory.toString()));
        cases.put("VERIFY_SANITIZE_RULE_SET", Map.entry("ai.gateway.sanitize.verify-rule-set", "mask-v1r"));
        for (var item : cases.entrySet()) {
            var environment = prod();
            environment.setProperty(item.getValue().getKey(), item.getValue().getValue());
            assertThat(violations(environment)).as(item.getKey()).containsExactly(item.getKey());
        }
        var configuredOnly = prod(); configuredOnly.setProperty("ai.gateway.test-hooks.token-sha256-file", directory.resolve("absent").toString());
        assertThat(violations(configuredOnly)).containsExactly("HOOK_TOKEN_FILE_CONFIGURED");
        var defaultRules = prod(); defaultRules.setProperty("ai.gateway.sanitize.verify-rule-set", "mask-v1");
        assertThat(violations(defaultRules)).isEmpty();
    }

    @Test void testSignerKeyAndUnreadableJwksAreRejected() throws Exception {
        var testKey = prod();
        Files.writeString(directory.resolve("jwks.json"), "{\"keys\":[{\"kid\":\"prod-1\"},{\"kid\":\"test-20261107\"}]}");
        assertThat(violations(testKey)).containsExactly("TEST_SIGNER_KEY_TRUSTED");
        var unreadable = prod(); unreadable.setProperty("ai.gateway.security.actor-jwks-file", directory.resolve("missing.json").toString());
        assertThat(violations(unreadable)).containsExactly("ACTOR_JWKS_UNREADABLE");
    }

    @Test void mockTcAdapterDefinitionIsRejected() throws Exception {
        var beans = new DefaultListableBeanFactory();
        beans.registerBeanDefinition("mockTcBusinessReadAdapter", new RootBeanDefinition(MockTcBusinessReadAdapter.class));
        assertThat(ProductionSafetyGuard.violations(prod(), beans)).containsExactly("TC_ADAPTER_MOCK");
    }

    // 차트는 설정을 환경 변수로 전달하므로 속성 이름 그대로가 아닌 relaxed 이름도 거부해야 한다.
    @Test void environmentVariableFormIsJudgedLikePropertyForm() throws Exception {
        var environment = prod();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("systemEnvironment",
            Map.of("AI_GATEWAY_TEST_HOOKS_CLOCK_MODE", "FROZEN", "AI_GATEWAY_TCMOCK_STATE_DIR", "/work/mock", "AI_GATEWAY_RECOVERY_RESTORE_MODE", "true")));
        assertThat(violations(environment)).containsExactlyInAnyOrder("TEST_CLOCK_CONFIGURED", "MOCK_STATE_DIR_CONFIGURED", "RESTORE_MODE");
    }

    @Test void refusalCarriesCodesOnlyAndRunsBeforeBeans() throws Exception {
        var environment = prod();
        environment.setProperty("ai.gateway.tcmock.state-dir", directory.resolve("secret-path").toString());
        var policy = ProductionSafetyGuard.productionSafetyPolicy(environment);
        assertThatThrownBy(() -> policy.postProcessBeanFactory(new DefaultListableBeanFactory()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("MOCK_STATE_DIR_CONFIGURED").hasMessageNotContaining("secret-path");
    }
}
