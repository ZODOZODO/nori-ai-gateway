package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.authz.SourcePermissionMap;
import com.nori.ai.gateway.core.registry.ManifestLoader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@Profile("verify")
@ConditionalOnProperty(name = "ai.gateway.test-hooks.enabled", havingValue = "true")
@Import({MockDelegationController.class, MockScenarioController.class})
public class MockHooksConfiguration {
    @Bean SeamRegistry seamRegistry(TestHookRegistry hooks, ManifestLoader loader, SourcePermissionMap permissions) {
        return new SeamRegistry(hooks, loader, permissions);
    }
}
