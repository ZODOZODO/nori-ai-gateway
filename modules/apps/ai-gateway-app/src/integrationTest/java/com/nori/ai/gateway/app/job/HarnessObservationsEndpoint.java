package com.nori.ai.gateway.app.job;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;

@TestConfiguration(proxyBeanMethods=false)
@Import(HarnessObservationsEndpoint.Endpoint.class)
public class HarnessObservationsEndpoint {
    @RestController
    public static class Endpoint {
        private final P09TestPorts.Observations observations;private final com.nori.ai.gateway.tcmock.MockCallCounter calls;
        public Endpoint(P09TestPorts.Observations observations,com.nori.ai.gateway.tcmock.MockCallCounter calls) {this.observations=observations;this.calls=calls;}
        @GetMapping("/__test/harness-observations") public Map<String,Long> observations(){return Map.of("authorization",calls.count("authorize"),"workflows",(long)observations.workflows.size());}
    }
}
