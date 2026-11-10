package com.nori.ai.gateway.app.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ai.gateway.capacity")
public record CapacityProfile(@NotNull @Positive Integer maxRunningJobs, @NotNull @Positive Integer generationConcurrency,
        @NotNull @Positive Integer queuedDepth, @NotNull Duration queueWaitLimit, @NotNull Duration jobLifetimeLimit) {
    @AssertTrue(message="시간 한도는 양의 정수 초여야 합니다")
    public boolean isWholePositiveSeconds() {
        return valid(queueWaitLimit) && valid(jobLifetimeLimit);
    }
    private boolean valid(Duration value) { return value!=null && value.getSeconds()>0 && value.getNano()==0; }
}
