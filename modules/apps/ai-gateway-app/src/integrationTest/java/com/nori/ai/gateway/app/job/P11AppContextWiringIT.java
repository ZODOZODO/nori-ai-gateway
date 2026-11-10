package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.hooks.SeamRegistry;
import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.port.*;
import com.nori.ai.gateway.core.retention.*;
import com.nori.ai.gateway.core.seam.*;
import com.nori.ai.gateway.tcmock.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class P11AppContextWiringIT extends P09QueueSupport {
    @Test void actualMockAndCorePoliciesUseOneGatewayClockAndOneRealSeamOfEachInterface() {
        assertThat(context.getBeansOfType(TcSourceAuthorizationPort.class)).hasSize(1);assertThat(context.getBean(TcSourceAuthorizationPort.class)).isExactlyInstanceOf(MockTcSourceAuthorizationAdapter.class);
        assertThat(context.getBeansOfType(TcBusinessReadPort.class)).hasSize(1);assertThat(context.getBean(TcBusinessReadPort.class)).isExactlyInstanceOf(MockTcBusinessReadAdapter.class);
        for(Class<?> type:List.of(SourcePermissionMap.class,SourceAuthorizationStep.class,RetentionClassifier.class,TrimCompletenessEvaluator.class,DlqCompletenessEvaluator.class))assertThat(context.getBeansOfType(type)).hasSize(1);
        assertThat(context.getBeansOfType(PlanInjectionSeam.class)).hasSize(1);assertThat(context.getBeansOfType(DispatchInjectionSeam.class)).hasSize(1);
        assertThat(context.getBean(PlanInjectionSeam.class)).isSameAs(context.getBean(DispatchInjectionSeam.class)).isSameAs(context.getBean(SeamRegistry.class));
        assertThat(context.getBeansOfType(java.time.Clock.class)).hasSize(1);
        for(Object bean:List.of(context.getBean(MockDelegationRegistry.class),context.getBean(MockTcSourceAuthorizationAdapter.class),context.getBean(MockTcBusinessReadAdapter.class)))assertThat(ReflectionTestUtils.getField(bean,"clock")).isSameAs(clock());
        var call=new TcSourceAuthorizationPort.DelegatedCall("actor",registeredDlg("actor"));
        assertThat(context.getBean(SourceAuthorizationStep.class).authorizeBefore("sources://TC/eqp-backend",scope("actor"),call)).isInstanceOf(SourceAuthorizationStep.Granted.class);
        assertThat(context.getBean(TcBusinessReadPort.class).getEqpSnapshot(call,"EQP-001").data().eqpId()).isEqualTo("EQP-001");
    }
}
