package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.port.JobRequestInputReader;
import com.nori.ai.gateway.persistence.repository.JobRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WebAppContextWiringIT extends ApiWebSupport {
    @Test void brokenMessageBindingFailsExplicitlyBeforeWorkflowRatherThanUsingDefaultInput() throws Exception {
        var point=com.nori.ai.gateway.app.hooks.PausePointRegistry.Point.BEFORE_CLASSIFICATION;
        var accepted=admit("actor","broken",Set.of(point));await(()->pauses().reachedCount(point,accepted.jobId())==1);
        jdbc().sql("UPDATE gw.message SET role='assistant' WHERE id=:message").param("message",accepted.messageId()).update();
        pauses().release(point,accepted.jobId());await(()->state(accepted.jobId()).equals("FAILED"));
        assertThat(jdbc().sql("SELECT error_code FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query(String.class).single()).isEqualTo("CONTRACT_INVALID");
        assertThat(observations().workflows).isEmpty();assertThat(observations().authorization).hasValue(1);
        var broken=admit("actor","broken-snapshot",Set.of(point));await(()->pauses().reachedCount(point,broken.jobId())==1);
        jdbc().sql("UPDATE gw.job SET request_input='{\"context\":false}' WHERE id=:job").param("job",broken.jobId()).update();
        pauses().release(point,broken.jobId());await(()->state(broken.jobId()).equals("FAILED"));
        assertThat(jdbc().sql("SELECT error_code FROM gw.result WHERE job_id=:job").param("job",broken.jobId()).query(String.class).single()).isEqualTo("CONTRACT_INVALID");
        assertThat(observations().workflows).isEmpty();assertThat(observations().authorization).hasValue(2);
    }
    @Test void realSingleReadersAndEvaluatorAreWiredThroughApiSseAndWorker() throws Exception {
        assertThat(context.getBeansOfType(JobRequestInputReader.class)).hasSize(1);assertThat(context.getBean(JobRequestInputReader.class)).isSameAs(context.getBean(JobRepository.class));
        assertThat(context.getBeansOfType(KnowledgeDocumentStateReader.class)).hasSize(1);assertThat(context.getBean(KnowledgeDocumentStateReader.class)).isInstanceOf(FailClosedKnowledgeDocumentStateReader.class);
        assertThat(context.getBeansOfType(EvidenceSourceReader.class)).hasSize(1);assertThat(context.getBean(EvidenceSourceReader.class)).isInstanceOf(FailClosedEvidenceSourceReader.class);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(ResultResponseAssembler.class),"evaluator")).isSameAs(context.getBean(ResultAccessEvaluator.class));
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(SseHub.class),"expiry")).isSameAs(context.getBean(com.nori.ai.gateway.core.expiry.ExpiryCheckpoint.class));
        var accepted=admit("actor","wiring",Set.of());await(()->state(accepted.jobId()).equals("SUCCEEDED"));
        var workflow=observations().workflows.getFirst();assertThat(workflow.requestInput()).isEqualTo(context.getBean(JobRequestInputReader.class).findRequestInput(scope("actor"),accepted.jobId()).orElseThrow());
        assertThat(workflow.requestInput().messageId()).isEqualTo(accepted.messageId());assertThat(workflow.requestInput().maskedQuestion()).isEqualTo("질문");
    }
}
