package com.nori.ai.gateway.app.config;

import com.nori.ai.gateway.core.job.InferenceMode;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated @ConfigurationProperties("ai.gateway.inference")
public record InferenceModeProperties(@NotNull InferenceMode mode) {}
