package com.nori.ai.gateway.app.config;

import org.springframework.context.annotation.Configuration;

/** core 빈은 각 구현 phase에서 이 등록 정본에만 추가합니다. */
@Configuration(proxyBeanMethods = false)
public class CoreBeansConfiguration {}
