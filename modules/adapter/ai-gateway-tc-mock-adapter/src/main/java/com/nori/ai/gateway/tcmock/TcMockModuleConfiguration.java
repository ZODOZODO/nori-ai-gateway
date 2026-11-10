package com.nori.ai.gateway.tcmock;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import java.time.Clock;
import java.nio.file.Path;
import com.nori.ai.gateway.core.registry.ManifestLoader;

@Configuration(proxyBeanMethods = false)
public class TcMockModuleConfiguration {
    @Bean MockDelegationRegistry mockDelegationRegistry(Clock clock, Environment environment) {
        String directory = environment.getProperty("ai.gateway.tcmock.state-dir");
        return new MockDelegationRegistry(clock, directory == null ? null : Path.of(directory));
    }
    @Bean MockScenarioStore mockScenarioStore(Environment environment) {
        String directory = environment.getProperty("ai.gateway.tcmock.state-dir");
        return new MockScenarioStore(directory == null ? null : Path.of(directory));
    }
    @Bean MockCallCounter mockCallCounter() { return new MockCallCounter(); }
    @Bean MockTcSourceAuthorizationAdapter mockTcSourceAuthorizationAdapter(MockDelegationRegistry delegations, MockScenarioStore scenarios, Clock clock, MockCallCounter calls) {
        return new MockTcSourceAuthorizationAdapter(delegations, scenarios, clock, calls);
    }
    @Bean MockTcBusinessReadAdapter mockTcBusinessReadAdapter(MockScenarioStore scenarios, MockTcSourceAuthorizationAdapter authorization, Clock clock, MockCallCounter calls) {
        return new MockTcBusinessReadAdapter(scenarios, authorization, clock, calls, new ManifestLoader().loadClasspath("manifests/tc/index.json"));
    }
}
