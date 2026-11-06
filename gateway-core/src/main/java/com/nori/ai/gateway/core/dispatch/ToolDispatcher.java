package com.nori.ai.gateway.core.dispatch;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import com.nori.ai.gateway.core.seam.DispatchInjectionSeam;
import java.time.Clock;
import java.util.*;

public final class ToolDispatcher {
    private final CapabilityRegistry registry;
    private final PlanValidator validator;
    private final ToolResultContractValidator results;
    private final DispatchInjectionSeam seam;
    private final Map<ToolKind, ToolExecutor> executors;
    private final Clock clock;
    public ToolDispatcher(CapabilityRegistry registry, PlanValidator validator, ToolResultContractValidator results,
            DispatchInjectionSeam seam, List<ToolExecutor> executors, Clock clock) {
        this.registry = registry; this.validator = validator; this.results = results; this.seam = seam; this.clock = clock;
        Map<ToolKind, ToolExecutor> registered = new EnumMap<>(ToolKind.class);
        for (ToolExecutor executor : executors) if (registered.putIfAbsent(executor.toolKind(), executor) != null)
            throw new IllegalArgumentException("Tool 실행기가 중복되었습니다");
        this.executors = Map.copyOf(registered);
    }
    public ToolResultEnvelope dispatch(PlanStep step, VerifiedSystemScope scope, ExecutionContext context) {
        Objects.requireNonNull(step); Objects.requireNonNull(scope); Objects.requireNonNull(context);
        CapabilityManifest registered = registry.find(step.capabilityId()).orElse(null);
        // 로컬에서 거부할 수 있는 인증·소유권 오류는 Backend 조회와 시험 주입보다 먼저 차단합니다.
        if (!scope.validAt(clock.instant())) {
            if (registered == null) return unsupported(step, scope);
            return reject(step, scope, registered, context, RejectReason.SYSTEM_SCOPE_MISMATCH, ResultStatus.FAILED, ErrorCode.SYSTEM_SCOPE_MISMATCH, null);
        }
        if (registered == null) return unsupported(step, scope);
        if (!OwnerBoundary.toolMatches(registered, scope))
            return reject(step, scope, registered, context, RejectReason.OWNER_MISMATCH, ResultStatus.FAILED, ErrorCode.SYSTEM_SCOPE_MISMATCH, null);
        List<ResourceResolver.ResourceState> states = ResourceInputs.bound(step, registered).stream().map(r -> context.resolver().resolve(r, scope)).toList();
        DispatchTarget target = seam.inspect(new DispatchTarget(registered, states, context.resolver().source(registered, scope)), scope);
        // 시험 주입 이후의 실제 실행 대상을 다시 검사하며 다른 소유 시스템으로 전환하지 않습니다.
        if (target == null || !OwnerBoundary.toolMatches(target.tool(), scope) || !OwnerBoundary.resourcesMatch(target.resources(), scope))
            return reject(step, scope, registered, context, RejectReason.OWNER_MISMATCH, ResultStatus.FAILED, ErrorCode.SYSTEM_SCOPE_MISMATCH, null);
        CapabilityManifest tool = target.tool();
        if (!tool.equals(registered) || target.resources().size() != states.size()) return reject(step, scope, registered, context, RejectReason.PLAN_INVALID, ResultStatus.FAILED, ErrorCode.CONTRACT_INVALID, null);
        var source = target.source();
        if (source == null || !source.allowed() || source.ownerSystem() != scope.boundSystem() || !tool.sourceAllowlistRef().equals(source.sourceAllowlistRef()))
            return reject(step, scope, registered, context, RejectReason.SOURCE_NOT_ALLOWED, ResultStatus.DENIED, null, null);
        if (!PlanValidator.authorized(tool, context.resolver().authorize(tool, step, scope), scope))
            return reject(step, scope, registered, context, RejectReason.DENIED, ResultStatus.DENIED, null, null);
        RejectReason executable = validator.executable(tool);
        if (executable != null) return reject(step, scope, registered, context, executable, ResultStatus.UNSUPPORTED, null, null);
        var invalid = new InputValidator().validate(tool, step.arguments(), context.resolver().timezone(scope));
        if (invalid != null) {
            ErrorCode error = invalid.reason() == RejectReason.INPUT_BUDGET_EXCEEDED ? ErrorCode.INPUT_BUDGET_EXCEEDED : ErrorCode.PLAN_INVALID;
            return reject(step, scope, registered, context, invalid.reason(), ResultStatus.FAILED, error,
                invalid.fields().isEmpty() ? null : new Clarification(error, invalid.fields()));
        }
        if (states.size() > tool.maxResourcesPerCall() || target.resources().stream().anyMatch(r -> r == null || !r.exists() || !tool.resourceTypes().contains(r.type())))
            return reject(step, scope, registered, context, RejectReason.RESOURCE_NOT_FOUND, ResultStatus.FAILED, ErrorCode.RESOURCE_NOT_FOUND,
                new Clarification(ErrorCode.RESOURCE_NOT_FOUND, List.of("targetResources")));
        ToolExecutor executor = executors.get(tool.toolKind());
        if (executor == null) return reject(step, scope, registered, context, RejectReason.UNSUPPORTED, ResultStatus.UNSUPPORTED, null, null);
        ToolResultEnvelope result = results.validate(executor.execute(step, tool, scope, context), tool);
        if (result.evidence().stream().anyMatch(e -> !scope.actorRef().equals(e.actorAccessScope())))
            return results.failure(tool, ResultStatus.FAILED, ErrorCode.CONTRACT_INVALID, null);
        return result;
    }
    private ToolResultEnvelope reject(PlanStep step, VerifiedSystemScope scope, CapabilityManifest registered, ExecutionContext context,
            RejectReason reason, ResultStatus status, ErrorCode error, Clarification clarification) {
        context.trace().accept(new ExecutionContext.DispatchTrace(step.stepId(), reason));
        // 실제 실행 대상이 다른 소유자여도 응답은 인증된 원래 범위만 가집니다.
        if (registered.ownerSystem() != scope.boundSystem()) return unsupported(step, scope);
        return results.failure(registered, status, error, clarification);
    }
    private ToolResultEnvelope unsupported(PlanStep step, VerifiedSystemScope scope) {
        return new ToolResultEnvelope(null, step.capabilityId(), null, null, scope.boundSystem(), ResultStatus.UNSUPPORTED,
            null, List.of(), null, null, List.of(), List.of(), null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
