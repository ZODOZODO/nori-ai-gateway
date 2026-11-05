package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.core.dispatch.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

final class DomainFixtures {
    static final Path ROOT = Path.of(System.getProperty("aigw.rootDir"));
    static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String ID = "tc.eqp.getOperationalSnapshot";
    static VerifiedSystemScope scope() { return scope(OwnerSystem.TC, Set.of("EQP_READ", "DLQ_READ", "AI_LOG_READ", "AI_TRACE_READ", "AI_CODE_READ", "AI_DOC_READ", "MODEL_READ")); }
    static VerifiedSystemScope scope(OwnerSystem owner, Set<String> perms) {
        return new VerifiedSystemScope(owner, "service", new ActorRef("actor"), "actor", perms, "digest", NOW.minusSeconds(60), NOW.plusSeconds(60), "request");
    }
    static CapabilityManifest manifest() { return new ManifestLoader().loadClasspath("manifests/tc/index.json").stream().filter(m -> m.capabilityId().equals(ID)).findFirst().orElseThrow(); }
    static CapabilityManifest modify(CapabilityManifest manifest, String field, Object value) {
        ObjectNode node = SchemaContracts.JSON.valueToTree(manifest);
        node.set(field, SchemaContracts.JSON.valueToTree(value));
        return new ManifestLoader().decode(node);
    }
    static CapabilityManifest otherOwner(CapabilityManifest manifest) {
        ObjectNode node = SchemaContracts.JSON.valueToTree(manifest);
        node.put("ownerSystem", "WM").put("toolId", "wm.eqp.getOperationalSnapshot").put("capabilityId", "wm.eqp.getOperationalSnapshot").put("sourceAllowlistRef", "sources://WM/eqp-backend");
        return new ManifestLoader().decode(node);
    }
    static List<WorkflowDefinition> workflows() {
        return new ManifestLoader().loadWorkflowsClasspath("workflows/tc/index.json");
    }
    static CapabilityRegistry registry(CapabilityManifest m, ContractVersionPolicy versions) {
        return new CapabilityRegistry(List.of(m), workflows(), Set.of(ImplementationPhase.PHASE_1), versions);
    }
    static PlanStep step() { return step(SchemaContracts.parse("{\"eqp\":\"EQP_001\",\"q\":\"WM 으로 보낸 메시지\"}")); }
    static PlanStep step(JsonNode args) { return new PlanStep("s1", ID, List.of(), List.of(new ResourceRef("EQP", "EQP_001")), args); }
    static PlanEnvelope plan(PlanStep... steps) { return new PlanEnvelope("request", "conversation", "WM 으로 보낸 메시지", List.of(), TaskType.LIVE_QUERY, List.of(steps)); }
    static PlanValidator validator(CapabilityRegistry registry, PlanInjectionSeam seam) { return new PlanValidator(registry, seam, CLOCK, 9, 9); }
    static class Resolver implements ResourceResolver {
        OwnerSystem resourceOwner = OwnerSystem.TC;
        boolean exists = true, sourceAllowed = true, sessionValid = true;
        Set<String> permissions = scope().perm(), actorScopes = Set.of("READ"); String pdg = "digest"; String actualType = "EQP";
        public ResourceState resolve(ResourceRef ref, VerifiedSystemScope scope) { return new ResourceState(resourceOwner, actualType, exists); }
        public SourceBinding source(CapabilityManifest m, VerifiedSystemScope scope) { return new SourceBinding(m.ownerSystem(), m.sourceAllowlistRef(), sourceAllowed); }
        public CurrentAuthorization authorize(CapabilityManifest m, PlanStep step, VerifiedSystemScope scope) {
            return new CurrentAuthorization(m.ownerSystem(), m.sourceAllowlistRef(), scope.actorRef(), pdg, sessionValid, permissions, actorScopes);
        }
        public ZoneId timezone(VerifiedSystemScope scope) { return ZoneOffset.UTC; }
    }
    static ToolResultEnvelope result(CapabilityManifest m) {
        OwnedReference data = new OwnedReference(m.ownerSystem(), "result://" + m.ownerSystem() + "/request/data");
        OwnedReference evidence = new OwnedReference(m.ownerSystem(), "evidence://" + m.ownerSystem() + "/request/e1");
        OwnedReference source = new OwnedReference(m.ownerSystem(), m.sourceAllowlistRef());
        return new ToolResultEnvelope("execution", m.capabilityId(), "1.0", m.schemaDigest(), m.ownerSystem(), ResultStatus.SUCCESS,
            data, List.of(evidence), NOW, "rev", List.of(), List.of(), null, "trace", new ToolResultMetadata(source, "LIVE", false, List.of()), null,
            List.of(new ResultBlock("table", data, SchemaContracts.parse("{\"items\":[]}"))),
            List.of(new Claim("claim", Verdict.CONFIRMED, "관측값", List.of(evidence))),
            List.of(new Evidence("e1", m.ownerSystem(), "BUSINESS", source, NOW, NOW, "trace", null, "VERIFIED", new ActorRef("actor"))),
            List.of(evidence), List.of(data));
    }
    static ToolResultEnvelope changeResult(ToolResultEnvelope result, String field, Object value) {
        ObjectNode tree = SchemaContracts.JSON.valueToTree(result); tree.set(field, SchemaContracts.JSON.valueToTree(value));
        return SchemaContracts.JSON.treeToValue(tree, ToolResultEnvelope.class);
    }
    static ToolExecutor executor(AtomicInteger count) {
        return new ToolExecutor() {
            public ToolKind toolKind() { return ToolKind.BUSINESS; }
            public ToolResultEnvelope execute(PlanStep step, CapabilityManifest m, VerifiedSystemScope scope, ExecutionContext context) { count.incrementAndGet(); return result(m); }
        };
    }
}
