package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.authz.SourcePermissionMap;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import java.time.*;
import java.util.*;
import tools.jackson.databind.JsonNode;

public final class MockTcSourceAuthorizationAdapter implements TcSourceAuthorizationPort {
    public record AuthorizationResponse(Set<String> allowed, Set<String> denied, String pdg, Instant evaluatedAt) {
        public AuthorizationResponse {
            allowed = Set.copyOf(allowed); denied = Set.copyOf(denied); Objects.requireNonNull(evaluatedAt);
            if (pdg == null || !pdg.matches("[0-9a-f]{64}") || allowed.stream().anyMatch(p -> !SourcePermissionMap.validPermission(p))
                    || denied.stream().anyMatch(p -> !SourcePermissionMap.validPermission(p)))
                throw new IllegalArgumentException("인가 응답 계약이 잘못되었습니다");
        }
    }
    private static final JsonNode RESPONSE_SCHEMA = SchemaContracts.parse("""
        {"type":"object","additionalProperties":false,"required":["allowed","denied","pdg","evaluatedAt"],"properties":{
        "allowed":{"type":"array","uniqueItems":true,"items":{"type":"string","pattern":"^[A-Z0-9_]{1,64}$"}},
        "denied":{"type":"array","uniqueItems":true,"items":{"type":"string","pattern":"^[A-Z0-9_]{1,64}$"}},
        "pdg":{"type":"string","pattern":"^[0-9a-f]{64}$"},"evaluatedAt":{"type":"string","format":"date-time"}}}
        """);
    private final MockDelegationRegistry delegations;
    private final MockScenarioStore scenarios;
    private final Clock clock;
    private final MockCallCounter calls;
    public MockTcSourceAuthorizationAdapter(MockDelegationRegistry delegations, MockScenarioStore scenarios, Clock clock, MockCallCounter calls) {
        this.delegations = Objects.requireNonNull(delegations); this.scenarios = Objects.requireNonNull(scenarios);
        this.clock = Objects.requireNonNull(clock); this.calls = Objects.requireNonNull(calls);
    }
    @Override public Outcome authorize(DelegatedCall call, Set<String> requiredPermCodes) {
        Set<String> requested = Set.copyOf(requiredPermCodes);
        calls.recordAuthorization(requested);
        if (requested.stream().anyMatch(p -> !SourcePermissionMap.validPermission(p))) return new Unavailable();
        var scenario = scenarios.current();
        if (scenario.authorizationUnavailable() || delegations.validate(call) == MockDelegationRegistry.Validity.UNAVAILABLE) return new Unavailable();
        if (scenario.delegationInvalid() || delegations.validate(call) != MockDelegationRegistry.Validity.VALID) return new DelegationInvalid();
        if (!scenario.catalogAvailable()) return new Unavailable();
        Set<String> allowed = new HashSet<>(requested); allowed.retainAll(scenario.grantedPermissions());
        Set<String> denied = new HashSet<>(requested); denied.removeAll(allowed);
        var response = new AuthorizationResponse(allowed, denied,
            PdgCanonical.compute(scenario.catalogRevision(), List.copyOf(allowed)), clock.instant());
        try { return project(SchemaContracts.JSON.writeValueAsString(response), requested); }
        catch (RuntimeException failure) { return new Unavailable(); }
    }
    public Outcome project(String json, Set<String> requested) {
        try {
            JsonNode node = SchemaContracts.parse(json);
            if (!SchemaContracts.matches(RESPONSE_SCHEMA, node)) return new Unavailable();
            var response = new AuthorizationResponse(permissions(node.get("allowed")), permissions(node.get("denied")),
                node.get("pdg").asString(), Instant.parse(node.get("evaluatedAt").asString()));
            if (!requested.containsAll(response.allowed()) || !requested.containsAll(response.denied())
                    || !Collections.disjoint(response.allowed(), response.denied())) return new Unavailable();
            return new Allowed(response.allowed(), response.pdg());
        } catch (RuntimeException failure) { return new Unavailable(); }
    }
    private Set<String> permissions(JsonNode array) {
        Set<String> result = new HashSet<>(); array.forEach(value -> result.add(value.asString())); return Set.copyOf(result);
    }
}
