package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.tcmock.MockDelegationRegistry;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/__test/tcmock/delegations")
public final class MockDelegationController {
    private final MockDelegationRegistry delegations;
    public MockDelegationController(MockDelegationRegistry delegations) { this.delegations = delegations; }
    @PostMapping public ResponseEntity<Void> register(@RequestBody JsonNode body) {
        if (!body.isObject() || body.size() != 2 || !body.has("sub") || !body.get("sub").isString() || !body.has("dlg") || !body.get("dlg").isString())
            throw new IllegalArgumentException();
        var registered = delegations.register(body.get("sub").asString(), body.get("dlg").asString());
        return ResponseEntity.status(registered == MockDelegationRegistry.Registration.REGISTERED ? 204 : 409).build();
    }
    @DeleteMapping("/{dlgSha256}") public ResponseEntity<Void> revoke(@PathVariable String dlgSha256) {
        delegations.revoke(dlgSha256); return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Void> invalid() { return ResponseEntity.badRequest().build(); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<Void> unavailable() { return ResponseEntity.status(503).build(); }
}
