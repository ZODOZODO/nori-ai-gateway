package com.nori.ai.gateway.core.result;

import com.nori.ai.gateway.core.scope.*;
import java.time.Instant;
public record Evidence(String evidenceId, OwnerSystem ownerSystem, String sourceType, OwnedReference sourceRef,
        Instant eventTime, Instant observedAt, String traceId, OwnedReference deploymentRef,
        String verificationStatus, ActorRef actorAccessScope) {}
