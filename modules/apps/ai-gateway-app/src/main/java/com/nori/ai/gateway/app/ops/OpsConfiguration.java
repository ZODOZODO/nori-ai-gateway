package com.nori.ai.gateway.app.ops;

import com.nori.ai.gateway.app.config.SourceConnectorConfiguration.LokiProbeFactory;
import com.nori.ai.gateway.app.config.SourceConnectorConfiguration.ProbeWindowInput;
import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.app.job.SlotLedger;
import com.nori.ai.gateway.persistence.maintenance.RetentionMaintenanceRepository;
import com.nori.ai.gateway.persistence.queue.JobTerminalCommitter;
import com.nori.ai.gateway.persistence.queue.QueueJobRepository;
import com.nori.ai.gateway.persistence.retention.SourceRetentionProbeRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 보존 정리·만료 경계·보존 탐침 Job 등록이다. 모두 복구 완료 신호 뒤에만 시작한다. */
@Configuration(proxyBeanMethods = false)
public class OpsConfiguration {
    @Bean RetentionPurgeJob retentionPurgeJob(RetentionMaintenanceRepository retention, Clock clock, Optional<PausePointRegistry> pauses, MeterRegistry meters) {
        return new RetentionPurgeJob(retention, clock, pauses, meters);
    }
    @Bean ExpiryEdgeJobCanceller expiryEdgeJobCanceller(QueueJobRepository jobs, JobTerminalCommitter terminal, SlotLedger slots, Clock clock, Optional<PausePointRegistry> pauses) {
        return new ExpiryEdgeJobCanceller(jobs, terminal, slots, clock, pauses);
    }
    @Bean SourceRetentionProbeScheduler sourceRetentionProbeScheduler(SourceRetentionProbeRepository probes, ObjectProvider<LokiProbeFactory> factories,
            ObjectProvider<ProbeWindowInput> windows, Clock clock, MeterRegistry meters) {
        return new SourceRetentionProbeScheduler(probes, factories, windows, clock, meters);
    }
}
