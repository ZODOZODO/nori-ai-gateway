package com.nori.ai.gateway.core.registry;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.time.Duration;
import java.util.*;

public record WorkflowDefinition(OwnerSystem ownerSystem, TaskType taskType, List<Step> steps,
        AnswerSource answerSource, Duration defaultWindowLength) {
    public enum AnswerSource { TOOL_RESULTS, REGISTRY_VIEW }
    public record IncludeWhen(String capabilityActive, String bindingNonEmpty) {
        public IncludeWhen {
            if ((capabilityActive == null) == (bindingNonEmpty == null)
                || capabilityActive != null && capabilityActive.isBlank() || bindingNonEmpty != null && bindingNonEmpty.isBlank())
                throw new IllegalArgumentException("지원하지 않는 단계 조건입니다");
        }
    }
    public record Step(String stepId, String capabilityId, String processor, List<String> dependsOn,
            boolean optional, List<IncludeWhen> includeWhen) {
        public Step {
            dependsOn = List.copyOf(dependsOn); includeWhen = List.copyOf(includeWhen);
            if (stepId == null || stepId.isBlank() || (capabilityId == null) == (processor == null)
                || !optional && !includeWhen.isEmpty() || optional && includeWhen.isEmpty())
                throw new IllegalArgumentException("Workflow 단계가 잘못되었습니다");
            if (optional && (includeWhen.size() != 2 || includeWhen.stream().filter(c -> capabilityId != null && capabilityId.equals(c.capabilityActive())).count() != 1
                || includeWhen.stream().filter(c -> c.bindingNonEmpty() != null).count() != 1))
                throw new IllegalArgumentException("선택 단계 조건이 잘못되었습니다");
        }
    }
    public WorkflowDefinition {
        Objects.requireNonNull(ownerSystem); Objects.requireNonNull(taskType); Objects.requireNonNull(answerSource);
        steps = List.copyOf(steps);
        if (taskType == TaskType.UNSUPPORTED_REQUEST || defaultWindowLength != null && (defaultWindowLength.isZero() || defaultWindowLength.isNegative()))
            throw new IllegalArgumentException("지원하지 않는 Workflow입니다");
        if (answerSource == AnswerSource.REGISTRY_VIEW && !steps.isEmpty()) throw new IllegalArgumentException("도움말은 업무 단계를 포함할 수 없습니다");
        Set<String> preceding = new HashSet<>();
        for (Step step : steps) {
            if (!preceding.containsAll(step.dependsOn()) || !preceding.add(step.stepId())) throw new IllegalArgumentException("Workflow 의존성이 잘못되었습니다");
        }
    }
}
