package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.*;
import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class DispatcherPreReadBoundaryTest {
    static class CountingResolver extends Resolver {
        int resolves, sources, authorizations, timezones;
        @Override public ResourceState resolve(ResourceRef ref, VerifiedSystemScope scope) { resolves++; return super.resolve(ref, scope); }
        @Override public SourceBinding source(CapabilityManifest manifest, VerifiedSystemScope scope) { sources++; return super.source(manifest, scope); }
        @Override public CurrentAuthorization authorize(CapabilityManifest manifest, PlanStep step, VerifiedSystemScope scope) { authorizations++; return super.authorize(manifest, step, scope); }
        @Override public ZoneId timezone(VerifiedSystemScope scope) { timezones++; return super.timezone(scope); }
    }

    @Test void expiredFutureAndExpiryBoundaryScopesPerformNoExternalOrSeamCalls() {
        var valid = scope();
        for (var bounds : List.of(List.of(NOW.minusSeconds(120), NOW.minusSeconds(60)),
                List.of(NOW.minusSeconds(60), NOW), List.of(NOW.plusSeconds(1), NOW.plusSeconds(60)))) {
            var invalid = new VerifiedSystemScope(valid.boundSystem(), valid.authenticatedServiceRef(), valid.actorRef(),
                valid.sub(), valid.perm(), valid.pdg(), bounds.get(0), bounds.get(1), valid.requestId());
            assertLocalRejection(manifest(), step(), invalid, RejectReason.SYSTEM_SCOPE_MISMATCH);
        }
    }

    @Test void knownOtherOwnerAndUnregisteredVersionPerformNoExternalOrSeamCalls() {
        var other = otherOwner(manifest());
        var otherStep = new PlanStep("s1", other.capabilityId(), List.of(), step().resources(), step().arguments());
        assertLocalRejection(other, otherStep, scope(), RejectReason.OWNER_MISMATCH);
        assertLocalRejection(modify(manifest(), "contractVersion", "2.0"), step(), scope(), null);
        var knowledge=new ManifestLoader().loadClasspath("manifests/tc/index.json").stream().filter(value->value.capabilityId().equals("tc.knowledge.search")).findFirst().orElseThrow();
        var foreign=(tools.jackson.databind.node.ObjectNode)SchemaContracts.JSON.valueToTree(knowledge);foreign.put("ownerSystem","WM").put("capabilityId","wm.knowledge.search").put("toolId","wm.knowledge.search").put("sourceAllowlistRef","sources://WM/knowledge");var foreignKnowledge=new ManifestLoader().decode(foreign);
        assertLocalRejection(foreignKnowledge,new PlanStep("knowledge",foreignKnowledge.capabilityId(),List.of(),List.of(new ResourceRef("EQP","EQP_001")),SchemaContracts.JSON.createObjectNode().put("eqp","EQP_001")),scope(),RejectReason.OWNER_MISMATCH);
    }

    private void assertLocalRejection(CapabilityManifest manifest, PlanStep step, VerifiedSystemScope scope, RejectReason reason) {
        var versions = new ContractVersionPolicy();
        var registry = registry(manifest, versions);
        var resolver = new CountingResolver();
        var seams = new AtomicInteger(); var executions = new AtomicInteger();
        var traces = new ArrayList<ExecutionContext.DispatchTrace>();
        var dispatcher = new ToolDispatcher(registry, validator(registry, new PlanInjectionSeam.NoOp()),
            new ToolResultContractValidator(versions), target -> { seams.incrementAndGet(); return target; }, List.of(executor(executions)), CLOCK);
        var result = dispatcher.dispatch(step, scope, new ExecutionContext(resolver, traces::add));
        assertThat(List.of(resolver.resolves, resolver.sources, resolver.authorizations, resolver.timezones)).containsOnly(0);
        assertThat(seams).hasValue(0); assertThat(executions).hasValue(0);
        if (reason == null) assertThat(traces).isEmpty();
        else assertThat(traces).singleElement().extracting(ExecutionContext.DispatchTrace::reason).isEqualTo(reason);
        assertThat(result.ownerSystem()).isEqualTo(scope.boundSystem());
        assertThat(result.dataRef()).isNull(); assertThat(result.metadata()).isNull();
        assertThat(result.blocks()).isEmpty(); assertThat(result.claims()).isEmpty();
        assertThat(result.evidence()).isEmpty(); assertThat(result.evidenceRefs()).isEmpty();
        assertThat(result.citations()).isEmpty(); assertThat(result.composerInputs()).isEmpty();
    }
}
