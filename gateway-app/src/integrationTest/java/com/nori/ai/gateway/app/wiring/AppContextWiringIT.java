package com.nori.ai.gateway.app.wiring;

import com.nori.ai.gateway.app.StackContextSupport;
import com.nori.ai.gateway.app.config.CoreBeansConfiguration;
import com.nori.ai.gateway.integration.IntegrationModuleConfiguration;
import com.nori.ai.gateway.persistence.PersistenceModuleConfiguration;
import com.nori.ai.gateway.tcmock.TcMockModuleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class AppContextWiringIT extends StackContextSupport {
    @Test void applicationImportsAllModuleConfigurationsAndCurrentCoreRegistration() {
        start();
        assertThat(context.getEnvironment().getActiveProfiles()).contains("verify");
        for (Class<?> type : new Class<?>[]{CoreBeansConfiguration.class, IntegrationModuleConfiguration.class,
                PersistenceModuleConfiguration.class, TcMockModuleConfiguration.class}) {
            assertThat(context.getBeansOfType(type)).hasSize(1);
        }
        // P04에는 core 구현 빈이 없으며 이후 phase가 누적 책임과 실제 호출 검증을 추가합니다.
        assertThat(Arrays.stream(CoreBeansConfiguration.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Bean.class))).isEmpty();
        assertThat(Arrays.stream(context.getBeanDefinitionNames()).map(context::getType).filter(java.util.Objects::nonNull)
            .filter(type -> type.getPackageName().startsWith("com.nori.ai.gateway.core"))).isEmpty();
    }
}
