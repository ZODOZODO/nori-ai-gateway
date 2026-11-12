package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.workflow.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** P20: 분류가 복원한 eqp·q 의 P13 workflow 반영과 Composer 결과의 종결 집계 반영. */
class ClassifiedArgumentsWorkflowTest {
    private final CapabilityRegistry registry = new CapabilityRegistry(new ManifestLoader().loadClasspath("manifests/tc/index.json"), workflows(), Set.of(ImplementationPhase.PHASE_1), new ContractVersionPolicy());
    private final WorkflowPlanBuilder builder = new WorkflowPlanBuilder(registry);
    private static final Map<String,Object> WINDOW = Map.of("from", "2026-11-04T00:00Z", "to", "2026-11-04T01:00Z");
    private JobContext job(Map<String,Object> classification, Map<String,Object> fields) {
        return new JobContext("request", "conversation", scope(), "delegation", NOW.plusSeconds(1000), new CancellationToken(), InferenceMode.ENABLED, classification, new JobRequestInput("message", 1, "질문", fields));
    }
    private WorkflowDefinition live() { return workflows().stream().filter(w -> w.taskType() == TaskType.LIVE_QUERY).findFirst().orElseThrow(); }

    @Test void questionEquipmentBecomesTheValidatedTargetAndTheRestoredQueryIsTheToolArgument() {
        var job = job(Map.of("taskType", "LIVE_QUERY", "eqp", "EQ", "q", "C:\\temp"), Map.of());
        var plan = builder.build(live(), registry.view(job.scope()), job, WINDOW);
        assertThat(plan.toolPlan().targetResources()).containsExactly(new ResourceRef("EQP", "EQ"));
        var snapshot = plan.toolPlan().plan().getFirst();
        assertThat(snapshot.arguments().path("eqp").asString()).isEqualTo("EQ");
        assertThat(snapshot.arguments().path("q").asString()).isEqualTo("C:\\temp");
        assertThat(job.requestInput().selectedResourceRefs()).as("접수 입력 보존").isEmpty();
    }

    @Test void sameSelectedAndQuestionEquipmentIsOneTargetAndADifferentOneIsNotSilentlyChosen() {
        var selected = Map.<String,Object>of("resourceRefs", List.of(Map.of("type", "EQP", "ref", "EQP_001")));
        var same = job(Map.of("taskType", "LIVE_QUERY", "eqp", "EQP_001"), selected);
        assertThat(builder.build(live(), registry.view(same.scope()), same, WINDOW).toolPlan().targetResources()).containsExactly(new ResourceRef("EQP", "EQP_001"));
        var different = job(Map.of("taskType", "LIVE_QUERY", "eqp", "EQP_002"), selected);
        var plan = builder.build(live(), registry.view(different.scope()), different, WINDOW);
        assertThat(plan.toolPlan().targetResources()).containsExactly(new ResourceRef("EQP", "EQP_001"), new ResourceRef("EQP", "EQP_002"));
        assertThat(plan.toolPlan().plan().getFirst().arguments().has("eqp")).as("모호한 설비를 단일 인자로 고르지 않음").isFalse();
        var absent = job(Map.of("taskType", "LIVE_QUERY"), selected);
        assertThat(builder.build(live(), registry.view(absent.scope()), absent, WINDOW).toolPlan().targetResources()).containsExactly(new ResourceRef("EQP", "EQP_001"));
    }

    @Test void composedNarrativeIsCarriedAsAnswerWithoutChangingTheAggregateAndMismatchedCombinationsFail() {
        var assembler = new TerminalOutcomeAssembler();
        var outcome = new WorkflowOutcome(List.of(), List.of(), List.of(), new ResultSourceDeps(List.of(), List.of()), List.of(), List.of(), Optional.empty());
        var spec = assembler.assemble(outcome, ComposerOutcome.COMPOSED, Optional.of("서술[e1]"), InferenceMode.ENABLED);
        assertThat(spec.resultStatus()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(spec.resultPayload()).containsEntry("answer", "서술[e1]");
        assertThat(assembler.assemble(outcome, ComposerOutcome.NOT_INVOKED, InferenceMode.ENABLED).resultPayload()).doesNotContainKey("answer");
        assertThatThrownBy(() -> assembler.assemble(outcome, ComposerOutcome.COMPOSED, InferenceMode.ENABLED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assembler.assemble(outcome, ComposerOutcome.LLM_UNAVAILABLE, Optional.of("x"), InferenceMode.ENABLED)).isInstanceOf(IllegalArgumentException.class);
        var rejected = new WorkflowOutcome(List.of(), List.of(), List.of(), new ResultSourceDeps(List.of(), List.of()), List.of(), List.of(),
            Optional.of(new ValidationOutcome(false, List.of(), RejectReason.UNSUPPORTED, ResultStatus.UNSUPPORTED, null, null)));
        assertThatThrownBy(() -> assembler.assemble(rejected, ComposerOutcome.LLM_UNAVAILABLE, Optional.empty(), InferenceMode.ENABLED)).isInstanceOf(IllegalArgumentException.class);
    }
}
