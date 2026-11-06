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
    private final SourcePermissionMap permissions = new SourcePermissionMap(Map.of("sources://TC/test", new SourcePermissionMap.Entry(Set.of("A", "B"), true)));
    @Test void anyRequiredPermissionSufficesAndDoesNotUseAdmissionPermissions() {
        var count = new AtomicInteger();
        var step = new SourceAuthorizationStep(permissions, (c, required) -> {
            assertThat(c).isEqualTo(call); assertThat(required).containsExactlyInAnyOrder("A", "B"); count.incrementAndGet();
            return new Allowed(Set.of("B"), "b".repeat(64));
        });
        assertThat(step.authorizeBefore("sources://TC/test", scope, call)).isEqualTo(new SourceAuthorizationStep.Granted(Set.of("B"), "b".repeat(64)));
        step.authorizeBefore("sources://TC/test", scope, call);
        assertThat(count).hasValue(2);
    }
    @Test void missingMappingAndForeignActorNeverCallAuthorization() {
        var step = new SourceAuthorizationStep(permissions, (c, required) -> { throw new AssertionError("인가 호출 금지"); });
        assertThat(step.authorizeBefore("absent", scope, call)).isInstanceOf(SourceAuthorizationStep.Denied.class);
        assertThat(step.authorizeBefore("sources://TC/test", scope, new DelegatedCall("other", call.dlg()))).isInstanceOf(SourceAuthorizationStep.Denied.class);
        for (OwnerSystem owner : List.of(OwnerSystem.WM, OwnerSystem.HRM)) {
            var foreign = new VerifiedSystemScope(owner, scope.authenticatedServiceRef(), scope.actorRef(), scope.sub(), scope.perm(), scope.pdg(), scope.issuedAt(), scope.expiresAt(), scope.requestId());
            assertThat(step.authorizeBefore("sources://TC/test", foreign, call)).isInstanceOf(SourceAuthorizationStep.Denied.class);
        }
        for (String invalid : List.of("source", "sources://TC/../test", "sources://TC/test?other=true", "sources://TC/test#other"))
            assertThat(step.authorizeBefore(invalid, scope, call)).isInstanceOf(SourceAuthorizationStep.Denied.class);
    }
    @Test void deniedEmptyGateAndInvalidDelegationNeverGrantSource() {
        for (Outcome outcome : List.of(new Allowed(Set.of(), "a".repeat(64)), new DelegationInvalid())) {
            assertThat(new SourceAuthorizationStep(permissions, (c, r) -> outcome).authorizeBefore("sources://TC/test", scope, call))
                .isInstanceOf(SourceAuthorizationStep.Denied.class);
        }
    }
    @Test void unavailableMalformedPdgAndThrownTransportFailureAreUnavailable() {
        for (Outcome outcome : Arrays.asList(null, new Unavailable(), new Allowed(Set.of(), "bad"), new Allowed(Set.of("A"), "A".repeat(64)), new Allowed(Set.of("UNREQUESTED"), "a".repeat(64)))) {
            assertThat(new SourceAuthorizationStep(permissions, (c, r) -> outcome).authorizeBefore("sources://TC/test", scope, call))
                .isInstanceOf(SourceAuthorizationStep.SourceUnavailable.class);
        }
        assertThat(new SourceAuthorizationStep(permissions, (c, r) -> { throw new IllegalStateException("통신 실패"); })
            .authorizeBefore("sources://TC/test", scope, call)).isInstanceOf(SourceAuthorizationStep.SourceUnavailable.class);
    }
    @Test void mappingParserValidatesExactFieldsAndPermissionFormat() {
        assertThat(SourcePermissionMap.fromJson("{\"source\":{\"requiredPermCodes\":[\"A\"],\"mockOnly\":true}}").find("source")).isPresent();
        for (String json : List.of("[]", "{\"s\":{\"requiredPermCodes\":[],\"mockOnly\":true}}", "{\"s\":{\"requiredPermCodes\":[\"a\"],\"mockOnly\":true}}",
                "{\"s\":{\"requiredPermCodes\":[\"A\",\"A\"],\"mockOnly\":true}}", "{\"s\":{\"requiredPermCodes\":[\"A\"],\"mockOnly\":\"true\"}}"))
            assertThatThrownBy(() -> SourcePermissionMap.fromJson(json)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void scopedExclusionCanOnlyRemoveTheExistingMappingAndCannotLeakToAnotherJob() {
        var calls=new AtomicInteger();var lookups=new AtomicInteger();
        var step=new SourceAuthorizationStep(permissions,(c,r)->{calls.incrementAndGet();return new Allowed(Set.of("A"),"a".repeat(64));},
            (source,actual)->{lookups.incrementAndGet();return source.equals("sources://TC/test")&&actual.requestId().equals("req");});
        assertThat(step.authorizeBefore("sources://TC/test",scope,call)).isInstanceOf(SourceAuthorizationStep.Denied.class);
        assertThat(calls).hasValue(0);
        var next=new VerifiedSystemScope(scope.boundSystem(),scope.authenticatedServiceRef(),scope.actorRef(),scope.sub(),scope.perm(),scope.pdg(),scope.issuedAt(),scope.expiresAt(),"next");
        assertThat(step.authorizeBefore("sources://TC/test",next,call)).isInstanceOf(SourceAuthorizationStep.Granted.class);
        assertThat(step.authorizeBefore("missing",next,call)).isInstanceOf(SourceAuthorizationStep.Denied.class);
        assertThat(calls).hasValue(1);assertThat(lookups).hasValue(2);assertThat(permissions.find("sources://TC/test")).isPresent();
        var broken=new SourceAuthorizationStep(permissions,(c,r)->{throw new AssertionError("인가 호출 금지");},(s,c)->{throw new IllegalStateException("주입 결속 오류");});
        assertThatThrownBy(()->broken.authorizeBefore("sources://TC/test",scope,call)).isInstanceOf(IllegalStateException.class);
    }
}
