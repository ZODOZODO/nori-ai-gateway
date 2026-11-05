package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.ResultStatus;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class VerifiedIdentityAndReadScopeTest {
    @Test void requiresOneIdentityForSignedSubjectAndActorReference() {
        var valid = scope();
        assertThat(valid.actorRef().value()).isEqualTo(valid.sub());
        assertThatThrownBy(() -> new VerifiedSystemScope(valid.boundSystem(), valid.authenticatedServiceRef(),
            new ActorRef("different-actor"), valid.sub(), valid.perm(), valid.pdg(), valid.issuedAt(), valid.expiresAt(), valid.requestId()))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("사용자 참조와 인증 주체가 일치하지 않습니다");
    }

    @Test void approvedReadScopePassesRegistryValidatorAndDispatcherButWrongOrMissingScopeIsDenied() {
        var versions = new ContractVersionPolicy();
        var manifest = manifest();
        assertThat(manifest.requiredActorScope()).isEqualTo("READ");
        var registry = registry(manifest, versions);
        var validator = validator(registry, new PlanInjectionSeam.NoOp());
        var resolver = new Resolver();
        var calls = new AtomicInteger();
        var dispatcher = new ToolDispatcher(registry, validator, new ToolResultContractValidator(versions),
            new DispatchInjectionSeam.NoOp(), List.of(executor(calls)), CLOCK);
        assertThat(registry.view(scope()).find(ID)).contains(manifest);
        assertThat(validator.validate(plan(step()), scope(), resolver).accepted()).isTrue();
        assertThat(dispatcher.dispatch(step(), scope(), new ExecutionContext(resolver, t -> {})).status()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(calls).hasValue(1);
        for (Set<String> scopes : List.of(Set.of("WRITE"), Set.<String>of())) {
            resolver.actorScopes = scopes;
            var denied = validator.validate(plan(step()), scope(), resolver);
            assertThat(denied.rejectReason()).isEqualTo(RejectReason.DENIED);
            assertThat(denied.trace()).last().extracting(ValidationOutcome.Trace::step).isEqualTo(5);
            assertThat(dispatcher.dispatch(step(), scope(), new ExecutionContext(resolver, t -> {})).status()).isEqualTo(ResultStatus.DENIED);
            assertThat(calls).hasValue(1);
        }
        assertThat(registry(modify(manifest, "requiredActorScope", "WRITE"), new ContractVersionPolicy()).view(scope()).capabilities()).isEmpty();
    }

    @Test void eventPermissionRemainsUnmappedAndDeniedUntilDetailedContract() {
        var event = new ManifestLoader().loadClasspath("manifests/tc/index.json").stream()
            .filter(m -> m.capabilityId().equals("tc.event.getRecent")).findFirst().orElseThrow();
        assertThat(event.requiredPermission()).isEmpty();
        assertThat(registry(event, new ContractVersionPolicy()).view(scope()).capabilities()).isEmpty();
        assertThat(PlanValidator.authorized(event, new Resolver().authorize(event, step(), scope()), scope())).isFalse();
    }
}
