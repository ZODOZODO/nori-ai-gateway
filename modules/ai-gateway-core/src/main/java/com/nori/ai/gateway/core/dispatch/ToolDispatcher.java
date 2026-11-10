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
        if(context.boundInputs().isPresent())throw new IllegalArgumentException("기존 실행 경로에 단계 자료를 전달할 수 없습니다");
        return dispatchInternal(step,scope,context,null,null,null,null);
    }
    private ToolResultEnvelope dispatchInternal(PlanStep step, VerifiedSystemScope scope, ExecutionContext context,
            PlanValidator.Prepared prepared, com.nori.ai.gateway.core.authz.StepAttempt attempt,
            com.nori.ai.gateway.core.authz.SourceAuthorizationStep.StepGrant grant, InvocationState invocation) {
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
        if (prepared == null ? !PlanValidator.authorized(tool, context.resolver().authorize(tool, step, scope), scope)
            : attempt.mode()==com.nori.ai.gateway.core.authz.StepAttempt.Mode.DIRECT_SOURCE && !validator.authorizedStep(prepared,attempt,grant,scope))
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
        if (attempt != null) {
            if (grant != null) grant.consume(attempt,com.nori.ai.gateway.core.authz.StepAttempt.Purpose.DISPATCH);
            else attempt.consume(com.nori.ai.gateway.core.authz.StepAttempt.Purpose.DISPATCH);
        }
        if (invocation != null) invocation.enter(tool);
        ToolResultEnvelope raw = executor.execute(step, tool, scope, context);
        if (invocation != null) invocation.returned();
        ToolResultEnvelope result = results.validate(raw, tool);
        boolean validated = result == raw;
        if (result.evidence().stream().anyMatch(e -> !scope.actorRef().equals(e.actorAccessScope())))
            { result = results.failure(tool, ResultStatus.FAILED, ErrorCode.CONTRACT_INVALID, null); validated=false; }
        if(validated)try{com.nori.ai.gateway.core.evidence.EvidenceAssembler.projectKnowledge(tool,result,scope);}
        catch(IllegalArgumentException invalidMetadata){result=results.failure(tool,ResultStatus.FAILED,ErrorCode.CONTRACT_INVALID,null);validated=false;}
        if (invocation != null) invocation.complete(result,validated);
        return result;
    }
    public sealed interface InvocationOutcome permits Returned, Threw {}
    /** latencyMs는 executor.execute 직전부터 반환·throw까지의 단조 경과 시간이다. */
    public record InvocationRecord(String id,String capabilityId,ResultStatus status,Optional<ErrorCode> errorCode,Optional<ToolResultEnvelope> validatedResult,long latencyMs) {
        public InvocationRecord { Objects.requireNonNull(id);Objects.requireNonNull(capabilityId);Objects.requireNonNull(status);Objects.requireNonNull(errorCode);Objects.requireNonNull(validatedResult);if(latencyMs<0)throw new IllegalArgumentException("Tool 호출 경과 시간이 음수입니다"); }
        @Override public String toString() { return "InvocationRecord[보호됨]"; }
    }
    public record Returned(ToolResultEnvelope result,Optional<InvocationRecord> invocation) implements InvocationOutcome {}
    public static final class Threw implements InvocationOutcome {
        private final Throwable failure;
        private final InvocationRecord invocation;
        private Threw(Throwable failure,InvocationRecord invocation) { this.failure=failure;this.invocation=invocation; }
        public InvocationRecord invocation() { return invocation; }
        public void suppress(Throwable secondary) { if (failure!=secondary) failure.addSuppressed(secondary); }
        public void rethrow() { if (failure instanceof RuntimeException runtime) throw runtime; throw (Error)failure; }
        @Override public String toString() { return "Threw[보호됨]"; }
    }
    private static final class InvocationState {
        private String fallback,capability;
        private InvocationRecord record;
        // 시험 고정 Clock은 실제 구간을 재지 못하므로 단조 시계를 씁니다.
        private long enteredAt,returnedAt;
        private boolean returned;
        void enter(CapabilityManifest tool) { fallback=UUID.randomUUID().toString();capability=tool.capabilityId();enteredAt=System.nanoTime(); }
        void returned() { returnedAt=System.nanoTime();returned=true; }
        private long latencyMs() { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis((returned?returnedAt:System.nanoTime())-enteredAt); }
        void complete(ToolResultEnvelope result,boolean validated) {
            record=new InvocationRecord(validated?result.toolExecutionId():fallback,capability,result.status(),Optional.ofNullable(result.errorCode()),validated?Optional.of(result):Optional.empty(),latencyMs());
        }
        InvocationRecord failed() { return new InvocationRecord(fallback,capability,ResultStatus.FAILED,Optional.empty(),Optional.empty(),latencyMs()); }
    }
    public InvocationOutcome dispatchCurrent(PlanValidator.Prepared prepared,com.nori.ai.gateway.core.authz.StepAttempt attempt,
            VerifiedSystemScope scope,com.nori.ai.gateway.core.authz.SourceAuthorizationStep.StepGrant grant,ExecutionContext context) {
        if (!validator.authorizedStep(prepared,attempt,grant,scope) || attempt.mode()!=com.nori.ai.gateway.core.authz.StepAttempt.Mode.DIRECT_SOURCE)
            throw new IllegalArgumentException("현재 단계 인가 결속이 다릅니다");
        return dispatchAttempt(prepared,attempt,scope,grant,context);
    }
    public InvocationOutcome dispatchBusiness(PlanValidator.Prepared prepared,com.nori.ai.gateway.core.authz.StepAttempt attempt,
            VerifiedSystemScope scope,ExecutionContext context) {
        if (!prepared.owns(attempt,scope) || !attempt.fresh() || attempt.mode()!=com.nori.ai.gateway.core.authz.StepAttempt.Mode.BACKEND_READ_REQUIRED)
            throw new IllegalArgumentException("업무 조회 결속이 다릅니다");
        return dispatchAttempt(prepared,attempt,scope,null,context);
    }
    private InvocationOutcome dispatchAttempt(PlanValidator.Prepared prepared,com.nori.ai.gateway.core.authz.StepAttempt attempt,
            VerifiedSystemScope scope,com.nori.ai.gateway.core.authz.SourceAuthorizationStep.StepGrant grant,ExecutionContext context) {
        if (!prepared.owns(attempt,scope)||attempt.purpose()!=com.nori.ai.gateway.core.authz.StepAttempt.Purpose.DISPATCH || context.resolver()!=prepared.resolver()) throw new IllegalArgumentException("실행 목적이 다릅니다");
        PlanStep original=prepared.plan().plan().stream().filter(s->s.stepId().equals(attempt.binding().stepId())).findFirst().orElseThrow();
        // 실행 인자는 시도에 결속된 동일 객체를 Prepared에서 복원합니다.
        PlanStep step=prepared.boundStep(attempt);
        if (!prepared.matches(attempt,step) || !original.capabilityId().equals(step.capabilityId())) throw new IllegalArgumentException("실행 단계가 다릅니다");
        validateBoundInputs(prepared,attempt,scope,context,step);
        InvocationState state=new InvocationState();
        try { return new Returned(dispatchInternal(step,scope,context,prepared,attempt,grant,state),Optional.ofNullable(state.record)); }
        catch (RuntimeException | Error failure) { if (state.fallback==null) throw failure; return new Threw(failure,state.failed()); }
        finally { attempt.close(); }
    }
    private void validateBoundInputs(PlanValidator.Prepared prepared,com.nori.ai.gateway.core.authz.StepAttempt attempt,VerifiedSystemScope scope,ExecutionContext context,PlanStep step) {
        boolean git=prepared.manifest(step.stepId()).toolKind()==ToolKind.GIT;
        if(context.boundInputs().isEmpty()){if(git)throw new IllegalArgumentException("단계 자료 결속이 필요합니다");return;}
        var inputs=context.boundInputs().orElseThrow();if(!git||inputs.attempt()!=attempt)throw new IllegalArgumentException("단계 자료 결속이 다릅니다");
        Set<String> ancestors=new HashSet<>();collectAncestors(step,prepared.plan(),ancestors);
        var actual=new ArrayList<>(inputs.bindings().keySet());
        var ordered=prepared.plan().plan().stream().map(PlanStep::stepId).filter(actual::contains).toList();
        if(!actual.equals(ordered))throw new IllegalArgumentException("단계 자료 순서가 다릅니다");
        inputs.bindings().forEach((key,binding)->{
            if(!ancestors.contains(key)||prepared.manifest(key).toolKind()!=ToolKind.TRACE||binding.ownerSystem()!=scope.boundSystem()||!binding.dataRef().belongsTo(scope.boundSystem()))throw new IllegalArgumentException("단계 자료 소유 범위가 다릅니다");
        });
    }
    private void collectAncestors(PlanStep step,PlanEnvelope plan,Set<String> ancestors) {
        for(String id:step.dependsOn())if(ancestors.add(id))collectAncestors(plan.plan().stream().filter(value->value.stepId().equals(id)).findFirst().orElseThrow(),plan,ancestors);
    }
    private ToolResultEnvelope reject(PlanStep step, VerifiedSystemScope scope, CapabilityManifest registered, ExecutionContext context,
            RejectReason reason, ResultStatus status, ErrorCode error, Clarification clarification) {
        context.trace().accept(new ExecutionContext.DispatchTrace(step.stepId(), reason, error));
        // 실제 실행 대상이 다른 소유자여도 응답은 인증된 원래 범위만 가집니다.
        if (registered.ownerSystem() != scope.boundSystem()) return unsupported(step, scope);
        return results.failure(registered, status, error, clarification);
    }
    private ToolResultEnvelope unsupported(PlanStep step, VerifiedSystemScope scope) {
        return new ToolResultEnvelope(null, step.capabilityId(), null, null, scope.boundSystem(), ResultStatus.UNSUPPORTED,
            null, List.of(), null, null, List.of(), List.of(), null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
