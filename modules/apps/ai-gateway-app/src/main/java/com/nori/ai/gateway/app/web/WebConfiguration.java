package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.core.expiry.ExpiryCheckpoint;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.persistence.repository.*;
import com.nori.ai.gateway.persistence.result.JobAuditReader;
import java.time.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods=false)
@Import({QueryController.class,ConversationController.class,AnalysisController.class,JobEventStreamController.class,
    EvidenceSourceController.class,BlockExportController.class,AuditQueryController.class,ApiExceptionHandler.class})
public class WebConfiguration {
    @Bean ResultResponseAssembler resultResponseAssembler(JobRepository jobs,ConversationRepository conversations,ResultRepository results,ResultAccessEvaluator evaluator,KnowledgeDocumentStateReader documents,ExpiryCheckpoint expiry) {return new ResultResponseAssembler(jobs,conversations,results,evaluator,documents,expiry);}
    @Bean JobAuditReader jobAuditReader(JdbcClient jdbc,Clock clock) {return new JobAuditReader(jdbc,clock);}
    @Bean EventReplayService eventReplayService(JobEventRepository events) {return new EventReplayService(events);}
    @Bean ExpiryEmitterCloser expiryEmitterCloser(Clock clock) {return new ExpiryEmitterCloser(clock);}
    @Bean SseHub sseHub(EventReplayService replay,ExpiryCheckpoint expiry,ExpiryEmitterCloser closer,Environment env) {return new SseHub(replay,expiry,closer,env.getProperty("ai.gateway.sse.heartbeat",Duration.class,Duration.ofSeconds(15)));}
}
