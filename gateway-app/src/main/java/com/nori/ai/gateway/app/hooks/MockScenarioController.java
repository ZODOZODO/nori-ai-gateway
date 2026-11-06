package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.tcmock.*;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/__test/tcmock")
public final class MockScenarioController {
    private final MockScenarioStore scenarios;
    private final MockCallCounter calls;
    public MockScenarioController(MockScenarioStore scenarios, MockCallCounter calls) { this.scenarios = scenarios; this.calls = calls; }
    @PostMapping("/scenario") public ResponseEntity<Void> select(@RequestBody JsonNode body) {
        if (!body.isObject() || body.size() != 1 || !body.has("id") || !body.get("id").isString()) throw new IllegalArgumentException();
        scenarios.select(body.get("id").asString()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/calls") public Map<String, Long> calls() { return calls.snapshot(); }
    @GetMapping("/scenario") public Map<String, String> selected() { return Map.of("id", scenarios.current().id()); }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Void> invalid() { return ResponseEntity.badRequest().build(); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<Void> unavailable() { return ResponseEntity.status(503).build(); }
}
