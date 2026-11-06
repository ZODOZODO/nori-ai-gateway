package com.nori.ai.gateway.core.authz;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.scope.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class SourceAuthorizationStepTest {
    private final DelegatedCall call = new DelegatedCall("actor", "a".repeat(22));
    private final VerifiedSystemScope scope = new VerifiedSystemScope(OwnerSystem.TC, "service", new ActorRef("actor"),
        "actor", Set.of(), "a".repeat(64), Instant.EPOCH, Instant.parse("2100-01-01T00:00:00Z"), "req");
    private final SourcePermissionMap permissions = new SourcePermissionMap(Map.of("source", new SourcePermissionMap.Entry(Set.of("A", "B"), true)));
    @Test void anyRequiredPermissionSufficesAndDoesNotUseAdmissionPermissions() {
        var count = new AtomicInteger();
        var step = new SourceAuthorizationStep(permissions, (c, required) -> {
            assertThat(c).isEqualTo(call); assertThat(required).containsExactlyInAnyOrder("A", "B"); count.incrementAndGet();
            return new Allowed(Set.of("B"), "b".repeat(64));
        });
        assertThat(step.authorizeBefore("source", scope, call)).isEqualTo(new SourceAuthorizationStep.Granted(Set.of("B"), "b".repeat(64)));
        step.authorizeBefore("source", scope, call);
        assertThat(count).hasValue(2);
    }
    @Test void missingMappingAndForeignActorNeverCallAuthorization() {
        var step = new SourceAuthorizationStep(permissions, (c, required) -> { throw new AssertionError("인가 호출 금지"); });
        assertThat(step.authorizeBefore("absent", scope, call)).isInstanceOf(SourceAuthorizationStep.Denied.class);
        assertThat(step.authorizeBefore("source", scope, new DelegatedCall("other", call.dlg()))).isInstanceOf(SourceAuthorizationStep.Denied.class);
    }
    @Test void deniedEmptyGateAndInvalidDelegationNeverGrantSource() {
        for (Outcome outcome : List.of(new Allowed(Set.of(), "a".repeat(64)), new DelegationInvalid())) {
            assertThat(new SourceAuthorizationStep(permissions, (c, r) -> outcome).authorizeBefore("source", scope, call))
                .isInstanceOf(SourceAuthorizationStep.Denied.class);
        }
    }
    @Test void unavailableMalformedPdgAndThrownTransportFailureAreUnavailable() {
        for (Outcome outcome : Arrays.asList(null, new Unavailable(), new Allowed(Set.of(), "bad"), new Allowed(Set.of("A"), "A".repeat(64)), new Allowed(Set.of("UNREQUESTED"), "a".repeat(64)))) {
            assertThat(new SourceAuthorizationStep(permissions, (c, r) -> outcome).authorizeBefore("source", scope, call))
                .isInstanceOf(SourceAuthorizationStep.SourceUnavailable.class);
        }
        assertThat(new SourceAuthorizationStep(permissions, (c, r) -> { throw new IllegalStateException("통신 실패"); })
            .authorizeBefore("source", scope, call)).isInstanceOf(SourceAuthorizationStep.SourceUnavailable.class);
    }
    @Test void mappingParserValidatesExactFieldsAndPermissionFormat() {
        assertThat(SourcePermissionMap.fromJson("{\"source\":{\"requiredPermCodes\":[\"A\"],\"mockOnly\":true}}").find("source")).isPresent();
        for (String json : List.of("[]", "{\"s\":{\"requiredPermCodes\":[],\"mockOnly\":true}}", "{\"s\":{\"requiredPermCodes\":[\"a\"],\"mockOnly\":true}}",
                "{\"s\":{\"requiredPermCodes\":[\"A\",\"A\"],\"mockOnly\":true}}", "{\"s\":{\"requiredPermCodes\":[\"A\"],\"mockOnly\":\"true\"}}"))
            assertThatThrownBy(() -> SourcePermissionMap.fromJson(json)).isInstanceOf(IllegalArgumentException.class);
    }
}
