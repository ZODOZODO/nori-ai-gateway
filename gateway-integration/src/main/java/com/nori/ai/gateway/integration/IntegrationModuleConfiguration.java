package com.nori.ai.gateway.integration;

import com.nori.ai.gateway.integration.tokenizer.HfTokenizerCounter;
import com.nori.ai.gateway.integration.tokenizer.PromptTokenCounter;
import com.nori.ai.gateway.integration.tokenizer.QwenChatTemplateRenderer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({HfTokenizerCounter.class, QwenChatTemplateRenderer.class, PromptTokenCounter.class})
public class IntegrationModuleConfiguration {}
