package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MockAuthorizationContractTest {
    private final Instant now = Instant.parse("2026-11-06T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final DelegatedCall call = new DelegatedCall("actor", "d".repeat(22));
    private final MockDelegationRegistry registry = new MockDelegationRegistry(clock);
    private final MockScenarioStore scenarios = new MockScenarioStore();
    private final MockCallCounter counter = new MockCallCounter();
    private final MockTcSourceAuthorizationAdapter adapter = new MockTcSourceAuthorizationAdapter(registry, scenarios, clock, counter);
    @BeforeEach void register() { registry.register(call.sub(), call.dlg()); }
    private tools.jackson.databind.node.ObjectNode valid() {
        return (tools.jackson.databind.node.ObjectNode) SchemaContracts.parse("""
            {"allowed":["EQP_READ"],"denied":["AI_LOG_READ"],"pdg":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","evaluatedAt":"2026-11-06T00:00:00Z"}
            """);
    }
    @Test void typedSerializationAndProjectionPreserveDigestAndExcludeDeniedPermissions() {
        var response = new MockTcSourceAuthorizationAdapter.AuthorizationResponse(Set.of("EQP_READ"), Set.of("AI_LOG_READ"), "b".repeat(64), now);
        var outcome = adapter.project(SchemaContracts.JSON.writeValueAsString(response), Set.of("EQP_READ", "AI_LOG_READ"));
        assertThat(outcome).isEqualTo(new Allowed(Set.of("EQP_READ"), "b".repeat(64)));
    }
    @ParameterizedTest @ValueSource(strings={"allowed", "denied", "pdg", "evaluatedAt"})
    void eachMissingAndWrongTypeFieldIsUnavailable(String field) {
        var missing = valid(); missing.remove(field);
        assertThat(adapter.project(missing.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
        var wrong = valid(); wrong.put(field, 1);
        assertThat(adapter.project(wrong.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
        var nulled = valid(); nulled.putNull(field);
        assertThat(adapter.project(nulled.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
    }
    @ParameterizedTest @ValueSource(strings={"ABC", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaag"})
    void invalidDigestIsUnavailable(String pdg) {
        var node = valid(); node.put("pdg", pdg);
        assertThat(adapter.project(node.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
    }
    @Test void invalidInstantPermissionDuplicateAndContradictionFailClosed() {
        for (String date : List.of("not-an-instant", "2026-02-30T00:00:00Z", "2026-11-06T00:00:00")) {
            var node = valid(); node.put("evaluatedAt", date);
            assertThat(adapter.project(node.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
        }
        for (String field : List.of("allowed", "denied")) {
            var outside = valid(); outside.putArray(field).add("UNREQUESTED");
            assertThat(adapter.project(outside.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
            var invalid = valid(); invalid.putArray(field).add("bad");
            assertThat(adapter.project(invalid.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
            var duplicate = valid(); duplicate.putArray(field).add("EQP_READ").add("EQP_READ");
            assertThat(adapter.project(duplicate.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Unavailable.class);
        }
        var contradictory = valid(); contradictory.putArray("denied").add("EQP_READ");
        assertThat(adapter.project(contradictory.toString(), Set.of("EQP_READ"))).isInstanceOf(Unavailable.class);
    }
    @Test void evaluatedAtHasNoAddedAgeOrClockSkewPolicy() {
        var node = valid(); node.put("evaluatedAt", "2000-01-01T00:00:00.123456789Z");
        assertThat(adapter.project(node.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Allowed.class);
        node.put("evaluatedAt", "2100-01-01T00:00:00Z");
        assertThat(adapter.project(node.toString(), Set.of("EQP_READ", "AI_LOG_READ"))).isInstanceOf(Allowed.class);
    }
    @Test void validEmptyGateRequiresBothWireArraysEmpty() {
        assertThat(adapter.authorize(call, Set.of())).isInstanceOfSatisfying(Allowed.class, a -> assertThat(a.allowed()).isEmpty());
        assertThat(adapter.project(valid().toString(), Set.of())).isInstanceOf(Unavailable.class);
        var node = valid(); node.putArray("allowed"); node.putArray("denied");
        assertThat(adapter.project(node.toString(), Set.of())).isEqualTo(new Allowed(Set.of(), "a".repeat(64)));
    }
    @Test void unknownWrongActorAndRevokedDelegationStayInvalid() {
        assertThat(adapter.authorize(new DelegatedCall("other", call.dlg()), Set.of())).isInstanceOf(DelegationInvalid.class);
        assertThat(adapter.authorize(new DelegatedCall(call.sub(), "x".repeat(22)), Set.of())).isInstanceOf(DelegationInvalid.class);
        registry.revoke(MockDelegationRegistry.hash(call.dlg()));
        assertThat(adapter.authorize(call, Set.of())).isInstanceOf(DelegationInvalid.class);
    }
    @Test void emptyAndSourceRequestsHaveSameFailureClassification() {
        for (Set<String> requested : List.of(Set.<String>of(), Set.of("EQP_READ"))) {
            for (String id : List.of("authz-down", "catalog-down")) {
                scenarios.select(id); assertThat(adapter.authorize(call, requested)).isInstanceOf(Unavailable.class);
            }
            scenarios.select("logout"); assertThat(adapter.authorize(call, requested)).isInstanceOf(DelegationInvalid.class);
            scenarios.select("default"); registry.setAvailable(false);
            assertThat(adapter.authorize(call, requested)).isInstanceOf(Unavailable.class); registry.setAvailable(true);
        }
    }
    @Test void permissionRevocationChangesCurrentDigestAndAllowedSetWithoutCache() {
        Allowed before = (Allowed) adapter.authorize(call, Set.of("AI_EVENT_READ"));
        scenarios.select("permission-revoked"); Allowed after = (Allowed) adapter.authorize(call, Set.of("AI_EVENT_READ"));
        assertThat(before.allowed()).containsExactly("AI_EVENT_READ"); assertThat(after.allowed()).isEmpty(); assertThat(after.pdg()).isNotEqualTo(before.pdg());
    }
    @Test void exactlyExpiredDelegationFailsBothEmptyAndSourceRequests() {
        class MutableClock extends Clock {
            Instant value = now;
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return value; }
        }
        var controlled = new MutableClock();
        var store = new MockDelegationRegistry(controlled); store.register(call.sub(), call.dlg());
        var api = new MockTcSourceAuthorizationAdapter(store, scenarios, controlled, counter);
        controlled.value = now.plusSeconds(299);
        assertThat(api.authorize(call, Set.of())).isInstanceOf(Allowed.class);
        controlled.value = now.plusSeconds(300);
        assertThat(api.authorize(call, Set.of())).isInstanceOf(DelegationInvalid.class);
        assertThat(api.authorize(call, Set.of("EQP_READ"))).isInstanceOf(DelegationInvalid.class);
    }
}
