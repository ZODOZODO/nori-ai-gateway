package com.nori.ai.gateway.integration;

import com.nori.ai.gateway.integration.inference.InferenceEndpointConfig;
import com.nori.ai.gateway.integration.inference.InferenceModelConfiguration;
import com.nori.ai.gateway.integration.inference.InferenceReadinessService;
import com.nori.ai.gateway.integration.inference.OllamaReadinessProbe;
import com.nori.ai.gateway.integration.inference.SpringAiOpenAiInferenceClient;
import com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter;
import com.nori.ai.gateway.integration.tokenizer.PromptTokenCounter;
import com.nori.ai.gateway.integration.tokenizer.QwenChatTemplateRenderer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({HfTokenizerCounter.class, QwenChatTemplateRenderer.class, PromptTokenCounter.class,
    InferenceEndpointConfig.class, InferenceModelConfiguration.class, OllamaReadinessProbe.class, InferenceReadinessService.class,
    SpringAiOpenAiInferenceClient.class})
public class InferenceAdapterModuleConfiguration {}
