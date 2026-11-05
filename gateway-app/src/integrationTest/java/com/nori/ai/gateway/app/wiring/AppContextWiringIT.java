package com.nori.ai.gateway.app.wiring;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.app.config.CoreBeansConfiguration;
import com.nori.ai.gateway.integration.IntegrationModuleConfiguration;
import com.nori.ai.gateway.persistence.PersistenceModuleConfiguration;
import com.nori.ai.gateway.tcmock.TcMockModuleConfiguration;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.dispatch.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.core.scope.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AppContextWiringIT extends StackContextSupport {
    @Test void applicationImportsAllModulesAndRealP05CoreBeans() {
        start();
        assertThat(context.getEnvironment().getActiveProfiles()).contains("verify");
        for (Class<?> type : new Class<?>[]{CoreBeansConfiguration.class, IntegrationModuleConfiguration.class,
                PersistenceModuleConfiguration.class, TcMockModuleConfiguration.class, ManifestLoader.class,
                CapabilityRegistry.class, ContractVersionPolicy.class, PlanValidator.class, ToolDispatcher.class,
                ToolResultContractValidator.class, PlanInjectionSeam.class, DispatchInjectionSeam.class})
            assertThat(context.getBeansOfType(type)).hasSize(1);
        assertThat(Arrays.stream(CoreBeansConfiguration.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Bean.class))).hasSize(8);
        assertThat(context.getBean(PlanInjectionSeam.class)).isExactlyInstanceOf(PlanInjectionSeam.NoOp.class);
        assertThat(context.getBean(DispatchInjectionSeam.class)).isExactlyInstanceOf(DispatchInjectionSeam.NoOp.class);
        assertThat(context.getBeansOfType(ToolExecutor.class)).isEmpty();
        var loader=context.getBean(ManifestLoader.class);var manifests=loader.loadClasspath("manifests/tc/index.json");
        assertThat(manifests).hasSize(9);
        var now=Instant.now();var scope=new VerifiedSystemScope(OwnerSystem.TC,"service",new ActorRef("actor"),"actor",Set.of("EQP_READ","DLQ_READ","MODEL_READ","AI_LOG_READ","AI_TRACE_READ","AI_CODE_READ","AI_DOC_READ"),"digest",now.minusSeconds(1),now.plusSeconds(60),"request");
        var registry=context.getBean(CapabilityRegistry.class);var view=registry.view(scope);
        assertThat(view.ownerSystem()).isEqualTo(scope.boundSystem());
        var snapshot=manifests.stream().filter(m -> m.capabilityId().equals("tc.eqp.getOperationalSnapshot")).findFirst().orElseThrow();
        assertThat(view.find(snapshot.capabilityId())).contains(snapshot);
        assertThat(view.capabilities()).hasSize(6).allSatisfy(m -> {
            assertThat(m.ownerSystem()).isEqualTo(scope.boundSystem());assertThat(m.availability()).isEqualTo(Availability.IMPLEMENTED);
            assertThat(context.getBean(ContractVersionPolicy.class).accepts(m.contractVersion(),m.schemaDigest())).isTrue();
        });
        assertThat(view.find("tc.event.getRecent")).isEmpty();
        assertThat(view.find("tc.knowledge.search")).isEmpty();
        assertThat(registry.workflow(scope,TaskType.INCIDENT_ANALYSIS)).isPresent();
    }
}
