package com.nori.ai.gateway.persistence.audit.auth;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class AuthRejectionPersistenceConfiguration {
    @Bean AuthRejectionAppendRepository authRejectionAppendRepository(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
        return new AuthRejectionAppendRepository(jdbc, transactions, clock);
    }
}
