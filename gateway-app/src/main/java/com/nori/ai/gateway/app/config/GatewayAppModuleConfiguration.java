package com.nori.ai.gateway.app.config;

import com.nori.ai.gateway.persistence.PersistenceModuleConfiguration;
import com.nori.ai.gateway.app.security.IngressSecurityConfiguration;
import com.nori.ai.gateway.integration.IntegrationModuleConfiguration;
import com.nori.ai.gateway.tcmock.TcMockModuleConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({CoreBeansConfiguration.class, NormalDatabaseConfiguration.class, PersistenceModuleConfiguration.class,
    IntegrationModuleConfiguration.class, TcMockModuleConfiguration.class, IngressSecurityConfiguration.class})
public class GatewayAppModuleConfiguration {}
