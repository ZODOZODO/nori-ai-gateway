package com.nori.ai.gateway.app;

import com.nori.ai.gateway.app.config.GatewayAppModuleConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ModuleConfigurationCompletenessTest {
    @Test void everyMainComponentHasAnExplicitImportPathFromApplication() throws Exception {
        Set<Class<?>> reachable = new HashSet<>();
        visit(AiGatewayApplication.class, reachable);
        assertThat(reachable).contains(GatewayAppModuleConfiguration.class);
        for (var type : ArchitectureRulesTest.mainClasses()) {
            if (!type.getName().startsWith("com.nori.ai.gateway.eval.") && AnnotatedElementUtils.hasAnnotation(type.reflect(), Component.class)) {
                assertThat(reachable).as("명시적 빈 등록: %s", type.getName()).contains(type.reflect());
            }
        }
    }
    private void visit(Class<?> type, Set<Class<?>> reachable) {
        if (!reachable.add(type)) return;
        Import imports = type.getDeclaredAnnotation(Import.class);
        if (imports != null) for (Class<?> imported : imports.value()) visit(imported, reachable);
    }
}
