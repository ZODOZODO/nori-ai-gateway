package com.nori.ai.gateway.app;

import com.nori.ai.gateway.app.config.GatewayAppModuleConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(GatewayAppModuleConfiguration.class)
public class AiGatewayApplication {
    public static void main(String[] args) { SpringApplication.run(AiGatewayApplication.class, args); }
}
