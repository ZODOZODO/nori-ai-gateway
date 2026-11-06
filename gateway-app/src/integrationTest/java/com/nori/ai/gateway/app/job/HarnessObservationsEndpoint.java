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
        private final P09TestPorts.Observations observations;
        public Endpoint(P09TestPorts.Observations observations) {this.observations=observations;}
        @GetMapping("/__test/harness-observations") public Map<String,Integer> observations(){return Map.of("authorization",observations.authorization.get(),"workflows",observations.workflows.size());}
    }
}
