package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import tools.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class WorkflowOptionalStepContractTest {
    @Test void actualWorkflowOrderOptionalConditionsAndDefaultWindowsMatchContract() {
        var workflows=workflows();assertThat(workflows).hasSize(4).extracting(WorkflowDefinition::taskType).doesNotContain(TaskType.UNSUPPORTED_REQUEST);
        var incident=workflows.stream().filter(w -> w.taskType()==TaskType.INCIDENT_ANALYSIS).findFirst().orElseThrow();
        assertThat(incident.steps()).extracting(WorkflowDefinition.Step::stepId).containsExactly("snapshot","logs","trace","dlq","summary","git","knowledge");
        assertThat(incident.steps().stream().filter(WorkflowDefinition.Step::optional)).extracting(WorkflowDefinition.Step::capabilityId).containsExactly("tc.git.searchCode","tc.knowledge.search");
        for (var step:incident.steps()) {
            if (step.optional()) assertThat(step.includeWhen()).containsExactly(new WorkflowDefinition.IncludeWhen(step.capabilityId(),null),new WorkflowDefinition.IncludeWhen(null,"summary.terms"));
            else assertThat(step.includeWhen()).isEmpty();
        }
        for (var w:workflows) {
            if (Set.of(TaskType.LIVE_QUERY,TaskType.INCIDENT_ANALYSIS).contains(w.taskType())) assertThat(w.defaultWindowLength()).isEqualTo(java.time.Duration.ofHours(1));
            else assertThat(w.defaultWindowLength()).isNull();
        }
        var general=workflows.stream().filter(w -> w.taskType()==TaskType.GENERAL_QUERY).findFirst().orElseThrow();
        assertThat(general.steps()).isEmpty();assertThat(general.answerSource()).isEqualTo(WorkflowDefinition.AnswerSource.REGISTRY_VIEW);
    }
    @Test void rejectsUnknownConditionsRequiredStepOptionalAndMalformedDependencies() {
        var loader=new ManifestLoader();
        ObjectNode original=(ObjectNode)SchemaContracts.parse(readIncident()).deepCopy();
        ObjectNode unknown=original.deepCopy();((ObjectNode)unknown.get("steps").get(5).get("includeWhen").get(0)).put("inventedCondition","yes");
        assertThatThrownBy(() -> loader.decodeWorkflow(unknown)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode required=original.deepCopy();((ObjectNode)required.get("steps").get(0)).put("optional",true);
        assertThatThrownBy(() -> loader.decodeWorkflow(required)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode wrong=original.deepCopy();((ObjectNode)wrong.get("steps").get(5).get("includeWhen").get(0)).put("capabilityActive","tc.logs.search");
        assertThatThrownBy(() -> loader.decodeWorkflow(wrong)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode badDependency=original.deepCopy();((ObjectNode)badDependency.get("steps").get(1)).set("dependsOn",SchemaContracts.parse("[\"absent\"]"));
        assertThatThrownBy(() -> loader.decodeWorkflow(badDependency)).isInstanceOf(IllegalArgumentException.class);
        ObjectNode binding=original.deepCopy();((ObjectNode)binding.get("steps").get(5).get("includeWhen").get(1)).put("bindingNonEmpty","arbitrary.text");
        assertThatThrownBy(() -> loader.decodeWorkflow(binding)).isInstanceOf(IllegalArgumentException.class);
    }
    private String readIncident() {
        try{return java.nio.file.Files.readString(ROOT.resolve("gateway-app/src/main/resources/workflows/tc/INCIDENT_ANALYSIS.json"));}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
