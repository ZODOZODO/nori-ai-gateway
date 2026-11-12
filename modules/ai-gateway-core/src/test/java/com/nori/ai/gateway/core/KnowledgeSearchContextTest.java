package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.dispatch.ExecutionContext;
import com.nori.ai.gateway.core.knowledge.KnowledgeSearchContext;
import com.nori.ai.gateway.core.plan.ResourceRef;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static com.nori.ai.gateway.core.workflow.EvidenceNeed.*;
import static org.assertj.core.api.Assertions.*;

/** Knowledge 서버 입력의 정확 q/의미 입력 분리·Job 결속·값 보호(SPEC §28.4, Plan §16.3 R1·R2)와 ExecutionContext 호환 생성자. */
class KnowledgeSearchContextTest {
    private static final String EXACT = "C:\\log\\\"TIMEOUT\" ";
    private final KnowledgeSearchContext context = new KnowledgeSearchContext("job-1", "knowledge", OwnerSystem.TC, "EQP_001 이 왜 시작을 안 해?", Optional.of(EXACT),
        List.of(new ResourceRef("EQP", "EQP_001")), List.of(H, N));

    @Test void exactQueryAndSemanticInputAreSeparateFieldsAndTheExactValueIsKeptByteForByte() {
        assertThat(context.exactQuery()).contains(EXACT);
        assertThat(context.semanticInput()).startsWith("EQP_001 이 왜 시작을 안 해?").contains("EQP EQP_001", "[H, N]").doesNotContain("TIMEOUT");
        var unquoted = new KnowledgeSearchContext("job-1", "knowledge", OwnerSystem.TC, "시작이 안 돼", Optional.empty(), List.of(), List.of());
        assertThat(unquoted.exactQuery()).as("무인용 질문은 정확 q 없이 의미 입력만").isEmpty();
        assertThat(unquoted.semanticInput()).isEqualTo("시작이 안 돼");
    }

    @Test void bindingIsRecheckedAgainstJobStepAndOwnerAndAnotherJobInputIsRejected() {
        assertThatCode(() -> context.requireBoundTo("job-1", "knowledge", OwnerSystem.TC)).doesNotThrowAnyException();
        assertThatThrownBy(() -> context.requireBoundTo("job-2", "knowledge", OwnerSystem.TC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.requireBoundTo("job-1", "git", OwnerSystem.TC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.requireBoundTo("job-1", "knowledge", OwnerSystem.WM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void blankOrNullInputsAreRejectedAndToStringNeverShowsValues() {
        assertThatThrownBy(() -> new KnowledgeSearchContext("job-1", "knowledge", OwnerSystem.TC, " ", Optional.empty(), List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeSearchContext("", "knowledge", OwnerSystem.TC, "q", Optional.empty(), List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeSearchContext("job-1", "knowledge", OwnerSystem.TC, "q", null, List.of(), List.of())).isInstanceOf(NullPointerException.class);
        assertThat(context.toString()).isEqualTo("KnowledgeSearchContext[보호됨]").doesNotContain("EQP_001", "TIMEOUT");
    }

    @Test void legacyExecutionContextConstructorsDelegateWithEmptyKnowledgeInput() {
        var resolver = new DomainFixtures.Resolver();
        assertThat(new ExecutionContext(resolver, trace -> {}).knowledge()).isEmpty();
        assertThat(new ExecutionContext(resolver, trace -> {}, Optional.empty()).knowledge()).isEmpty();
        assertThat(new ExecutionContext(resolver, trace -> {}, Optional.empty(), Optional.of(context)).knowledge()).containsSame(context);
        assertThatThrownBy(() -> new ExecutionContext(resolver, trace -> {}, Optional.empty(), null)).isInstanceOf(NullPointerException.class);
    }
}
