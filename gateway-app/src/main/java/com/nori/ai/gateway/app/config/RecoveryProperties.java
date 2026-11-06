package com.nori.ai.gateway.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.gateway.recovery")
public record RecoveryProperties(boolean restoreMode) {}
