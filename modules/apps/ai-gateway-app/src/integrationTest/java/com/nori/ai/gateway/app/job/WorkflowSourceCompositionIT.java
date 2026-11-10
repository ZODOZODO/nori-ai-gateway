package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.integration.source.*;
import com.nori.ai.gateway.integration.loki.*;
import com.nori.ai.gateway.core.authz.SourceAuthorizationStep;
import com.nori.ai.gateway.core.port.SourceRetentionReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class WorkflowSourceCompositionIT extends P09QueueSupport {
    @Test void absentProductionSourceProvidersFailClosedWhileOtherActualBusinessStepsContinue() throws Exception {
        assertThat(context.getBeansOfType(LokiLogConnector.class)).isEmpty();assertThat(context.getBeansOfType(com.nori.ai.gateway.integration.tempo.TempoTraceConnector.class)).isEmpty();
        var accepted=incident("absent-source",Set.of());await(()->state(accepted.jobId()).equals("PARTIAL"));
        var payload=json.readTree(jdbc().sql("SELECT payload::text FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query(String.class).single());
        var stages=java.util.stream.StreamSupport.stream(payload.path("toolSummary").spliterator(),false).filter(stage->Set.of("logs","trace").contains(stage.path("stepId").asString())).toList();assertThat(stages).hasSize(2).allSatisfy(stage->assertThat(stage.path("status").asString()).isEqualTo("SOURCE_UNAVAILABLE"));
        assertThat(calls().count("getEqpSnapshot")).isOne();assertThat(calls().count("listDlq")).isEqualTo(2);assertThat(count("cache_entry")).isZero();
    }
}

class WorkflowDuplicateSourceIT extends P09QueueSupport {
    @TestConfiguration(proxyBeanMethods=false) static class Duplicates {
        @Bean LokiLogConnector duplicateWorkflowLoki(WorkflowSourceFixture.Server server,SourceAuthorizationStep authorization,Clock clock,SourceRetentionReader retention,LokiProperties properties){return new LokiLogConnector(new SourceHttpClient(server.base(),"loki",server.observations),new SourceReadAccess(authorization,clock),retention,properties,clock);}
    }
    @Override protected Class<?>[] extraSources(){return new Class<?>[]{HarnessObservationsEndpoint.class,WorkflowSourceFixture.class,Duplicates.class};}
    @Test void duplicateConcreteProvidersRemainAnExplicitFailureAndNeverChooseOneToSendHttp() throws Exception {
        assertThat(context.getBeansOfType(LokiLogConnector.class)).hasSize(2);var accepted=incident("duplicate-source",Set.of());await(()->state(accepted.jobId()).equals("FAILED"));
        var server=context.getBean(WorkflowSourceFixture.Server.class);assertThat(server.logs).hasValue(0);assertThat(server.traces).hasValue(0);assertThat(count("cache_entry")).isZero();
        assertThat(jdbc().sql("SELECT error_code FROM gw.result WHERE job_id=:job").param("job",accepted.jobId()).query(String.class).single()).isEqualTo("CONTRACT_INVALID");
        String raw=jdbc().sql("SELECT payload::text FROM gw.tool_execution WHERE job_id=:job AND capability_id='tc.logs.search'").param("job",accepted.jobId()).query(String.class).single();assertThat(raw).doesNotContain("127.0.0.1","duplicateWorkflowLoki","NoUniqueBeanDefinitionException");
    }
}
