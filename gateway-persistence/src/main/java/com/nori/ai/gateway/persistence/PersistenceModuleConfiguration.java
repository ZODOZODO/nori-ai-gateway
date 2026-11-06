package com.nori.ai.gateway.persistence;

import com.nori.ai.gateway.persistence.repository.*;
import com.nori.ai.gateway.persistence.maintenance.*;
import com.nori.ai.gateway.persistence.schema.SchemaVersionGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@Import({PersistenceClockConfiguration.class, SchemaVersionGuard.class,
    ConversationRepository.class, MessageRepository.class, JobRepository.class, JobEventRepository.class, ResultRepository.class, EvidenceRepository.class, ToolExecutionRepository.class, CacheRepository.class, AuditRepository.class,
    QueueMaintenanceRepository.class, RetentionMaintenanceRepository.class, RecoveryMaintenanceRepository.class})
public class PersistenceModuleConfiguration {
    @Bean TransactionTemplate persistenceTransactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
}
