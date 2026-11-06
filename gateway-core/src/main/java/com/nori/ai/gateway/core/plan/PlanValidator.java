package com.nori.ai.gateway.core.plan;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.seam.PlanInjectionSeam;
import java.time.Clock;
import java.util.*;

public final class PlanValidator {
    private final CapabilityRegistry registry;
    private final PlanInjectionSeam seam;
    private final Clock clock;
    private final int maxSteps;
    private final long maxCost;
    private final InputValidator inputs = new InputValidator();
    public PlanValidator(CapabilityRegistry registry, PlanInjectionSeam seam, Clock clock, int maxSteps, long maxCost) {
        this.registry = Objects.requireNonNull(registry); this.seam = Objects.requireNonNull(seam); this.clock = Objects.requireNonNull(clock);
        if (maxSteps < 1 || maxCost < 0) throw new IllegalArgumentException("계획 한도가 잘못되었습니다");
        this.maxSteps = maxSteps; this.maxCost = maxCost;
    }
    public ValidationOutcome validate(PlanEnvelope plan, VerifiedSystemScope scope, ResourceResolver resolver) {
        List<ValidationOutcome.Trace> trace = new ArrayList<>();
        if (scope == null || !scope.validAt(clock.instant()) || plan != null && !scope.requestId().equals(plan.requestId()))
            return reject(trace, 1, RejectReason.SYSTEM_SCOPE_MISMATCH, List.of());
        pass(trace, 1);
        List<PlanStep> steps = plan == null ? List.of() : plan.plan();
        Map<PlanStep, CapabilityManifest> tools = new LinkedHashMap<>();
        for (PlanStep step : steps) registry.find(step.capabilityId()).ifPresent(m -> tools.put(step, seam.inspect(m, scope)));
        if (tools.values().stream().anyMatch(m -> m != null && !OwnerBoundary.toolMatches(m, scope))) return reject(trace, 2, RejectReason.OWNER_MISMATCH, List.of());
        pass(trace, 2);
        Set<ResourceRef> refs = new LinkedHashSet<>();
        if (plan != null) refs.addAll(plan.targetResources());
        steps.forEach(s -> refs.addAll(tools.get(s) == null ? s.resources() : ResourceInputs.bound(s, tools.get(s))));
        Map<ResourceRef, ResourceResolver.ResourceState> resources = new LinkedHashMap<>();
        for (ResourceRef ref : refs) resources.put(ref, resolver == null ? null : resolver.resolve(ref, scope));
        // 부재 결과는 ⑧에서 판정하며 확인되지 않은 타 시스템 정보를 조회하지 않습니다.
        if (resources.values().stream().anyMatch(r -> r != null && r.ownerSystem() != null && r.ownerSystem() != scope.boundSystem()))
            return reject(trace, 3, RejectReason.OWNER_MISMATCH, List.of());
        pass(trace, 3);
        for (CapabilityManifest tool : tools.values()) {
            if (tool == null) continue;
            var binding = resolver == null ? null : resolver.source(tool, scope);
            if (binding == null || !binding.allowed() || binding.ownerSystem() != scope.boundSystem()
                || !tool.sourceAllowlistRef().equals(binding.sourceAllowlistRef())) return reject(trace, 4, RejectReason.SOURCE_NOT_ALLOWED, List.of());
        }
        pass(trace, 4);
        for (var entry : tools.entrySet()) {
            if (entry.getValue() == null) continue;
            var authorization = resolver == null ? null : resolver.authorize(entry.getValue(), entry.getKey(), scope);
            if (!authorized(entry.getValue(), authorization, scope)) return reject(trace, 5, RejectReason.DENIED, List.of());
        }
        pass(trace, 5);
        for (CapabilityManifest tool : tools.values()) {
            if (tool == null) continue;
            RejectReason failure = executable(tool);
            if (failure != null) return reject(trace, 6, failure, List.of());
        }
        pass(trace, 6);
        if (plan == null || plan.taskType() == null || plan.conversationId() == null || plan.conversationId().isBlank() || steps.size() > maxSteps || tools.values().stream().anyMatch(Objects::isNull)
            || refs.stream().anyMatch(ref -> ref.type() == null || ref.type().isBlank() || ref.ref() == null || ref.ref().isBlank()))
            return reject(trace, 7, RejectReason.PLAN_INVALID, List.of());
        Set<String> ids = new HashSet<>(), capabilities = new HashSet<>();
        long cost = 0;
        for (PlanStep step : steps) {
            if (step.stepId() == null || step.stepId().isBlank() || step.capabilityId() == null || step.capabilityId().isBlank()
                || !ids.add(step.stepId()) || !capabilities.add(step.capabilityId()) || new HashSet<>(step.dependsOn()).size() != step.dependsOn().size())
                return reject(trace, 7, RejectReason.PLAN_INVALID, List.of());
            CapabilityManifest tool = tools.get(step);
            if (tool != null) {
                cost += tool.cost();
                if (cost > maxCost || ResourceInputs.bound(step, tool).size() > tool.maxResourcesPerCall()) return reject(trace, 7, RejectReason.PLAN_INVALID, List.of());
                var failure = inputs.validate(tool, step.arguments(), resolver == null ? null : resolver.timezone(scope));
                if (failure != null) return reject(trace, 7, failure.reason(), failure.fields());
            }
        }
        Map<String, PlanStep> byId = new HashMap<>(); steps.forEach(s -> byId.put(s.stepId(), s));
        for (PlanStep step : steps) if (cycleOrMissing(step, byId, new HashSet<>(), new HashSet<>())) return reject(trace, 7, RejectReason.PLAN_INVALID, List.of());
        pass(trace, 7);
        for (ResourceRef ref : plan.targetResources())
            if (!tools.isEmpty() && tools.values().stream().noneMatch(m -> m.resourceTypes().contains(ref.type())))
                return reject(trace, 8, RejectReason.RESOURCE_NOT_FOUND, List.of("targetResources"));
        for (var entry : resources.entrySet()) {
            var state = entry.getValue();
            if (state == null || !state.exists() || state.ownerSystem() == null || !Objects.equals(state.type(), entry.getKey().type()))
                return reject(trace, 8, RejectReason.RESOURCE_NOT_FOUND, List.of("targetResources"));
        }
        for (var entry : tools.entrySet()) for (ResourceRef ref : ResourceInputs.bound(entry.getKey(), entry.getValue()))
            if (!entry.getValue().resourceTypes().contains(ref.type())) return reject(trace, 8, RejectReason.RESOURCE_NOT_FOUND, List.of("targetResources"));
        pass(trace, 8);
        if (plan.taskType() == TaskType.UNSUPPORTED_REQUEST || tools.size() != steps.size() || registry.workflow(scope, plan.taskType()).isEmpty()
            || registry.workflow(scope, plan.taskType()).orElseThrow().answerSource() == WorkflowDefinition.AnswerSource.TOOL_RESULTS && steps.isEmpty())
            return reject(trace, 9, RejectReason.UNSUPPORTED, List.of());
        pass(trace, 9);
        return new ValidationOutcome(true, trace, null, ResultStatus.SUCCESS, null, null);
    }
    public RejectReason executable(CapabilityManifest tool) {
        if (tool.availability() != Availability.IMPLEMENTED) return switch (tool.availability()) {
            case PLANNED -> RejectReason.PLANNED; case UNSUPPORTED -> RejectReason.UNSUPPORTED;
            case DISABLED -> RejectReason.DISABLED; default -> throw new IllegalStateException("실행 상태가 잘못되었습니다");
        };
        if (!registry.phaseActive(tool.implementationPhase())) return RejectReason.PHASE_INACTIVE;
        if (tool.sideEffects() != SideEffects.NONE || tool.riskLevel() != RiskLevel.READ || tool.requiresApproval()) return RejectReason.SIDE_EFFECTS;
        return null;
    }
    public static boolean authorized(CapabilityManifest tool, ResourceResolver.CurrentAuthorization current, VerifiedSystemScope scope) {
        return current != null && current.sessionValid() && current.ownerSystem() == scope.boundSystem()
            && tool.sourceAllowlistRef().equals(current.sourceAllowlistRef()) && scope.actorRef().equals(current.actorRef())
            && scope.pdg().equals(current.permissionDigest()) && current.actorScopes().contains(tool.requiredActorScope())
            && tool.requiredPermission().stream().anyMatch(p -> scope.perm().contains(p) && current.permissions().contains(p));
    }
    private boolean cycleOrMissing(PlanStep step, Map<String, PlanStep> all, Set<String> visiting, Set<String> done) {
        if (done.contains(step.stepId())) return false;
        if (!visiting.add(step.stepId())) return true;
        for (String dependency : step.dependsOn()) if (!all.containsKey(dependency) || cycleOrMissing(all.get(dependency), all, visiting, done)) return true;
        visiting.remove(step.stepId()); done.add(step.stepId()); return false;
    }
    private void pass(List<ValidationOutcome.Trace> trace, int step) { trace.add(new ValidationOutcome.Trace(step, true, null)); }
    private ValidationOutcome reject(List<ValidationOutcome.Trace> trace, int step, RejectReason reason, List<String> fields) {
        trace.add(new ValidationOutcome.Trace(step, false, reason));
        ErrorCode error = switch (reason) {
            case SYSTEM_SCOPE_MISMATCH, OWNER_MISMATCH -> ErrorCode.SYSTEM_SCOPE_MISMATCH;
            case RESOURCE_NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case INPUT_BUDGET_EXCEEDED -> ErrorCode.INPUT_BUDGET_EXCEEDED;
            case PLAN_INVALID -> ErrorCode.PLAN_INVALID; default -> null;
        };
        ResultStatus status = error != null ? ResultStatus.FAILED : reason == RejectReason.DENIED || reason == RejectReason.SOURCE_NOT_ALLOWED ? ResultStatus.DENIED : ResultStatus.UNSUPPORTED;
        return new ValidationOutcome(false, trace, reason, status, error, fields.isEmpty() || error == null ? null : new Clarification(error, fields));
    }
}
